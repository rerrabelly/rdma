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
package org.finra.herd.dao;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.xml.datatype.XMLGregorianCalendar;

import com.amazonaws.services.s3.AmazonS3;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import org.finra.herd.core.AbstractCoreTest;
import org.finra.herd.core.HerdDateUtils;
import org.finra.herd.dao.config.DaoSpringModuleConfig;
import org.finra.herd.dao.config.DaoTestSpringModuleConfig;
import org.finra.herd.dao.helper.EmrVpcPricingStateFormatter;
import org.finra.herd.dao.helper.HerdCollectionHelper;
import org.finra.herd.dao.helper.JavaPropertiesHelper;
import org.finra.herd.model.api.xml.Attribute;
import org.finra.herd.model.api.xml.AttributeDefinition;
import org.finra.herd.model.api.xml.AttributeValueFilter;
import org.finra.herd.model.api.xml.BusinessObjectDataKey;
import org.finra.herd.model.api.xml.BusinessObjectDataStatusChangeEvent;
import org.finra.herd.model.api.xml.DataProviderKey;
import org.finra.herd.model.api.xml.EmrClusterDefinitionEbsConfiguration;
import org.finra.herd.model.api.xml.EmrClusterDefinitionVolumeSpecification;
import org.finra.herd.model.api.xml.IndexSearchFilter;
import org.finra.herd.model.api.xml.IndexSearchResultTypeKey;
import org.finra.herd.model.api.xml.LatestAfterPartitionValue;
import org.finra.herd.model.api.xml.LatestBeforePartitionValue;
import org.finra.herd.model.api.xml.MessageHeaderDefinition;
import org.finra.herd.model.api.xml.NamespacePermissionEnum;
import org.finra.herd.model.api.xml.PartitionValueFilter;
import org.finra.herd.model.api.xml.PartitionValueRange;
import org.finra.herd.model.api.xml.RegistrationDateRangeFilter;
import org.finra.herd.model.api.xml.SampleDataFile;
import org.finra.herd.model.api.xml.Schema;
import org.finra.herd.model.api.xml.SchemaColumn;
import org.finra.herd.model.api.xml.StorageUnit;
import org.finra.herd.model.api.xml.TagKey;
import org.finra.herd.model.dto.MessageHeader;
import org.finra.herd.model.jpa.BusinessObjectDataStatusEntity;
import org.finra.herd.model.jpa.SearchIndexTypeEntity;
import org.finra.herd.model.jpa.StorageEntity;
import org.finra.herd.model.jpa.StoragePlatformEntity;

/**
 * This is an abstract base class that provides useful methods for DAO test drivers.
 */
@ContextConfiguration(classes = DaoTestSpringModuleConfig.class, inheritLocations = false)
@Transactional(value = DaoSpringModuleConfig.HERD_TRANSACTION_MANAGER_BEAN_NAME)
public abstract class AbstractDaoTest extends AbstractCoreTest
{
    public static final String ACTIVITI_ID = "UT_Activiti_ID_1_" + RANDOM_SUFFIX;

    public static final String ACTIVITI_ID_2 = "UT_Activiti_ID_2_" + RANDOM_SUFFIX;

    public static final String ACTIVITI_ID_3 = "UT_Activiti_ID_3_" + RANDOM_SUFFIX;

    public static final String ACTIVITI_ID_4 = "UT_Activiti_ID_4_" + RANDOM_SUFFIX;

    public static final String AD_DOMAIN_JOIN_PASSWORD = "UT_ADDomainJoinPassword_" + RANDOM_SUFFIX;

    public static final String AD_DOMAIN_JOIN_USER = "UT_ADDomainJoinUser_" + RANDOM_SUFFIX;

    public static final String AGGREGATION_NAME = "UT_AggregationName_" + RANDOM_SUFFIX;

    public static final String ALLOCATION_STRATEGY_1 = "UT_TimeoutAction_1_" + RANDOM_SUFFIX;

    public static final String ALLOCATION_STRATEGY_2 = "UT_TimeoutAction_2_" + RANDOM_SUFFIX;

    public static final String ALLOWED_ATTRIBUTE_VALUE = "UT_ALLOWED_ATTRIBUTE_VALUE" + RANDOM_SUFFIX;

    public static final Boolean ALLOW_DUPLICATE_BUSINESS_OBJECT_DATA = true;

    public static final String ARCHIVE_RETRIEVAL_OPTION = null;

    public static final String ATTRIBUTE_NAME = "UT_AttributeName_1_" + RANDOM_SUFFIX;

    public static final String ATTRIBUTE_NAME_1_MIXED_CASE = "Attribute Name 1";

    public static final String ATTRIBUTE_NAME_2 = "UT_AttributeName_2_" + RANDOM_SUFFIX;

    public static final String ATTRIBUTE_NAME_2_MIXED_CASE = "Attribute Name 2";

    public static final String ATTRIBUTE_NAME_3 = "UT_AttributeName_3_" + RANDOM_SUFFIX;

    public static final String ATTRIBUTE_NAME_3_MIXED_CASE = "Attribute Name 3";

    public static final String ATTRIBUTE_NAME_4 = "UT_AttributeName_3_" + RANDOM_SUFFIX;

    public static final String ATTRIBUTE_NAME_4_MIXED_CASE = "Attribute Name 4";

    public static final String ATTRIBUTE_VALUE = "UT_AttributeValue_1_" + RANDOM_SUFFIX;

    public static final String ATTRIBUTE_VALUE_1 = "Attribute Value 1";

    public static final String ATTRIBUTE_VALUE_1_UPDATED = "Attribute Value 1 Updated";

    public static final String ATTRIBUTE_VALUE_2 = "   Attribute Value 2  ";

    public static final String ATTRIBUTE_VALUE_3 = "Attribute Value 3";

    public static final String ATTRIBUTE_VALUE_4 = "Attribute Value 4";

    public static final String ATTRIBUTE_VALUE_LIST = "UT_Attribute_Value_list_1_" + RANDOM_SUFFIX;

    public static final Long ATTRIBUTE_VALUE_LIST_ID = 1009L;

    public static final String ATTRIBUTE_VALUE_LIST_NAME = "UT_Attribute_Value_List_Name_1_" + RANDOM_SUFFIX;

    public static final String ATTRIBUTE_VALUE_LIST_NAMESPACE = "UT_Attribute_Value_List_Namespace_1_" + RANDOM_SUFFIX;

    public static final String ATTRIBUTE_VALUE_LIST_NAMESPACE_2 = "UT_Attribute_Value_List_Namespace_2_" + RANDOM_SUFFIX;

    public static final String ATTRIBUTE_VALUE_LIST_NAME_2 = "UT_Attribute_Value_List_Name_2_" + RANDOM_SUFFIX;

    public static final String AWS_ACCOUNT_ID = "UT_AwsAccountId_1_" + RANDOM_SUFFIX;

    public static final String AWS_ACCOUNT_ID_2 = "UT_AwsAccountId_2_" + RANDOM_SUFFIX;

    public static final String AWS_ASSUMED_ROLE_ACCESS_KEY = "UT_AwsAssumedRoleAccessKey_1_" + RANDOM_SUFFIX;

    public static final String AWS_ASSUMED_ROLE_ACCESS_KEY_2 = "UT_AwsAssumedRoleAccessKey_2_" + RANDOM_SUFFIX;

    public static final String AWS_ASSUMED_ROLE_SECRET_KEY = "UT_AwsAssumedRoleSecretKey_1_" + RANDOM_SUFFIX;

    public static final String AWS_ASSUMED_ROLE_SECRET_KEY_2 = "UT_AwsAssumedRoleSecretKey_2_" + RANDOM_SUFFIX;

    public static final XMLGregorianCalendar AWS_ASSUMED_ROLE_SESSION_EXPIRATION_TIME = HerdDateUtils.getXMLGregorianCalendarValue(getRandomDate());

    public static final String AWS_ASSUMED_ROLE_SESSION_TOKEN = "UT_AwsAssumedRoleSessionToken_1_" + RANDOM_SUFFIX;

    public static final String AWS_ASSUMED_ROLE_SESSION_TOKEN_2 = "UT_AwsAssumedRoleSessionToken_2_" + RANDOM_SUFFIX;

    public static final String AWS_KMS_KEY_ID = "UT_AwsKmsKeyId_" + RANDOM_SUFFIX;

    public static final String AWS_PRE_SIGNED_URL = "UT_AwsKmsKeyId_" + RANDOM_SUFFIX;

    public static final String AWS_REGION_NAME = "UT_AwsRegionName_1_" + RANDOM_SUFFIX;

    public static final String AWS_REGION_NAME_2 = "UT_AwsRegionName_2_" + RANDOM_SUFFIX;

    public static final String AWS_REGION_NAME_3 = "UT_AwsRegionName_3_" + RANDOM_SUFFIX;

    public static final String AWS_REGION_NAME_4 = "UT_AwsRegionName_4_" + RANDOM_SUFFIX;

    public static final String AWS_REGION_NAME_US_EAST_1 = "us-east-1";

    public static final String AWS_ROLE_ARN = "UT_AwsRoleArn" + RANDOM_SUFFIX;

    public static final String AWS_SNS_TOPIC_ARN = "UT_AWS_SNS_Topic_ARN_" + RANDOM_SUFFIX;

    public static final String AWS_SQS_QUEUE_NAME = "UT_AWS_SQS_Queue_Name_" + RANDOM_SUFFIX;

    public static final String BACKSLASH = "\\";

    public static final boolean BATCH_RESTORE_MODE = false;

    public static final boolean BATCH_DESTROY_MODE = false;

    public static final Integer BDATA_AGE_IN_DAYS = 1000;

    public static final Integer BDATA_FINAL_DESTROY_DELAY_IN_DAYS = 15;

    public static final Integer BDATA_PARTITION_VALUE_AGE_IN_DAYS = 1000;

    public static final String BDATA_STATUS = "UT_Status_1_" + RANDOM_SUFFIX;

    public static final String BDATA_STATUS_2 = "UT_Status_2_" + RANDOM_SUFFIX;

    public static final String BDATA_STATUS_3 = "UT_Status_3_" + RANDOM_SUFFIX;

    public static final String BDATA_STATUS_4 = "UT_Status_4_" + RANDOM_SUFFIX;

    public static final Boolean BDATA_STATUS_PRE_REGISTRATION_FLAG_SET = true;

    public static final String BDEF_COLUMN_DESCRIPTION = "UT_BusinessObjectDefinition_Column_Description_1_" + RANDOM_SUFFIX;

    public static final String BDEF_COLUMN_DESCRIPTION_2 = "UT_BusinessObjectDefinition_Column_Description_2_" + RANDOM_SUFFIX;

    public static final String BDEF_COLUMN_DESCRIPTION_STARTS_WITH_EQUALS_TO = "=UT_BusinessObjectDefinition_Column_Description_1_" + RANDOM_SUFFIX;

    public static final String BDEF_COLUMN_NAME = "UT_BusinessObjectDefinition_Column_Name_1_" + RANDOM_SUFFIX;

    public static final String BDEF_COLUMN_NAME_2 = "UT_BusinessObjectDefinition_Column_Name_2_" + RANDOM_SUFFIX;

    public static final String BDEF_COLUMN_NAME_STARTS_WITH_EQUALS_TO = "=UT_BusinessObjectDefinition_Column_Name_" + RANDOM_SUFFIX;

    public static final String BDEF_COLUMN_NAME_STARTS_WITH_WHITESPACES_THEN_EQUALS_TO = "  =UT_BusinessObjectDefinition_Column_Name_" + RANDOM_SUFFIX;

    public static final String BDEF_DESCRIPTION = "UT_BusinessObjectDefinition_Description_" + RANDOM_SUFFIX;

    public static final String BDEF_DESCRIPTION_2 = "UT_BusinessObjectDefinition_Description_" + RANDOM_SUFFIX_2;

    public static final String BDEF_DESCRIPTION_SUGGESTION_STATUS = "UT_BusinessObjectDefinition_Description_Suggestion_Status_1_" + RANDOM_SUFFIX;

    public static final String BDEF_DESCRIPTION_SUGGESTION_STATUS_2 = "UT_BusinessObjectDefinition_Description_Suggestion_Status_2_" + RANDOM_SUFFIX;

