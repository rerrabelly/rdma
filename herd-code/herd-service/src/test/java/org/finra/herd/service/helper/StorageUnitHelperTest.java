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
package org.finra.herd.service.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.finra.herd.core.HerdDateUtils;
import org.finra.herd.model.api.xml.BusinessObjectDataKey;
import org.finra.herd.model.api.xml.BusinessObjectDataStorageUnitKey;
import org.finra.herd.model.api.xml.Storage;
import org.finra.herd.model.api.xml.StorageDirectory;
import org.finra.herd.model.api.xml.StorageFile;
import org.finra.herd.model.api.xml.StorageUnit;
import org.finra.herd.model.dto.StorageUnitAvailabilityDto;
import org.finra.herd.model.jpa.StorageFileEntity;
import org.finra.herd.model.jpa.StoragePlatformEntity;
import org.finra.herd.model.jpa.StorageUnitEntity;
import org.finra.herd.service.AbstractServiceTest;

public class StorageUnitHelperTest extends AbstractServiceTest
{
    @Mock
    private AlternateKeyHelper alternateKeyHelper;

    @Mock
    private BusinessObjectDataHelper businessObjectDataHelper;

    @Mock
    private StorageFileHelper storageFileHelper;

    @InjectMocks
    private StorageUnitHelper storageUnitHelper;

