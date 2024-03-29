/*
* Copyright 2015 herd contributors
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.spark.sql.herd

import java.net.URI
import java.util

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.kms.AWSKMSClient
import com.jessecoyle.{CredStashBouncyCastleCrypto, JCredStash}
import org.apache.commons.text.StringEscapeUtils
import org.apache.hadoop.fs.Path
import org.apache.spark.SparkException
import org.apache.spark.annotation.DeveloperApi
import org.apache.spark.internal.Logging
import org.apache.spark.sql._
import org.apache.spark.sql.catalyst.parser.{CatalystSqlParser, ParseException}
import org.apache.spark.sql.execution.QueryExecution
import org.apache.spark.sql.execution.datasources._
import org.apache.spark.sql.internal.SQLConf
import org.apache.spark.sql.sources._
import org.apache.spark.sql.types._
import org.apache.spark.sql.util.QueryExecutionListener
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}
import scala.util.matching.Regex

import org.finra.herd.sdk.invoker.{ApiClient, ApiException}
import org.finra.herd.sdk.model._


/** A custom data source that integrates with Herd for metadata management
    *
    * It delegates to the built-in Spark file formats (PARQUET, CSV, ORC) for the actual reading and writing of data.
    *
    * ==Options==
    * `url` - The URL of the Herd service to connect to
    *
    * `namespace` - The namespace in Herd to query
    *
    * `businessObjectName` - The business object which to query
    *
    * `businessObjectFormatUsage` - The business object format usage to query (optional, default `PRC`)
    *
    * `businessObjectFormatFileType` - A list of preferred file types to use (optional, default `PARQUET, ORC, BZ`)
    *
    * `username` - The username to authenticate with (optional)
    *
    * `password` - The password to authenticate with (optional)
    *
    * `credName` - The CredStash credential name to use for authentication (optional)
    *
    * `credAGS` - The CredStash AGS to use (optional, default `DATABRICKS`)
    *
    * `credSDLC` - The CredStash SDLC to use (optional, default `prody`)
    *
    * If `username` and `password` are defined those will be used for authentication. If `credName` is specified
    * then that will be used. If no credentials are specified, then anonymous authentication will be used.
    *
    * ==Example==
    * {{{
    * val df = spark.read.format("herd")
    *   .option("url", "http://localhost:8998/herd-app/rest")
    *   .option("namespace", "mynamespace")
    *   .option("businessObjectName", "myobject")
    *   .load()
    * }}}
    *
  */
