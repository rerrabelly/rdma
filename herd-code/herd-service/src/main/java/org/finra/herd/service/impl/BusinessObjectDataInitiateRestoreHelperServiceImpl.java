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
package org.finra.herd.service.impl;

import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.StorageClass;
import com.amazonaws.services.s3.model.Tier;
import com.amazonaws.services.s3control.model.S3GlacierJobTier;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import org.finra.herd.core.HerdDateUtils;
import org.finra.herd.core.helper.ConfigurationHelper;
import org.finra.herd.dao.S3Dao;
import org.finra.herd.dao.StorageUnitDao;
import org.finra.herd.dao.helper.HerdStringHelper;
import org.finra.herd.dao.helper.JsonHelper;
import org.finra.herd.model.annotation.PublishNotificationMessages;
import org.finra.herd.model.api.xml.BusinessObjectData;
import org.finra.herd.model.api.xml.BusinessObjectDataKey;
import org.finra.herd.model.api.xml.StorageFile;
import org.finra.herd.model.dto.BatchJobConfigDto;
import org.finra.herd.model.dto.BusinessObjectDataBatchRestoreDto;
import org.finra.herd.model.dto.BusinessObjectDataRestoreDto;
import org.finra.herd.model.dto.ConfigurationValue;
import org.finra.herd.model.dto.S3FileTransferRequestParamsDto;
import org.finra.herd.model.jpa.BusinessObjectDataEntity;
import org.finra.herd.model.jpa.StoragePlatformEntity;
import org.finra.herd.model.jpa.StorageUnitEntity;
import org.finra.herd.model.jpa.StorageUnitStatusEntity;
import org.finra.herd.service.BusinessObjectDataInitiateRestoreHelperService;
import org.finra.herd.service.S3Service;
import org.finra.herd.service.helper.BusinessObjectDataDaoHelper;
import org.finra.herd.service.helper.BusinessObjectDataHelper;
import org.finra.herd.service.helper.S3KeyPrefixHelper;
import org.finra.herd.service.helper.StorageFileHelper;
import org.finra.herd.service.helper.StorageHelper;
import org.finra.herd.service.helper.StorageUnitDaoHelper;
import org.finra.herd.service.helper.StorageUnitStatusDaoHelper;

/**
 * An implementation of the helper service class for the business object data initiate a restore request functionality.
 */
