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

import java.text.SimpleDateFormat
import java.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.base.Charsets
import com.google.common.io.Resources
import org.apache.commons.io.FileUtils
import org.apache.spark.sql._
import org.apache.spark.sql.types._
import org.junit.Assert.assertEquals
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import scala.collection.JavaConverters._

import org.finra.herd.sdk.invoker.ApiException
import org.finra.herd.sdk.model._

private class BaseHerdApi(testCase: String, partitions: Map[(String, String), String]) extends HerdApi with Serializable {
  private val mapper = new ObjectMapper()

  override def getBusinessObjectByName(namespace: String, businessObjectName: String): BusinessObjectDefinition = {
    val bizObjJson = Resources.toString(
      Resources.getResource(s"herd-models/$testCase/businessObjectDefinition.json"), Charsets.UTF_8)
    val bizObj: BusinessObjectDefinition = mapper.readValue(bizObjJson, classOf[BusinessObjectDefinition])

    bizObj
  }

  override def getBusinessObjectsByNamespace(namespace: String): BusinessObjectDefinitionKeys = {
    val businessObjectDefinitionKeysJson = Resources.toString(
      Resources.getResource(s"herd-models/$testCase/businessObjectDefinitionKeys.json"), Charsets.UTF_8)

    val businessObjectDefinitionKeys: BusinessObjectDefinitionKeys = mapper.readValue(
      businessObjectDefinitionKeysJson, classOf[BusinessObjectDefinitionKeys])

    businessObjectDefinitionKeys
  }

  override def registerBusinessObject(namespace: String, businessObjectName: String, dataProvider: String): Unit = {
    // no op
  }

  override def getBusinessObjectFormats(namespace: String, businessObjectName: String,
                                        latestBusinessObjectFormatVersion: Boolean = true): BusinessObjectFormatKeys = {
    val formatKeysJson = Resources.toString(
      Resources.getResource(s"herd-models/$testCase/businessObjectFormats.json"), Charsets.UTF_8)
    val formatKeys: BusinessObjectFormatKeys = mapper.readValue(formatKeysJson, classOf[BusinessObjectFormatKeys])

    formatKeys
  }

  override def getBusinessObjectFormat(namespace: String, businessObjectName: String, formatUsage: String,
                                       formatFileType: String, formatVersion: Integer): BusinessObjectFormat = {
    val formatJson = Resources.toString(
      Resources.getResource(s"herd-models/$testCase/businessObjectFormat.json"), Charsets.UTF_8)
    val format: BusinessObjectFormat = mapper.readValue(formatJson, classOf[BusinessObjectFormat])

    format
  }

  override def registerBusinessObjectFormat(namespace: String, businessObjectName: String, formatUsage: String,
                                            formatFileType: String, partitionKey: String,
                                            schema: Option[Schema]): Integer = {
    // no op
    0
  }

  override def getBusinessObjectPartitions(namespace: String, businessObjectName: String, formatUsage: String,
                                           formatFileType: String, formatVersion: Integer,
                                           partitionFilter: Option[PartitionFilter]): Seq[(Integer, String, Seq[String], Integer)] = {
    val keysJson = Resources.toString(
      Resources.getResource(s"herd-models/$testCase/businessObjectDataByFormat.json"), Charsets.UTF_8)
    val keys = mapper.readValue(keysJson, classOf[BusinessObjectDataKeys])

    keys.getBusinessObjectDataKeys.asScala.map { element =>
      (element.getBusinessObjectFormatVersion, element.getPartitionValue,
        element.getSubPartitionValues.asScala.toSeq, element.getBusinessObjectDataVersion)
    }
  }

  override def getBusinessObjectData(namespace: String, businessObjectName: String, formatUsage: String,
                                     formatFileType: String, formatVersion: Integer, partitionKey: String,
                                     partitionValue: String, subPartitionValues: Seq[String],
                                     dataVersion: Integer): BusinessObjectData = {
    val dataJson = Resources.toString(
      Resources.getResource(s"herd-models/$testCase/${partitions((partitionValue, subPartitionValues.mkString(",")))}"), Charsets.UTF_8)
    val data = mapper.readValue(dataJson, classOf[BusinessObjectData])

    data
  }

