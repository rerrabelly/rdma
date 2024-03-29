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
package org.finra.herd.tools.access.validator;

import static org.finra.herd.tools.access.validator.PropertiesHelper.ACCESS_TOKEN_URL_PROPERTY;
import static org.finra.herd.tools.access.validator.PropertiesHelper.AWS_REGION_PROPERTY;
import static org.finra.herd.tools.access.validator.PropertiesHelper.AWS_ROLE_ARN_PROPERTY;
import static org.finra.herd.tools.access.validator.PropertiesHelper.AWS_SQS_QUEUE_URL_PROPERTY;
import static org.finra.herd.tools.access.validator.PropertiesHelper.BUSINESS_OBJECT_DATA_VERSION_PROPERTY;
import static org.finra.herd.tools.access.validator.PropertiesHelper.BUSINESS_OBJECT_DEFINITION_NAME_PROPERTY;
import static org.finra.herd.tools.access.validator.PropertiesHelper.BUSINESS_OBJECT_FORMAT_FILE_TYPE_PROPERTY;
import static org.finra.herd.tools.access.validator.PropertiesHelper.BUSINESS_OBJECT_FORMAT_USAGE_PROPERTY;
import static org.finra.herd.tools.access.validator.PropertiesHelper.BUSINESS_OBJECT_FORMAT_VERSION_PROPERTY;
import static org.finra.herd.tools.access.validator.PropertiesHelper.HERD_BASE_URL_PROPERTY;
import static org.finra.herd.tools.access.validator.PropertiesHelper.HERD_PASSWORD_PROPERTY;
import static org.finra.herd.tools.access.validator.PropertiesHelper.HERD_USERNAME_PROPERTY;
import static org.finra.herd.tools.access.validator.PropertiesHelper.NAMESPACE_PROPERTY;
import static org.finra.herd.tools.access.validator.PropertiesHelper.PRIMARY_PARTITION_VALUE_PROPERTY;
import static org.finra.herd.tools.access.validator.PropertiesHelper.SUB_PARTITION_VALUES_PROPERTY;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.UUID;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import org.finra.herd.core.HerdStringUtils;
import org.finra.herd.dao.S3Operations;
import org.finra.herd.model.api.xml.BusinessObjectDataKey;
import org.finra.herd.sdk.api.ApplicationApi;
import org.finra.herd.sdk.api.BusinessObjectDataApi;
import org.finra.herd.sdk.api.CurrentUserApi;
import org.finra.herd.sdk.invoker.ApiClient;
import org.finra.herd.sdk.invoker.ApiException;
import org.finra.herd.sdk.model.Attribute;
import org.finra.herd.sdk.model.BusinessObjectData;
import org.finra.herd.sdk.model.StorageFile;
import org.finra.herd.tools.common.databridge.OAuthTokenProvider;

/**
 * The controller for the application.
 */
@Component
class AccessValidatorController
{
    static final String S3_BUCKET_NAME_ATTRIBUTE = "bucket.name";

    private static final String LINE_FEED = "\n\n\n";

    private static final Logger LOGGER = LoggerFactory.getLogger(AccessValidatorController.class);

    @Autowired
    private HerdApiClientOperations herdApiClientOperations;

    @Autowired
    private PropertiesHelper propertiesHelper;

    @Autowired
    private OAuthTokenProvider oauthTokenProvider;

    @Autowired
    private S3Operations s3Operations;