    public static final String BDEF_DESCRIPTION_WITH_HTML_AND_CARET_VALUES = "Test Description. Value should be <30> <div> <p> value should be <40> </p>";

    public static final String BDEF_DESCRIPTION_WITH_REMOVED_HTML = "Test Description. Value should be &lt;30&gt;   value should be &lt;40&gt; ";

    public static final String BDEF_DISPLAY_NAME = "UT_BusinessObjectDefinition_Display_Name_1_" + RANDOM_SUFFIX;

    public static final String BDEF_DISPLAY_NAME_2 = "UT_BusinessObjectDefinition_Display_Name_2_" + RANDOM_SUFFIX;

    public static final String BDEF_DISPLAY_NAME_3 = "UT_BusinessObjectDefinition_Display_Name_3_" + RANDOM_SUFFIX;

    public static final String BDEF_NAME = "UT_BusinessObjectDefinition_Name_1_" + RANDOM_SUFFIX;

    public static final String BDEF_NAMESPACE = "UT_BusinessObjectDefinition_Namespace_1_" + RANDOM_SUFFIX;

    public static final String BDEF_NAMESPACE_2 = "UT_BusinessObjectDefinition_Namespace_2_" + RANDOM_SUFFIX;

    public static final String BDEF_NAME_2 = "UT_BusinessObjectDefinition_Name_2_" + RANDOM_SUFFIX;

    public static final String BDEF_NAME_3 = "UT_BusinessObjectDefinition_Name_3_" + RANDOM_SUFFIX;

    public static final String BDEF_SHORT_DESCRIPTION = "UT_BusinessObjectDefinition_ShortDescription_" + RANDOM_SUFFIX;

    public static final String BDEF_SHORT_DESCRIPTION_2 = "UT_BusinessObjectDefinition_ShortDescription_2_" + RANDOM_SUFFIX;

    public static final BigDecimal BID_PRICE = getRandomBigDecimal();

    public static final Integer BLOCK_DURATION_MINUTES = getRandomInteger();

    public static final String BUSINESS_OBJECT_DEFINITION_COLUMN_CSV_INJECTION_ERROR_MSG =
        "One or more business object definition column fields start with a prohibited character.";

    public static final float BUSINESS_OBJECT_DEFINITION_INDEX_BOOST = 1f;

    public static final String BUSINESS_OBJECT_DEFINITION_SEARCH_INDEX_NAME = "UT_BusinessObjectDefinitionSearchIndexName_" + RANDOM_SUFFIX;

    public static final String CAPACITY_PREFERENCE_1 = "open";

    public static final String CAPACITY_RESERVATION_RESOURCE_GROUP_ARN = "arn:aws:resource-groups:us-east-1:123456789012:group/TestGroup";

    public static final String CAPACITY_USAGE_STRATEGY_1 = "use-capacity-reservations-first";

    public static final String CHARGE_CODE = "UT_ChargeCode_1" + RANDOM_SUFFIX;

    public static final String CHARGE_CODE_2 = "UT_ChargeCode_2" + RANDOM_SUFFIX;

    public static final String CODE = "code";

    public static final String COLUMNS_NAME_FIELD = "columns.name";

    public static final String COLUMN_DATA_TYPE = "UT_Column_Data_Type_1_" + RANDOM_SUFFIX;

    public static final String COLUMN_DATA_TYPE_2 = "UT_Column_Data_Type_2_" + RANDOM_SUFFIX;

    public static final String COLUMN_DATA_TYPE_CHAR = "CHAR";

    public static final String COLUMN_DATA_TYPE_STARTS_WITH_EQUALS_TO = "=UT_Column_Data_Type_" + RANDOM_SUFFIX;

    public static final String COLUMN_DATA_TYPE_STARTS_WITH_WHITESPACES_THEN_EQUALS_TO = "  =UT_Column_Data_Type_" + RANDOM_SUFFIX;

    public static final String COLUMN_DEFAULT_VALUE = "UT_Column_Default_Value" + RANDOM_SUFFIX;

    public static final String COLUMN_DEFAULT_VALUE_STARTS_WITH_EQUALS_TO = "=UT_Column_Default_Value" + RANDOM_SUFFIX;

    public static final String COLUMN_DEFAULT_VALUE_STARTS_WITH_WHITESPACES_THEN_EQUALS_TO = "=UT_Column_Default_Value" + RANDOM_SUFFIX;

    public static final String COLUMN_DESCRIPTION = "UT_Column_Description_1_" + RANDOM_SUFFIX;

    public static final String COLUMN_DESCRIPTION_2 = "UT_Column_Description_2_" + RANDOM_SUFFIX;

    public static final String COLUMN_DESCRIPTION_3 = "UT_Column_Description_3_" + RANDOM_SUFFIX;

    public static final String COLUMN_DESCRIPTION_4 = "UT_Column_Description_4_" + RANDOM_SUFFIX;

    public static final String COLUMN_DESCRIPTION_STARTS_WITH_EQUALS_TO = "=UT_Column_Description_1_" + RANDOM_SUFFIX;

    public static final String COLUMN_NAME = "UT_Column_Name_1_" + RANDOM_SUFFIX;

    public static final String COLUMN_NAME_2 = "UT_Column_Name_2_" + RANDOM_SUFFIX;

    public static final String COLUMN_NAME_STARTS_WITH_EQUALS_TO = "=UT_Column_Name_1_" + RANDOM_SUFFIX;

    public static final String COLUMN_NAME_STARTS_WITH_WHITESPACES_THEN_EQUALS_TO = "   =UT_Column_Name_1_" + RANDOM_SUFFIX;

    public static final Boolean COLUMN_REQUIRED = true;

    public static final String COLUMN_SIZE = "1" + RANDOM_SUFFIX;

    public static final String COLUMN_SIZE_2 = "2" + RANDOM_SUFFIX;

    public static final String COLUMN_SIZE_START_WITH_WHITESPACES_THEN_MINUS = "   -1" + RANDOM_SUFFIX;

    public static final String CONFIGURATION_KEY = "UT_Configuration_Key_" + RANDOM_SUFFIX;

    public static final String CONFIGURATION_VALUE = "UT_Configuration_Value_" + RANDOM_SUFFIX;

    public static final Integer CONNECTION_TIMEOUT = (int) (Math.random() * (Short.MAX_VALUE << 1));

    public static final String CORRELATION_DATA = "UT_Correlation_Data" + RANDOM_SUFFIX;

    public static final String CORRELATION_DATA_2 = "UT_Correlation_Data_2" + RANDOM_SUFFIX;

    public static final String CORRELATION_DATA_3 = "UT_Correlation_Data_3" + RANDOM_SUFFIX;

    public static final String CREATED_BY = "UT_CreatedBy_" + RANDOM_SUFFIX;

    public static final XMLGregorianCalendar CREATED_ON = HerdDateUtils.getXMLGregorianCalendarValue(getRandomDate());

    public static final String CREDSTASH_ENCRYPTION_CONTEXT = "UT_CredStashEncryptionContext_" + RANDOM_SUFFIX;

    public static final String CROSS_REALM_TRUST_PRINCIPAL_PASSWORD = "UT_CrossRealmTrustPrincipalPassword_" + RANDOM_SUFFIX;

    public static final String CUSTOM_DDL_NAME = "UT_CustomDdl" + RANDOM_SUFFIX;

    public static final String CUSTOM_DDL_NAME_2 = "UT_CustomDdl_2" + RANDOM_SUFFIX;

    public static final List<DataProviderKey> DATA_PROVIDER_KEYS = Collections.unmodifiableList(
        Arrays.asList(new DataProviderKey("UT_DataProvider_1_" + RANDOM_SUFFIX), new DataProviderKey("UT_DataProvider_2_" + RANDOM_SUFFIX)));

    public static final String DATA_PROVIDER_NAME = "UT_DataProvider_1_" + RANDOM_SUFFIX;

    public static final String DATA_PROVIDER_NAME_2 = "UT_DataProvider_2_" + RANDOM_SUFFIX;

    public static final Integer DATA_VERSION = (int) (Math.random() * Integer.MAX_VALUE);

    public static final Integer DATA_VERSION_2 = (int) (Math.random() * Integer.MAX_VALUE);

    public static final String DESCRIPTION = "UT_Description_1_" + RANDOM_SUFFIX;

    public static final String DESCRIPTION_2 = "UT_Description_2_" + RANDOM_SUFFIX;

    public static final String DESCRIPTION_SUGGESTION = "UT_Description_Suggestion_1_" + RANDOM_SUFFIX;

    public static final String DESCRIPTION_SUGGESTION_2 = "UT_Description_Suggestion_2_" + RANDOM_SUFFIX;

    public static final boolean DISABLE_COLUMN_FIELDS = false;

    public static final String DISPLAY_NAME_FIELD = "displayname";

    public static final Double DOUBLE_VALUE = Math.random() * Double.MAX_VALUE;

    public static final String DOWNLOADER_ROLE_ARN = "UT_DownloaderRoleArn" + RANDOM_SUFFIX;

    public static final Boolean DO_NOT_TRANSITION_LATEST_VALID = true;

    public static final Boolean EBS_OPTIMIZED = true;

    public static final String EC2_INSTANCE_ID = "UT_Ec2InstanceId" + RANDOM_SUFFIX;

    public static final String EC2_INSTANCE_TYPE = "UT_Ec2InstanceType_1_" + RANDOM_SUFFIX;

    public static final String EC2_INSTANCE_TYPE_2 = "UT_Ec2InstanceType_2_" + RANDOM_SUFFIX;

    public static final String EC2_INSTANCE_TYPE_3 = "UT_Ec2InstanceType_3_" + RANDOM_SUFFIX;

    public static final String EC2_INSTANCE_TYPE_4 = "UT_Ec2InstanceType_4_" + RANDOM_SUFFIX;

    public static final String EC2_SECURITY_GROUP_1 = "UT_Ec2SecurityGroup1" + RANDOM_SUFFIX;

    public static final String EC2_SECURITY_GROUP_2 = "UT_Ec2SecurityGroup2" + RANDOM_SUFFIX;

    public static final String EC2_SUBNET = "UT_EC2_Subnet_1_" + RANDOM_SUFFIX;

    public static final String EC2_SUBNET_2 = "UT_EC2_Subnet_2_" + RANDOM_SUFFIX;

    public static final String ELASTICSEARCH_HOSTNAME = "UT_Elasticsearch_Hostname" + RANDOM_SUFFIX;

    public static final Integer ELASTICSEARCH_PORT = (int) (Math.random() * (Short.MAX_VALUE << 1));

    public static final List<String> EMPTY_ROLES = new ArrayList<>();

    public static final String EMPTY_S3_BUCKET_NAME = "";

    public static final String EMR_CLUSTER_DAEMON_CONFIG_NAME = "UT_EMR_CLUSTER_DAEMON_CONFIG_NAME" + RANDOM_SUFFIX;

    public static final String EMR_CLUSTER_DAEMON_CONFIG_VALUE = "UT_EMR_CLUSTER_DAEMON_CONFIG_VALUE" + RANDOM_SUFFIX;

    public static final String EMR_CLUSTER_DEFINITION_NAME = "UT_EmrClusterDefinitionName_1_" + RANDOM_SUFFIX;

    public static final String EMR_CLUSTER_DEFINITION_NAME_2 = "UT_EmrClusterDefinitionName_2_" + RANDOM_SUFFIX;

    public static final String EMR_CLUSTER_DEFINITION_XML_FILE_MINIMAL_CLASSPATH = "classpath:testEmrClusterDefinitionMinimal.xml";

    public static final String EMR_CLUSTER_DEFINITION_XML_FILE_WITH_CLASSPATH = "classpath:testEmrClusterDefinition.xml";

    public static final String EMR_CLUSTER_ID = "UT_EMR_ClusterId_" + RANDOM_SUFFIX;

    public static final String EMR_CLUSTER_NAME = "UT_EMR_CLUSTER" + RANDOM_SUFFIX;

    public static final String EMR_CONFIGURE_DAEMON = "UT_EMR_CONFIGURE_DAEMON_" + RANDOM_SUFFIX;

    public static final String EMR_INVALID_STATE = "UT_EMR_InValidState_" + RANDOM_SUFFIX;

