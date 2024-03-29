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

import org.apache.commons.lang3.StringUtils
import org.apache.log4j.Logger
import org.apache.spark.sql.herd.PartitionFilter._
import scala.collection.JavaConverters._

import org.finra.herd.sdk.api._
import org.finra.herd.sdk.invoker.{ApiClient}
import org.finra.herd.sdk.model.{PartitionValueFilter, _}

/** A subset of business object data statuses used by the custom data source */
object ObjectStatus extends Enumeration {
  val UPLOADING: ObjectStatus.Value = Value("UPLOADING")

  val VALID: ObjectStatus.Value = Value("VALID")

  val INVALID: ObjectStatus.Value = Value("INVALID")
}

/** List all Herd APIs used by the custom data source */
trait HerdApi {

  /**
   * Refresh apiClient using the provided accessToken
   * @param accessToken access token
   */
  def refreshApiClient(accessToken: String): Unit

  /** Retrieve the business object definition by the namespace and business object values.
   *
   * @param namespace          The namespace name
   * @param businessObjectName The business object definition name
   * @return The business object definition
   */
  def getBusinessObjectByName(namespace: String, businessObjectName: String): BusinessObjectDefinition

  /** Retrieve all business object definitions by namespace.
   *
   * @param namespace          The namespace name
   * @return List of business object definition keys
   */
  def getBusinessObjectsByNamespace(namespace: String): BusinessObjectDefinitionKeys

  /** Create a business object definition
   *
   * @param namespace          The namespace value
   * @param businessObjectName The business object definition
   * @param dataProvider       The name of a valid data provider known by the system.
   */
  def registerBusinessObject(namespace: String, businessObjectName: String, dataProvider: String): Unit

  /** Retrieve list of business object formats based on the namespace and business object definition
   *
   * @param namespace          The namespace
   * @param businessObjectName The business object definition name
   * @return list of business object formats
   */
  def getBusinessObjectFormats(namespace: String, businessObjectName: String, latestBusinessObjectFormatVersion: Boolean = true): BusinessObjectFormatKeys

  /** Retrieve a single business object format
   *
   * @param namespace          The namespace value
   * @param businessObjectName The business object definition name
   * @param formatUsage        The business object format usage (e.g. PRC).
   * @param formatFileType     The business object format file type (e.g. GZ).
   * @param formatVersion      The version of the business object format (e.g. 0).
   * @return single business object format
   */
  def getBusinessObjectFormat(namespace: String, businessObjectName: String,
                              formatUsage: String, formatFileType: String,
                              formatVersion: Integer): BusinessObjectFormat

  /** Create a new business object format
   *
   * @param namespace          The namespace
   * @param businessObjectName The business object definition name
   * @param formatUsage        The business object format usage (e.g. PRC).
   * @param formatFileType     The business object format file type (e.g. GZ).
   * @param partitionKey       The business object format partition key.
   * @param schema             An optional schema associated with this business object format.
   * @return business object format version
   */
  def registerBusinessObjectFormat(namespace: String, businessObjectName: String, formatUsage: String,
                                   formatFileType: String, partitionKey: String, schema: Option[Schema]): Integer

  /** Retrieve all available partitions
   *
   * @param namespace          The namespace
   * @param businessObjectName The business object definition name
   * @param formatUsage        The business object format usage (e.g. PRC).
   * @param formatFileType     The business object format file type (e.g. GZ).
   * @param formatVersion      The version of the business object format (e.g. 0).
   * @param partitionFilter    the partition filter
   * @return list of partitions
   */
  def getBusinessObjectPartitions(namespace: String, businessObjectName: String,
                                  formatUsage: String, formatFileType: String, formatVersion: Integer,
                                  partitionFilter: Option[PartitionFilter]): Seq[(Integer, String, Seq[String], Integer)]

  /** Get the business object data based on the specified parameters
   *
   * @param namespace          The namespace
   * @param businessObjectName The business object definition name
   * @param formatUsage        The business object format usage (e.g. PRC).
   * @param formatFileType     The business object format file type (e.g. GZ).
   * @param formatVersion      The version of the business object format (e.g. 0).
   * @param partitionKey       The business object format partition key.
   * @param partitionValue     The partition value that the data is associated with (e.g. a specific trade date such as 20140401).
   * @param subPartitionValues list of sub partition values
   * @param dataVersion        The version of the business object data (e.g. 0).
   * @return the business object data
   */
  def getBusinessObjectData(namespace: String, businessObjectName: String,
                            formatUsage: String, formatFileType: String, formatVersion: Integer,
                            partitionKey: String, partitionValue: String,
                            subPartitionValues: Seq[String], dataVersion: Integer): BusinessObjectData