  override def searchBusinessObjectData(businessObjectDataSearchRequest: BusinessObjectDataSearchRequest, pageNum: Integer = 1,
                                        pageSize: Integer = 1000): BusinessObjectDataSearchResult = {
    val dataJson = Resources.toString(
      Resources.getResource(s"herd-models/$testCase/businessObjectDataSearchResult.json"), Charsets.UTF_8)
    val data = mapper.readValue(dataJson, classOf[BusinessObjectDataSearchResult])

    data
  }

  override def getBusinessObjectDataGenerateDdl(namespace: String, businessObjectName: String,
                                       formatUsage: String, formatFileType: String,
                                       formatVersion: Integer, partitionKey: String, partitionValues: Seq[String],
                                       dataVersion: Integer): BusinessObjectDataDdl = {
    val dataFile = if (partitionValues.length == 1) {
      partitions((partitionValues(0), ""))
    } else {
      partitions((partitionValues(0), partitionValues(1)))
    }

    val dataJson = Resources.toString(Resources.getResource(s"herd-models/$testCase/$dataFile"), Charsets.UTF_8)
    val data = mapper.readValue(dataJson, classOf[BusinessObjectDataDdl])

    data

  }
  override def getBusinessObjectDataAvailability(namespace: String, businessObjectName: String,
                                        formatUsage: String, formatFileType: String,
                                        partitionKey: String, firstPartitionValue: String,
                                        lastPartitionValue: String): BusinessObjectDataAvailability = {
    val dataJson = Resources.toString(
      Resources.getResource(s"herd-models/$testCase/businessObjectDataAvailability.json"), Charsets.UTF_8)
    val data = mapper.readValue(dataJson, classOf[BusinessObjectDataAvailability])

    data

  }

  override def registerBusinessObjectData(namespace: String, businessObjectName: String, formatUsage: String,
                                          formatFileType: String, formatVersion: Integer, partitionKey: String,
                                          partitionValue: String, subPartitionValues: Seq[String],
                                          status: ObjectStatus.Value, storageName: String,
                                          storageDirectory: Option[String] = None): (Integer, Seq[StorageUnit]) = {
    val dataJson = Resources.toString(
      Resources.getResource(s"herd-models/$testCase/registerBusinessObjectData1.json"), Charsets.UTF_8)
    val data = mapper.readValue(dataJson, classOf[BusinessObjectData])

    (data.getVersion, data.getStorageUnits.asScala)
  }

  override def setStorageFiles(namespace: String, businessObjectName: String, formatUsage: String,
                               formatFileType: String, formatVersion: Integer, partitionKey: String,
                               partitionValue: String, subPartitionValues: Seq[String], dataVersion: Integer,
                               storageName: String, files: Seq[(String, Long)]): Unit = {

  }

  override def updateBusinessObjectData(namespace: String, businessObjectName: String, formatUsage: String,
                                        formatFileType: String, formatVersion: Integer, partitionKey: String,
                                        partitionValue: String, subPartitionValues: Seq[String], dataVersion: Integer,
                                        status: ObjectStatus.Value): Unit = {

  }

  override def removeBusinessObjectData(namespace: String, businessObjectName: String, formatUsage: String,
                                        formatFileType: String, formatVersion: Integer, partitionKey: String,
                                        partitionValue: String, subPartitionValues: Seq[String],
                                        dataVersion: Integer): Unit = {

  }

  override def removeBusinessObjectDefinition(namespace: String, businessObjectName: String): Unit = {
    // no op
  }

  override def removeBusinessObjectFormat(namespace: String, businessObjectName: String, formatUsage: String,
                                          formatFileType: String, formatVersion: Integer): Unit = {
    // no op
  }

  override def getStorage(name: String): Storage = {
    val storage = new Storage()

    storage.setName(name)

    storage.setAttributes(List({
      val attr = new Attribute()
      attr.setName("bucket.name")
      attr.setValue("mybucket")
      attr
    }, {
      val attr = new Attribute()
      attr.setName("spark.base.path")
      attr.setValue("test-output/test-case-6")
      attr
    }).asJava)

    storage.setStoragePlatformName("S3")

    storage
  }

  override def getNamespaceByNamespaceCode(namespaceCode: String): Namespace = {
    val namespaceJson = Resources.toString(
      Resources.getResource(s"herd-models/$testCase/namespace.json"), Charsets.UTF_8)

    val namespace: Namespace = mapper.readValue(namespaceJson, classOf[Namespace])

    namespace
  }

  override def getAllNamespaces(): NamespaceKeys = {
    val namespaceKeysJson = Resources.toString(
      Resources.getResource(s"herd-models/$testCase/namespaceKeys.json"), Charsets.UTF_8)

    val namespaceKeys: NamespaceKeys = mapper.readValue(namespaceKeysJson, classOf[NamespaceKeys])

    namespaceKeys
  }