    /**
     * Runs the application with the given command line arguments.
     *
     * @param propertiesFile the properties file
     * @param messageFlag    the message flag that specifies to read SQS message
     * @throws IOException  if an I/O error was encountered
     * @throws ApiException if a Herd API client error was encountered
     */
    void validateAccess(File propertiesFile, Boolean messageFlag) throws IOException, ApiException
    {
        // Load properties.
        propertiesHelper.loadProperties(propertiesFile);

        // Check properties
        herdApiClientOperations.checkPropertiesFile(propertiesHelper, messageFlag);

        // Create the API client to a specific REST endpoint with proper authentication.
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(propertiesHelper.getProperty(HERD_BASE_URL_PROPERTY));
        apiClient.setUsername(propertiesHelper.getProperty(HERD_USERNAME_PROPERTY));
        apiClient.setPassword(propertiesHelper.getProperty(HERD_PASSWORD_PROPERTY));

        if (!propertiesHelper.isBlankOrNull(ACCESS_TOKEN_URL_PROPERTY))
        {
            apiClient.setAccessToken(
                oauthTokenProvider.getAccessToken(propertiesHelper.getProperty(HERD_USERNAME_PROPERTY), propertiesHelper.getProperty(HERD_PASSWORD_PROPERTY),
                    propertiesHelper.getProperty(ACCESS_TOKEN_URL_PROPERTY)));
        }

        // Setup specific API classes.
        ApplicationApi applicationApi = new ApplicationApi(apiClient);
        CurrentUserApi currentUserApi = new CurrentUserApi(apiClient);

        // Retrieve build information from the registration server.
        LOGGER.info("Retrieving build information from the registration server...");
        LOGGER.info("{}", herdApiClientOperations.applicationGetBuildInfo(applicationApi));

        // Retrieve user information from the registration server.
        LOGGER.info("Retrieving user information from the registration server...");
        LOGGER.info("{}", herdApiClientOperations.currentUserGetCurrentUser(currentUserApi));

        // Create AWS client configuration.
        ClientConfiguration clientConfiguration = new ClientConfiguration();

        // Get AWS region.
        String awsRegion = propertiesHelper.getProperty(AWS_REGION_PROPERTY);

        // Get ARN for the AWS role to assume.
        String awsRoleArn = propertiesHelper.getProperty(AWS_ROLE_ARN_PROPERTY);
        LOGGER.info("Assuming \"{}\" AWS role...", awsRoleArn);
        AWSCredentialsProvider awsCredentialsProvider = new STSAssumeRoleSessionCredentialsProvider.Builder(awsRoleArn, UUID.randomUUID().toString())
            .withStsClient(AWSSecurityTokenServiceClientBuilder.standard().withClientConfiguration(clientConfiguration).withRegion(awsRegion).build()).build();

        // Create AWS S3 client using the assumed role.
        LOGGER.info("Creating AWS S3 client using role: \"{}\".", awsRoleArn);

        AmazonS3 amazonS3 =
            AmazonS3ClientBuilder.standard().withCredentials(awsCredentialsProvider).withClientConfiguration(clientConfiguration).withRegion(awsRegion).build();

        // Create AWS SQS client using the assumed role.
        LOGGER.info("Creating AWS SQS client using role: \"{}\".", awsRoleArn);

        AmazonSQS amazonSQS =
            AmazonSQSClientBuilder.standard().withCredentials(awsCredentialsProvider).withClientConfiguration(clientConfiguration).withRegion(awsRegion)
                .build();

        BusinessObjectDataKey bdataKey;

        // Check if -m flag passed
        if (messageFlag)
        {
            String sqsQueueUrl = propertiesHelper.getProperty(AWS_SQS_QUEUE_URL_PROPERTY);
            LOGGER.info("Getting message from SQS queue: {}", sqsQueueUrl);
            bdataKey = herdApiClientOperations.getBdataKeySqs(amazonSQS, sqsQueueUrl);
        }
        else
        {
            LOGGER.info("Creating BusinessObjectDataKey from properties file");
            bdataKey = getBusinessObjectDataKeyFromPropertiesFile();
        }
        LOGGER.info("Using business object data key: {}", bdataKey);

        BusinessObjectDataApi businessObjectDataApi = new BusinessObjectDataApi(apiClient);

        // Retrieve business object data from the registration server.
        LOGGER.info("Retrieving business object data information from the registration server...");
        BusinessObjectData businessObjectData = herdApiClientOperations
            .businessObjectDataGetBusinessObjectData(businessObjectDataApi, bdataKey.getNamespace(), bdataKey.getBusinessObjectDefinitionName(),
                bdataKey.getBusinessObjectFormatUsage(), bdataKey.getBusinessObjectFormatFileType(), null, bdataKey.getPartitionValue(),
                StringUtils.join(bdataKey.getSubPartitionValues(), "|"), bdataKey.getBusinessObjectFormatVersion(), bdataKey.getBusinessObjectDataVersion(),
                null, false, false, false);

        // Log business object data information returned by the registration server.
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        LOGGER.info("{}", gson.toJson(businessObjectData));

        // Check if retrieved business object data has storage unit registered with it.
        Assert.isTrue(CollectionUtils.isNotEmpty(businessObjectData.getStorageUnits()), "Business object data has no storage unit registered with it.");
        Assert.isTrue(businessObjectData.getStorageUnits().get(0).getStorage() != null, "Business object data storage unit does not have storage information.");

        // Get S3 bucket name.
        String bucketName = null;
        for (Attribute attribute : businessObjectData.getStorageUnits().get(0).getStorage().getAttributes())
        {
            if (StringUtils.equals(attribute.getName(), S3_BUCKET_NAME_ATTRIBUTE))
            {
                bucketName = attribute.getValue();
                break;
            }
        }
        Assert.isTrue(StringUtils.isNotBlank(bucketName), "S3 bucket name is not configured for the storage.");

        // Validate that S3 files registered with the business object data under can be streamed to disk.
        LOGGER.info("Validating that S3 files registered with the business object data are downloadable.");

        // Initialize a flag to be used to determine if we did not find any non-zero byte files registered for this business object data.
        boolean readAccessValidated = false;

        // If business object data has registered storage files, we go though the list until we fail
        // accessing S3 metadata or find a non-zero byte file that we can try to download.
        if (CollectionUtils.isNotEmpty(businessObjectData.getStorageUnits().get(0).getStorageFiles()))
        {
            // Loop through the list of storage files and attempt to read at least one file which has valid content.
            for (StorageFile storageFile : businessObjectData.getStorageUnits().get(0).getStorageFiles())
            {
                LOGGER.info("Attempting to read \"{}/{}\" S3 file...", bucketName, storageFile.getFilePath());

                // Get S3 object metadata.
                ObjectMetadata objectMetadata = s3Operations.getObjectMetadata(bucketName, storageFile.getFilePath(), amazonS3);

                // Try to verify read access to the S3 object.
                if (verifyReadAccessToS3Object(bucketName, storageFile.getFilePath(), objectMetadata.getContentLength(), amazonS3))
                {
                    readAccessValidated = true;
                    break;
                }
            }
        }
        // If business object data has no registered storage files, we go though the list of S3 files found under
        // the storage unit directory path (S3 key prefix) until we find a non-zero byte file that we can try to download.
        else
        {
            // Check if storage unit has a non-blank directory path.
            Assert.isTrue(businessObjectData.getStorageUnits().get(0).getStorageDirectory() != null &&
                    StringUtils.isNotBlank(businessObjectData.getStorageUnits().get(0).getStorageDirectory().getDirectoryPath()),
                "" + "No storage files or directory path is registered with the business object data storage unit.");

            // Since storage unit directory path represents a directory, we add a trailing '/' character to it.
            String getS3KeyPrefix = StringUtils.appendIfMissing(businessObjectData.getStorageUnits().get(0).getStorageDirectory().getDirectoryPath(), "/");

            // List all S3 files located under the S3 prefix.
            // We are not using pagination here assuming that AWS page limit is enough to find at least one non-zero byte file.
            LOGGER.info("Attempting to list S3 files located under \"{}/{}\" S3 key prefix...", bucketName, getS3KeyPrefix);
            ListObjectsRequest listObjectsRequest = new ListObjectsRequest().withBucketName(bucketName).withPrefix(getS3KeyPrefix);
            ObjectListing objectListing = s3Operations.listObjects(listObjectsRequest, amazonS3);

            for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries())
            {
                // Try to verify read access to the S3 object.
                if (verifyReadAccessToS3Object(bucketName, objectSummary.getKey(), objectSummary.getSize(), amazonS3))
                {
                    readAccessValidated = true;
                    break;
                }
            }
        }