   /** Search the business object data based on the specified parameters
    *
    * @param businessObjectDataSearchRequest The search request
    * @param pageNum                         The page number
    * @param pageSize                        The page size
    * @return the business object data search result
    */
  def searchBusinessObjectData(businessObjectDataSearchRequest: BusinessObjectDataSearchRequest, pageNum: Integer = 1,
                               pageSize: Integer = 1000): BusinessObjectDataSearchResult

  /** Retrieves the DDL to initialize the specified type of the database system (e.g. Hive) to perform queries for a range of requested business object data
   * in the optionally specified storage of the S3 storage platform type.
   *
   * @param namespace          The namespace
   * @param businessObjectName The business object definition name
   * @param formatUsage        The business object format usage (e.g. PRC).
   * @param formatFileType     The business object format file type (e.g. GZ).
   * @param formatVersion      The version of the business object format (e.g. 0).
   * @param partitionKey       The business object format partition key.
   * @param partitionValues    The list of partition values that the data is associated with (e.g. a specific trade date such as 20140401).
   * @param dataVersion        The version of the business object data (e.g. 0).
   * @return The business object data DDL
   */
  def getBusinessObjectDataGenerateDdl(namespace: String, businessObjectName: String,
                                       formatUsage: String, formatFileType: String, formatVersion: Integer,
                                       partitionKey: String, partitionValues: Seq[String],
                                       dataVersion: Integer): BusinessObjectDataDdl

  /** Retrieves the list of partition locations for a given business object data (or a range of partition values).
   *
   * @param namespace          The namespace
   * @param businessObjectName The business object definition name
   * @param formatUsage        The business object format usage (e.g. PRC).
   * @param formatFileType     The business object format file type (e.g. GZ).
   * @param formatVersion      The version of the business object format (e.g. 0).
   * @param partitionKey       The business object format partition key.
   * @param partitionValues    The list of partition values that the data is associated with (e.g. a specific trade date such as 20140401).
   * @param dataVersion        The version of the business object data (e.g. 0).
   * @return The business object data DDL
   */
  def getBusinessObjectDataPartitions(namespace: String, businessObjectName: String,
                                      formatUsage: String, formatFileType: String, formatVersion: Integer,
                                      partitionKey: String, partitionValues: Seq[String],
                                      dataVersion: Integer): BusinessObjectDataPartitions

   /** Retrieves the business object data availability
    *
    * @param namespace           The namespace
    * @param businessObjectName  The business object definition name
    * @param formatUsage         The business object format usage (e.g. PRC).
    * @param formatFileType      The business object format file type (e.g. GZ).
    * @param partitionKey        The business object format partition key.
    * @param firstPartitionValue The first partition value
    * @param lastPartitionValue  The last partition value
    * @return The business object data availability
    */
  def getBusinessObjectDataAvailability(namespace: String, businessObjectName: String,
                                        formatUsage: String, formatFileType: String,
                                        partitionKey: String, firstPartitionValue: String,
                                        lastPartitionValue: String): BusinessObjectDataAvailability

  /** Create a business object data based on the specified parameters
   *
   * @param namespace          namespace
   * @param businessObjectName business object definition name
   * @param formatUsage        The business object format usage (e.g. PRC).
   * @param formatFileType     The business object format file type (e.g. GZ).
   * @param formatVersion      The version of the business object format (e.g. 0).
   * @param partitionKey       The business object format partition key.
   * @param partitionValue     The partition value that the data is associated with (e.g. a specific trade date such as 20140401).
   * @param subPartitionValues list of sub partitions
   * @param status             the status of the business object data(UPLOADING, VALID, INVALID...etc)
   * @param storageName        the storage name
   * @param storageDirectory   the storage directory
   * @return the business object data
   */
  def registerBusinessObjectData(namespace: String, businessObjectName: String, formatUsage: String,
                                 formatFileType: String, formatVersion: Integer, partitionKey: String,
                                 partitionValue: String, subPartitionValues: Seq[String],
                                 status: ObjectStatus.Value, storageName: String,
                                 storageDirectory: Option[String] = None): (Integer, Seq[StorageUnit])