    public static final String EMR_MASTER_SECURITY_GROUP = "UT_EMR_MASTER_SECURITY_GROUP" + RANDOM_SUFFIX;

    public static final String EMR_NSCD_SCRIPT = "UT_EMR_NscdScript_" + RANDOM_SUFFIX;

    public static final String EMR_SERVICE_ACCESS_SECURITY_GROUP = "UT_EMR_SERVICE_ACCESS_SECURITY_GROUP" + RANDOM_SUFFIX;

    public static final String EMR_SLAVE_SECURITY_GROUP = "UT_EMR_SLAVE_SECURITY_GROUP" + RANDOM_SUFFIX;

    public static final String EMR_VALID_STATE = "UT_EMR_ValidState_" + RANDOM_SUFFIX;

    public static final boolean ENABLE_COLUMN_FIELDS = true;

    public static final boolean ENABLE_HIT_HIGHLIGHTING = true;

    public static final String ENVIRONMENT_NAME = "TEST";

    public static final String ERROR_CODE = "UT_Error_Code_" + RANDOM_SUFFIX;

    public static final String ERROR_MESSAGE = "UT_Error_Message_" + RANDOM_SUFFIX;

    public static final Integer EXPIRATION_IN_DAYS = (int) (Math.random() * Integer.MAX_VALUE);

    public static final String EXTERNAL_INTERFACE = "UT_ExternalInterface_1_" + RANDOM_SUFFIX;

    public static final String EXTERNAL_INTERFACE_2 = "UT_ExternalInterface_2_" + RANDOM_SUFFIX;

    public static final String EXTERNAL_INTERFACE_3 = "UT_ExternalInterface_3_" + RANDOM_SUFFIX;

    public static final String FIELD_DISPLAY_NAME = "displayName";

    public static final String FIELD_SHORT_DESCRIPTION = "shortDescription";

    public static final Integer FIFTH_FORMAT_VERSION = 4;

    public static final Boolean FILTER_ON_LATEST_VALID_VERSION = true;

    public static final Boolean FILTER_ON_RETENTION_EXPIRATION = true;

    public static final String FIRST_COLUMN_DATA_TYPE = "TINYINT";

    public static final String FIRST_COLUMN_NAME = "COLUMN001";

    public static final String FIRST_PARTITION_COLUMN_NAME = "PRTN_CLMN001";

    public static final String FORMAT_DESCRIPTION = "UT_Format_1_" + RANDOM_SUFFIX;

    public static final String FORMAT_DESCRIPTION_2 = "UT_Format_2_" + RANDOM_SUFFIX;

    public static final String FORMAT_DESCRIPTION_3 = "UT_Format_3_" + RANDOM_SUFFIX;

    public static final String FORMAT_DOCUMENT_SCHEMA = "UT_DocumentSchema_1_" + RANDOM_SUFFIX;

    public static final String FORMAT_DOCUMENT_SCHEMA_2 = "UT_DocumentSchema_2_" + RANDOM_SUFFIX;

    public static final String FORMAT_DOCUMENT_SCHEMA_3 = "UT_DocumentSchema_3_" + RANDOM_SUFFIX;

    public static final String FORMAT_DOCUMENT_SCHEMA_URL = "UT_DocumentSchemaUrl_1_" + RANDOM_SUFFIX;

    public static final String FORMAT_FILE_TYPE_CODE = "UT_FileType" + RANDOM_SUFFIX;

    public static final String FORMAT_FILE_TYPE_CODE_2 = "UT_FileType_2" + RANDOM_SUFFIX;

    public static final String FORMAT_FILE_TYPE_CODE_3 = "UT_FileType_3" + RANDOM_SUFFIX;

    public static final String FORMAT_FILE_TYPE_DESCRIPTION = "UT_Description of " + FORMAT_FILE_TYPE_CODE;

    public static final String FORMAT_USAGE_CODE = "UT_Usage" + RANDOM_SUFFIX;

    public static final String FORMAT_USAGE_CODE_2 = "UT_Usage_2" + RANDOM_SUFFIX;

    public static final String FORMAT_USAGE_CODE_3 = "UT_Usage_3" + RANDOM_SUFFIX;

    public static final String FORMAT_USAGE_CODE_4 = "UT_Usage_4" + RANDOM_SUFFIX;

    public static final Integer FORMAT_VERSION = (int) (Math.random() * Integer.MAX_VALUE);

    public static final Integer FORMAT_VERSION_2 = (int) (Math.random() * Integer.MAX_VALUE);

    public static final String FORMAT_RELATIONAL_SCHEMA_NAME = "UT_relationalSchemaName" + RANDOM_SUFFIX;

    public static final String FORMAT_RELATIONAL_TABLE_NAME = "UT_relationalTableName" + RANDOM_SUFFIX;

    public static final Integer FOURTH_FORMAT_VERSION = 3;

    public static final Long GLOBAL_ATTRIBUTE_DEFINITON_ID = (long) (Math.random() * Long.MAX_VALUE);

    public static final String GLOBAL_ATTRIBUTE_DEFINITON_INVALID_LEVEL = "BUS_OBJECT_FORMAT";

    public static final String GLOBAL_ATTRIBUTE_DEFINITON_LEVEL = "BUS_OBJCT_FRMT";

    public static final String GLOBAL_ATTRIBUTE_DEFINITON_NAME = "UT_GlobalAttributeDefinitionName_1_" + RANDOM_SUFFIX;

    public static final String GLOBAL_ATTRIBUTE_DEFINITON_NAME_2 = "UT_GlobalAttributeDefinitionName_2_" + RANDOM_SUFFIX;

    public static final BigDecimal HOURLY_PRICE = getRandomBigDecimal();

    public static final BigDecimal HOURLY_PRICE_2 = getRandomBigDecimal();

    public static final BigDecimal HOURLY_PRICE_3 = getRandomBigDecimal();

    public static final BigDecimal HOURLY_PRICE_4 = getRandomBigDecimal();

    public static final BigDecimal HOURLY_PRICE_5 = getRandomBigDecimal();

    public static final String HTTP_PROXY_HOST = "UT_HttpProxyHost_1_" + RANDOM_SUFFIX;

    public static final String HTTP_PROXY_HOST_2 = "UT_HttpProxyHost_2_" + RANDOM_SUFFIX;

    public static final Integer HTTP_PROXY_PORT = (int) (Math.random() * (Short.MAX_VALUE << 1));

    public static final Integer HTTP_PROXY_PORT_2 = (int) (Math.random() * (Short.MAX_VALUE << 1));

    public static final String IAM_ROLE_DESCRIPTION = "UT_IamRoleDescription_1_" + RANDOM_SUFFIX;

    public static final String IAM_ROLE_DESCRIPTION_2 = "UT_IamRoleDescription_2_" + RANDOM_SUFFIX;

    public static final String IAM_ROLE_DESCRIPTION_3 = "UT_IamRoleDescription_3_" + RANDOM_SUFFIX;

    public static final String IAM_ROLE_DESCRIPTION_4 = "UT_IamRoleDescription_4_" + RANDOM_SUFFIX;

    public static final String IAM_ROLE_NAME = "UT_IamRoleName_1_" + RANDOM_SUFFIX;

    public static final String IAM_ROLE_NAME_2 = "UT_IamRoleName_2_" + RANDOM_SUFFIX;

    public static final String IAM_ROLE_NAME_3 = "UT_IamRoleName_3_" + RANDOM_SUFFIX;

    public static final String IAM_ROLE_NAME_4 = "UT_IamRoleName_4_" + RANDOM_SUFFIX;

    public static final Boolean INCLUDE_BUSINESS_OBJECT_DEFINITION_UPDATE_HISTORY = true;

    public static final Boolean INCLUDE_TAG_HIERARCHY = true;

    public static final Integer INITIAL_DATA_VERSION = 0;

    public static final Integer INITIAL_FORMAT_VERSION = 0;

    public static final Integer INITIAL_VERSION = 0;

    public static final Integer INSTANCE_COUNT = getRandomInteger();

    public static final String INVALID_ARCHIVE_RETRIEVAL_OPTION = "UT_RETRIEVAL_OPTION_INVALID";

    public static final Integer INVALID_DATA_VERSION = -1 * DATA_VERSION;

    public static final Integer INVALID_FORMAT_VERSION = -1 * FORMAT_VERSION;

    public static final String INVALID_SEARCH_INDEX_NAME = "InvalidSearchIndexName";

    public static final String INVALID_VALUE = "UT_InvalidValue_1_" + RANDOM_SUFFIX;

    public static final String INVALID_VALUE_2 = "UT_InvalidValue_2_" + RANDOM_SUFFIX;

    public static final Integer IOPS = getRandomInteger();

    public static final String I_DO_NOT_EXIST = "I_DO_NOT_EXIST";

    public static final String JDBC_URL = "jdbc:h2:mem:herdTestDb";

    public static final String JOB_DESCRIPTION = "UT_JobDescription" + RANDOM_SUFFIX;

    public static final String JOB_ID = "UT_JobId_" + RANDOM_SUFFIX;

    public static final String JOB_NAME = "UT_Job" + RANDOM_SUFFIX;

    public static final String JOB_NAMESPACE = "UT_Job_Namespace" + RANDOM_SUFFIX;

    public static final String JOB_NAMESPACE_2 = "UT_Job_Namespace_2" + RANDOM_SUFFIX;

    public static final String JOB_NAMESPACE_3 = "UT_Job_Namespace_3" + RANDOM_SUFFIX;

    public static final String JOB_NAME_2 = "UT_Job_2" + RANDOM_SUFFIX;

    public static final String JOB_NAME_3 = "UT_Job_3" + RANDOM_SUFFIX;

    public static final String JOB_RECEIVE_TASK_ID = "UT_JobReceiveTaskId_" + RANDOM_SUFFIX;

    public static final String JSON_STRING = "UT_JsonString_" + RANDOM_SUFFIX;

    public static final String KDC_ADMIN_PASSWORD = "UT_KdcAdminPassword" + RANDOM_SUFFIX;

    public static final String KEY = "UT_Key_" + RANDOM_SUFFIX;

    public static final Boolean LATEST_VERSION_FLAG_SET = true;

    public static final String LDAP_ATTRIBUTE_USER_EMAIL_ADDRESS = "UT_LdapAttributeUserEmailAddress_" + RANDOM_SUFFIX;

    public static final String LDAP_ATTRIBUTE_USER_FULL_NAME = "UT_LdapAttributeUserFullName_" + RANDOM_SUFFIX;

    public static final String LDAP_ATTRIBUTE_USER_SHORT_ID = "UT_LdapAttributeUserId";

    public static final String LDAP_ATTRIBUTE_USER_JOB_TITLE = "UT_LdapAttributeUserJobTitle_" + RANDOM_SUFFIX;

    public static final String LDAP_ATTRIBUTE_USER_TELEPHONE_NUMBER = "UT_LdapAttributeUserTelephoneNumber_" + RANDOM_SUFFIX;

    public static final String LDAP_BASE = "ou=locations,dc=corp,dc=root,dc=test,dc=com";

    public static final String LDAP_URL = "UT_LdapUrl_" + RANDOM_SUFFIX;

    public static final String LDAP_USER_DN = "UT_LdapUserDn_" + RANDOM_SUFFIX;

    public static final String LOCAL_FILE = "foo.dat";

    public static final List<String> LOCAL_FILES = Arrays.asList("foo1.dat", "Foo2.dat", "FOO3.DAT", "folder/foo3.dat", "folder/foo2.dat", "folder/foo1.dat");

    public static final List<String> LOCAL_FILES_2 = Arrays.asList("bar1.dat", "Bar2.dat", "BAR3.DAT", "folder/bar3.dat", "folder/bar2.dat", "folder/bar1.dat");

    public static final List<String> LOCAL_FILES_SUBSET = Arrays.asList("Foo2.dat", "FOO3.DAT", "folder/foo2.dat");

    public static final Long LONG_VALUE = (long) (Math.random() * Long.MAX_VALUE);

    public static final Long LONG_VALUE_2 = (long) (Math.random() * Long.MAX_VALUE);

    public static final String MARKER = "UT_Marker_" + RANDOM_SUFFIX;

    public static final String MATCH_COLUMN = "column";