  // todo: add implementation
  override def getBusinessObjectDataPartitions(namespace: String, businessObjectName: String,
                                               formatUsage: String, formatFileType: String,
                                               formatVersion: Integer, partitionKey: String,
                                               partitionValues: Seq[String], dataVersion: Integer): BusinessObjectDataPartitions = {

    val dataFile = if (partitionValues.length == 1) {
      partitions((partitionValues(0), ""))
    } else {
      partitions((partitionValues(0), partitionValues(1)))
    }

    val dataJson = Resources.toString(Resources.getResource(s"herd-models/$testCase/$dataFile"), Charsets.UTF_8)
    val data = mapper.readValue(dataJson, classOf[BusinessObjectDataPartitions])

    data

  }

  override def refreshApiClient(accessToken: String): Unit = {
    // no op
  }
}

class DefaultSourceSuite extends FunSuite with BeforeAndAfterAll with Matchers {

  /* creates a local SparkSession with a mock S3A filesystem */
  private lazy val spark = SparkSession
    .builder()
    .appName("test")
    .master("local[*]")
    .enableHiveSupport()
    .config("spark.hadoop.fs.s3a.impl", "org.apache.spark.sql.herd.MockS3AFileSystem")
    .getOrCreate()

  import spark.implicits._

  private val namespace = "FOO"

  private val businessObjectDefinitionName = "TEST"

  private val defaultParams = Map(
    "url" -> "http://localhost",
    "username" -> "testUsername",
    "password" -> "testPassword",
    "namespace" -> namespace,
    "businessObjectName" -> businessObjectDefinitionName,
    "businessObjectFormatFileType" -> "CSV, orc, PARQUET"
  )

  private val EXPECTED_ROWS = {
    val fmt = new SimpleDateFormat("yyyy-MM-dd")

    Seq(
      Row(new java.sql.Date(fmt.parse("2017-01-01").getTime), "A", "row1", 100),
      Row(new java.sql.Date(fmt.parse("2017-01-01").getTime), "A", "row2", 200),
      Row(new java.sql.Date(fmt.parse("2017-01-01").getTime), "B", "row3", 300),
      Row(new java.sql.Date(fmt.parse("2017-01-01").getTime), "B", "row4", 400),
      Row(new java.sql.Date(fmt.parse("2017-01-02").getTime), "A", "row5", 500),
      Row(new java.sql.Date(fmt.parse("2017-01-02").getTime), "A", "row6", 600),
      Row(new java.sql.Date(fmt.parse("2017-01-02").getTime), "B", "row7", 700),
      Row(new java.sql.Date(fmt.parse("2017-01-02").getTime), "B", "row8", 800)
    )
  }

  private val EXPECTED_COMPLEX_ROWS = {
    val fmt = new SimpleDateFormat("yyyy-MM-dd")

    Seq(
      Row(new java.sql.Date(fmt.parse("2017-01-01").getTime), "A", "row1", 100, Map("1"->"11")),
      Row(new java.sql.Date(fmt.parse("2017-01-01").getTime), "A", "row2", 200, Map("2"->"22")),
      Row(new java.sql.Date(fmt.parse("2017-01-01").getTime), "B", "row3", 300, Map("3"->"33")),
      Row(new java.sql.Date(fmt.parse("2017-01-01").getTime), "B", "row4", 400, Map("4"->"44")),
      Row(new java.sql.Date(fmt.parse("2017-01-02").getTime), "A", "row5", 500, Map("5"->"55")),
      Row(new java.sql.Date(fmt.parse("2017-01-02").getTime), "A", "row6", 600, Map("6"->"66")),
      Row(new java.sql.Date(fmt.parse("2017-01-02").getTime), "B", "row7", 700, Map("7"->"77")),
      Row(new java.sql.Date(fmt.parse("2017-01-02").getTime), "B", "row8", 800, Map("8"->"88"))
    )
  }

  private val ORC_EXPECTED_ROWS = EXPECTED_ROWS.map(f => Row.fromSeq(f.toSeq :+ f(0)))

  private val EXPECTED_SCHEMA = new StructType()
    .add("SDATE", DateType)
    .add("SYMBOL", StringType)
    .add("COL1", StringType)
    .add("COL2", IntegerType)