  /** Add storage files to an existing storage unit in a business object data
   *
   * @param namespace          namespace
   * @param businessObjectName business object definition name
   * @param formatUsage        The business object format usage (e.g. PRC).
   * @param formatFileType     The business object format file type (e.g. GZ).
   * @param formatVersion      The version of the business object format (e.g. 0).
   * @param partitionKey       The business object format partition key.
   * @param partitionValue     The partition value that the data is associated with (e.g. a specific trade date such as 20140401).
   * @param subPartitionValues The list of subpartition values of the business object data.
   * @param dataVersion        The version of the business object data (e.g. 0).
   * @param storageName        The storage name
   * @param files              The list of storage files that need to be added to the storage unit
   */
  def setStorageFiles(namespace: String, businessObjectName: String, formatUsage: String,
                      formatFileType: String, formatVersion: Integer, partitionKey: String,
                      partitionValue: String, subPartitionValues: Seq[String], dataVersion: Integer,
                      storageName: String, files: Seq[(String, Long)]): Unit

  /** Updates an existing business object data based on the specified parameters.
   *
   * @param namespace          The namespace
   * @param businessObjectName The business object definition name
   * @param formatUsage        The business object format usage (e.g. PRC).
   * @param formatFileType     The business object format file type (e.g. GZ).
   * @param formatVersion      The version of the business object format (e.g. 0).
   * @param partitionKey       The business object format partition key.
   * @param partitionValue     The partition value that the data is associated with (e.g. a specific trade date such as 20140401).
   * @param subPartitionValues The list of subpartition values of the business object data.
   * @param dataVersion        The version of the business object data (e.g. 0).
   * @param status             The business object data status
   */
  def updateBusinessObjectData(namespace: String, businessObjectName: String, formatUsage: String,
                               formatFileType: String, formatVersion: Integer, partitionKey: String,
                               partitionValue: String, subPartitionValues: Seq[String], dataVersion: Integer,
                               status: ObjectStatus.Value): Unit

  /** Deletes an existing business object data based on the specified parameters.
   *
   * @param namespace          The namespace
   * @param businessObjectName The business object definition name
   * @param formatUsage        The business object format usage (e.g. PRC).
   * @param formatFileType     The business object format file type (e.g. GZ).
   * @param formatVersion      The version of the business object format (e.g. 0).
   * @param partitionKey       The business object format partition key.
   * @param partitionValue     The partition value that the data is associated with (e.g. a specific trade date such as 20140401).
   * @param subPartitionValues The list of subpartition values of the business object data.
   * @param dataVersion        The version of the business object data (e.g. 0).
   */
  def removeBusinessObjectData(namespace: String, businessObjectName: String, formatUsage: String,
                               formatFileType: String, formatVersion: Integer, partitionKey: String,
                               partitionValue: String, subPartitionValues: Seq[String], dataVersion: Integer): Unit

   /** Deletes an existing business object definition based on the specified parameters.
    *
    * @param namespace          The namespace
    * @param businessObjectName The business object definition name
    */
  def removeBusinessObjectDefinition(namespace: String, businessObjectName: String): Unit

   /** Deletes an existing business object format based on the specified parameters.
    *
    * @param namespace          The namespace
    * @param businessObjectName The business object definition name
    * @param formatUsage        The business object format usage (e.g. PRC).
    * @param formatFileType     The business object format file type (e.g. GZ).
    * @param formatVersion      The version of the business object format (e.g. 0).
    */
  def removeBusinessObjectFormat(namespace: String, businessObjectName: String, formatUsage: String,
                               formatFileType: String, formatVersion: Integer): Unit

  /** Gets information about an existing storage.
   *
   * @param name storage name
   * @return The storage
   */
  def getStorage(name: String): Storage

   /** Retrieve namespace by namespace code.
    *
    * @param namespaceCode The namespace code
    * @return The namespace.
    */
  def getNamespaceByNamespaceCode(namespaceCode: String): Namespace

   /** Retrieve all namespace keys.
    *
    * @return List of namespace keys
    */
  def getAllNamespaces: NamespaceKeys
}

class DefaultHerdApi(var apiClient: ApiClient) extends HerdApi with Retry {
  override val log: Logger = Logger.getLogger(classOf[DefaultHerdApi])

  def refreshApiClient(accessToken: String): Unit = {
    apiClient.setAccessToken(accessToken)
  }