    public static final Integer MAX_COLUMNS = 10;

    public static final Integer MAX_PARTITIONS = 5;

    public static final Integer MAX_RESULT = 10;

    public static final Integer MAX_RESULTS_1 = 1;

    public static final String MESSAGE_DESTINATION = "UT_MessageDestination_1_" + RANDOM_SUFFIX;

    public static final String MESSAGE_DESTINATION_2 = "UT_MessageDestination_2_" + RANDOM_SUFFIX;

    public static final String MESSAGE_ID = "UT_Message_ID_" + RANDOM_SUFFIX;

    public static final String MESSAGE_TEXT = "UT_Message_Text" + RANDOM_SUFFIX;

    public static final String MESSAGE_TEXT_2 = "UT_Message_Text_2" + RANDOM_SUFFIX;

    public static final String MESSAGE_TYPE = "UT_MessageType_1_" + RANDOM_SUFFIX;

    public static final String MESSAGE_TYPE_2 = "UT_MessageType_2_" + RANDOM_SUFFIX;

    public static final List<String> MULTI_STORAGE_AVAILABLE_AS_UPLOADING_PARTITION_VALUES_UNION =
        Collections.unmodifiableList(Arrays.asList("2014-04-01", "2014-04-09", "2014-04-10", "2014-04-11"));

    public static final List<String> MULTI_STORAGE_AVAILABLE_AS_VALID_PARTITION_VALUES_UNION =
        Collections.unmodifiableList(Arrays.asList("2014-04-02", "2014-04-02A", "2014-04-03", "2014-04-05", "2014-04-06", "2014-04-08"));

    public static final List<String> MULTI_STORAGE_AVAILABLE_PARTITION_VALUES_INTERSECTION = Collections.unmodifiableList(Arrays.asList("2014-04-08"));

    public static final List<String> MULTI_STORAGE_AVAILABLE_PARTITION_VALUES_UNION = Collections.unmodifiableList(
        Arrays.asList("2014-04-01", "2014-04-02", "2014-04-02A", "2014-04-03", "2014-04-05", "2014-04-06", "2014-04-08", "2014-04-09", "2014-04-10",
            "2014-04-11"));

    public static final String MULTI_STORAGE_GREATEST_PARTITION_VALUE = "2014-04-11";

    public static final String MULTI_STORAGE_GREATEST_PARTITION_VALUE_STATUS = BusinessObjectDataStatusEntity.UPLOADING;

    public static final String MULTI_STORAGE_GREATEST_UPLOADING_PARTITION_VALUE = "2014-04-11";

    public static final String MULTI_STORAGE_GREATEST_VALID_PARTITION_VALUE = "2014-04-08";

    public static final String MULTI_STORAGE_LEAST_PARTITION_VALUE = "2014-04-01";

    public static final String MULTI_STORAGE_LEAST_PARTITION_VALUE_STATUS = BusinessObjectDataStatusEntity.UPLOADING;

    public static final String MULTI_STORAGE_LEAST_UPLOADING_PARTITION_VALUE = "2014-04-01";

    public static final String MULTI_STORAGE_LEAST_VALID_PARTITION_VALUE = "2014-04-02";

    public static final List<String> MULTI_STORAGE_NOT_AVAILABLE_NOT_REGISTERED_PARTITION_VALUES =
        Collections.unmodifiableList(Arrays.asList("2014-04-04", "2014-04-07"));

    public static final String NAMESPACE = "UT_Namespace_1_" + RANDOM_SUFFIX;

    public static final String NAMESPACE_2 = "UT_Namespace_2_" + RANDOM_SUFFIX;

    public static final String NAMESPACE_3 = "UT_Namespace_3_" + RANDOM_SUFFIX;

    public static final String NAMESPACE_4 = "UT_Namespace_4_" + RANDOM_SUFFIX;

    public static final String NAMESPACE_CHARGE_CODE = "UT_NamespaceChargeCode_1_" + RANDOM_SUFFIX;

    public static final String NAMESPACE_CHARGE_CODE_2 = "UT_NamespaceChargeCode_2_" + RANDOM_SUFFIX;

    public static final String NAMESPACE_CODE = "UT_NamespaceCode_1_" + RANDOM_SUFFIX;

    public static final String NAMESPACE_S3_KEY_PREFIX = "ut-namespace-1-" + RANDOM_SUFFIX;

    public static final String NAMESPACE_S3_KEY_PREFIX_2 = "ut-namespace-2-" + RANDOM_SUFFIX;

    public static final String NAMESPACE_S3_KEY_PREFIX_3 = "ut-namespace-3-" + RANDOM_SUFFIX;

    public static final String NAMESPACE_S3_KEY_PREFIX_4 = "ut-namespace-4-" + RANDOM_SUFFIX;

    public static final String NESTED_AGGREGATION_JSON_STRING = "UT_NestedAggregationJsonString_" + RANDOM_SUFFIX;

    public static final String NESTED_AGGREGATION_NAME = "UT_NestedAggregationName_" + RANDOM_SUFFIX;

    public static final String NOTIFICATION_EVENT_TYPE = "UT_NotificationEventType_1_" + RANDOM_SUFFIX;

    public static final String NOTIFICATION_EVENT_TYPE_2 = "UT_NotificationEventType_2_" + RANDOM_SUFFIX;

    public static final String NOTIFICATION_NAME = "UT_Ntfcn_Name" + RANDOM_SUFFIX;

    public static final String NOTIFICATION_NAME_2 = "UT_Ntfcn_Name_2" + RANDOM_SUFFIX;

    public static final String NOTIFICATION_REGISTRATION_STATUS = "UT_NotificationRegistrationStatus_1_" + RANDOM_SUFFIX;

    public static final Boolean NOT_INCLUDE_BUSINESS_OBJECT_DEFINITION_UPDATE_HISTORY = false;

    public static final Boolean NOT_INCLUDE_TAG_HIERARCHY = false;

    public static final List<String> NO_ALLOWED_ATTRIBUTE_VALUES = new ArrayList<>();

    public static final Boolean NO_ALLOW_DUPLICATE_BUSINESS_OBJECT_DATA = false;

    public static final XMLGregorianCalendar NO_AS_OF_TIME = null;

    public static final List<Attribute> NO_ATTRIBUTES = new ArrayList<>();

    public static final List<AttributeDefinition> NO_ATTRIBUTE_DEFINITIONS = new ArrayList<>();

    public static final List<AttributeValueFilter> NO_ATTRIBUTE_VALUE_FILTERS = new ArrayList<>();

    public static final String NO_AWS_ACCESS_KEY = null;

    public static final String NO_AWS_ACCOUNT_ID = null;

    public static final String NO_AWS_KMS_KEY_ID = null;

    public static final String NO_AWS_REGION_NAME = null;

    public static final String NO_AWS_SECRET_KEY = null;

    public static final String NO_BDATA_STATUS = null;

    public static final BusinessObjectDataStatusEntity NO_BDATA_STATUS_ENTITY = null;

    public static final Boolean NO_BDATA_STATUS_PRE_REGISTRATION_FLAG_SET = false;

    public static final String NO_BDEF_COLUMN_DESCRIPTION = null;

    public static final String NO_BDEF_DESCRIPTION = null;

    public static final String NO_BDEF_DISPLAY_NAME = null;

    public static final String NO_BDEF_NAME = null;

    public static final String NO_BDEF_NAMESPACE = null;

    public static final String NO_BDEF_SHORT_DESCRIPTION = null;

    public static final BigDecimal NO_BID_PRICE = null;

    public static final String NO_CHARGE_CODE = null;

    public static final List<SchemaColumn> NO_COLUMNS = null;

    public static final String NO_COLUMN_NAME = null;

    public static final String NO_CREATED_BY = null;

    public static final XMLGregorianCalendar NO_CREATED_ON = null;

    public static final Timestamp NO_CREATED_ON_TIMESTAMP = null;

    public static final String NO_CUSTOM_DDL_NAME = null;

    public static final Integer NO_DATA_VERSION = null;

    public static final Boolean NO_DO_NOT_TRANSITION_LATEST_VALID = false;

    public static final EmrClusterDefinitionEbsConfiguration NO_EMR_CLUSTER_DEFINITION_EBS_CONFIGURATION = null;

    public static final EmrClusterDefinitionVolumeSpecification NO_EMR_CLUSTER_DEFINITION_VOLUME_SPECIFICATION = null;

    public static final Boolean NO_ENABLE_HIT_HIGHLIGHTING = false;

    public static final StoragePlatformEntity NO_EXCLUDED_STORAGE_PLATFORM_ENTITY = null;

    public static final String NO_EXCLUDED_STORAGE_PLATFORM_TYPE = null;

    public static final Boolean NO_EXCLUSION_SEARCH_FILTER = Boolean.FALSE;

    public static final Integer NO_EXPIRATION_IN_DAYS = null;

    public static final Set<String> NO_FIELDS = new HashSet<>();

    public static final Boolean NO_FILTER_ON_LATEST_VALID_VERSION = false;

    public static final Boolean NO_FILTER_ON_RETENTION_EXPIRATION = false;

    public static final String NO_FORMAT_DESCRIPTION = null;

    public static final String NO_FORMAT_DOCUMENT_SCHEMA = null;

    public static final String NO_FORMAT_DOCUMENT_SCHEMA_URL = null;

    public static final String NO_FORMAT_FILE_TYPE_CODE = null;

    public static final String NO_FORMAT_USAGE_CODE = null;

    public static final Integer NO_FORMAT_VERSION = null;

    public static final BigDecimal NO_HOURLY_PRICE = null;

    public static final String NO_HTTP_PROXY_HOST = null;

    public static final Integer NO_HTTP_PROXY_PORT = null;

    public static final List<String> NO_INDEX_SEARCH_FACET_FIELDS = new ArrayList<>();

    public static final List<IndexSearchFilter> NO_INDEX_SEARCH_FILTERS = null;

    public static final IndexSearchResultTypeKey NO_INDEX_SEARCH_RESULT_TYPE_KEY = null;

    public static final BigDecimal NO_INSTANCE_MAX_SEARCH_PRICE = null;

    public static final BigDecimal NO_INSTANCE_ON_DEMAND_THRESHOLD = null;

    public static final BigDecimal NO_INSTANCE_SPOT_PRICE = null;

    public static final Boolean NO_IS_PARENT_TAG_NULL_FLAG = null;

    public static final String NO_JOB_NAME = null;

    public static final String NO_JOB_NAMESPACE = null;

    public static final LatestAfterPartitionValue NO_LATEST_AFTER_PARTITION_VALUE = null;

    public static final LatestBeforePartitionValue NO_LATEST_BEFORE_PARTITION_VALUE = null;

    public static final Boolean NO_LATEST_VERSION_FLAG_SET = false;

    public static final String NO_LOWER_BOUND_PARTITION_VALUE = null;

    public static final Set<String> NO_MATCH = new HashSet<>();

    public static final Integer NO_MAX_RESULTS = null;

    public static final String NO_MESSAGE_DESTINATION = null;

    public static final List<MessageHeader> NO_MESSAGE_HEADERS = new ArrayList<>();

    public static final List<MessageHeaderDefinition> NO_MESSAGE_HEADER_DEFINITIONS = new ArrayList<>();

    public static final String NO_MESSAGE_TYPE = null;

    public static final String NO_MESSAGE_VELOCITY_TEMPLATE = null;

    public static final String NO_NAMESPACE = null;

    public static final String NO_NAMESPACE_S3_KEY_PREFIX = null;

    public static final Map<String, String> NO_NEW_BUSINESS_OBJECT_DATA_ATTRIBUTES = null;

    public static final Map<String, String> NO_OLD_BUSINESS_OBJECT_DATA_ATTRIBUTES = null;

    public static final String NO_PARENT_TAG_CODE = null;

    public static final List<SchemaColumn> NO_PARTITION_COLUMNS = null;

    public static final String NO_PARTITION_KEY = null;

    public static final String NO_PARTITION_KEY_GROUP = null;

    public static final Map<String, Integer> NO_PARTITION_KEY_TO_LEVEL_MAPPINGS = new HashMap<>();

    public static final List<String> NO_PARTITION_VALUES = null;

    public static final List<PartitionValueFilter> NO_PARTITION_VALUE_FILTERS = new ArrayList<>();