@Service
public class BusinessObjectDataInitiateRestoreHelperServiceImpl implements BusinessObjectDataInitiateRestoreHelperService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BusinessObjectDataInitiateRestoreHelperServiceImpl.class);

    @Autowired
    private BusinessObjectDataDaoHelper businessObjectDataDaoHelper;

    @Autowired
    private BusinessObjectDataHelper businessObjectDataHelper;

    @Autowired
    private ConfigurationHelper configurationHelper;

    @Autowired
    private HerdStringHelper herdStringHelper;

    @Autowired
    private JsonHelper jsonHelper;

    @Autowired
    private S3KeyPrefixHelper s3KeyPrefixHelper;

    @Autowired
    private S3Service s3Service;

    @Autowired
    private S3Dao s3Dao;

    @Autowired
    private StorageFileHelper storageFileHelper;

    @Autowired
    private StorageHelper storageHelper;

    @Autowired
    private StorageUnitDao storageUnitDao;

    @Autowired
    private StorageUnitDaoHelper storageUnitDaoHelper;

    @Autowired
    private StorageUnitStatusDaoHelper storageUnitStatusDaoHelper;

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation starts a new transaction.
     */
    @PublishNotificationMessages
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BusinessObjectData executeInitiateRestoreAfterStep(BusinessObjectDataRestoreDto businessObjectDataRestoreDto)
    {
        return executeInitiateRestoreAfterStepImpl(businessObjectDataRestoreDto);
    }

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation executes non-transactionally, suspends the current transaction if one exists.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void executeS3SpecificSteps(BusinessObjectDataRestoreDto businessObjectDataRestoreDto)
    {
        executeS3SpecificStepsImpl(businessObjectDataRestoreDto);
    }

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation starts a new transaction.
     */
    @PublishNotificationMessages
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BusinessObjectDataRestoreDto prepareToInitiateRestore(BusinessObjectDataKey businessObjectDataKey, Integer expirationInDays,
        String archiveRetrievalOption, Boolean batchMode)
    {
        return prepareToInitiateRestoreImpl(businessObjectDataKey, expirationInDays, archiveRetrievalOption, batchMode);
    }

    /**
     * Executes an after step for the initiation of a business object data restore request.
     *
     * @param businessObjectDataRestoreDto the DTO that holds various parameters needed to perform a business object data restore
     *
     * @return the business object data information
     */
    protected BusinessObjectData executeInitiateRestoreAfterStepImpl(BusinessObjectDataRestoreDto businessObjectDataRestoreDto)
    {
        // Retrieve the business object data and ensure it exists.
        BusinessObjectDataEntity businessObjectDataEntity =
            businessObjectDataDaoHelper.getBusinessObjectDataEntity(businessObjectDataRestoreDto.getBusinessObjectDataKey());

        // On failure, set the storage unit status back to ARCHIVED.
        if (businessObjectDataRestoreDto.getException() != null)
        {
            // Retrieve the storage unit and ensure it exists.
            StorageUnitEntity storageUnitEntity =
                storageUnitDaoHelper.getStorageUnitEntity(businessObjectDataRestoreDto.getStorageName(), businessObjectDataEntity);

            // Retrieve and ensure the ARCHIVED storage unit status entity exists.
            StorageUnitStatusEntity newStorageUnitStatusEntity = storageUnitStatusDaoHelper.getStorageUnitStatusEntity(StorageUnitStatusEntity.ARCHIVED);

            // Save the old storage unit status value.
            String oldStorageUnitStatus = storageUnitEntity.getStatus().getCode();

            // Update the S3 storage unit status to ARCHIVED.
            String reason = StorageUnitStatusEntity.ARCHIVED;
            storageUnitDaoHelper.updateStorageUnitStatus(storageUnitEntity, newStorageUnitStatusEntity, reason);

            // Update the new and old storage unit status values for the storage unit in the business object data restore DTO.
            businessObjectDataRestoreDto.setNewStorageUnitStatus(newStorageUnitStatusEntity.getCode());
            businessObjectDataRestoreDto.setOldStorageUnitStatus(oldStorageUnitStatus);
        }

        // Create and return the business object data object from the entity.
        BusinessObjectData businessObjectData = businessObjectDataHelper.createBusinessObjectDataFromEntity(businessObjectDataEntity);

        // Return the business object data information.
        return businessObjectData;
    }

    /**
     * Executes S3 specific steps for the initiation of a business object data restore request. The method also updates the specified DTO.
     *
     * @param businessObjectDataRestoreDto the DTO that holds various parameters needed to perform a business object data restore
     */
    protected void executeS3SpecificStepsImpl(BusinessObjectDataRestoreDto businessObjectDataRestoreDto)
    {
        try
        {
            // Create an S3 file transfer parameters DTO to access the S3 bucket.
            // Since the S3 key prefix represents a directory, we add a trailing '/' character to it.
            S3FileTransferRequestParamsDto s3FileTransferRequestParamsDto = storageHelper.getS3FileTransferRequestParamsDto();
            s3FileTransferRequestParamsDto.setS3BucketName(businessObjectDataRestoreDto.getS3BucketName());
            s3FileTransferRequestParamsDto.setS3Endpoint(businessObjectDataRestoreDto.getS3Endpoint());
            s3FileTransferRequestParamsDto.setS3KeyPrefix(StringUtils.appendIfMissing(businessObjectDataRestoreDto.getS3KeyPrefix(), "/"));

            // Get a list of S3 files matching the S3 key prefix. When listing S3 files, we ignore 0 byte objects that represent S3 directories.
            List<S3ObjectSummary> actualS3Files = s3Service.listDirectory(s3FileTransferRequestParamsDto, true);

            // For directory only registration, we have no registered storage files to check against actual S3 files.
            if (CollectionUtils.isNotEmpty(businessObjectDataRestoreDto.getStorageFiles()))
            {
                // Validate existence and file size of the S3 files.
                storageFileHelper.validateRegisteredS3Files(businessObjectDataRestoreDto.getStorageFiles(), actualS3Files,
                    businessObjectDataRestoreDto.getStorageName(), businessObjectDataRestoreDto.getBusinessObjectDataKey());
            }

            // Validate that all files to be restored are currently archived in Glacier or DeepArchive storage class.
            // Fail on any S3 file that does not have Glacier or DeepArchive storage class. This can happen when request to restore business object
            // data is posted after business object data archiving transition is executed (relative S3 objects get tagged),
            // but before AWS actually transitions the S3 files to Glacier or DeepArchive (changes S3 object storage class to Glacier or DeepArchive).
            for (S3ObjectSummary s3ObjectSummary : actualS3Files)
            {
                if (!(StringUtils.equals(s3ObjectSummary.getStorageClass(), StorageClass.Glacier.toString()) ||
                    StringUtils.equals(s3ObjectSummary.getStorageClass(), StorageClass.DeepArchive.toString())))
                {
                    throw new IllegalArgumentException(
                        String.format("S3 file \"%s\" is not archived (found %s storage class when expecting %s or %s). S3 Bucket Name: \"%s\"",
                            s3ObjectSummary.getKey(), s3ObjectSummary.getStorageClass(), StorageClass.Glacier.toString(), StorageClass.DeepArchive.toString(),
                            s3FileTransferRequestParamsDto.getS3BucketName()));
                }
            }

            // Set a list of files to restore.
            s3FileTransferRequestParamsDto.setFiles(storageFileHelper.getFiles(storageFileHelper.createStorageFilesFromS3ObjectSummaries(actualS3Files)));

            // Check if the operation needs to be executed in batch mode.
            if (businessObjectDataRestoreDto instanceof BusinessObjectDataBatchRestoreDto)
            {
                // Create and execute s3 batch restore job with given parameters
                s3Dao.batchRestoreObjects(s3FileTransferRequestParamsDto, ((BusinessObjectDataBatchRestoreDto) businessObjectDataRestoreDto).getJobConfig(),
                    36135, businessObjectDataRestoreDto.getArchiveRetrievalOption());
            }
            else
            {
                // Initiate restore requests for the list of objects in the Glacier bucket.
                s3Dao.restoreObjects(s3FileTransferRequestParamsDto, 36135, businessObjectDataRestoreDto.getArchiveRetrievalOption());
            }
        }
        catch (RuntimeException e)
        {
            // Log the exception.
            LOGGER.error("Failed to initiate a restore request for the business object data. businessObjectDataKey={}",
                jsonHelper.objectToJson(businessObjectDataRestoreDto.getBusinessObjectDataKey()), e);

            // Update the DTO with the caught exception.
            businessObjectDataRestoreDto.setException(e);
        }
    }

    /**
     * Retrieves storage unit for the business object data. The method validates that there one and only one storage unit for this business object data in
     * "ARCHIVED" or "RESTORED" state.
     *
     * @param businessObjectDataEntity the business object data entity
     *
     * @return the archived storage unit entity
     */
    protected StorageUnitEntity getStorageUnit(BusinessObjectDataEntity businessObjectDataEntity)
    {
        // Retrieve all S3 storage units for this business object data.
        List<StorageUnitEntity> s3StorageUnitEntities =
            storageUnitDao.getStorageUnitsByStoragePlatformAndBusinessObjectData(StoragePlatformEntity.S3, businessObjectDataEntity);

        // Validate that business object data has at least one S3 storage unit.
        if (CollectionUtils.isEmpty(s3StorageUnitEntities))
        {
            throw new IllegalArgumentException(String.format("Business object data has no S3 storage unit. Business object data: {%s}",
                businessObjectDataHelper.businessObjectDataEntityAltKeyToString(businessObjectDataEntity)));
        }

        // Validate that this business object data has no multiple S3 storage units.
        if (CollectionUtils.size(s3StorageUnitEntities) > 1)
        {
            throw new IllegalArgumentException(
                String.format("Business object data has multiple (%s) %s storage units. Business object data: {%s}", s3StorageUnitEntities.size(),
                    StoragePlatformEntity.S3, businessObjectDataHelper.businessObjectDataEntityAltKeyToString(businessObjectDataEntity)));
        }

        // Get the S3 storage unit.
        StorageUnitEntity storageUnitEntity = s3StorageUnitEntities.get(0);

        // Get the storage unit status code.
        String storageUnitStatus = storageUnitEntity.getStatus().getCode();

        // Validate that this business object data has its S3 storage unit in "ARCHIVED" state.
        if (!StorageUnitStatusEntity.ARCHIVED.equals(storageUnitStatus) && !StorageUnitStatusEntity.RESTORED.equals(storageUnitStatus))
        {
            // Get the storage name.
            String storageName = storageUnitEntity.getStorage().getName();

            // Fail with a custom error message if the S3 storage unit is already enabled.
            if (StorageUnitStatusEntity.ENABLED.equals(storageUnitStatus))
            {
                throw new IllegalArgumentException(
                    String.format("Business object data is already available in \"%s\" S3 storage. Business object data: {%s}", storageName,
                        businessObjectDataHelper.businessObjectDataEntityAltKeyToString(storageUnitEntity.getBusinessObjectData())));
            }
            // Fail with a custom error message if this business object data is already marked as being restored.
            else if (StorageUnitStatusEntity.RESTORING.equals(storageUnitStatus))
            {
                throw new IllegalArgumentException(
                    String.format("Business object data is already being restored in \"%s\" S3 storage. Business object data: {%s}", storageName,
                        businessObjectDataHelper.businessObjectDataEntityAltKeyToString(storageUnitEntity.getBusinessObjectData())));
            }
            // Else, fail and report the actual S3 storage unit status.
            else
            {
                throw new IllegalArgumentException(String.format("Business object data is not archived or restored. " +
                        "S3 storage unit in \"%s\" storage must have \"%s\" or \"%s\" status, but it actually has \"%s\" status. Business object data: {%s}",
                    storageName, StorageUnitStatusEntity.ARCHIVED, StorageUnitStatusEntity.RESTORED, storageUnitStatus,
                    businessObjectDataHelper.businessObjectDataEntityAltKeyToString(storageUnitEntity.getBusinessObjectData())));
            }
        }

        return storageUnitEntity;
    }

    /**
     * Prepares for the business object data initiate a restore request by validating the business object data along with other related database entities. The
     * method also creates and returns a business object data restore DTO.
     *
     * @param businessObjectDataKey the business object data key
     * @param expirationInDays the the time, in days, between when the business object data is restored to the S3 bucket and when it expires
     * @param archiveRetrievalOption the archive retrieval option when restoring an archived object.
     * @param batchMode the flag indicates that operation should be executed using S3 Batch Operations
     *
     * @return the DTO that holds various parameters needed to perform a business object data restore
     */
    protected BusinessObjectDataRestoreDto prepareToInitiateRestoreImpl(BusinessObjectDataKey businessObjectDataKey, Integer expirationInDays,
        String archiveRetrievalOption, Boolean batchMode)
    {
        // Validate and trim the business object data key.
        businessObjectDataHelper.validateBusinessObjectDataKey(businessObjectDataKey, true, true);

        // If expiration time is not specified, use the configured default value.
        int localExpirationInDays = expirationInDays != null ? expirationInDays :
            herdStringHelper.getConfigurationValueAsInteger(ConfigurationValue.BDATA_RESTORE_EXPIRATION_IN_DAYS_DEFAULT);

        // Validate the expiration time.
        Assert.isTrue(localExpirationInDays > 0, "Expiration in days value must be a positive integer.");

        // Trim the whitespaces
        if (archiveRetrievalOption != null)
        {
            archiveRetrievalOption = archiveRetrievalOption.trim();
        }

        // Validate the archive retrieval option
        if (StringUtils.isNotEmpty(archiveRetrievalOption))
        {
            //Validate archiveRetrievalOption to be a valid option.
            // Batch mode must provide tier in upper case and does not support Expedited, so validation is a bit different between modes
            if (BooleanUtils.isTrue(batchMode))
            {
                try
                {
                    S3GlacierJobTier.fromValue(archiveRetrievalOption);
                }
                catch (IllegalArgumentException ex)
                {
                    throw new IllegalArgumentException(String.format(
                        "The archive retrieval option value \"%s\" is invalid. Valid archive retrieval option values for S3 batch operations are:%s",
                        archiveRetrievalOption, Stream.of(S3GlacierJobTier.values()).map(Enum::name).collect(Collectors.toList())));
                }
            }
            else
            {
                try
                {
                    Tier.fromValue(archiveRetrievalOption);
                }
                catch (IllegalArgumentException ex)
                {
                    throw new IllegalArgumentException(
                        String.format("The archive retrieval option value \"%s\" is invalid. Valid archive retrieval option values are:%s",
                            archiveRetrievalOption, Stream.of(Tier.values()).map(Enum::name).collect(Collectors.toList())));
                }
            }

        }

        // Retrieve the business object data and ensure it exists.
        BusinessObjectDataEntity businessObjectDataEntity = businessObjectDataDaoHelper.getBusinessObjectDataEntity(businessObjectDataKey);

        // Retrieve and validate a Glacier storage unit for this business object data.
        StorageUnitEntity storageUnitEntity = getStorageUnit(businessObjectDataEntity);

        // Get current time.
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());

        // Compute the expiration timestamp.
        Timestamp restoreExpirationOn = HerdDateUtils.addDays(currentTime, localExpirationInDays);

        // Build the business object data restore parameters DTO.
        BusinessObjectDataRestoreDto businessObjectDataRestoreDto =
            BooleanUtils.isTrue(batchMode) ? new BusinessObjectDataBatchRestoreDto() : new BusinessObjectDataRestoreDto();

        // If storage unit is already in RESTORED state, we just need to update expiration time for the restored storage unit and get the business object data
        // information populated the business object data restore parameters DTO.
        if (StorageUnitStatusEntity.RESTORED.equals(storageUnitEntity.getStatusCode()))
        {
            // Update the expiration time for the restored storage unit and persist the entity.
            storageUnitEntity.setRestoreExpirationOn(restoreExpirationOn);
            storageUnitDao.saveAndRefresh(storageUnitEntity);

            // Populate business object data restore parameters DTO with business object data information.
            businessObjectDataRestoreDto.setBusinessObjectData(businessObjectDataHelper.createBusinessObjectDataFromEntity(businessObjectDataEntity));
        }
        // Otherwise, storage unit is in ARCHIVED stare and we continue with steps required to restore this business object data.
        else
        {
            // Get the storage name.
            String storageName = storageUnitEntity.getStorage().getName();

            // Validate that S3 storage has S3 bucket name configured.
            // Please note that since S3 bucket name attribute value is required we pass a "true" flag.
            String s3BucketName =
                storageHelper.getStorageAttributeValueByName(configurationHelper.getProperty(ConfigurationValue.S3_ATTRIBUTE_NAME_BUCKET_NAME),
                    storageUnitEntity.getStorage(), true);

            // Get storage specific S3 key prefix for this business object data.
            String s3KeyPrefix =
                s3KeyPrefixHelper.buildS3KeyPrefix(storageUnitEntity.getStorage(), businessObjectDataEntity.getBusinessObjectFormat(), businessObjectDataKey);

            // Retrieve and validate storage files registered with the storage unit.
            // This call supports directory only registration when no storage files are registered.
            List<StorageFile> storageFiles =
                storageFileHelper.getAndValidateStorageFiles(storageUnitEntity, s3KeyPrefix, storageName, businessObjectDataKey, false);

            // Validate that this storage does not have any other registered storage files that
            // start with the S3 key prefix, but belong to other business object data instances.
            storageUnitDaoHelper.validateNoExplicitlyRegisteredSubPartitionInStorageForBusinessObjectData(storageUnitEntity.getStorage(),
                businessObjectDataEntity.getBusinessObjectFormat(), businessObjectDataKey, s3KeyPrefix);

            // Set the expiration time for the restored storage unit.
            storageUnitEntity.setRestoreExpirationOn(restoreExpirationOn);

            // Retrieve and ensure the RESTORING storage unit status entity exists.
            StorageUnitStatusEntity newStorageUnitStatusEntity = storageUnitStatusDaoHelper.getStorageUnitStatusEntity(StorageUnitStatusEntity.RESTORING);

            // Save the old storage unit status value.
            String oldOriginStorageUnitStatus = storageUnitEntity.getStatus().getCode();

            // Update the S3 storage unit status to RESTORING.
            storageUnitDaoHelper.updateStorageUnitStatus(storageUnitEntity, newStorageUnitStatusEntity, StorageUnitStatusEntity.RESTORING);

            // Pull all configuration values for batch job processing
            BatchJobConfigDto batchJobConfig;
            if (BooleanUtils.isTrue(batchMode))
            {
                batchJobConfig = new BatchJobConfigDto();
                batchJobConfig.setAwsAccountId(configurationHelper.getRequiredProperty(ConfigurationValue.AWS_ACCOUNT_ID));
                batchJobConfig.setS3BatchRoleArn(configurationHelper.getRequiredProperty(ConfigurationValue.S3_BATCH_ROLE_ARN));
                batchJobConfig.setManifestS3BucketName(configurationHelper.getRequiredProperty(ConfigurationValue.S3_BATCH_MANIFEST_BUCKET_NAME));
                batchJobConfig.setManifestS3Prefix(configurationHelper.getRequiredProperty(ConfigurationValue.S3_BATCH_MANIFEST_LOCATION_PREFIX));
                batchJobConfig.setBackoffPeriod(configurationHelper.getProperty(ConfigurationValue.S3_BATCH_RESTORE_BACKOFF_PERIOD, Integer.class));
                batchJobConfig.setMaxAttempts(configurationHelper.getProperty(ConfigurationValue.S3_BATCH_RESTORE_MAX_ATTEMPTS, Integer.class));

                ((BusinessObjectDataBatchRestoreDto) businessObjectDataRestoreDto).setJobConfig(batchJobConfig);
            }

            // Populate business object data restore parameters DTO with information needed to initiate the restore.
            businessObjectDataRestoreDto.setBusinessObjectDataKey(businessObjectDataHelper.getBusinessObjectDataKey(businessObjectDataEntity));
            businessObjectDataRestoreDto.setStorageName(storageName);
            businessObjectDataRestoreDto.setS3Endpoint(configurationHelper.getProperty(ConfigurationValue.S3_ENDPOINT));
            businessObjectDataRestoreDto.setS3BucketName(s3BucketName);
            businessObjectDataRestoreDto.setS3KeyPrefix(s3KeyPrefix);
            businessObjectDataRestoreDto.setStorageFiles(storageFiles);
            businessObjectDataRestoreDto.setArchiveRetrievalOption(archiveRetrievalOption);
            businessObjectDataRestoreDto.setNewStorageUnitStatus(newStorageUnitStatusEntity.getCode());
            businessObjectDataRestoreDto.setOldStorageUnitStatus(oldOriginStorageUnitStatus);
        }

        // Return the parameters DTO.
        return businessObjectDataRestoreDto;
    }
}