  def getBusinessObjectDefinitionApi(apiClient: ApiClient) : BusinessObjectDefinitionApi = {
    new BusinessObjectDefinitionApi(apiClient)
  }

  def getBusinessObjectDataApi(apiClient: ApiClient) : BusinessObjectDataApi = {
    new BusinessObjectDataApi(apiClient)
  }

  def getBusinessObjectDataStorageFileApi(apiClient: ApiClient) : BusinessObjectDataStorageFileApi = {
    new BusinessObjectDataStorageFileApi(apiClient)
  }

  def getBusinessObjectFormatApi(apiClient: ApiClient) : BusinessObjectFormatApi = {
    new BusinessObjectFormatApi(apiClient)
  }

  def getStorageApi(apiClient: ApiClient) : StorageApi = {
    new StorageApi(apiClient)
  }

  def getNamespaceApi(apiClient: ApiClient) : NamespaceApi = {
    new NamespaceApi(apiClient)
  }

  def getBusinessObjectDataStatusApi(apiClient: ApiClient) : BusinessObjectDataStatusApi = {
    new BusinessObjectDataStatusApi(apiClient)
  }

  override def getBusinessObjectByName(namespace: String, businessObjectDefinitionName: String): BusinessObjectDefinition = {
    val api = getBusinessObjectDefinitionApi(apiClient)

    withRetry {
      api.businessObjectDefinitionGetBusinessObjectDefinition(namespace, businessObjectDefinitionName, false)
    }
  }

  override def getBusinessObjectsByNamespace(namespace: String): BusinessObjectDefinitionKeys = {
    val api = getBusinessObjectDefinitionApi(apiClient)

    withRetry {
      api.businessObjectDefinitionGetBusinessObjectDefinitions1(namespace)
    }
  }

  override def registerBusinessObject(namespace: String, businessObjectName: String, dataProvider: String): Unit = {
    val api = getBusinessObjectDefinitionApi(apiClient)

    val req = new BusinessObjectDefinitionCreateRequest()
    req.setNamespace(namespace)
    req.setBusinessObjectDefinitionName(businessObjectName)
    req.setDataProviderName(dataProvider)

    withRetry {
      api.businessObjectDefinitionCreateBusinessObjectDefinition(req)
    }
  }

  override def getBusinessObjectFormats(namespace: String, businessObjectName: String,
                                        latestBusinessObjectFormatVersion: Boolean = true): BusinessObjectFormatKeys = {
    val api = getBusinessObjectFormatApi(apiClient)

    withRetry {
      api.businessObjectFormatGetBusinessObjectFormats(namespace, businessObjectName, latestBusinessObjectFormatVersion)
    }
  }

  override def getBusinessObjectFormat(namespace: String, businessObjectDefinitionName: String,
                                       formatUsage: String, formatFileType: String,
                                       formatVersion: Integer): BusinessObjectFormat = {
    val api = getBusinessObjectFormatApi(apiClient)

    withRetry {
      api.businessObjectFormatGetBusinessObjectFormat(namespace, businessObjectDefinitionName, formatUsage,
        formatFileType, formatVersion)
    }
  }

  override def registerBusinessObjectFormat(namespace: String, businessObjectDefinitionName: String,
                                            formatUsage: String, formatFileType: String, partitionKey: String, schema: Option[Schema]): Integer = {
    val api = getBusinessObjectFormatApi(apiClient)

    val req = new BusinessObjectFormatCreateRequest()
    req.setNamespace(namespace)
    req.setBusinessObjectDefinitionName(businessObjectDefinitionName)
    req.setBusinessObjectFormatUsage(formatUsage)
    req.setBusinessObjectFormatFileType(formatFileType)
    req.setPartitionKey(partitionKey)
    req.setSchema(schema.orNull)

    withRetry {
      api.businessObjectFormatCreateBusinessObjectFormat(req).getBusinessObjectFormatVersion
    }
  }