    @Before
    public void before()
    {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testCreateBusinessObjectDataStorageUnitKey()
    {
        // Get a business object data storage unit key.
        BusinessObjectDataStorageUnitKey result = storageUnitHelper.createBusinessObjectDataStorageUnitKey(
            new BusinessObjectDataKey(BDEF_NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE, SUBPARTITION_VALUES,
                DATA_VERSION), STORAGE_NAME);

        // Validate the result object.
        assertEquals(new BusinessObjectDataStorageUnitKey(BDEF_NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
            SUBPARTITION_VALUES, DATA_VERSION, STORAGE_NAME), result);
    }

    @Test
    public void testCreateStorageUnitKey()
    {
        // Get a storage unit key.
        BusinessObjectDataStorageUnitKey result = storageUnitHelper.createStorageUnitKey(
            new BusinessObjectDataKey(BDEF_NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE, SUBPARTITION_VALUES,
                DATA_VERSION), STORAGE_NAME);

        // Validate the result object.
        assertEquals(new BusinessObjectDataStorageUnitKey(BDEF_NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
            SUBPARTITION_VALUES, DATA_VERSION, STORAGE_NAME), result);
    }

    @Test
    public void testCreateStorageUnitKeyFromEntity()
    {
        // Create a business object data storage unit key.
        BusinessObjectDataStorageUnitKey businessObjectDataStorageUnitKey =
            new BusinessObjectDataStorageUnitKey(BDEF_NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
                SUBPARTITION_VALUES, DATA_VERSION, STORAGE_NAME);

        // Create a storage unit entity.
        StorageUnitEntity storageUnitEntity = storageUnitDaoTestHelper.createStorageUnitEntity(businessObjectDataStorageUnitKey, STORAGE_UNIT_STATUS);

        // Mock the external calls.
        when(businessObjectDataHelper.getSubPartitionValues(storageUnitEntity.getBusinessObjectData())).thenReturn(SUBPARTITION_VALUES);

        // Call the method under test.
        BusinessObjectDataStorageUnitKey result = storageUnitHelper.createStorageUnitKeyFromEntity(storageUnitEntity);

        // Verify the external calls.
        verify(businessObjectDataHelper).getSubPartitionValues(storageUnitEntity.getBusinessObjectData());
        verifyNoMoreInteractionsHelper();

        // Validate the results.
        assertEquals(new BusinessObjectDataStorageUnitKey(BDEF_NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
            SUBPARTITION_VALUES, DATA_VERSION, STORAGE_NAME), result);
    }

    @Test
    public void testCreateStorageUnitsFromEntities()
    {
        // Create a business object data storage unit key.
        BusinessObjectDataStorageUnitKey businessObjectDataStorageUnitKey =
            new BusinessObjectDataStorageUnitKey(BDEF_NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
                SUBPARTITION_VALUES, DATA_VERSION, STORAGE_NAME);

        // Create a storage file entity with a file path that does not start with storage unit directory path.
        StorageFileEntity storageFileOneEntity = new StorageFileEntity();
        storageFileOneEntity.setStorageUnit(null);
        storageFileOneEntity.setPath(FILE_NAME);
        storageFileOneEntity.setFileSizeBytes(FILE_SIZE);
        storageFileOneEntity.setRowCount(ROW_COUNT);

        // Create a storage file entity with a file path that starts with the storage unit directory path.
        StorageFileEntity storageFileTwoEntity = new StorageFileEntity();
        storageFileTwoEntity.setStorageUnit(null);
        storageFileTwoEntity.setPath(STORAGE_DIRECTORY_PATH + "/" + FILE_NAME_2);
        storageFileTwoEntity.setFileSizeBytes(FILE_SIZE_2);
        storageFileTwoEntity.setRowCount(ROW_COUNT_2);

        // Create an expiration timestamp for restoration.
        final Timestamp restoredExpirationOn = new Timestamp(new Date().getTime());

        // Create a storage unit entity.
        StorageUnitEntity storageUnitEntity = storageUnitDaoTestHelper.createStorageUnitEntity(businessObjectDataStorageUnitKey, STORAGE_UNIT_STATUS);
        storageUnitEntity.setDirectoryPath(STORAGE_DIRECTORY_PATH);
        storageUnitEntity.setStorageFiles(Lists.newArrayList(storageFileOneEntity, storageFileTwoEntity));
        storageUnitEntity.setStoragePolicyTransitionFailedAttempts(STORAGE_POLICY_TRANSITION_FAILED_ATTEMPTS);
        storageUnitEntity.setRestoreExpirationOn(restoredExpirationOn);

        // Mock the external calls.
        when(storageFileHelper.createStorageFileFromEntity(any(StorageFileEntity.class), eq(STORAGE_DIRECTORY_PATH))).thenCallRealMethod();

        // Call the method under test.
        List<StorageUnit> result =
            storageUnitHelper.createStorageUnitsFromEntities(Collections.singletonList(storageUnitEntity), NO_INCLUDE_STORAGE_UNIT_STATUS_HISTORY,
                    NO_EXCLUDE_BUSINESS_OBJECT_DATA_STORAGE_FILES);

        // Verify the external calls.
        verify(storageFileHelper).createStorageFileFromEntity(storageFileOneEntity, STORAGE_DIRECTORY_PATH);
        verify(storageFileHelper).createStorageFileFromEntity(storageFileTwoEntity, STORAGE_DIRECTORY_PATH);
        verifyNoMoreInteractionsHelper();

        // Validate the results.
        assertEquals(Collections.singletonList(
            new StorageUnit(new Storage(STORAGE_NAME, StoragePlatformEntity.S3, null), new StorageDirectory(STORAGE_DIRECTORY_PATH), Lists
                .newArrayList(new StorageFile(STORAGE_DIRECTORY_PATH + "/" + FILE_NAME, FILE_SIZE, ROW_COUNT),
                    new StorageFile(STORAGE_DIRECTORY_PATH + "/" + FILE_NAME_2, FILE_SIZE_2, ROW_COUNT_2)), STORAGE_UNIT_STATUS, NO_STORAGE_UNIT_STATUS_HISTORY,
                STORAGE_POLICY_TRANSITION_FAILED_ATTEMPTS, HerdDateUtils.getXMLGregorianCalendarValue(restoredExpirationOn))), result);
    }

    @Test
    public void testGetBusinessObjectDataKey()
    {
        // Get a business object data key.
        BusinessObjectDataKey result = storageUnitHelper.getBusinessObjectDataKey(
            new BusinessObjectDataStorageUnitKey(BDEF_NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
                SUBPARTITION_VALUES, DATA_VERSION, STORAGE_NAME));

        // Validate the result object.
        assertEquals(
            new BusinessObjectDataKey(BDEF_NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE, SUBPARTITION_VALUES,
                DATA_VERSION), result);
    }

    @Test
    public void testGetStorageUnitIds()
    {
        // Create a storage unit availability DTOs.
        StorageUnitAvailabilityDto storageUnitAvailabilityDto1 = new StorageUnitAvailabilityDto();
        storageUnitAvailabilityDto1.setStorageUnitId(LONG_VALUE);
        StorageUnitAvailabilityDto storageUnitAvailabilityDto2 = new StorageUnitAvailabilityDto();
        storageUnitAvailabilityDto2.setStorageUnitId(LONG_VALUE_2);

        // Get a list of storage unit ids.
        List<Long> result = storageUnitHelper.getStorageUnitIds(Arrays.asList(storageUnitAvailabilityDto1, storageUnitAvailabilityDto2));

        // Validate the returned object.
        assertEquals(Arrays.asList(LONG_VALUE, LONG_VALUE_2), result);

        // Get a list of storage unit ids when the list of entities is empty.
        result = storageUnitHelper.getStorageUnitIds(new ArrayList<>());

        // Validate the returned object.
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void testValidateBusinessObjectDataStorageUnitKey()
    {
        // Create a business object data storage unit key.
        BusinessObjectDataStorageUnitKey businessObjectDataStorageUnitKey =
            new BusinessObjectDataStorageUnitKey(BDEF_NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
                SUBPARTITION_VALUES, DATA_VERSION, STORAGE_NAME);

        // Mock the external calls.
        when(alternateKeyHelper.validateStringParameter("namespace", BDEF_NAMESPACE)).thenReturn(BDEF_NAMESPACE);
        when(alternateKeyHelper.validateStringParameter("business object definition name", BDEF_NAME)).thenReturn(BDEF_NAME);
        when(alternateKeyHelper.validateStringParameter("business object format usage", FORMAT_USAGE_CODE)).thenReturn(FORMAT_USAGE_CODE);
        when(alternateKeyHelper.validateStringParameter("business object format file type", FORMAT_FILE_TYPE_CODE)).thenReturn(FORMAT_FILE_TYPE_CODE);
        when(alternateKeyHelper.validateStringParameter("partition value", PARTITION_VALUE)).thenReturn(PARTITION_VALUE);
        when(alternateKeyHelper.validateStringParameter("storage name", STORAGE_NAME)).thenReturn(STORAGE_NAME);

        // Call the method under test.
        storageUnitHelper.validateBusinessObjectDataStorageUnitKey(businessObjectDataStorageUnitKey);

        // Verify the external calls.
        verify(alternateKeyHelper).validateStringParameter("namespace", BDEF_NAMESPACE);
        verify(alternateKeyHelper).validateStringParameter("business object definition name", BDEF_NAME);
        verify(alternateKeyHelper).validateStringParameter("business object format usage", FORMAT_USAGE_CODE);
        verify(alternateKeyHelper).validateStringParameter("business object format file type", FORMAT_FILE_TYPE_CODE);
        verify(alternateKeyHelper).validateStringParameter("partition value", PARTITION_VALUE);
        verify(businessObjectDataHelper).validateSubPartitionValues(SUBPARTITION_VALUES);
        verify(alternateKeyHelper).validateStringParameter("storage name", STORAGE_NAME);
        verifyNoMoreInteractionsHelper();
    }

    @Test
    public void testValidateBusinessObjectDataStorageUnitKeyInvalidParameters()
    {
        // Try to call the method under test when business object data storage unit key is not specified.
        try
        {
            storageUnitHelper.validateBusinessObjectDataStorageUnitKey(null);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertEquals("A business object data storage unit key must be specified.", e.getMessage());
        }

        // Try to call the method under test when business object format version is not specified.
        try
        {
            storageUnitHelper.validateBusinessObjectDataStorageUnitKey(
                new BusinessObjectDataStorageUnitKey(BDEF_NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, NO_FORMAT_VERSION, PARTITION_VALUE,
                    SUBPARTITION_VALUES, DATA_VERSION, STORAGE_NAME));
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertEquals("A business object format version must be specified.", e.getMessage());
        }

        // Try to call the method under test when business object data version is not specified.
        try
        {
            storageUnitHelper.validateBusinessObjectDataStorageUnitKey(
                new BusinessObjectDataStorageUnitKey(BDEF_NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
                    SUBPARTITION_VALUES, NO_DATA_VERSION, STORAGE_NAME));
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertEquals("A business object data version must be specified.", e.getMessage());
        }
    }

    /**
     * Checks if any of the mocks has any interaction.
     */
    private void verifyNoMoreInteractionsHelper()
    {
        verifyNoMoreInteractions(alternateKeyHelper, businessObjectDataHelper, storageFileHelper);
    }
}