        // Report success if we were able to find a non-zero byte file and verify read
        // access by downloading some number of bytes from the beginning of the file.
        if (readAccessValidated)
        {
            LOGGER.info("{}Finished: SUCCESS", LINE_FEED);
        }
        // Otherwise, report a failure.
        else
        {
            LOGGER.error("{}Could not read valid content from any file: FAILURE", LINE_FEED);
        }
    }

    /**
     * Gets business object data key from the properties file.
     *
     * @return the business object data key
     */
    private BusinessObjectDataKey getBusinessObjectDataKeyFromPropertiesFile()
    {
        BusinessObjectDataKey businessObjectDataKey = new BusinessObjectDataKey();

        Integer businessObjectFormatVersion =
            HerdStringUtils.convertStringToInteger(propertiesHelper.getProperty(BUSINESS_OBJECT_FORMAT_VERSION_PROPERTY), null);
        Integer businessObjectDataVersion = HerdStringUtils.convertStringToInteger(propertiesHelper.getProperty(BUSINESS_OBJECT_DATA_VERSION_PROPERTY), null);

        businessObjectDataKey.setNamespace(propertiesHelper.getProperty(NAMESPACE_PROPERTY));
        businessObjectDataKey.setBusinessObjectDefinitionName(propertiesHelper.getProperty(BUSINESS_OBJECT_DEFINITION_NAME_PROPERTY));
        businessObjectDataKey.setBusinessObjectFormatUsage(propertiesHelper.getProperty(BUSINESS_OBJECT_FORMAT_USAGE_PROPERTY));
        businessObjectDataKey.setBusinessObjectFormatFileType(propertiesHelper.getProperty(BUSINESS_OBJECT_FORMAT_FILE_TYPE_PROPERTY));
        businessObjectDataKey.setPartitionValue(propertiesHelper.getProperty(PRIMARY_PARTITION_VALUE_PROPERTY));

        String subpartition = propertiesHelper.getProperty(SUB_PARTITION_VALUES_PROPERTY);
        if (subpartition != null)
        {
            businessObjectDataKey.setSubPartitionValues(Arrays.asList(subpartition.split("\\s*\\|\\s*")));
        }
        else
        {
            businessObjectDataKey.setSubPartitionValues(null);
        }

        businessObjectDataKey.setBusinessObjectFormatVersion(businessObjectFormatVersion);
        businessObjectDataKey.setBusinessObjectDataVersion(businessObjectDataVersion);

        return businessObjectDataKey;
    }

    /**
     * Verifies read access to an S3 object by downloading up to 200 bytes from the beginning of the file. If object size is zero, the method logs a warning
     * message and returns false.
     *
     * @param bucketName   the name of the bucket containing the S3 object
     * @param s3ObjectKey  the key in the specified bucket under which the S3 object is stored
     * @param s3ObjectSize the size of the S3 object in bytes
     * @param amazonS3     the interface to access Amazon S3 web service
     * @return true if able to download a range of bytes from the S3 object, false otherwise
     * @throws IOException if an I/O error was encountered
     */
    private boolean verifyReadAccessToS3Object(String bucketName, String s3ObjectKey, long s3ObjectSize, AmazonS3 amazonS3) throws IOException
    {
        final long maxBytesToRead = 200;

        boolean readAccessVerified = false;

        // Ignore zero-byte S3 files.
        if (s3ObjectSize > 0L)
        {
            // Attempt to read content of the file from S3. Try to get only the first 200 bytes to prevent downloading massive files.
            try (S3Object s3Object = s3Operations
                .getS3Object(new GetObjectRequest(bucketName, s3ObjectKey).withRange(0, Math.min(s3ObjectSize, maxBytesToRead)), amazonS3))
            {
                StringWriter stringWriter = new StringWriter();
                IOUtils.copy(s3Object.getObjectContent(), stringWriter, Charset.defaultCharset());
                readAccessVerified = true;
            }
        }
        // Otherwise, log a warning message and return false.
        else
        {
            LOGGER.warn("Encountered empty file: \"{}/{}\". Skipping.", bucketName, s3ObjectKey);
        }

        return readAccessVerified;
    }
}