    public static final PartitionValueRange NO_PARTITION_VALUE_RANGE = null;

    public static final String NO_PASSWORD = null;

    public static final Boolean NO_PUBLISH_ATTRIBUTE = false;

    public static final Boolean NO_PUBLISH_FOR_FILTER = false;

    public static final RegistrationDateRangeFilter NO_REGISTRATION_DATE_RANGE_FILTER = null;

    public static final XMLGregorianCalendar NO_RESTORE_EXPIRATION_ON = null;

    public static final String NO_S3_BUCKET_NAME = null;

    public static final AmazonS3 NO_S3_CLIENT = null;

    public static final String NO_S3_ENDPOINT = null;

    public static final String NO_S3_TRUSTING_ACCOUNT_STAGING_BUCKET_NAME = null;

    public static final List<SampleDataFile> NO_SAMPLE_DATA_FILES = new ArrayList<>();

    public static final Schema NO_SCHEMA = null;

    public static final Boolean NO_SELECT_ONLY_AVAILABLE_STORAGE_UNITS = false;

    public static final String NO_SESSION_TOKEN = null;

    public static final String NO_STORAGE_DIRECTORY_PATH = null;

    public static final List<StorageEntity> NO_STORAGE_ENTITIES = null;

    public static final String NO_STORAGE_NAME = null;

    public static final List<String> NO_STORAGE_NAMES = null;

    public static final StoragePlatformEntity NO_STORAGE_PLATFORM_ENTITY = null;

    public static final String NO_STORAGE_PLATFORM_TYPE = null;

    public static final String NO_STORAGE_UNIT_STATUS = null;

    public static final Boolean NO_STORAGE_UNIT_STATUS_AVAILABLE_FLAG_SET = false;

    public static final List<String> NO_SUBPARTITION_VALUES = new ArrayList<>();

    public static final SchemaColumn[] NO_SUB_PARTITION_KEYS = null;

    public static final String NO_TAG_DESCRIPTION = null;

    public static final String NO_TAG_DISPLAY_NAME = null;

    public static final Boolean NO_TAG_HAS_CHILDREN_FLAG = null;

    public static final TagKey NO_TAG_KEY = null;

    public static final BigDecimal NO_TAG_SEARCH_SCORE_MULTIPLIER = null;

    public static final String NO_TAG_TYPE_DESCRIPTION = null;

    public static final String NO_TAG_TYPE_DISPLAY_NAME = null;

    public static final Integer NO_TAG_TYPE_ORDER = null;

    public static final XMLGregorianCalendar NO_UPDATED_ON = null;

    public static final Timestamp NO_UPDATED_ON_TIMESTAMP = null;

    public static final String NO_UPPER_BOUND_PARTITION_VALUE = null;

    public static final String NO_USER_CREDENTIAL_NAME = null;

    public static final String NO_USER_TELEPHONE_NUMBER = null;

    public static final List<Attribute> NULL_AS_ATTRIBUTES = null;

    public static final List<BusinessObjectDataKey> NULL_AS_BUSINESS_OBJECT_DATA_CHILDREN = null;

    public static final List<BusinessObjectDataKey> NULL_AS_BUSINESS_OBJECT_DATA_PARENTS = null;

    public static final List<BusinessObjectDataStatusChangeEvent> NULL_AS_BUSINESS_OBJECT_DATA_STATUS_HISTORY = null;

    public static final List<StorageUnit> NULL_AS_STORAGE_UNITS = null;

    public static final List<String> NULL_AS_SUBPARTITION_VALUES = null;

    public static final String OOZIE_WORKFLOW_LOCATION = "UT_Oozie_workflow_2" + RANDOM_SUFFIX;

    public static final Boolean PARENT_TAG_IS_NOT_NULL = false;

    public static final Boolean PARENT_TAG_IS_NULL = true;

    public static final String[][] PARTITION_COLUMNS =
        new String[][] {{"DATE", null}, {"STRING", null}, {"INT", null}, {"NUMBER", null}, {"BOOLEAN", null}, {"NUMBER", null}, {"NUMBER", null}};

    public static final String PARTITION_KEY = "UT_PartitionKey" + RANDOM_SUFFIX;

    public static final String PARTITION_KEY_GROUP = "UT_Calendar_A" + RANDOM_SUFFIX;

    public static final String PARTITION_KEY_GROUP_2 = "UT_Calendar_B" + RANDOM_SUFFIX;

    public static final String PARTITION_VALUE = "UT_2014-12-31" + RANDOM_SUFFIX;

    public static final String PARTITION_VALUE_2 = "UT_2015-01-13" + RANDOM_SUFFIX;

    public static final String PARTITION_VALUE_3 = "UT_2015-08-20" + RANDOM_SUFFIX;

    public static final String PARTITION_VALUE_4 = "UT_2020-01-05" + RANDOM_SUFFIX;

    public static final String PASSWORD = "UT_Password_" + RANDOM_SUFFIX;

    public static final Boolean PUBLISH_ATTRIBUTE = true;

    public static final Boolean PUBLISH_FOR_FILTER = true;

    public static final Integer READ_TIMEOUT = (int) (Math.random() * (Short.MAX_VALUE << 1));

    public static final String REALM = "UT_Realm_" + RANDOM_SUFFIX;

    public static final String REASON = "UT_Reason_1_" + RANDOM_SUFFIX;

    public static final Integer RETENTION_PERIOD_DAYS = (int) (Math.random() * (Short.MAX_VALUE << 1));

    public static final String S3_ATTRIBUTE_NAME_BUCKET_NAME = "UT_S3_Attribute_Name_Bucket_Name_" + RANDOM_SUFFIX;

    public static final String S3_ATTRIBUTE_NAME_VALIDATE_FILE_EXISTENCE = "UT_S3_Attribute_Name_Validate_File_Existence_" + RANDOM_SUFFIX;

    public static final String S3_ATTRIBUTE_NAME_VALIDATE_FILE_SIZE = "UT_S3_Attribute_Name_Validate_File_Size_" + RANDOM_SUFFIX;

    public static final String S3_ATTRIBUTE_NAME_VALIDATE_PATH_PREFIX = "UT_S3_Attribute_Name_Validate_Path_Prefix_" + RANDOM_SUFFIX;

    public static final String S3_BUCKET_NAME = "UT_S3_Bucket_Name" + RANDOM_SUFFIX;

    public static final String S3_BUCKET_NAME_2 = "UT_S3_Bucket_Name2" + RANDOM_SUFFIX;

    public static final List<String> S3_DIRECTORY_MARKERS = Arrays.asList("", "folder");

    public static final String S3_ENDPOINT = "UT_S3_Endpoint_" + RANDOM_SUFFIX;

    public static final String S3_ENDPOINT_US_EAST_1 = "s3-external-1.amazonaws.com";

    public static final String S3_KEY = "UT_S3_Key_1_" + RANDOM_SUFFIX;

    public static final String S3_KEY_2 = "UT_S3_Key_2_" + RANDOM_SUFFIX;

    public static final String S3_KEY_PREFIX = "UT_S3_Key_Prefix_" + RANDOM_SUFFIX;

    public static final String S3_KMS_KEY_ID = "arn:aws:kms:us-east-1:123456789012:key/12345678-1234-1234-1234-123456789012";

    public static final String S3_OBJECT_TAGGER_ROLE_ARN = "UT_S3_Object_Tagger_Role_Arn_" + RANDOM_SUFFIX;

    public static final Integer S3_OBJECT_TAGGER_ROLE_SESSION_DURATION_SECONDS = getRandomInteger();

    public static final String S3_OBJECT_TAGGER_ROLE_SESSION_NAME = "UT_S3_Object_Tagger_Role_Session_Name_" + RANDOM_SUFFIX;

    public static final String S3_OBJECT_TAG_KEY = "UT_S3_Object_Tag_Key_1_" + RANDOM_SUFFIX;

    public static final String S3_OBJECT_TAG_KEY_2 = "UT_S3_Object_Tag_Key_2_" + RANDOM_SUFFIX;

    public static final String S3_OBJECT_TAG_VALUE = "UT_S3_Object_Tag_Value_1_" + RANDOM_SUFFIX;

    public static final String S3_OBJECT_TAG_VALUE_2 = "UT_S3_Object_Tag_Value_2_" + RANDOM_SUFFIX;

    public static final Integer S3_RESTORE_OBJECT_EXPIRATION_IN_DAYS = 7;

    public static final String S3_STAGING_BUCKET_NAME = "UT_S3_Staging_Bucket_Name_" + RANDOM_SUFFIX;

    public static final String S3_STAGING_RESOURCE_BASE = "UT_S3_STAGING_RESOURCE_BASE" + RANDOM_SUFFIX;

    public static final String S3_TRUSTING_ACCOUNT_STAGING_BUCKET_NAME = "UT_S3_Trusting_Account_Staging_Bucket_Name_" + RANDOM_SUFFIX;

    public static final String S3_URL_PATH_DELIMITER = "UT_S3_PATH_DELIMITER" + RANDOM_SUFFIX;

    public static final String S3_URL_PROTOCOL = "UT_S3_URL_PROTOCOL" + RANDOM_SUFFIX;

    public static final String S3_VERSION_ID = "UT_S3_Version_ID_" + RANDOM_SUFFIX;

    public static final String SCHEMA_COLLECTION_ITEMS_DELIMITER_COMMA = ",";

    public static final String SCHEMA_COLLECTION_ITEMS_DELIMITER_PIPE = "|";

    public static final String[][] SCHEMA_COLUMNS =
        new String[][] {{"TINYINT", null}, {"SMALLINT", null}, {"INT", null}, {"BIGINT", null}, {"FLOAT", null}, {"DOUBLE", null}, {"DECIMAL", null},
            {"DECIMAL", "p,s"}, {"NUMBER", null}, {"NUMBER", "p"}, {"NUMBER", "p,s"}, {"TIMESTAMP", null}, {"DATE", null}, {"STRING", null}, {"VARCHAR", "n"},
            {"VARCHAR2", "n"}, {"CHAR", "n"}, {"BOOLEAN", null}, {"BINARY", null}, {"ARRAY<BIGINT>", null}, {"ARRAY<INT(5)>", null},
            {"MAP<INT,ARRAY<BIGINT>>", null}};

    public static final String SCHEMA_COLUMNS_NAME_FIELD = "schemaColumns.name";

    public static final String SCHEMA_COLUMN_CSV_INJECTION_ERROR_MSG = "One or more schema column fields start with a prohibited character.";

    public static final String SCHEMA_COLUMN_NAME_PREFIX = "Clmn-Name";

    public static final String SCHEMA_CUSTOM_CLUSTERED_BY_VALUE = "(osi_sym_id) SORTED BY (osi_sym_id) INTO 500 BUCKETS";

    public static final String SCHEMA_CUSTOM_ROW_FORMAT = "SERDE 'org.apache.hive.hcatalog.data.JsonSerDe'";

    public static final String SCHEMA_CUSTOM_ROW_FORMAT_WITH_SERDE_PROPS =
        "SERDE 'org.apache.hadoop.hive.serde2.OpenCSVSerde'\n" + "WITH SERDEPROPERTIES (\n" + "   \"separatorChar\" = \"\\t\",\n" +
            "   \"quoteChar\"     = \"'\",\n" + "   \"escapeChar\"    = \"\\\\\"\n" + ") ";

    public static final String SCHEMA_CUSTOM_TBL_PROPERTIES = "(\"skip.header.line.count\"=\"1\")";

    public static final String NO_SCHEMA_CUSTOM_TBL_PROPERTIES = null;

    public static final String SCHEMA_DELIMITER_COMMA = ",";

    public static final String SCHEMA_DELIMITER_PIPE = "|";

    public static final String SCHEMA_ESCAPE_CHARACTER_BACKSLASH = "\\";

    public static final String SCHEMA_ESCAPE_CHARACTER_TILDE = "~";

    public static final String SCHEMA_MAP_KEYS_DELIMITER_EQUALS = "=";

    public static final String SCHEMA_MAP_KEYS_DELIMITER_HASH = "#";

    public static final String SCHEMA_NULL_VALUE_BACKSLASH_N = "\\N";

    public static final String SCHEMA_NULL_VALUE_NULL_WORD = "NULL";