class DefaultSource(apiClientFactory: (String, Option[String], Option[String], Option[String]) => HerdApi)
    extends RelationProvider
    with CreatableRelationProvider
    with DataSourceRegister
    with Logging {

  def this() = this(DefaultSource.defaultApiClientFactory)

  def getApiConfig(parameters: Map[String, String], sparkSession: SparkSession): (String, Option[String], Option[String], Option[String]) = {
    val url = parameters.get("url")
      .orElse(sparkSession.conf.getOption("spark.herd.url"))
      .getOrElse(sys.error("Must specify either `url` option or `spark.herd.url` in config"))
    val storagePathPrefix = parameters.get("storagePathPrefix")
      .orElse(sparkSession.conf.getOption("spark.herd.default.storagePathPrefix"))
      .getOrElse("s3a")
    val user = parameters.get("username")
      .orElse(sparkSession.conf.getOption("spark.herd.username"))
    val pwd = parameters.get("password")
      .orElse(sparkSession.conf.getOption("spark.herd.password"))
    val accessTokenUrl = parameters.get("accessTokenUrl")
      .orElse(sparkSession.conf.getOption("spark.herd.accessTokenUrl"))
    if (user.isDefined && pwd.isDefined) {
      (url, user, pwd, accessTokenUrl)
    } else {
      val credName = parameters.get("credName")
        .orElse(sparkSession.conf.getOption("spark.herd.credential.name"))
      val credAGS = parameters.get("credAGS")
        .orElse(sparkSession.conf.getOption("spark.herd.credential.ags"))
        .getOrElse("DATABRICKS")
      val credSDLC = parameters.get("credSDLC")
        .orElse(sparkSession.conf.getOption("spark.herd.credential.sdlc"))
        .getOrElse("prody")
      val credComponent = parameters.get("credComponent")
        .orElse(sparkSession.conf.getOption("spark.herd.credential.component"))
        .getOrElse(null)

      var context = new util.HashMap[String, String] {
        put("AGS", credAGS)
        put("SDLC", credSDLC)
        if (credComponent != null) {
          put("Component", credComponent)
        }
      }

      val proxyHost = sparkSession.conf.getOption("spark.herd.proxy.host")
      val proxyPort = sparkSession.conf.getOption("spark.herd.proxy.port")

      val clientConf = new ClientConfiguration
      if (proxyHost.isDefined && proxyPort.isDefined) {
        clientConf.setProxyHost(proxyHost.get)
        clientConf.setProxyPort(Integer.parseInt(proxyPort.get))
      }

      var prefixedCredName : String = null
      if (context.containsKey("Component")) {
        prefixedCredName = context.get("AGS") + "." + context.get("Component") + "." + context.get("SDLC") + "." + credName.get
      } else {
        prefixedCredName = context.get("AGS") + "." + context.get("SDLC") + "." + credName.get
      }

      val provider = new DefaultAWSCredentialsProviderChain
      val ddb: AmazonDynamoDBClient = new AmazonDynamoDBClient(provider, clientConf).withRegion(Regions.US_EAST_1)
      val kms: AWSKMSClient = new AWSKMSClient(provider, clientConf).withRegion(Regions.US_EAST_1)
      val credPwd = new JCredStash("credential-store", ddb, kms, new CredStashBouncyCastleCrypto).getSecret(prefixedCredName, context)

      (url, credName, Option(credPwd), accessTokenUrl)
    }
  }

  override def shortName(): String = "herd"

  private def registerIfRequired(api: HerdApi, params: HerdOptions): Unit = {
    Try(api.getBusinessObjectByName(params.namespace, params.businessObjectName)) match {
      case Success(_) => Unit
      case Failure(ex: ApiException) if ex.getCode == 404 =>
        log.info(s"Business object not found, registering it")
        api.registerBusinessObject(params.namespace, params.businessObjectName, params.dataProvider)
      case Failure(ex) => throw ex
    }
  }

  private def registerNewSchema(api: HerdApi, schema: StructType, params: HerdOptions): (String, String, Int) = {
    val herdSchema = makeHerdSchema(schema, params)

    val fileType = params.fileTypes.head

    if (Set("bz", "gz", "txt", "csv").contains(fileType.toLowerCase)) {
      herdSchema.setDelimiter(params.delimiter)
      herdSchema.setEscapeCharacter(params.escapeCode)
      herdSchema.setNullValue(params.nullValue)
    } else {
      herdSchema.setDelimiter("")
      herdSchema.setEscapeCharacter("")
      herdSchema.setNullValue("")
    }

    log.info(s"Registering a new schema with ${params.formatUsage} $fileType ${params.partitionKey} $herdSchema")

    val newVersion = api.registerBusinessObjectFormat(
      params.namespace,
      params.businessObjectName,
      params.formatUsage,
      fileType,
      params.partitionKey,
      Some(herdSchema)
    )

    log.info(s"New schema version registered: $newVersion")

    (params.formatUsage, fileType, newVersion)
  }

  private def getFormatUsageAndFileType(
    api: HerdApi,
    params: HerdOptions,
    data: Option[DataFrame] = None,
    registerIfNotPresent: Boolean = false,
    actualParams: Map[String, String] = null
  ): (String, String, Int) = {

    log.info("checking if herd format exist")
    val formats = api.getBusinessObjectFormats(params.namespace, params.businessObjectName)

    val preferredTypes = params.fileTypes.map(_.toLowerCase).zipWithIndex.toMap

    val result = formats.getBusinessObjectFormatKeys.asScala
      .filter(i => i.getBusinessObjectFormatUsage.equalsIgnoreCase(params.formatUsage) &&
        preferredTypes.contains(i.getBusinessObjectFormatFileType.toLowerCase))
      .sortBy(f => preferredTypes(f.getBusinessObjectFormatFileType.toLowerCase))
      .headOption
      .map(i => (i.getBusinessObjectFormatUsage,
        i.getBusinessObjectFormatFileType,
        i.getBusinessObjectFormatVersion.intValue()))

    // if format doesn't exist and dataFrame exists, create new format
    if (result.isEmpty && registerIfNotPresent && data.nonEmpty) {
      log.info("bformat doesn't exist and dataFrame exists, create new format")
      registerNewSchema(api, data.get.schema, params)
    } else if (result.nonEmpty) {
      val fmt = api.getBusinessObjectFormat(
        params.namespace,
        params.businessObjectName,
        result.get._1,
        result.get._2,
        result.get._3
      )
      // if format exist but doesn't match existing data frame, create new format
      if (registerIfNotPresent && data.nonEmpty && !doesDataFrameMatchBformat(fmt, data.get, actualParams)) {
        log.info("herd format exist but doesn't match provided data frame, so creating new herd format")
        registerNewSchema(api, data.get.schema, params)
        // if format exist and matches existing data frame(save dataFrame), or data frame is none(load/get dataFrame, return format
      } else if (data.isEmpty || (data.nonEmpty && doesDataFrameMatchBformat(fmt, data.get, actualParams))) {
        log.info("herd format exist and match provided data frame, return existing herd format")
        result.get
      }
      else {
        sys.error("provided dataframe doesn't match herd schema but failed to create new format," +
          "either provided dataframe is Invalid, or registerNewSchema is not enabled")
      }
    } else {
      sys.error("no suitable format usage and file type found")
    }
  }

  private def doesDataFrameMatchBformat(fmt: BusinessObjectFormat, data: DataFrame, parameters: Map[String, String]): Boolean = {
    val partitionColumns = fmt.getSchema.getPartitions.asScala.map(_.getName)

    val targetSchema = makeSparkSchema(fmt.getSchema.getColumns.asScala)

    val rows = data.drop(partitionColumns: _*)
    rows.schema.sql.equalsIgnoreCase(targetSchema.sql) && compareParams(fmt, parameters)
  }

  // compares delimiter, escapeChar and nullValue
  private def compareParams(fmt: BusinessObjectFormat, parameters: Map[String, String]): Boolean = {

    val providedDelimiter = parameters.get("delimiter")
    val providedEscapeChar = parameters.get("escape")
    val providedNullValue = parameters.get("nullValue")

    val formatNullValue = StringEscapeUtils unescapeJava fmt.getSchema.getNullValue
    val formatEscapeChar = StringEscapeUtils unescapeJava fmt.getSchema.getEscapeCharacter
    val formatDelimiter = StringEscapeUtils unescapeJava fmt.getSchema.getDelimiter

    if (providedDelimiter.isDefined) {
      if (formatDelimiter != providedDelimiter.get) {
        log.error(prepareSchemaOptionUnmatchMsg("delimiter", providedDelimiter.get, formatDelimiter))
        throw new IllegalArgumentException(prepareSchemaOptionUnmatchMsg("delimiter", providedDelimiter.get, formatDelimiter))
      }
    }

    if (providedEscapeChar.isDefined) {
      if (formatEscapeChar != providedEscapeChar.get) {
        log.error(prepareSchemaOptionUnmatchMsg("escapeChar", providedEscapeChar.get, formatEscapeChar))
        throw new IllegalArgumentException(prepareSchemaOptionUnmatchMsg("escapeChar", providedEscapeChar.get, formatEscapeChar))
      }
    }

    if (providedNullValue.isDefined) {
      if (formatNullValue != providedNullValue.get) {
        log.error(prepareSchemaOptionUnmatchMsg("nullValue", providedNullValue.get, formatNullValue))
        throw new IllegalArgumentException(prepareSchemaOptionUnmatchMsg("nullValue", providedNullValue.get, formatNullValue))
      }
    }

    true
  }

  private def prepareSchemaOptionUnmatchMsg(providedItemName: String, providedItem: String, expectedItem: String) = {
    s"Provided $providedItemName: $providedItem does not match existing $providedItem: $expectedItem on format registered with Herd. Ambiguous state reached."
  }

  private def getPathPrefix(storage: Storage, storagePathPrefix: String): String = {
    storage.getStoragePlatformName match {
      case "S3" =>
        val bucketName = storage.getAttributes.asScala
          .find(_.getName.equalsIgnoreCase("bucket.name"))
          .map(_.getValue)
          .getOrElse(sys.error("Storage must have a 'bucket.name' attribute"))

        storagePathPrefix +
          (if (storagePathPrefix.endsWith("/")) "" else "/") + bucketName
      case "FILE" => storagePathPrefix
      case _ => sys.error(s"Unsupported storage platform ${storage.getStoragePlatformName}")
    }
  }

  private def getFilePaths(storageUnit: StorageUnit): Seq[String] = {
    val paths = Option(storageUnit.getStorageFiles).map(i => i.asScala.map(_.getFilePath))
      .orElse(Some(Seq(storageUnit.getStorageDirectory.getDirectoryPath)))
      .getOrElse(sys.error("No storage paths could be found!"))

    paths
  }

  override def createRelation(sqlContext: SQLContext, parameters: Map[String, String]): BaseRelation = {
    val sparkSession = sqlContext.sparkSession

    val currentRules = sparkSession.sessionState.experimentalMethods.extraOptimizations
    if (currentRules.find(_.isInstanceOf[PrunedFilteredScan]).isEmpty) {
      sparkSession.sessionState.experimentalMethods.extraOptimizations = currentRules :+ PruneHerdPartitions
    }

    val params = HerdOptions(parameters)(sparkSession)

    val (url, username, password, accessTokenUrl) = getApiConfig(parameters, sparkSession)

    val api = apiClientFactory(url, username, password, accessTokenUrl)

    val (formatUsage, formatFileType, formatVersion) = getFormatUsageAndFileType(api, params)

    log.info(s"Querying ${params.namespace} ${params.businessObjectName} ${formatUsage} ${formatFileType} ${formatVersion}")

    val fmt = api.getBusinessObjectFormat(
      params.namespace,
      params.businessObjectName,
      formatUsage,
      formatFileType,
      formatVersion
    )

    if (fmt.getSchema == null) {
      throw new Exception("Schema not found")
    }

    log.info(s"Using PartitionKey ${fmt.getPartitionKey}, PartitionKeyGroup ${fmt.getSchema.getPartitionKeyGroup}")
    // all data partitions from DM availability call
    val allAvailableDataPartitions = api.getBusinessObjectPartitions(
      params.namespace,
      params.businessObjectName,
      formatUsage,
      formatFileType,
      formatVersion,
      params.partitionFilter
    )

    val partitionList = allAvailableDataPartitions.map(_._2)
    val partitionsFromDDL = api
      .getBusinessObjectDataPartitions(params.namespace, params.businessObjectName, formatUsage, formatFileType, null, fmt.getPartitionKey,
        partitionList, null)
    val versionPattern = new Regex("/data-v([0-9]+)/")
    val allData = Seq.empty ++ partitionsFromDDL.getPartitions.asScala.map {
      partition =>
      (
        new Integer(formatVersion),

        if (partition.getPartitionColumns.get(0).getPartitionColumnValue == null) {
          "none" } else {
          partition.getPartitionColumns.get(0).getPartitionColumnValue
        },

        partition.getPartitionColumns.asScala.drop(1).map(_.getPartitionColumnValue),

        versionPattern.findFirstMatchIn(partition.getPartitionLocation)
        match {
          case Some(i) => new Integer(i.group(1).toInt)
          case None => new Integer(0)
        },

        partition.getPartitionLocation
      )
    }

    log.info(s"Got ${allData.size} results")

    val (dataSourceFormat, options) = toSparkDataSourceAndOptions(formatFileType, fmt.getSchema, parameters)

    log.info(s"Using Spark DataSource $dataSourceFormat")

    val partitionSchema = Option(fmt.getSchema.getPartitions).map(p => makeSparkSchema(p.asScala.toSeq))

    log.info(s"Object partition schema $partitionSchema")

    val herdSchema = makeSparkSchema(fmt.getSchema.getColumns.asScala)

    log.info(s"Object schema $herdSchema")

    val localApiClientFactory = apiClientFactory

    val fileIndex = new HerdFileIndex(
      sparkSession,
      () => localApiClientFactory(url, username, password, accessTokenUrl),
      allData,
      params.namespace,
      params.businessObjectName,
      formatUsage,
      formatFileType,
      fmt.getPartitionKey,
      partitionSchema.getOrElse(new StructType),
      params.storagePathPrefix
    )

    val useHerdOrcFormat = sparkSession.version < "2.3.0"
    val sparkV3 = sparkSession.version >= "3.0.0"

    val correctedDataSourceFormat = dataSourceFormat match {
      case "orc" if useHerdOrcFormat => "org.apache.spark.sql.hive.orc.HerdOrcFileFormat"
      case "orc" if sparkV3 && sparkSession.conf.get("spark.sql.orc.impl") == "native" => "org.apache.spark.sql.execution.datasources.orc"
      case "orc" if sparkV3 => "org.apache.spark.sql.hive.orc"
      case "csv" if sparkV3 => "com.databricks.spark.csv"
      case "parquet" if sparkV3 => "org.apache.spark.sql.parquet"
      case _ => dataSourceFormat
    }

    val fileFormat: FileFormat = {
      val dataSourceClazz = Try(classOf[DataSource].getMethod("lookupDataSource", classOf[String])) match {
        case Success(method) => method.invoke(null, correctedDataSourceFormat).asInstanceOf[Class[FileFormat]]
        case Failure(_) => classOf[DataSource]
          .getMethod("lookupDataSource", classOf[String], classOf[SQLConf])
          .invoke(null, correctedDataSourceFormat, sparkSession.sessionState.conf)
          .asInstanceOf[Class[FileFormat]]
      }

      dataSourceClazz
        .newInstance()
    }

    val baseRelation = new HadoopFsRelation(
      fileIndex,
      partitionSchema.getOrElse(new StructType()),
      herdSchema.asNullable,
      None,
      fileFormat,
      options
    )(sparkSession)

    baseRelation
  }

  override def createRelation(sqlContext: SQLContext, mode: SaveMode, parameters: Map[String, String], data: DataFrame): BaseRelation = {
    val sparkSession = sqlContext.sparkSession

    val params = HerdOptions(parameters)(sparkSession)

    val (url, username, password, accessTokenUrl) = getApiConfig(parameters, sparkSession)

    val api = apiClientFactory(url, username, password, accessTokenUrl)

    registerIfRequired(api, params)

    val (formatUsage, formatFileType, formatVersion) = getFormatUsageAndFileType(
      api,
      params,
      Some(data),
      params.registerNewFormat,
      parameters
    )

    log.info(s"Writing to ${params.namespace} ${params.businessObjectName} ${formatUsage} ${formatFileType} ${formatVersion}")

    val fmt = api.getBusinessObjectFormat(
      params.namespace,
      params.businessObjectName,
      formatUsage,
      formatFileType,
      formatVersion
    )

    val partitionColumns = fmt.getSchema.getPartitions.asScala.map(_.getName)
    log.info(s"Using PartitionKey ${fmt.getPartitionKey}, " +
      s"PartitionKeyGroup ${fmt.getSchema.getPartitionKeyGroup}, " +
      s"Partitions ${partitionColumns.mkString(",")}")

    val (dataSourceFormat, options) = toSparkDataSourceAndOptions(formatFileType, fmt.getSchema, parameters)

    // prevent password from appearing in the logs
    val logOptions = options.filterNot(_._1.equalsIgnoreCase("password"))

    log.info(s"Using $dataSourceFormat with options[${logOptions.mkString(",")}]")

    val partitionValue = params.partitionValue.getOrElse("none")

    log.info(s"Preregistering partition ${partitionValue} in order to get path prefix and data version")

    val (dataVersion, storageUnits) = api.registerBusinessObjectData(
      params.namespace,
      params.businessObjectName,
      formatUsage,
      formatFileType,
      formatVersion,
      fmt.getPartitionKey,
      partitionValue,
      params.subPartitions,
      ObjectStatus.UPLOADING,
      params.storageName
    )

    val path = storageUnits.flatMap(getFilePaths).head

    val storage = storageUnits.head.getStorage

    val pathPrefix = getPathPrefix(storage, params.storagePathPrefix)

    val baseDir = storage.getAttributes.asScala
      .find(_.getName.equalsIgnoreCase("spark.base.path"))
      .map(_.getValue + "/")
      .getOrElse("")

    val basePath = pathPrefix + "/" + baseDir + "/" + path

    var numberOutputRows = 0L

    val queryExecutionListener = new QueryExecutionListener() {
      @DeveloperApi
      override def onSuccess(funcName: String, qe: QueryExecution, durationNs: Long): Unit = {
        numberOutputRows = qe.executedPlan.metrics("numOutputRows").value
      }

      @DeveloperApi
      override def onFailure(funcName: String, qe: QueryExecution, exception: Exception): Unit = Unit
    }

    try {
      log.info(s"Writing to $basePath")

      val partitionKey = fmt.getPartitionKey

      sparkSession.sessionState.listenerManager.register(queryExecutionListener)

      val partitionColumns = fmt.getSchema.getPartitions.asScala.map(_.getName)
      val targetSchema = makeSparkSchema(fmt.getSchema.getColumns.asScala)

      val rows = data.drop(partitionColumns : _*)
      rows.write.format(dataSourceFormat).options(options).save(basePath)

      // this is to remove issues with schemes
      val basePathURI = new URI(new URI(pathPrefix).getPath)

      val index = new InMemoryFileIndex(sparkSession, Seq(new Path(basePath)), Map.empty, None)
      val storageFiles = index.allFiles().asInstanceOf[Seq[Any]].map { status =>
        val path = FileStatusShim.getPath(status)
        val size = FileStatusShim.getLen(status)

        (basePathURI.relativize(new URI(path.toUri.getPath)).getPath, size)
      }

      log.info(s"Registering partition $partitionValue" +
        s" with files ${storageFiles.map(_._1).mkString(",")}")

      api.setStorageFiles(params.namespace,
        params.businessObjectName,
        formatUsage,
        formatFileType,
        formatVersion,
        partitionKey,
        partitionValue,
        params.subPartitions,
        dataVersion,
        params.storageName,
        storageFiles)

      api.updateBusinessObjectData(
        params.namespace,
        params.businessObjectName,
        formatUsage,
        formatFileType,
        formatVersion,
        fmt.getPartitionKey,
        partitionValue,
        params.subPartitions,
        dataVersion,
        ObjectStatus.VALID
      )

      log.trace(s"OUTPUT\t${params.businessObjectName}\t${params.namespace}\t" +
        s"${formatUsage}\t${formatFileType}\t" +
        s"${formatVersion}\t${dataVersion}\t" +
        s"${partitionValue}\t${params.subPartitions.mkString(",")}\t" +
        s"${params.storageName}\t" +
        s"${numberOutputRows}\t" +
        s"${storageFiles.map(_._1).mkString(",")}\t" +
        "AVAILABLE")
    } catch {
      case e: Throwable =>
        log.error(s"Job failed, setting partition ${partitionValue} to INVALID")

        Try(api.updateBusinessObjectData(
          params.namespace,
          params.businessObjectName,
          formatUsage,
          formatFileType,
          formatVersion,
          fmt.getPartitionKey,
          partitionValue,
          params.subPartitions,
          dataVersion,
          ObjectStatus.INVALID
        )) match {
          case Failure(ex) => log.error("Error while setting object data to INVALID after job failure", ex)
          case _ => Unit
        }

        throw e
    } finally {
      sparkSession.sessionState.listenerManager.unregister(queryExecutionListener)
    }

    new BaseRelation() {
      override def sqlContext: SQLContext = sparkSession.sqlContext

      override def schema: StructType = data.schema
    }
  }

  private def toSparkDataSourceAndOptions(format: String, schema: Schema,
                                          options: Map[String, String]): (String, Map[String, String]) = {

    val compression = format.toLowerCase match {
      case "bz" => Map("compression" -> "bzip2")
      case "gz" => Map("compression" -> "gzip")
      case _ => Map.empty
    }

    format.toLowerCase match {
      case "bz" | "gz" | "txt" | "csv" =>
        ("csv", Map(
          "inferSchema" -> "false",
          "header" -> "false",
          "delimiter" -> {
            if (schema.getDelimiter != null) {
              val dValue = StringEscapeUtils unescapeJava schema.getDelimiter
              dValue
            } else {
              null
            }
          },
          "collectionItemsDelimiter" -> {
            if (schema.getCollectionItemsDelimiter != null) {
              schema.getCollectionItemsDelimiter.contains("\\") match {
                case true => schema.getCollectionItemsDelimiter.replace("\\", "").toInt.toChar.toString
                case false => schema.getCollectionItemsDelimiter
              }
            } else {
              null
            }
          },
          "mapKeysDelimiter" -> {
            if (schema.getMapKeysDelimiter != null) {
              schema.getMapKeysDelimiter.contains("\\") match {
                case true => schema.getMapKeysDelimiter.replace("\\", "").toInt.toChar.toString
                case false => schema.getMapKeysDelimiter
              }
            } else {
              null
            }
          },
          "nullValue" -> {
            if (schema.getNullValue != null) {
              val nValue = StringEscapeUtils unescapeJava schema.getNullValue
              nValue
            }
            else {
              schema.getNullValue
            }
          },
          "escape" -> {
            if (schema.getEscapeCharacter != null) {
              val eValue = StringEscapeUtils unescapeJava schema.getEscapeCharacter
              eValue
            }
            else
              {
                schema.getEscapeCharacter
              }
            }

        ) ++ compression ++ options)
      case "parquet" =>
        ("parquet", Map(
          "mergeSchema" -> "true" // in case different schema formats exist
        ) ++ options)
      case _ => (format.toLowerCase, options)
    }
  }

  private def makeHerdSchema(schema: StructType, params: HerdOptions): Schema = {
    val partitionKeys = Set(params.partitionKey) ++ params.subPartitionKeys filterNot(_.equalsIgnoreCase("partition"))

    val columns = schema.filterNot(f => partitionKeys.contains(f.name)).map(toHerdType)

    val partitionColumns = partitionKeys.map(schema(_)).map(toHerdType)

    val herdSchema = new Schema()
    herdSchema.setColumns(columns.asJava)
    herdSchema.setPartitions(partitionColumns.toSeq.asJava)
    params.partitionKeyGroup.foreach(herdSchema.setPartitionKeyGroup)

    log.info(s"Herd Schema: $herdSchema")
    herdSchema
  }

  private def makeSparkSchema(columns: Seq[SchemaColumn]): StructType = {
    val fields = columns.map {
      case c: SchemaColumn =>
        val nullable: Boolean = if (c.getRequired == null) {
          true
        } else {
          !c.getRequired
        }

        StructField(c.getName, toSparkType(c), nullable)
    }.toArray

    new StructType(fields)
  }

  private def toHerdType(column: StructField): SchemaColumn = {
    val col = new SchemaColumn
    col.setName(column.name)
    col.setRequired(!column.nullable)

    column.dataType match {
      case StringType => col.setType("STRING")
      case ByteType => col.setType("TINYINT")
      case ShortType => col.setType("SMALLINT")
      case IntegerType => col.setType("INT")
      case LongType => col.setType("BIGINT")
      case FloatType => col.setType("FLOAT")
      case DoubleType => col.setType("DOUBLE")
      case DateType => col.setType("DATE")
      case d: DecimalType =>
        col.setType("DECIMAL")
        col.setSize(d.precision + "," + d.scale)
      case TimestampType => col.setType("TIMESTAMP")
      case BooleanType => col.setType("BOOLEAN")
      case _ => col.setType(toComplexHerdType(column))

    }

    col
  }

  def toComplexHerdType(column: StructField): String = {

      val typeString = if (column.metadata.contains("HIVE_TYPE_STRING")) {
        column.metadata.getString("HIVE_TYPE_STRING")
      } else {
        column.dataType.catalogString
      }

    return typeString;

  }

  private def toSparkType(col: SchemaColumn): DataType = {
    col.getType.toUpperCase match {
      case "STRING" | "VARCHAR" | "CHAR" => StringType
      case "TINYINT" => ByteType
      case "SMALLINT" => ShortType
      case "INT" => IntegerType
      case "BIGINT" => LongType
      case "FLOAT" => FloatType
      case "DOUBLE" => DoubleType
      case "DATE" => DateType
      case "DECIMAL" =>
        var size = "10,0"
        if(col.getSize != null) {
          size = col.getSize()
        }
        val Array(precision, scale) = (if (size.indexOf(",") == -1) (size + ",0") else size).split(",").map(_.toInt)
        DecimalType(precision, scale)
      case "TIMESTAMP" => TimestampType
      case "BOOLEAN" => BooleanType
      case _ => toComplexSparkType(col)
    }
  }

  def toComplexSparkType(col: SchemaColumn): DataType = {
    try {
      CatalystSqlParser.parseDataType(col.getType)

    } catch {
      case e: ParseException =>
        throw new SparkException("Cannot recognize hive type string: " + col.getType, e)
    }
  }

}

object DefaultSource {

  def defaultApiClientFactory(url: String, username: Option[String], password: Option[String], accessTokenUrl: Option[String]): HerdApi = {
    val apiClient = new ApiClient()
    apiClient.setBasePath(url)
    if(accessTokenUrl.isDefined && accessTokenUrl.get.trim.nonEmpty) {
      apiClient.setAccessToken(OAuthTokenProvider.getAccessToken(username.get, password.get, accessTokenUrl.get))
    } else {
      username.foreach(apiClient.setUsername)
      password.foreach(apiClient.setPassword)
    }

    new DefaultHerdApi(apiClient)
  }

}