  override def getBusinessObjectPartitions(namespace: String, businessObjectDefinitionName: String, formatUsage: String,
                                           formatFileType: String, formatVersion: Integer,
                                           partitionFilter: Option[PartitionFilter]): Seq[(Integer, String, Seq[String], Integer)] = {
    val api = getBusinessObjectDataApi(apiClient)

    partitionFilter match {
      case None =>
        val filter = new PartitionValueFilter()
        filter.setPartitionValues(List("${maximum.partition.value}", "${minimum.partition.value}").asJava)

        val req = new BusinessObjectDataAvailabilityRequest()
        req.setNamespace(namespace)
        req.setBusinessObjectDefinitionName(businessObjectDefinitionName)
        req.setBusinessObjectFormatUsage(formatUsage)
        req.setBusinessObjectFormatFileType(formatFileType)
        req.setPartitionValueFilters(null)
        req.setPartitionValueFilter(filter)
        req.setIncludeAllRegisteredSubPartitions(false)

        val range = withRetry {
          api.businessObjectDataCheckBusinessObjectDataAvailability(req)
        }.getAvailableStatuses.asScala.map { status =>
          status.getPartitionValue
        }

        filter.setPartitionValues(null)

        if (range.isEmpty) {
          return Seq.empty
        } else if (range.size == 1) {
          filter.setPartitionValues(List(range.head).asJava)
        } else {
          val filterRange = new PartitionValueRange()
          filterRange.setStartPartitionValue(range.head)
          filterRange.setEndPartitionValue(range.last)

          filter.setPartitionValueRange(filterRange)
        }

        req.setIncludeAllRegisteredSubPartitions(false)

        withRetry {
          api.businessObjectDataCheckBusinessObjectDataAvailability(req)
        }.getAvailableStatuses.asScala.map { status =>
          (status.getBusinessObjectFormatVersion,
            status.getPartitionValue,
            status.getSubPartitionValues.asScala,
            status.getBusinessObjectDataVersion)
        }
      case Some(filter) =>
        val req = new BusinessObjectDataAvailabilityRequest()
        req.setNamespace(namespace)
        req.setBusinessObjectDefinitionName(businessObjectDefinitionName)
        req.setBusinessObjectFormatUsage(formatUsage)
        req.setBusinessObjectFormatFileType(formatFileType)
        req.setPartitionValueFilters(null)
        req.setPartitionValueFilter(filter)
        req.setIncludeAllRegisteredSubPartitions(false)

        val convertedFilter: PartitionValueFilter = filter

        req.setPartitionValueFilter(convertedFilter)

        withRetry {
          api.businessObjectDataCheckBusinessObjectDataAvailability(req)
        }.getAvailableStatuses.asScala.map { status =>
          (status.getBusinessObjectFormatVersion,
            status.getPartitionValue,
            status.getSubPartitionValues.asScala,
            status.getBusinessObjectDataVersion
          )
        }
    }
  }

  override def getBusinessObjectData(namespace: String, businessObjectName: String,
                                     formatUsage: String, formatFileType: String,
                                     formatVersion: Integer, partitionKey: String, partitionValue: String,
                                     subPartitionValues: Seq[String], dataVersion: Integer): BusinessObjectData = {
    val api = getBusinessObjectDataApi(apiClient)

    withRetry {
      api.businessObjectDataGetBusinessObjectData(
        namespace,
        businessObjectName,
        formatUsage,
        formatFileType,
        partitionKey,
        partitionValue,
        subPartitionValues.mkString("|"),
        formatVersion,
        dataVersion,
        ObjectStatus.VALID.toString,
        false,
        false,
        false
      )
    }
  }

  override def searchBusinessObjectData(businessObjectDataSearchRequest: BusinessObjectDataSearchRequest, pageNum: Integer = 1,
                                        pageSize: Integer = 1000): BusinessObjectDataSearchResult = {
    val api = getBusinessObjectDataApi(apiClient)

    withRetry {
      api.businessObjectDataSearchBusinessObjectData(businessObjectDataSearchRequest, pageNum, pageSize)
    }
  }