    public static final String SCHEMA_PARTITION_COLUMN_NAME_PREFIX = "Prtn-Clmn-Name";

    public static final Boolean SEARCH_INDEX_ACTIVE_FLAG = true;

    public static final String SEARCH_INDEX_ALIAS_BDEF = "bdef";

    public static final String SEARCH_INDEX_ALIAS_TAG = "tag";

    public static final long SEARCH_INDEX_BUSINESS_OBJECT_DEFINITION_DOCUMENT_ID = 1L;

    public static final Boolean SEARCH_INDEX_DEFAULT_ACTIVE_FLAG = false;

    public static final String SEARCH_INDEX_DOCUMENT = "UT_SearchIndexDocument_1_" + RANDOM_SUFFIX;

    public static final long SEARCH_INDEX_DOCUMENT_COUNT = 10L;

    public static final String SEARCH_INDEX_DOCUMENT_ID = "UT_SearchIndexDocumentId_1_" + RANDOM_SUFFIX;

    public static final int SEARCH_INDEX_DOCUMENT_ID_INT = 1;

    public static final String SEARCH_INDEX_DOCUMENT_JSON = "UT_SearchIndexDocumentJson_1_" + RANDOM_SUFFIX;

    public static final String SEARCH_INDEX_ID = "UT_SearchIndexId_1_" + RANDOM_SUFFIX;

    public static final String SEARCH_INDEX_JSON_STRING = "UT_SearchIndexJsonString_" + RANDOM_SUFFIX;

    public static final String SEARCH_INDEX_MAPPING = "UT_SearchIndex_Mapping_" + RANDOM_SUFFIX;

    public static final String SEARCH_INDEX_NAME = "UT_SearchIndexName_1_" + RANDOM_SUFFIX;

    public static final String SEARCH_INDEX_NAME_2 = "UT_SearchIndexName_2_" + RANDOM_SUFFIX;

    public static final String SEARCH_INDEX_SCROLL_ID = "UT_SearchIndexScrollId_1_" + RANDOM_SUFFIX;

    public static final String SEARCH_INDEX_SETTINGS = "UT_SearchIndex_Settings_" + RANDOM_SUFFIX;

    public static final String SEARCH_INDEX_SETTINGS_JSON =
        "{\"analysis\":{\"filter\":{\"field_ngram_filter\":{\"type\":\"edgeNGram\",\"min_gram\":1,\"max_gram\":16,\"side\":\"front\"}}}}";

    public static final String SEARCH_INDEX_STATUS = "UT_SearchIndexStatus_1_" + RANDOM_SUFFIX;

    public static final String SEARCH_INDEX_STATUS_2 = "UT_SearchIndexStatus_2_" + RANDOM_SUFFIX;

    public static final String SEARCH_INDEX_TYPE = "UT_SearchIndexType_1_" + RANDOM_SUFFIX;

    public static final String SEARCH_INDEX_TYPE_2 = "UT_SearchIndexType_2_" + RANDOM_SUFFIX;

    public static final String SEARCH_INDEX_TYPE_BDEF = SearchIndexTypeEntity.SearchIndexTypes.BUS_OBJCT_DFNTN.name();

    public static final String SEARCH_INDEX_TYPE_TAG = SearchIndexTypeEntity.SearchIndexTypes.TAG.name();

    public static final String SEARCH_RESPONSE_JSON_STRING = "UT_SearchResponseJsonString_" + RANDOM_SUFFIX;

    public static final int SEARCH_RESULT_SIZE = 200;

    public static final String SEARCH_TERM = "Search Term -foo foo-bar";

    public static final Integer SECOND_DATA_VERSION = 1;

    public static final Integer SECOND_FORMAT_VERSION = 1;

    public static final Integer SECOND_VERSION = 1;

    public static final String SECURITY_FUNCTION = "UT_SecurityFunction_1_" + RANDOM_SUFFIX;

    public static final String SECURITY_FUNCTION_2 = "UT_SecurityFunction_2_" + RANDOM_SUFFIX;

    public static final String SECURITY_FUNCTION_3 = "UT_SecurityFunction_3_" + RANDOM_SUFFIX;

    public static final String SECURITY_ROLE = "UT_SecurityRole_1_" + RANDOM_SUFFIX;

    public static final String SECURITY_ROLE_2 = "UT_SecurityRole_2_" + RANDOM_SUFFIX;

    public static final Boolean SELECT_ONLY_AVAILABLE_STORAGE_UNITS = true;

    public static final String SESSION_NAME = "UT_SessionName" + RANDOM_SUFFIX;

    public static final String SHORT_DESCRIPTION = "UT_ShortDescription" + RANDOM_SUFFIX;

    public static final String SHORT_DESCRIPTION_FIELD = "shortdescription";

    public static final String SINGLE_QUOTE = "'";

    public static final List<String> SINGLE_STORAGE_NAMES = Arrays.asList("UT_Storage_1_" + RANDOM_SUFFIX);

    public static final Integer SIZE_IN_GB = getRandomInteger();

    public static final List<String> SORTED_LOCAL_FILES =
        Arrays.asList("FOO3.DAT", "Foo2.dat", "folder/foo1.dat", "folder/foo2.dat", "folder/foo3.dat", "foo1.dat");

    public static final List<String> SORTED_PARTITION_VALUES =
        Arrays.asList("2014-04-01", "2014-04-02", "2014-04-02A", "2014-04-03", "2014-04-04", "2014-04-05", "2014-04-06", "2014-04-07", "2014-04-08",
            "2014-04-09", "2014-04-10", "2014-04-11");

    public static final List<String> STORAGE_1_AVAILABLE_AS_UPLOADING_PARTITION_VALUES =
        Collections.unmodifiableList(Arrays.asList("2014-04-01", "2014-04-09", "2014-04-10"));

    public static final List<String> STORAGE_1_AVAILABLE_AS_VALID_PARTITION_VALUES =
        Collections.unmodifiableList(Arrays.asList("2014-04-02", "2014-04-02A", "2014-04-03", "2014-04-05", "2014-04-08"));

    public static final List<String> STORAGE_1_AVAILABLE_PARTITION_VALUES = Collections.unmodifiableList(
        Arrays.asList("2014-04-01", "2014-04-02", "2014-04-02A", "2014-04-03", "2014-04-05", "2014-04-08", "2014-04-09", "2014-04-10"));

    public static final String STORAGE_1_GREATEST_PARTITION_VALUE = "2014-04-10";

    public static final String STORAGE_1_GREATEST_PARTITION_VALUE_STATUS = BusinessObjectDataStatusEntity.UPLOADING;

    public static final String STORAGE_1_GREATEST_UPLOADING_PARTITION_VALUE = "2014-04-10";

    public static final String STORAGE_1_GREATEST_VALID_PARTITION_VALUE = "2014-04-08";

    public static final String STORAGE_1_LEAST_PARTITION_VALUE = "2014-04-01";

    public static final String STORAGE_1_LEAST_PARTITION_VALUE_STATUS = BusinessObjectDataStatusEntity.UPLOADING;

    public static final String STORAGE_1_LEAST_UPLOADING_PARTITION_VALUE = "2014-04-01";

    public static final String STORAGE_1_LEAST_VALID_PARTITION_VALUE = "2014-04-02";

    public static final List<String> STORAGE_1_NOT_AVAILABLE_NOT_REGISTERED_PARTITION_VALUES =
        Collections.unmodifiableList(Arrays.asList("2014-04-04", "2014-04-06", "2014-04-07", "2014-04-11"));

    public static final List<String> STORAGE_2_AVAILABLE_AS_UPLOADING_PARTITION_VALUES = Collections.unmodifiableList(Arrays.asList("2014-04-11"));

    public static final List<String> STORAGE_2_AVAILABLE_AS_VALID_PARTITION_VALUES = Collections.unmodifiableList(Arrays.asList("2014-04-06", "2014-04-08"));

    public static final List<String> STORAGE_2_AVAILABLE_PARTITION_VALUES =
        Collections.unmodifiableList(Arrays.asList("2014-04-06", "2014-04-08", "2014-04-11"));

    public static final String STORAGE_DIRECTORY_PATH = "UT_Storage_Directory/Some_Path/" + RANDOM_SUFFIX;

    public static final String STORAGE_NAME = "UT_Storage_1_" + RANDOM_SUFFIX;

    public static final List<String> STORAGE_NAMES = Arrays.asList("UT_Storage_1_" + RANDOM_SUFFIX, "UT_Storage_2_" + RANDOM_SUFFIX);

    public static final String STORAGE_NAME_2 = "UT_Storage_2_" + RANDOM_SUFFIX;

    public static final String STORAGE_NAME_3 = "UT_Storage_3_" + RANDOM_SUFFIX;

    public static final String STORAGE_NAME_4 = "UT_Storage_4_" + RANDOM_SUFFIX;

    public static final String STORAGE_NAME_5 = "UT_Storage_5_" + RANDOM_SUFFIX;

    public static final String STORAGE_PLATFORM_CODE = "UT_StoragePlatform_1_" + RANDOM_SUFFIX;

    public static final String STORAGE_PLATFORM_CODE_2 = "UT_StoragePlatform_2_" + RANDOM_SUFFIX;

    public static final String STORAGE_POLICY_NAME = "UT_Storage_Policy_Name_1_" + RANDOM_SUFFIX;

    public static final String STORAGE_POLICY_NAMESPACE_CD = "UT_Storage_Policy_Namespace_1_" + RANDOM_SUFFIX;

    public static final String STORAGE_POLICY_NAMESPACE_CD_2 = "UT_Storage_Policy_Namespace_2_" + RANDOM_SUFFIX;

    public static final String STORAGE_POLICY_NAME_2 = "UT_Storage_Policy_Name_2_" + RANDOM_SUFFIX;

    public static final String STORAGE_POLICY_RULE_TYPE = "UT_Storage_Policy_Rule_Type_1_" + RANDOM_SUFFIX;

    public static final String STORAGE_POLICY_RULE_TYPE_2 = "UT_Storage_Policy_Rule_Type_2_" + RANDOM_SUFFIX;

    public static final Integer STORAGE_POLICY_RULE_VALUE = (int) (Math.random() * Integer.MAX_VALUE);

    public static final Integer STORAGE_POLICY_RULE_VALUE_2 = (int) (Math.random() * Integer.MAX_VALUE);

    public static final String STORAGE_POLICY_TRANSITION_TYPE = "UT_Storage_Policy_Transition_Type_1_" + RANDOM_SUFFIX;

    public static final String STORAGE_POLICY_TRANSITION_TYPE_2 = "UT_Storage_Policy_Transition_Type_2_" + RANDOM_SUFFIX;

    public static final Integer STORAGE_POLICY_VERSION = (int) (Math.random() * Integer.MAX_VALUE);

    public static final String STORAGE_UNIT_STATUS = "UT_SU_Status_1_" + RANDOM_SUFFIX;

    public static final String STORAGE_UNIT_STATUS_2 = "UT_SU_Status_2_" + RANDOM_SUFFIX;

    public static final String STORAGE_UNIT_STATUS_3 = "UT_SU_Status_3_" + RANDOM_SUFFIX;

    public static final String STORAGE_UNIT_STATUS_4 = "UT_SU_Status_4_" + RANDOM_SUFFIX;

    public static final Boolean STORAGE_UNIT_STATUS_AVAILABLE_FLAG_SET = true;

    public static final List<String> SUBPARTITION_VALUES =
        Arrays.asList("Aa" + RANDOM_SUFFIX, "Bb" + RANDOM_SUFFIX, "Cc" + RANDOM_SUFFIX, "Dd" + RANDOM_SUFFIX);

    public static final List<String> SUBPARTITION_VALUES_2 =
        Arrays.asList("Ee" + RANDOM_SUFFIX, "Ff" + RANDOM_SUFFIX, "Gg" + RANDOM_SUFFIX, "Hh" + RANDOM_SUFFIX);

    public static final List<String> SUBPARTITION_VALUES_3 =
        Arrays.asList("Ii" + RANDOM_SUFFIX, "Jj" + RANDOM_SUFFIX, "Kk" + RANDOM_SUFFIX, "Ll" + RANDOM_SUFFIX);