  private val EXPECTED_COMPLEX_SCHEMA = new StructType()
    .add("SDATE", DateType)
    .add("SYMBOL", StringType)
    .add("COL1", StringType)
    .add("COL2", IntegerType)
    .add("COL3", MapType(StringType, StringType))

  private def getDataFrame(api: HerdApi, parameters: Map[String, String]): DataFrame = {
    val source = new DefaultSource((_, _, _, _) => api)
    val relation = source.createRelation(spark.sqlContext, parameters)

    spark.sqlContext.baseRelationToDataFrame(relation)
  }

  private def writeDataFrame(api: HerdApi, parameters: Map[String, String],
                             df: DataFrame, mode: SaveMode = SaveMode.Append): DataFrame = {
    val source = new DefaultSource((_, _, _, _) => api)
    val relation = source.createRelation(spark.sqlContext, mode, parameters, df)

    spark.sqlContext.baseRelationToDataFrame(relation)
  }

  override def afterAll(): Unit = {
    spark.stop()
  }

  test("load all partitions and filter") {
    val parts = Map(
      ("2017-01-01", "2017-01-02") -> "businessObjectDataPartitions.json",
      ("2017-01-01", "") -> "businessObjectDataPartitions.json"
    )
    val df = getDataFrame(new BaseHerdApi("test-case-1", parts), defaultParams)

    val result = df.filter('sdate === "2017-01-01").collect()

    val fmt = new SimpleDateFormat("yyyy-MM-dd")
    val sdate = fmt.parse("2017-01-01")

    val expected = EXPECTED_ROWS.filter(_.getDate(0) == sdate)

    result should contain theSameElementsAs(expected)
  }

  test("load non-partitioned data") {
    val parts = Map(("none", "") -> "businessObjectDataPartitions.json")
    val df = getDataFrame(new BaseHerdApi("test-case-2", parts), defaultParams)

    val result = df.collect()

    result should contain theSameElementsAs(EXPECTED_ROWS)
  }

  test("load and filter non-partitioned data") {
    val parts = Map(("none", "") -> "businessObjectDataPartitions.json")
    val df = getDataFrame(new BaseHerdApi("test-case-2", parts), defaultParams)

    val result = df.filter($"symbol" === "A").collect()

    val expected = EXPECTED_ROWS.filter(_.getString(1).equalsIgnoreCase("A"))

    result should contain theSameElementsAs(expected)
  }

  test("load ORC files, prune and filter") {
    val parts = Map(
      ("2017-01-01", "2017-01-02") -> "businessObjectDataPartitions.json",
      ("2017-01-01", "") -> "businessObjectDataPartitions1.json"
    )
    val df = getDataFrame(new BaseHerdApi("test-case-5", parts), defaultParams)

    val rows = df.select("sdate", "symbol").filter($"sdate" === "2017-01-01").collect()

    val fmt = new SimpleDateFormat("yyyy-MM-dd")
    val sdate = fmt.parse("2017-01-01")

    val expected = ORC_EXPECTED_ROWS.filter(_.getDate(4) == sdate).map(r => Row(r(4), r(1)))

    rows should contain theSameElementsAs(expected)
  }

  test("save partitioned data") {
    FileUtils.deleteDirectory(new java.io.File("./test-output"))

    val df = spark.createDataFrame(EXPECTED_ROWS.asJava, EXPECTED_SCHEMA).filter($"sdate" === "2017-01-01")

    val params = defaultParams + ("partitionValue" -> "2017-01-01")

    val parts = Map(
      ("2017-01-01", "") -> "businessObjectData1.json"
    )

    writeDataFrame(new BaseHerdApi("test-case-6", parts), params, df)
  }

  test("save subPartitioned data, one level") {
    FileUtils.deleteDirectory(new java.io.File("./test-output"))

    val df = spark.createDataFrame(EXPECTED_ROWS.asJava, EXPECTED_SCHEMA)
      .filter($"sdate" === "2017-01-01")
      .filter($"symbol" === "A")

    val params = defaultParams + ("partitionValue" -> "2017-01-01") + ("subPartitionKeys" -> "symbol") + ("subPartitionValues" -> "A")

    val parts = Map(
      ("2017-01-01", "") -> "businessObjectData1.json"
    )

    noException should be thrownBy {
      writeDataFrame(new BaseHerdApi("test-case-6", parts), params, df)
    }
  }

  test("save complex dataType dataframe") {
    FileUtils.deleteDirectory(new java.io.File("./test-output"))

    val df = spark.createDataFrame(EXPECTED_COMPLEX_ROWS.asJava, EXPECTED_COMPLEX_SCHEMA).filter($"sdate" === "2017-01-01")

    val params = defaultParams + ("partitionValue" -> "2017-01-01")

    val parts = Map(
      ("2017-01-01", "") -> "businessObjectData1.json"
    )

    val herdApi = new BaseHerdApi("test-case-6", parts) {
      override def getBusinessObjectFormats(namespace: String, businessObjectName: String,
                                            latestBusinessObjectFormatVersion: Boolean = true): BusinessObjectFormatKeys = {
        val formatKeysJson = Resources.toString(
          Resources.getResource(s"herd-models/test-case-6/complex-businessObjectFormats.json"), Charsets.UTF_8)
        val formatKeys: BusinessObjectFormatKeys = new ObjectMapper().readValue(formatKeysJson, classOf[BusinessObjectFormatKeys])

        formatKeys
      }

      override def getBusinessObjectFormat(namespace: String, businessObjectName: String, formatUsage: String,
                                           formatFileType: String, formatVersion: Integer): BusinessObjectFormat = {
        val formatJson = Resources.toString(
          Resources.getResource(s"herd-models/test-case-6/complex-businessObjectFormat.json"), Charsets.UTF_8)
        val format: BusinessObjectFormat = new ObjectMapper().readValue(formatJson, classOf[BusinessObjectFormat])
        format
      }
    }
    writeDataFrame(herdApi, params, df)
  }

  test("test GetFormatUsageAndFileType: failed to create new Format") {
    FileUtils.deleteDirectory(new java.io.File("./test-output"))

    val df = spark.createDataFrame(EXPECTED_COMPLEX_ROWS.asJava, EXPECTED_COMPLEX_SCHEMA).filter($"sdate" === "2017-01-01")

    val params = defaultParams + ("partitionValue" -> "2017-01-01") + ("registerNewFormat" -> "true" )
    val parts = Map(
      ("2017-01-01", "") -> "businessObjectData1.json"
    )

    val herdApi = new BaseHerdApi("test-case-6", parts) {
      override def registerBusinessObjectFormat(namespace: String, businessObjectName: String, formatUsage: String,
                                                formatFileType: String, partitionKey: String,
                                                schema: Option[Schema]): Integer = {
        throw new ApiException("failed to create format")
      }
    }

    val thrown = intercept[ApiException]{
      writeDataFrame(herdApi, params, df)
    }
    assert(thrown.getMessage == "failed to create format")
  }

  test("test GetFormatUsageAndFileType: create initial new bFormat happy path") {
    FileUtils.deleteDirectory(new java.io.File("./test-output"))

    val df = spark.createDataFrame(EXPECTED_ROWS.asJava, EXPECTED_SCHEMA).filter($"sdate" === "2017-01-01")

    val params = defaultParams + ("partitionValue" -> "2017-01-01") + ("registerNewFormat" -> "true")

    val parts = Map(
      ("2017-01-01", "") -> "businessObjectData1.json"
    )

    val herdApi = new BaseHerdApi("test-case-6", parts) {
      override def getBusinessObjectFormats(namespace: String, businessObjectName: String,
                                            latestBusinessObjectFormatVersion: Boolean = true): BusinessObjectFormatKeys = {
        var formatKeys = new BusinessObjectFormatKeys()
        formatKeys.setBusinessObjectFormatKeys(new util.ArrayList())
        formatKeys
      }
    }
    val sqlDataFrame = writeDataFrame(herdApi, params, df)
    assertEquals(EXPECTED_SCHEMA.sql, sqlDataFrame.schema.sql)
  }

  test("test GetFormatUsageAndFileType: failed to create initial new bFormat") {
    FileUtils.deleteDirectory(new java.io.File("./test-output"))

    val df = spark.createDataFrame(EXPECTED_ROWS.asJava, EXPECTED_SCHEMA).filter($"sdate" === "2017-01-01")

    val params = defaultParams + ("partitionValue" -> "2017-01-01") + ("registerNewFormat" -> "true")

    val parts = Map(
      ("2017-01-01", "") -> "businessObjectData1.json"
    )

    val herdApi = new BaseHerdApi("test-case-6", parts) {
      override def getBusinessObjectFormats(namespace: String, businessObjectName: String,
                                            latestBusinessObjectFormatVersion: Boolean = true): BusinessObjectFormatKeys = {
        throw new ApiException(403, "not Authorized to create bFormat" )
      }
    }

    val thrown = intercept[ApiException]{
      writeDataFrame(herdApi, params, df)
    }
    assertEquals(403, thrown.getCode)
    assertEquals("not Authorized to create bFormat", thrown.getMessage)
  }

  test("test GetFormatUsageAndFileType: create non-initial new bFormat happy path") {
    FileUtils.deleteDirectory(new java.io.File("./test-output"))

    val newRows = EXPECTED_ROWS.map(f => Row.fromSeq(f.toSeq :+ 1)).asJava
    val newSchema = EXPECTED_SCHEMA.add("EXTRACOL", IntegerType)
    val df = spark.createDataFrame(newRows, newSchema).filter($"sdate" === "2017-01-01")

    val params = defaultParams + ("partitionValue" -> "2017-01-01", "registerNewFormat" -> "true" )

    val parts = Map(
      ("2017-01-01", "") -> "businessObjectData1.json"
    )

    val herdApi = new BaseHerdApi("test-case-6", parts) {
        override def registerBusinessObjectFormat(namespace: String, businessObjectName: String, formatUsage: String,
                                                  formatFileType: String, partitionKey: String,
                                                  schema: Option[Schema]): Integer = {
          1
        }
      }
    val sqlDataFrame = writeDataFrame(herdApi, params, df)
    assertEquals(newSchema.sql, sqlDataFrame.schema.sql)
  }

  test("test GetFormatUsageAndFileType: failed to create non-initial new bFormat") {
    FileUtils.deleteDirectory(new java.io.File("./test-output"))

    val newRows = EXPECTED_ROWS.map(f => Row.fromSeq(f.toSeq :+ 1)).asJava
    val newSchema = EXPECTED_SCHEMA.add("EXTRACOL", IntegerType)
    val df = spark.createDataFrame(newRows, newSchema).filter($"sdate" === "2017-01-01")

    val params = defaultParams + ("partitionValue" -> "2017-01-01", "registerNewFormat" -> "true" )

    val parts = Map(
      ("2017-01-01", "") -> "businessObjectData1.json"
    )

    val herdApi = new BaseHerdApi("test-case-6", parts) {
      override def registerBusinessObjectFormat(namespace: String, businessObjectName: String, formatUsage: String,
                                                formatFileType: String, partitionKey: String,
                                                schema: Option[Schema]): Integer = {
        throw new ApiException(400, "failed to create bFormat" )
      }
    }

    val thrown = intercept[ApiException]{
      writeDataFrame(herdApi, params, df)
    }
    assertEquals(400, thrown.getCode)
    assertEquals("failed to create bFormat", thrown.getMessage)
  }

  test("conversion from Hive to Spark complex dataType") {
    val parts = Map(
      ("2017-01-01", "") -> "businessObjectData1.json"
    )
    val api = new BaseHerdApi("test-case-6", parts)
    val source = new DefaultSource((_, _, _, _) => api)

    val s1 = new SchemaColumn
    s1.setType("map<double,array<bigint>>")
    assertEquals("MapType(DoubleType,ArrayType(LongType,true),true)", source.toComplexSparkType(s1).toString)

    val s2 = new SchemaColumn
    s2.setType("struct<s:string,f:float,m:map<double,array<bigint>>>")
    assertEquals("StructType(StructField(s,StringType,true)," +
      " StructField(f,FloatType,true)," +
      " StructField(m,MapType(DoubleType,ArrayType(LongType,true),true),true))",
      source.toComplexSparkType(s2).toString)

  }

  test("conversion from Spark to Hive complex dataType") {
    val parts = Map(
      ("2017-01-01", "") -> "businessObjectData1.json"
    )
    val api = new BaseHerdApi("test-case-6", parts)
    val source = new DefaultSource((_, _, _, _) => api)

    val s = new StructField("mapCol", MapType(DoubleType, ArrayType(LongType, true), true), true)
    assertEquals("map<double,array<bigint>>", source.toComplexHerdType(s).toString)

    val s1 = StructField("structCol", StructType(List (StructField("s", StringType, true),
                                             StructField("f", FloatType, true),
                                             StructField("m", MapType(DoubleType, ArrayType(LongType, true), true), true)
                                            )
                                       )
                        )
    assertEquals("struct<s:string,f:float,m:map<double,array<bigint>>>", source.toComplexHerdType(s1).toString)

  }
}