  override def getBusinessObjectDataGenerateDdl(namespace: String, businessObjectName: String,
                                                formatUsage: String, formatFileType: String,
                                                formatVersion: Integer, partitionKey: String, partitionValues: Seq[String],
                                                dataVersion: Integer): BusinessObjectDataDdl = {
    val api = getBusinessObjectDataApi(apiClient)
    val businessObjectDataDdlRequest = new BusinessObjectDataDdlRequest()
    businessObjectDataDdlRequest.setNamespace(namespace)
    businessObjectDataDdlRequest.setBusinessObjectDefinitionName(businessObjectName)
    businessObjectDataDdlRequest.setBusinessObjectFormatUsage(formatUsage)
    businessObjectDataDdlRequest.setBusinessObjectFormatFileType(formatFileType)
    businessObjectDataDdlRequest.setBusinessObjectFormatVersion(formatVersion)

    val partitionValueFilter = new PartitionValueFilter()
    partitionValueFilter.setPartitionKey(partitionKey)
    partitionValueFilter.setPartitionValues(partitionValues.asJava)
    businessObjectDataDdlRequest.setPartitionValueFilters(List.fill(1)(partitionValueFilter).asJava)

    businessObjectDataDdlRequest.setOutputFormat(BusinessObjectDataDdlRequest.OutputFormatEnum.HIVE_13_DDL)
    businessObjectDataDdlRequest.setBusinessObjectDataVersion(dataVersion)
    businessObjectDataDdlRequest.setTableName("HerdSpark")

    withRetry {
      log.debug("businessObjectDataDdlRequest=" + businessObjectDataDdlRequest)
      val businessObjectDataDdl = api.businessObjectDataGenerateBusinessObjectDataDdl(businessObjectDataDdlRequest)

      log.debug("businessObjectDataDdl=" + businessObjectDataDdl)
      businessObjectDataDdl
    }
  }

  override def getBusinessObjectDataPartitions(namespace: String, businessObjectName: String,
                                               formatUsage: String, formatFileType: String,
                                               formatVersion: Integer, partitionKey: String, partitionValues: Seq[String],
                                               dataVersion: Integer): BusinessObjectDataPartitions = {
    val api = getBusinessObjectDataApi(apiClient)
    val businessObjectDataPartitionsRequest = new BusinessObjectDataPartitionsRequest
    businessObjectDataPartitionsRequest.setNamespace(namespace)
    businessObjectDataPartitionsRequest.setBusinessObjectDefinitionName(businessObjectName)
    businessObjectDataPartitionsRequest.setBusinessObjectFormatUsage(formatUsage)
    businessObjectDataPartitionsRequest.setBusinessObjectFormatFileType(formatFileType)
    businessObjectDataPartitionsRequest.setBusinessObjectFormatVersion(formatVersion)
    businessObjectDataPartitionsRequest.setAllowMissingData(true)

    val partitionValueFilter = new PartitionValueFilter()
    partitionValueFilter.setPartitionKey(partitionKey)
    partitionValueFilter.setPartitionValues(partitionValues.asJava)
    businessObjectDataPartitionsRequest.setPartitionValueFilters(List.fill(1)(partitionValueFilter).asJava)
    businessObjectDataPartitionsRequest.setBusinessObjectDataVersion(dataVersion)

    withRetry {
      log.debug("businessObjectDataPartitionsRequest=" + businessObjectDataPartitionsRequest)
      val businessObjectDataPartitions = api.businessObjectDataGenerateBusinessObjectDataPartitions(businessObjectDataPartitionsRequest)

      log.debug("businessObjectDataPartitions=" + businessObjectDataPartitions)
      businessObjectDataPartitions
    }
  }

  override def getBusinessObjectDataAvailability(namespace: String, businessObjectName: String,
                                                 formatUsage: String, formatFileType: String,
                                                 partitionKey: String, firstPartitionValue: String,
                                                 lastPartitionValue: String): BusinessObjectDataAvailability = {
    val api = getBusinessObjectDataApi(apiClient)

    val req = new BusinessObjectDataAvailabilityRequest
    req.setNamespace(namespace)
    req.setBusinessObjectDefinitionName(businessObjectName)
    req.setBusinessObjectFormatUsage(formatUsage)
    req.setBusinessObjectFormatFileType(formatFileType)
    val partitionValueFilter = new PartitionValueFilter
    partitionValueFilter.setPartitionKey(partitionKey)
    if (StringUtils.isEmpty(lastPartitionValue)) {
      val latestAfterPartitionValue = new LatestAfterPartitionValue();
      latestAfterPartitionValue.setPartitionValue(firstPartitionValue)
      partitionValueFilter.setLatestAfterPartitionValue(latestAfterPartitionValue)
    }
    else if (StringUtils.isEmpty(firstPartitionValue)) {
      val latestBeforePartitionValue = new LatestBeforePartitionValue();
      latestBeforePartitionValue.setPartitionValue(lastPartitionValue)
      partitionValueFilter.setLatestBeforePartitionValue(latestBeforePartitionValue)
    }
    else {
      val partitionValueRange = new PartitionValueRange()
      partitionValueRange.setStartPartitionValue(firstPartitionValue)
      partitionValueRange.setEndPartitionValue(lastPartitionValue)
      partitionValueFilter.setPartitionValueRange(partitionValueRange)
    }

    req.setPartitionValueFilter(partitionValueFilter)

    withRetry {
      api.businessObjectDataCheckBusinessObjectDataAvailability(req)
    }
  }