    public static final String SUB_AGGREGATION_NAME = "UT_SubAggregationName_" + RANDOM_SUFFIX;

    public static final String SUB_PARTITION_VALUE_1 = "UT_SubPartition_1_" + RANDOM_SUFFIX;

    public static final String SUB_PARTITION_VALUE_2 = "UT_SubPartition_2_" + RANDOM_SUFFIX;

    public static final String SUB_PARTITION_VALUE_3 = "UT_SubPartition_3_" + RANDOM_SUFFIX;

    public static final String SUB_PARTITION_VALUE_4 = "UT_SubPartition_4_" + RANDOM_SUFFIX;

    public static final List<NamespacePermissionEnum> SUPPORTED_NAMESPACE_PERMISSIONS = Collections.unmodifiableList(
        Arrays.asList(NamespacePermissionEnum.READ, NamespacePermissionEnum.WRITE, NamespacePermissionEnum.EXECUTE, NamespacePermissionEnum.GRANT,
            NamespacePermissionEnum.WRITE_DESCRIPTIVE_CONTENT, NamespacePermissionEnum.WRITE_ATTRIBUTE));

    public static final String TABLE_NAME = "Test_Table" + RANDOM_SUFFIX;

    public static final String TAG_CODE = "UT_TagCode_1_" + RANDOM_SUFFIX;

    public static final String TAG_CODE_2 = "UT_TagCode_2_" + RANDOM_SUFFIX;

    public static final String TAG_CODE_3 = "UT_TagCode_3_" + RANDOM_SUFFIX;

    public static final String TAG_CODE_4 = "UT_TagCode_4_" + RANDOM_SUFFIX;

    public static final String TAG_CODE_5 = "UT_TagCode_5_" + RANDOM_SUFFIX;

    public static final long TAG_COUNT = 120;

    public static final String TAG_DESCRIPTION = "UT_TagDescription_1_" + RANDOM_SUFFIX;

    public static final String TAG_DESCRIPTION_2 = "UT_TagDescription_2_" + RANDOM_SUFFIX;

    public static final String TAG_DESCRIPTION_3 = "UT_TagDescription_3_" + RANDOM_SUFFIX;

    public static final String TAG_DESCRIPTION_4 = "UT_TagDescription_4_" + RANDOM_SUFFIX;

    public static final String TAG_DESCRIPTION_5 = "UT_TagDescription_5_" + RANDOM_SUFFIX;

    public static final String TAG_DISPLAY_NAME = "UT_TagDisplayName_1_" + RANDOM_SUFFIX;

    public static final String TAG_DISPLAY_NAME_2 = "UT_TagDisplayName_2_" + RANDOM_SUFFIX;

    public static final String TAG_DISPLAY_NAME_3 = "UT_TagDisplayName_3_" + RANDOM_SUFFIX;

    public static final String TAG_DISPLAY_NAME_4 = "UT_TagDisplayName_4_" + RANDOM_SUFFIX;

    public static final String TAG_DISPLAY_NAME_5 = "UT_TagDisplayName_5_" + RANDOM_SUFFIX;

    public static final Boolean TAG_HAS_CHILDREN = true;

    public static final Boolean TAG_HAS_NO_CHILDREN = false;

    public static final float TAG_INDEX_BOOST = 1000f;

    public static final String TAG_SEARCH_INDEX_NAME = "UT_TagSearchIndexName_" + RANDOM_SUFFIX;

    public static final BigDecimal TAG_SEARCH_SCORE_MULTIPLIER = getRandomBigDecimal();

    public static final BigDecimal TAG_SEARCH_SCORE_MULTIPLIER_2 = getRandomBigDecimal();

    public static final BigDecimal TAG_SEARCH_SCORE_MULTIPLIER_3 = getRandomBigDecimal();

    public static final BigDecimal TAG_SEARCH_SCORE_MULTIPLIER_4 = getRandomBigDecimal();

    public static final BigDecimal TAG_SEARCH_SCORE_MULTIPLIER_NULL = null;

    public static final String TAG_TYPE = "UT_TagType_1_" + RANDOM_SUFFIX;

    public static final String TAG_TYPE_2 = "UT_TagType_2_" + RANDOM_SUFFIX;

    public static final String TAG_TYPE_CODE = "UT_TagTypeCode_1_" + RANDOM_SUFFIX;

    public static final String TAG_TYPE_CODE_2 = "UT_TagTypeCode_2_" + RANDOM_SUFFIX;

    public static final long TAG_TYPE_COUNT = 240;

    public static final String TAG_TYPE_DESCRIPTION = "UT_TagTypeDescription_1_" + RANDOM_SUFFIX;

    public static final String TAG_TYPE_DESCRIPTION_2 = "UT_TagTypeDescription_2_" + RANDOM_SUFFIX;

    public static final String TAG_TYPE_DISPLAY_NAME = "UT_TagTypeDisplayName_1_" + RANDOM_SUFFIX;

    public static final String TAG_TYPE_DISPLAY_NAME_2 = "UT_TagTypeDisplayName_2_" + RANDOM_SUFFIX;

    public static final Integer TAG_TYPE_ORDER = 1;

    public static final Integer TAG_TYPE_ORDER_2 = 2;

    public static final String TARGET_S3_KEY = "herd-dao-test-key-prefix" + RANDOM_SUFFIX + "/" + LOCAL_FILE;

    public static final String TEST_DDL =
        "CREATE EXTERNAL TABLE `ITEMS` (\n" + "    `ORGNL_TRANSFORM` INT,\n" + "    `DATA` DOUBLE)\n" + "PARTITIONED BY (`TRANSFORM` INT)\n" +
            "ROW FORMAT DELIMITED FIELDS TERMINATED BY ',' ESCAPED BY '\\\\' COLLECTION ITEMS TERMINATED BY ',' MAP KEYS TERMINATED BY '#' " +
            "NULL DEFINED AS '\\001'\n" + "STORED AS TEXTFILE;";

    public static final String TEST_DDL_2 = "DROP TABLE `Test`;\n" + "CREATE EXTERNAL TABLE `TEST`;";

    public static final String TEST_CSV_FILE_CONTENT = RANDOM_SUFFIX + "," + RANDOM_SUFFIX;

    public static final String TEST_S3_KEY_PREFIX = "herd-dao-test-key-prefix" + RANDOM_SUFFIX;

    public static final String TEST_S3_KEY_PREFIX_2 = "herd-dao-test-key-prefix-2-" + RANDOM_SUFFIX;

    public static final Integer THIRD_DATA_VERSION = 2;

    public static final Integer THIRD_FORMAT_VERSION = 2;

    public static final Integer THIRD_VERSION = 2;

    public static final String TIMEOUT_ACTION = "UT_TimeoutAction_" + RANDOM_SUFFIX;

    public static final Integer TIMEOUT_DURATION_MINUTES = getRandomInteger();

    public static final Long TOTAL_INDEX_SEARCH_RESULTS = (long) (Math.random() * Long.MAX_VALUE);

    public static final List<String> UNSORTED_PARTITION_VALUES =
        Arrays.asList("2014-04-02", "2014-04-04", "2014-04-03", "2014-04-02A", "2014-04-01", "2014-04-10", "2014-04-09", "2014-04-11", "2014-04-08",
            "2014-04-07", "2014-04-05", "2014-04-06");

    public static final String UPDATED_BY = "UT_UpdatedBy_" + RANDOM_SUFFIX;

    public static final XMLGregorianCalendar UPDATED_ON = HerdDateUtils.getXMLGregorianCalendarValue(getRandomDate());

    public static final String UPLOADER_ROLE_ARN = "UT_UploaderRoleArn" + RANDOM_SUFFIX;

    public static final String USERNAME = "UT_Username_" + RANDOM_SUFFIX;

    public static final String USER_CREDENTIAL_NAME = "UT_User_Credential_Name_" + RANDOM_SUFFIX;

    public static final String USER_EMAIL_ADDRESS = "UT_User_Email_Address_" + RANDOM_SUFFIX;

    public static final String USER_FULL_NAME = "UT_User_Full_Name_" + RANDOM_SUFFIX;

    public static final String USER_ID = "UT_User_Id_1@" + RANDOM_SUFFIX;

    public static final String SHORT_USER_ID = "UT_User_Id_1";

    public static final String USER_ID_2 = "UT_User_Id_2@" + RANDOM_SUFFIX;

    public static final String SHORT_USER_ID_2 = "UT_User_Id_2";

    public static final String USER_ID_3 = "UT_User_Id_3@" + RANDOM_SUFFIX;

    public static final String SHORT_USER_ID_3 = "UT_User_Id_3";

    public static final String USER_ID_4 = "UT_User_Id_4@" + RANDOM_SUFFIX;

    public static final String SHORT_USER_ID_4 = "UT_User_Id_4";

    public static final String USER_JOB_TITLE = "UT_User_Job_Title_" + RANDOM_SUFFIX;

    public static final String USER_TELEPHONE_NUMBER = "UT_User_Telephone_Number_" + RANDOM_SUFFIX;

    public static final String VALUE = "UT_Value_" + RANDOM_SUFFIX;

    public static final Integer VOLUMES_PER_INSTANCE = getRandomInteger();

    public static final String VOLUME_TYPE = "UT_VolumeType_" + RANDOM_SUFFIX;

    private static final String OVERRIDE_PROPERTY_SOURCE_MAP_NAME = "overrideMapPropertySource";

    // A holding location for a property source.
    // When we remove the property source from the environment, we will place it here as a holding area. Then when we want to add it back into the
    // environment, we will take it from this holding area and put it back in the environment. When the property source is in the environment, we
    // set this holder to null.
    public ReloadablePropertySource propertySourceHoldingLocation;

    @Autowired
    protected AllowedAttributeValueDaoTestHelper allowedAttributeValueDaoTestHelper;

    @Autowired
    protected AttributeValueListDao attributeValueListDao;

    @Autowired
    protected AttributeValueListDaoTestHelper attributeValueListDaoTestHelper;

    @Autowired
    protected BusinessObjectDataAttributeDao businessObjectDataAttributeDao;

    @Autowired
    protected BusinessObjectDataAttributeDaoTestHelper businessObjectDataAttributeDaoTestHelper;

    @Autowired
    protected BusinessObjectDataAvailabilityTestHelper businessObjectDataAvailabilityTestHelper;

    @Autowired
    protected BusinessObjectDataDao businessObjectDataDao;

    @Autowired
    protected BusinessObjectDataDaoTestHelper businessObjectDataDaoTestHelper;

    @Autowired
    protected BusinessObjectDataNotificationRegistrationDao businessObjectDataNotificationRegistrationDao;

    @Autowired
    protected BusinessObjectDataStatusDao businessObjectDataStatusDao;

    @Autowired
    protected BusinessObjectDataStatusDaoTestHelper businessObjectDataStatusDaoTestHelper;

    @Autowired
    protected BusinessObjectDefinitionColumnDao businessObjectDefinitionColumnDao;

    @Autowired
    protected BusinessObjectDefinitionColumnDaoTestHelper businessObjectDefinitionColumnDaoTestHelper;

    @Autowired
    protected BusinessObjectDefinitionDao businessObjectDefinitionDao;

    @Autowired
    protected BusinessObjectDefinitionDaoTestHelper businessObjectDefinitionDaoTestHelper;

    @Autowired
    protected BusinessObjectDefinitionDescriptionSuggestionDao businessObjectDefinitionDescriptionSuggestionDao;

    @Autowired
    protected BusinessObjectDefinitionDescriptionSuggestionDaoTestHelper businessObjectDefinitionDescriptionSuggestionDaoTestHelper;

    @Autowired
    protected BusinessObjectDefinitionDescriptionSuggestionStatusDao businessObjectDefinitionDescriptionSuggestionStatusDao;

    @Autowired
    protected BusinessObjectDefinitionDescriptionSuggestionStatusDaoTestHelper businessObjectDefinitionDescriptionSuggestionStatusDaoTestHelper;

    @Autowired
    protected BusinessObjectDefinitionSubjectMatterExpertDao businessObjectDefinitionSubjectMatterExpertDao;

    @Autowired
    protected BusinessObjectDefinitionSubjectMatterExpertDaoTestHelper businessObjectDefinitionSubjectMatterExpertDaoTestHelper;

