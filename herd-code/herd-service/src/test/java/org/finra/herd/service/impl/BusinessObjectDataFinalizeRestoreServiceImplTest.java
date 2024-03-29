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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import org.finra.herd.dao.StorageUnitDao;
import org.finra.herd.model.api.xml.BusinessObjectDataKey;
import org.finra.herd.model.api.xml.BusinessObjectDataStorageUnitKey;
import org.finra.herd.model.api.xml.StorageFile;
import org.finra.herd.model.dto.BusinessObjectDataRestoreDto;
import org.finra.herd.model.jpa.NotificationEventTypeEntity;
import org.finra.herd.model.jpa.StorageUnitEntity;
import org.finra.herd.model.jpa.StorageUnitStatusEntity;
import org.finra.herd.service.AbstractServiceTest;
import org.finra.herd.service.BusinessObjectDataFinalizeRestoreHelperService;
import org.finra.herd.service.NotificationEventService;
import org.finra.herd.service.helper.StorageUnitHelper;

/**
 * This class tests functionality within the business object data finalize restore service implementation.
 */
public class BusinessObjectDataFinalizeRestoreServiceImplTest extends AbstractServiceTest
{
    @Mock
    private BusinessObjectDataFinalizeRestoreHelperService businessObjectDataFinalizeRestoreHelperService;

    @InjectMocks
    private BusinessObjectDataFinalizeRestoreServiceImpl businessObjectDataFinalizeRestoreServiceImpl;

    @Mock
    private NotificationEventService notificationEventService;

    @Mock
    private StorageUnitDao storageUnitDao;

    @Mock
    private StorageUnitHelper storageUnitHelper;

    @Before
    public void before()
    {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testFinalizeRestore()
    {
        // Create a business object data key.
        BusinessObjectDataKey businessObjectDataKey =
            new BusinessObjectDataKey(BDEF_NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE, SUBPARTITION_VALUES,
                DATA_VERSION);

        // Create a storage unit key.
        BusinessObjectDataStorageUnitKey storageUnitKey =
            new BusinessObjectDataStorageUnitKey(BDEF_NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
                SUBPARTITION_VALUES, DATA_VERSION, STORAGE_NAME);

        // Create a DTO for business object data restore parameters.
        BusinessObjectDataRestoreDto businessObjectDataRestoreDto =
            new BusinessObjectDataRestoreDto(businessObjectDataKey, STORAGE_NAME, S3_ENDPOINT, S3_BUCKET_NAME, S3_KEY_PREFIX, NO_STORAGE_UNIT_STATUS,
                NO_STORAGE_UNIT_STATUS, Arrays.asList(new StorageFile(S3_KEY, FILE_SIZE, ROW_COUNT)), NO_EXCEPTION, ARCHIVE_RETRIEVAL_OPTION,
                NO_BUSINESS_OBJECT_DATA);

        // Mock the external calls.
        when(businessObjectDataFinalizeRestoreHelperService.prepareToFinalizeRestore(storageUnitKey)).thenReturn(businessObjectDataRestoreDto);
        doAnswer(new Answer<Void>()
        {
            public Void answer(InvocationOnMock invocation)
            {
                // Get the parameters DTO for the business object data restore.
                BusinessObjectDataRestoreDto businessObjectDataRestoreDto = (BusinessObjectDataRestoreDto) invocation.getArguments()[0];

                //Set the value for the new and old storage unit statuses.
                businessObjectDataRestoreDto.setNewStorageUnitStatus(StorageUnitStatusEntity.RESTORED);
                businessObjectDataRestoreDto.setOldStorageUnitStatus(StorageUnitStatusEntity.RESTORING);

                return null;
            }
        }).when(businessObjectDataFinalizeRestoreHelperService).completeFinalizeRestore(businessObjectDataRestoreDto);

        // Call the method under test.
        businessObjectDataFinalizeRestoreServiceImpl.finalizeRestore(storageUnitKey);

        // Verify the external calls.
        verify(businessObjectDataFinalizeRestoreHelperService).prepareToFinalizeRestore(storageUnitKey);
        verify(businessObjectDataFinalizeRestoreHelperService).executeS3SpecificSteps(businessObjectDataRestoreDto);
        verify(businessObjectDataFinalizeRestoreHelperService).completeFinalizeRestore(businessObjectDataRestoreDto);
        verify(notificationEventService).processStorageUnitNotificationEventAsync(NotificationEventTypeEntity.EventTypesStorageUnit.STRGE_UNIT_STTS_CHG,
            businessObjectDataKey, STORAGE_NAME, StorageUnitStatusEntity.RESTORED, StorageUnitStatusEntity.RESTORING);
        verifyNoMoreInteractionsHelper();
    }

    @Test
    public void testGetS3StorageUnitsToRestore()
    {
        // Create a storage unit entity.
        StorageUnitEntity storageUnitEntity = new StorageUnitEntity();

        // Create a list of storage unit entities.
        List<StorageUnitEntity> storageUnitEntities = Arrays.asList(storageUnitEntity);

        // Create a storage unit key.
        BusinessObjectDataStorageUnitKey storageUnitKey =
            new BusinessObjectDataStorageUnitKey(BDEF_NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
                SUBPARTITION_VALUES, DATA_VERSION, STORAGE_NAME);

        // Mock the external calls.
        when(storageUnitDao.getS3StorageUnitsToRestore(MAX_RESULT)).thenReturn(storageUnitEntities);
        when(storageUnitHelper.createStorageUnitKeyFromEntity(storageUnitEntity)).thenReturn(storageUnitKey);

        // Call the method under test.
        List<BusinessObjectDataStorageUnitKey> result = businessObjectDataFinalizeRestoreServiceImpl.getS3StorageUnitsToRestore(MAX_RESULT);

        // Verify the external calls.
        verify(storageUnitDao).getS3StorageUnitsToRestore(MAX_RESULT);
        verify(storageUnitHelper).createStorageUnitKeyFromEntity(storageUnitEntity);
        verifyNoMoreInteractionsHelper();

        // Validate the result.
        assertEquals(Arrays.asList(storageUnitKey), result);
    }

    /**
     * Checks if any of the mocks has any interaction.
     */
    private void verifyNoMoreInteractionsHelper()
    {
        verifyNoMoreInteractions(businessObjectDataFinalizeRestoreHelperService, notificationEventService, storageUnitDao, storageUnitHelper);
    }
}