  override def registerBusinessObjectData(namespace: String, businessObjectName: String, formatUsage: String,
                                          formatFileType: String, formatVersion: Integer, partitionKey: String,
                                          partitionValue: String, subPartitionValues: Seq[String],
                                          status: ObjectStatus.Value, storageName: String,
                                          storageDirectory: Option[String] = None): (Integer, Seq[StorageUnit]) = {
    val api = getBusinessObjectDataApi(apiClient)

    val req = new BusinessObjectDataCreateRequest()
    req.setNamespace(namespace)
    req.setBusinessObjectDefinitionName(businessObjectName)
    req.setBusinessObjectFormatUsage(formatUsage)
    req.setBusinessObjectFormatFileType(formatFileType)
    req.setBusinessObjectFormatVersion(formatVersion)
    req.setPartitionKey(partitionKey)
    req.setPartitionValue(partitionValue)
    req.setSubPartitionValues(subPartitionValues.toList.asJava)
    req.setCreateNewVersion(true)
    req.setStatus(status.toString)

    val storageUnit = new StorageUnitCreateRequest()
    storageUnit.setStorageName(storageName)
    if (storageDirectory.isEmpty) {
      storageUnit.setDiscoverStorageFiles(false)
    } else {
      val dir = new StorageDirectory()
      dir.setDirectoryPath(storageDirectory.get)
      storageUnit.setStorageDirectory(dir)
      storageUnit.setDiscoverStorageFiles(true)
    }

    req.setStorageUnits(List(storageUnit).asJava)

    val resp = withRetry {
      api.businessObjectDataCreateBusinessObjectData(req)
    }

    // we only expect one path
    (resp.getVersion, resp.getStorageUnits.asScala)
  }

  override def setStorageFiles(namespace: String, businessObjectName: String, formatUsage: String,
                               formatFileType: String, formatVersion: Integer, partitionKey: String,
                               partitionValue: String, subPartitionValues: Seq[String], dataVersion: Integer,
                               storageName: String, files: Seq[(String, Long)]): Unit = {
    val req = new BusinessObjectDataStorageFilesCreateRequest()
    req.setNamespace(namespace)
    req.setBusinessObjectDefinitionName(businessObjectName)
    req.setBusinessObjectFormatUsage(formatUsage)
    req.setBusinessObjectFormatFileType(formatFileType)
    req.setBusinessObjectFormatVersion(formatVersion)
    req.setPartitionValue(partitionValue)
    req.setSubPartitionValues(subPartitionValues.toList.asJava)
    req.setBusinessObjectDataVersion(dataVersion)
    req.setStorageName(storageName)
    req.setStorageFiles(files.map {
      case (f, size) =>
        val file = new StorageFile()
        file.setFilePath(f)
        file.setFileSizeBytes(size)
        file
    }.toList.asJava)

    val api = getBusinessObjectDataStorageFileApi(apiClient)

    withRetry {
      api.businessObjectDataStorageFileCreateBusinessObjectDataStorageFiles(req)
    }
  }