    @Autowired
    protected BusinessObjectDefinitionTagDao businessObjectDefinitionTagDao;

    @Autowired
    protected BusinessObjectDefinitionTagDaoTestHelper businessObjectDefinitionTagDaoTestHelper;

    @Autowired
    protected BusinessObjectFormatDao businessObjectFormatDao;

    @Autowired
    protected BusinessObjectFormatDaoTestHelper businessObjectFormatDaoTestHelper;

    @Autowired
    protected BusinessObjectFormatExternalInterfaceDao businessObjectFormatExternalInterfaceDao;

    @Autowired
    protected BusinessObjectFormatExternalInterfaceDaoTestHelper businessObjectFormatExternalInterfaceDaoTestHelper;

    @Autowired
    protected ConfigurationDao configurationDao;

    @Autowired
    protected CustomDdlDao customDdlDao;

    @Autowired
    protected CustomDdlDaoTestHelper customDdlDaoTestHelper;

    @Autowired
    protected DataProviderDao dataProviderDao;

    @Autowired
    protected DataProviderDaoTestHelper dataProviderDaoTestHelper;

    @Autowired
    protected Ec2Dao ec2Dao;

    @Autowired
    protected EmrClusterDefinitionDao emrClusterDefinitionDao;

    @Autowired
    protected EmrClusterDefinitionDaoTestHelper emrClusterDefinitionDaoTestHelper;

    @Autowired
    protected EmrDao emrDao;

    @Autowired
    protected EmrVpcPricingStateFormatter emrVpcPricingStateFormatter;

    @PersistenceContext
    protected EntityManager entityManager;

    @Autowired
    protected ExpectedPartitionValueDao expectedPartitionValueDao;

    @Autowired
    protected ExpectedPartitionValueDaoTestHelper expectedPartitionValueDaoTestHelper;

    @Autowired
    protected ExternalInterfaceDao externalInterfaceDao;

    @Autowired
    protected ExternalInterfaceDaoTestHelper externalInterfaceDaoTestHelper;

    @Autowired
    protected FileTypeDao fileTypeDao;

    @Autowired
    protected FileTypeDaoTestHelper fileTypeDaoTestHelper;

    @Autowired
    protected GlobalAttributeDefinitionDao globalAttributeDefinitionDao;

    @Autowired
    protected GlobalAttributeDefinitionDaoTestHelper globalAttributeDefinitionDaoTestHelper;

    @Autowired
    protected GlobalAttributeDefinitionLevelDao globalAttributeDefinitionLevelDao;

    @Autowired
    protected GlobalAttributeDefinitionLevelDaoTestHelper globalAttributeDefinitionLevelDaoTestHelper;

    @Autowired
    protected HerdCollectionHelper herdCollectionHelper;

    // Provide easy access to the herd DAO for all test methods.
    @Autowired
    protected HerdDao herdDao;

    @Autowired
    protected JavaPropertiesHelper javaPropertiesHelper;

    @Autowired
    protected JdbcDao jdbcDao;

    @Autowired
    protected JobDefinitionDao jobDefinitionDao;

    @Autowired
    protected JobDefinitionDaoTestHelper jobDefinitionDaoTestHelper;

    @Autowired
    protected KmsDao kmsDao;

    @Autowired
    protected LdapOperations ldapOperations;

    @Autowired
    protected MessageTypeDao messageTypeDao;

    @Autowired
    protected MessageTypeDaoTestHelper messageTypeDaoTestHelper;

    @Autowired
    protected NamespaceDao namespaceDao;

    @Autowired
    protected NamespaceDaoTestHelper namespaceDaoTestHelper;

    @Autowired
    protected NotificationEventTypeDao notificationEventTypeDao;

    @Autowired
    protected NotificationMessageDao notificationMessageDao;

    @Autowired
    protected NotificationMessageDaoTestHelper notificationMessageDaoTestHelper;

    @Autowired
    protected NotificationRegistrationDao notificationRegistrationDao;

    @Autowired
    protected NotificationRegistrationDaoTestHelper notificationRegistrationDaoTestHelper;

    @Autowired
    protected NotificationRegistrationStatusDao notificationRegistrationStatusDao;

    @Autowired
    protected PartitionKeyGroupDao partitionKeyGroupDao;

    @Autowired
    protected PartitionKeyGroupDaoTestHelper partitionKeyGroupDaoTestHelper;

    @Autowired
    protected RetentionTypeDao retentionTypeDao;

    @Autowired
    protected RetentionTypeDaoTestHelper retentionTypeDaoTestHelper;

    @Autowired
    protected S3Dao s3Dao;

    @Autowired
    protected S3DaoTestHelper s3DaoTestHelper;

    @Autowired
    protected S3Operations s3Operations;

    @Autowired
    protected SchemaColumnDao schemaColumnDao;

    @Autowired
    protected SchemaColumnDaoTestHelper schemaColumnDaoTestHelper;

    @Autowired
    protected SearchIndexDao searchIndexDao;

    @Autowired
    protected SearchIndexDaoTestHelper searchIndexDaoTestHelper;

    @Autowired
    protected SearchIndexStatusDao searchIndexStatusDao;

    @Autowired
    protected SearchIndexStatusDaoTestHelper searchIndexStatusDaoTestHelper;

    @Autowired
    protected SearchIndexTypeDao searchIndexTypeDao;

    @Autowired
    protected SearchIndexTypeDaoTestHelper searchIndexTypeDaoTestHelper;

    @Autowired
    protected SecurityFunctionDao securityFunctionDao;

    @Autowired
    protected SecurityFunctionDaoTestHelper securityFunctionDaoTestHelper;

    @Autowired
    protected SecurityRoleDao securityRoleDao;

    @Autowired
    protected SecurityRoleDaoTestHelper securityRoleDaoTestHelper;

    @Autowired
    protected SecurityRoleFunctionDao securityRoleFunctionDao;

    @Autowired
    protected SecurityRoleFunctionDaoTestHelper securityRoleFunctionDaoTestHelper;

    @Autowired
    protected SnsDao snsDao;

    @Autowired
    protected SqsDao sqsDao;

    @Autowired
    protected StorageDao storageDao;

    @Autowired
    protected StorageDaoTestHelper storageDaoTestHelper;

    @Autowired
    protected StorageFileDao storageFileDao;

    @Autowired
    protected StorageFileDaoTestHelper storageFileDaoTestHelper;

    @Autowired
    protected StoragePlatformDao storagePlatformDao;

    @Autowired
    protected StoragePlatformDaoTestHelper storagePlatformDaoTestHelper;

    @Autowired
    protected StoragePolicyDao storagePolicyDao;

    @Autowired
    protected StoragePolicyDaoTestHelper storagePolicyDaoTestHelper;

    @Autowired
    protected StoragePolicyRuleTypeDao storagePolicyRuleTypeDao;

    @Autowired
    protected StoragePolicyRuleTypeDaoTestHelper storagePolicyRuleTypeDaoTestHelper;

    @Autowired
    protected StoragePolicyStatusDao storagePolicyStatusDao;

    @Autowired
    protected StoragePolicyTransitionTypeDao storagePolicyTransitionTypeDao;

    @Autowired
    protected StoragePolicyTransitionTypeDaoTestHelper storagePolicyTransitionTypeDaoTestHelper;

    @Autowired
    protected StorageUnitDao storageUnitDao;

    @Autowired
    protected StorageUnitDaoTestHelper storageUnitDaoTestHelper;

    @Autowired
    protected StorageUnitNotificationRegistrationDao storageUnitNotificationRegistrationDao;

    @Autowired
    protected StorageUnitStatusDao storageUnitStatusDao;

    @Autowired
    protected StorageUnitStatusDaoTestHelper storageUnitStatusDaoTestHelper;

    @Autowired
    protected StsDao stsDao;

    @Autowired
    protected SubjectMatterExpertDao subjectMatterExpertDao;

    @Autowired
    protected TagDao tagDao;

    @Autowired
    protected TagDaoTestHelper tagDaoTestHelper;

    @Autowired
    protected TagTypeDao tagTypeDao;

    @Autowired
    protected TagTypeDaoTestHelper tagTypeDaoTestHelper;

    @Autowired
    protected TrustingAccountDao trustingAccountDao;

    @Autowired
    protected TrustingAccountDaoTestHelper trustingAccountDaoTestHelper;

    @Autowired
    protected UserDao userDao;

    @Autowired
    protected UserDaoTestHelper userDaoTestHelper;

    @Autowired
    protected UserNamespaceAuthorizationDao userNamespaceAuthorizationDao;

    @Autowired
    protected UserNamespaceAuthorizationDaoTestHelper userNamespaceAuthorizationDaoTestHelper;

    /**
     * Returns a random timestamp.
     */
    public static DateTime getRandomDateTime()
    {
        DateTime dt = new DateTime(new Random().nextLong()).withMillisOfSecond(0);
        return dt.withYear(2019);
    }

    /**
     * Returns a mixed case string.
     *
     * @param input a string
     *
     * @return a mixed case string
     */
    public String mixedCase(String input)
    {
        StringBuilder result = new StringBuilder(input.length());

        int index = 0;

        for (char character : input.toCharArray())
        {
            if (Character.isLetter(character))
            {
                if (index % 2 == 0)
                {
                    result.append(Character.toUpperCase(character));
                }
                else
                {
                    result.append(Character.toLowerCase(character));
                }
                index++;
            }
            else
            {
                result.append(character);
            }
        }

        return result.toString();
    }

    /**
     * Modifies the re-loadable property source. Copies all the existing properties and overrides with the properties passed in the map.
     *
     * @param overrideMap a map containing the properties.
     *
     * @throws Exception if the property source couldn't be modified.
     */
    protected void modifyPropertySourceInEnvironment(Map<String, Object> overrideMap) throws Exception
    {
        removeReloadablePropertySourceFromEnvironment();

        Map<String, Object> updatedPropertiesMap = new HashMap<>();
        updatedPropertiesMap.putAll(propertySourceHoldingLocation.getSource());
        updatedPropertiesMap.putAll(overrideMap);

        // Re-add in the property source we previously removed.
        getMutablePropertySources().addLast(new MapPropertySource(OVERRIDE_PROPERTY_SOURCE_MAP_NAME, updatedPropertiesMap));
    }

    /**
     * Removes the re-loadable properties source from the environment. It must not have been removed already. It can be added back using the
     * addReloadablePropertySourceToEnvironment method.
     *
     * @throws Exception if the property source couldn't be removed.
     */
    protected void removeReloadablePropertySourceFromEnvironment() throws Exception
    {
        // If the property source is in the holding location, then it has already been removed from the environment so throw an exception since it
        // shouldn't be removed again (i.e. it should be re-added first and then possibly removed again if needed).
        if (propertySourceHoldingLocation != null)
        {
            throw new Exception("Reloadable property source has already been removed.");
        }

        MutablePropertySources mutablePropertySources = getMutablePropertySources();
        propertySourceHoldingLocation = (ReloadablePropertySource) mutablePropertySources.remove(ReloadablePropertySource.class.getName());

        // Verify that the property source was removed and returned.
        if (propertySourceHoldingLocation == null)
        {
            throw new Exception("Property source with name \"" + ReloadablePropertySource.class.getName() +
                "\" is not configured and couldn't be removed from the environment.");
        }
    }

    /**
     * Restores the re-loadable property source back into the environment. It must have first been removed using the modifyPropertySourceInEnvironment method.
     *
     * @throws Exception if the property source wasn't previously removed or couldn't be re-added.
     */
    protected void restorePropertySourceInEnvironment() throws Exception
    {
        // If the property source isn't in the holding area, then it hasn't yet been removed from the environment so throw an exception informing the
        // caller that it first needs to be removed before it can be added back in.
        if (propertySourceHoldingLocation == null)
        {
            throw new Exception("Reloadable property source hasn't yet been removed so it can not be re-added.");
        }

        // Remove the modified map
        MutablePropertySources mutablePropertySources = getMutablePropertySources();
        mutablePropertySources.remove(OVERRIDE_PROPERTY_SOURCE_MAP_NAME);

        // Re-add in the property source we previously removed.
        getMutablePropertySources().addLast(propertySourceHoldingLocation);

        // Remove the property source so we know it was re-added.
        propertySourceHoldingLocation = null;
    }
}