  override def updateBusinessObjectData(namespace: String, businessObjectName: String, formatUsage: String,
                                        formatFileType: String, formatVersion: Integer, partitionKey: String,
                                        partitionValue: String, subPartitionValues: Seq[String], dataVersion: Integer,
                                        status: ObjectStatus.Value): Unit = {
    val api = getBusinessObjectDataStatusApi(apiClient)

    val req = new BusinessObjectDataStatusUpdateRequest()
    req.setStatus(status.toString)

    subPartitionValues.size match {
      case 0 => withRetry(api.businessObjectDataStatusUpdateBusinessObjectDataStatus(
        namespace,
        businessObjectName,
        formatUsage,
        formatFileType,
        formatVersion,
        partitionValue,
        dataVersion,
        req
      ))
      case 1 => withRetry(api.businessObjectDataStatusUpdateBusinessObjectDataStatus1(
        namespace,
        businessObjectName,
        formatUsage,
        formatFileType,
        formatVersion,
        partitionValue,
        subPartitionValues.head,
        dataVersion,
        req
      ))
      case 2 => withRetry(api.businessObjectDataStatusUpdateBusinessObjectDataStatus2(
        namespace,
        businessObjectName,
        formatUsage,
        formatFileType,
        formatVersion,
        partitionValue,
        subPartitionValues.head,
        subPartitionValues(1),
        dataVersion,
        req
      ))
      case 3 => withRetry(api.businessObjectDataStatusUpdateBusinessObjectDataStatus3(
        namespace,
        businessObjectName,
        formatUsage,
        formatFileType,
        formatVersion,
        partitionValue,
        subPartitionValues.head,
        subPartitionValues(1),
        subPartitionValues(2),
        dataVersion,
        req
      ))
      case 4 => withRetry(api.businessObjectDataStatusUpdateBusinessObjectDataStatus4(
        namespace,
        businessObjectName,
        formatUsage,
        formatFileType,
        formatVersion,
        partitionValue,
        subPartitionValues.head,
        subPartitionValues(1),
        subPartitionValues(2),
        subPartitionValues(3),
        dataVersion,
        req
      ))
      case _ => sys.error(s"Cannot update object with more than 4 sub-partition values!")
    }
  }

  override def removeBusinessObjectData(namespace: String, businessObjectName: String, formatUsage: String,
                                        formatFileType: String, formatVersion: Integer, partitionKey: String,
                                        partitionValue: String, subPartitionValues: Seq[String],
                                        dataVersion: Integer): Unit = {
    val api = getBusinessObjectDataApi(apiClient)

    subPartitionValues.size match {
      case 0 => withRetry(api.businessObjectDataDeleteBusinessObjectData(
        namespace,
        businessObjectName,
        formatUsage,
        formatFileType,
        formatVersion,
        partitionValue,
        dataVersion,
        false
      ))
      case 1 => withRetry(api.businessObjectDataDeleteBusinessObjectData1(
        namespace,
        businessObjectName,
        formatUsage,
        formatFileType,
        formatVersion,
        partitionValue,
        subPartitionValues.head,
        dataVersion,
        false
      ))
      case 2 => withRetry(api.businessObjectDataDeleteBusinessObjectData2(
        namespace,
        businessObjectName,
        formatUsage,
        formatFileType,
        formatVersion,
        partitionValue,
        subPartitionValues.head,
        subPartitionValues(1),
        dataVersion,
        false
      ))
      case 3 => withRetry(api.businessObjectDataDeleteBusinessObjectData3(
        namespace,
        businessObjectName,
        formatUsage,
        formatFileType,
        formatVersion,
        partitionValue,
        subPartitionValues.head,
        subPartitionValues(1),
        subPartitionValues(2),
        dataVersion,
        false
      ))
      case 4 => withRetry(api.businessObjectDataDeleteBusinessObjectData4(
        namespace,
        businessObjectName,
        formatUsage,
        formatFileType,
        formatVersion,
        partitionValue,
        subPartitionValues.head,
        subPartitionValues(1),
        subPartitionValues(2),
        subPartitionValues(3),
        dataVersion,
        false
      ))
      case _ => sys.error(s"Cannot delete object with more than 4 sub-partition values!")
    }
  }

  override def removeBusinessObjectDefinition(namespace: String, businessObjectName: String): Unit = {
    val api = getBusinessObjectDefinitionApi(apiClient)

    withRetry {
      api.businessObjectDefinitionDeleteBusinessObjectDefinition(namespace, businessObjectName)
    }
  }

  override def removeBusinessObjectFormat(namespace: String, businessObjectName: String, formatUsage: String,
                                 formatFileType: String, formatVersion: Integer): Unit = {
    val api = getBusinessObjectFormatApi(apiClient)

    withRetry {
      api.businessObjectFormatDeleteBusinessObjectFormat(namespace, businessObjectName, formatUsage, formatFileType, formatVersion)
    }
  }

  override def getStorage(name: String): Storage = {
    val api = getStorageApi(apiClient)

    withRetry {
      api.storageGetStorage(name)
    }
  }

  override def getNamespaceByNamespaceCode(namespaceCode: String): Namespace = {
    val api = getNamespaceApi(apiClient)

    withRetry {
      api.namespaceGetNamespace(namespaceCode)
    }
  }

  override def getAllNamespaces: NamespaceKeys = {
    val api = getNamespaceApi(apiClient)

    withRetry {
      api.namespaceGetNamespaces()
    }
  }
}
