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
package org.finra.herd.tools.downloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import com.sun.jersey.api.client.ClientHandlerException;
import org.apache.commons.io.FileUtils;

import org.finra.herd.model.dto.ConfigurationValue;
import org.finra.herd.model.dto.HerdAWSCredentialsProvider;
import org.finra.herd.model.dto.RegServerAccessParamsDto;
import org.finra.herd.model.dto.S3FileTransferRequestParamsDto;
import org.finra.herd.model.dto.S3FileTransferResultsDto;
import org.finra.herd.model.jpa.StorageEntity;
import org.finra.herd.sdk.model.*;
import org.finra.herd.tools.common.dto.DownloaderInputManifestDto;
import org.finra.herd.tools.common.dto.DownloaderOutputManifestDto;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

import org.finra.herd.core.helper.LogLevel;
import org.finra.herd.dao.impl.MockHttpClientOperationsImpl;
import org.finra.herd.tools.common.dto.UploaderInputManifestDto;
import org.finra.herd.model.jpa.BusinessObjectDataStatusEntity;
import org.finra.herd.model.jpa.StorageUnitStatusEntity;
import org.finra.herd.service.S3Service;
import org.finra.herd.service.helper.StorageFileHelper;
import org.finra.herd.tools.common.databridge.DataBridgeWebClient;

/**
 * Unit tests for DownloaderController class.
 */
public class DownloaderControllerTest extends AbstractDownloaderTest
{
    @Before
    @Override
    public void setup() throws Exception
    {
        super.setup();

        uploadAndRegisterTestData(S3_SIMPLE_TEST_PATH);

        // Set the web client logger to warn level so we don't get unnecessary info level logging on the output.
        setLogLevel(DataBridgeWebClient.class, LogLevel.WARN);
        setLogLevel(DownloaderWebClient.class, LogLevel.WARN);
    }

    @Test
    public void testPerformDownload() throws Exception
    {
        runDownload();
    }

    @Test(expected = ClientHandlerException.class)
    public void testPerformDownloadWithIoException() throws Exception
    {
        runDownload(getTestDownloaderInputManifestDto(), LOCAL_TEMP_PATH_OUTPUT.toString(), DownloaderController.MIN_THREADS,
            MockHttpClientOperationsImpl.HOSTNAME_THROW_IO_EXCEPTION_DURING_GET_STORAGE);
    }

    @Test
    public void testPerformDownloadTargetLocalDirectoryNoExists() throws Exception
    {
        Path localPath = Paths.get(LOCAL_TEMP_PATH_OUTPUT.toString(), "folder1" + RANDOM_SUFFIX, "folder2" + RANDOM_SUFFIX);
        runDownload(getTestDownloaderInputManifestDto(), localPath.toString(), DownloaderController.MIN_THREADS, null);
    }

    @Test
    public void testPerformDownloadInvalidLocalPath() throws Exception
    {
        File localFile = createLocalFile(LOCAL_TEMP_PATH_OUTPUT.toString(), LOCAL_FILE, FILE_SIZE_1_KB);
        try
        {
            // Try to download business object data when target local directory does not exist and cannot be created due to an invalid local path.
            runDownload(getTestDownloaderInputManifestDto(), localFile.toString(), DownloaderController.MIN_THREADS, null);
            fail("Should throw an IllegalArgumentException when local directory does not exist and cannot be created.");
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().startsWith("Failed to create target local directory "));
        }
    }

    @Test
    public void testPerformDownloadTargetLocalDirectoryAlreadyExists() throws Exception
    {
        // Create an empty target local directory.
        File targetLocalDirectory = Paths.get(LOCAL_TEMP_PATH_OUTPUT.toString(), S3_SIMPLE_TEST_PATH).toFile();
        assertTrue(targetLocalDirectory.mkdirs());

        runDownload();
    }

    @Test
    public void testPerformDownloadTargetLocalDirectoryNotEmpty() throws Exception
    {
        // Create a target local directory with a subfolder.
        File targetLocalDirectory = Paths.get(LOCAL_TEMP_PATH_OUTPUT.toString(), S3_SIMPLE_TEST_PATH).toFile();
        assertTrue(Paths.get(targetLocalDirectory.toString(), "folder" + RANDOM_SUFFIX).toFile().mkdirs());

        // Try to perform a download when the specified target local directory already exists and it is not empty.
        try
        {
            runDownload();
            fail("Suppose to throw an IllegalArgumentException when the specified target local directory already exists and it is not empty.");
        }
        catch (IllegalArgumentException e)
        {
            assertEquals(String.format("The target local directory \"%s\" is not empty.", targetLocalDirectory.toString()), e.getMessage());
        }
    }

    @Test
    public void testPerformDownloadWithLoggerLevelSetToWarn() throws Exception
    {
        LogLevel origLoggerLevel = getLogLevel(DownloaderController.class);
        setLogLevel(DownloaderController.class, LogLevel.WARN);

        try
        {
            runDownload();
        }
        finally
        {
            setLogLevel(DownloaderController.class, origLoggerLevel);
        }
    }

    @Test
    public void testPerformDownloadBusinessObjectDataHasAttributes() throws Exception
    {
        // Add attributes to the test business object data.
        UploaderInputManifestDto uploaderInputManifestDto = getTestUploaderInputManifestDto();
        HashMap<String, String> attributes = new HashMap<>();
        uploaderInputManifestDto.setAttributes(attributes);
        attributes.put(ATTRIBUTE_NAME_1_MIXED_CASE, ATTRIBUTE_VALUE_1);
        attributes.put(ATTRIBUTE_NAME_2_MIXED_CASE, ATTRIBUTE_VALUE_2);

        // Prepare test data and runs a normal download scenario.
        runDownload();
    }

    @Test
    public void testPerformDownloadZeroByteDirectoryMarkersPresent() throws Exception
    {
        // Upload and register business object data parents.
        uploadAndRegisterTestDataParents(downloaderWebClient);

        // Upload and register the initial version if of the test business object data.
        uploadAndRegisterTestData(S3_TEST_PATH_V0, testManifestFiles, S3_DIRECTORY_MARKERS);

        // Create a downloader input manifest file in LOCAL_TEMP_PATH_INPUT directory
        File downloaderInputManifestFile = createManifestFile(LOCAL_TEMP_PATH_INPUT.toString(), getTestDownloaderInputManifestDto());

        // Adjust the S3 file transfer parameters to be passed to the downloader controller.
        S3FileTransferRequestParamsDto s3FileTransferRequestParamsDto = getTestS3FileTransferRequestParamsDto();
        s3FileTransferRequestParamsDto.setLocalPath(LOCAL_TEMP_PATH_OUTPUT.toString());
        s3FileTransferRequestParamsDto.setMaxThreads(DownloaderController.MIN_THREADS);

        // Perform the download.
        RegServerAccessParamsDto regServerAccessParamsDto =
            RegServerAccessParamsDto.builder().withRegServerHost(WEB_SERVICE_HOSTNAME).withRegServerPort(WEB_SERVICE_HTTPS_PORT).withUseSsl(true)
                .withUsername(WEB_SERVICE_HTTPS_USERNAME).withPassword(WEB_SERVICE_HTTPS_PASSWORD).withTrustSelfSignedCertificate(true)
                .withDisableHostnameVerification(true).build();
        downloaderController.performDownload(regServerAccessParamsDto, downloaderInputManifestFile, s3FileTransferRequestParamsDto);
    }

    /**
     * Prepares test data and runs a normal download scenario.
     *
     * @param downloaderInputManifestDto the downloader input manifest object instance
     * @param localPath the local target directory
     * @param numOfThreads the maximum number of threads to use for file transfer to S3
     * @param hostname optional override of the default web service hostname.
     */
    protected void runDownload(DownloaderInputManifestDto downloaderInputManifestDto, String localPath, Integer numOfThreads, String hostname) throws Exception
    {
        String hostnameToUse = hostname == null ? WEB_SERVICE_HOSTNAME : hostname;

        // Upload and register business object data parents.
        uploadAndRegisterTestDataParents(downloaderWebClient);

        // Upload and register the initial version if of the test business object data.
        uploadAndRegisterTestData(S3_TEST_PATH_V0);

        // Create a downloader input manifest file in LOCAL_TEMP_PATH_INPUT directory
        File downloaderInputManifestFile = createManifestFile(LOCAL_TEMP_PATH_INPUT.toString(), downloaderInputManifestDto);

        // Perform the download.
        S3FileTransferRequestParamsDto s3FileTransferRequestParamsDto = getTestS3FileTransferRequestParamsDto();
        s3FileTransferRequestParamsDto.setS3KeyPrefix(S3_TEST_PATH_V0);
        s3FileTransferRequestParamsDto.setLocalPath(localPath);
        s3FileTransferRequestParamsDto.setMaxThreads(numOfThreads);
        RegServerAccessParamsDto regServerAccessParamsDto =
            RegServerAccessParamsDto.builder().withRegServerHost(hostnameToUse).withRegServerPort(WEB_SERVICE_HTTPS_PORT).withUseSsl(true)
                .withUsername(WEB_SERVICE_HTTPS_USERNAME).withPassword(WEB_SERVICE_HTTPS_PASSWORD).withTrustSelfSignedCertificate(true)
                .withDisableHostnameVerification(true).build();
        downloaderController.performDownload(regServerAccessParamsDto, downloaderInputManifestFile, s3FileTransferRequestParamsDto);
    }

    @Test
    public void testPerformDownloadWithStorageName() throws Exception
    {
        DownloaderInputManifestDto testDownloaderInputManifestDto = getTestDownloaderInputManifestDto();
        testDownloaderInputManifestDto.setStorageName("S3_MANAGED");
        runDownload(testDownloaderInputManifestDto, LOCAL_TEMP_PATH_OUTPUT.toString(), DownloaderController.MIN_THREADS, null);
    }

    /**
     * Asserts that the target directory is cleared (ie. all files under the directory is removed recursively) when there is an error during download.
     */
    @Test
    public void testPerformDownloadAssertCleanTargetDirectoryWhenError() throws Exception
    {
        /*
         * Create and inject mock objects
         */
        DownloaderWebClient mockDownloaderWebClient = mock(DownloaderWebClient.class);
        DownloaderWebClient originalDownloaderWebClient = (DownloaderWebClient) ReflectionTestUtils.getField(downloaderController, "downloaderWebClient");
        ReflectionTestUtils.setField(downloaderController, "downloaderWebClient", mockDownloaderWebClient);

        DownloaderManifestReader mockDownloaderManifestReader = mock(DownloaderManifestReader.class);
        DownloaderManifestReader originalDownloaderManifestReader =
            (DownloaderManifestReader) ReflectionTestUtils.getField(downloaderController, "manifestReader");
        ReflectionTestUtils.setField(downloaderController, "manifestReader", mockDownloaderManifestReader);

        S3Service mockS3Service = mock(S3Service.class);
        S3Service originalS3Service = (S3Service) ReflectionTestUtils.getField(downloaderController, "s3Service");
        ReflectionTestUtils.setField(downloaderController, "s3Service", mockS3Service);

        StorageFileHelper mockStorageFileHelper = mock(StorageFileHelper.class);
        StorageFileHelper originalStorageFileHelper = (StorageFileHelper) ReflectionTestUtils.getField(downloaderController, "storageFileHelper");
        ReflectionTestUtils.setField(downloaderController, "storageFileHelper", mockStorageFileHelper);

        /*
         * Start test
         */
        Path localPath = Files.createTempDirectory(null);
        try
        {
            String s3KeyPrefix = "s3KeyPrefix";
            String storageName = StorageEntity.MANAGED_STORAGE;
            IOException expectedException = new IOException();
            Path targetDirectoryPath = localPath.resolve(s3KeyPrefix);

            DownloaderInputManifestDto downloaderInputManifestDto = new DownloaderInputManifestDto();
            BusinessObjectData businessObjectData = new BusinessObjectData();
            StorageUnit storageUnit = new StorageUnit();
            Storage storage = new Storage();
            storage.setName(storageName);
            storageUnit.setStorage(storage);
            Attribute attribute = new Attribute();
            attribute.setName(configurationHelper.getProperty(ConfigurationValue.S3_ATTRIBUTE_NAME_BUCKET_NAME));
            attribute.setValue("value");
            storage.setAttributes(Collections.singletonList(attribute));
            storageUnit.setStorageUnitStatus(StorageUnitStatusEntity.ENABLED);
            businessObjectData.setStorageUnits(Arrays.asList(storageUnit));
            S3KeyPrefixInformation s3KeyPrefixInformation = new S3KeyPrefixInformation();
            s3KeyPrefixInformation.setS3KeyPrefix(s3KeyPrefix);

            /*
             * Mock operations on mocked dependencies
             */
            when(mockDownloaderManifestReader.readJsonManifest(any())).thenReturn(downloaderInputManifestDto);
            when(mockDownloaderWebClient.getBusinessObjectData(any())).thenReturn(businessObjectData);
            when(mockDownloaderWebClient.getStorage(any())).thenReturn(storage);
            when(mockDownloaderWebClient.getS3KeyPrefix(any())).thenReturn(s3KeyPrefixInformation);
            when(mockS3Service.downloadDirectory(any())).then(new Answer<S3FileTransferResultsDto>()
            {
                @Override
                public S3FileTransferResultsDto answer(InvocationOnMock invocation) throws Throwable
                {
                    Files.createFile(targetDirectoryPath.resolve("file"));
                    throw expectedException;
                }
            });

            /*
             * Make the call to the method under test
             */
            RegServerAccessParamsDto regServerAccessParamsDto = null;
            File manifestPath = null;
            S3FileTransferRequestParamsDto s3FileTransferRequestParamsDto = new S3FileTransferRequestParamsDto();
            s3FileTransferRequestParamsDto.setLocalPath(localPath.toString());
            s3FileTransferRequestParamsDto.setMaxThreads(1);

            try
            {
                downloaderController.performDownload(regServerAccessParamsDto, manifestPath, s3FileTransferRequestParamsDto);
                // Expect an exception, fail if no exception
                fail();
            }
            catch (Exception e)
            {
                // Assert that the exception thrown by the mock is what is actually thrown
                assertEquals(expectedException, e);
                // Assert that the target directory is cleaned
                assertEquals(0, targetDirectoryPath.toFile().list().length);
            }
        }
        finally
        {
            /*
             * Restore mocked dependencies to their original implementation
             */
            ReflectionTestUtils.setField(downloaderController, "downloaderWebClient", originalDownloaderWebClient);
            ReflectionTestUtils.setField(downloaderController, "manifestReader", originalDownloaderManifestReader);
            ReflectionTestUtils.setField(downloaderController, "s3Service", originalS3Service);
            ReflectionTestUtils.setField(downloaderController, "storageFileHelper", originalStorageFileHelper);

            // Clean up any temporary files
            FileUtils.deleteDirectory(localPath.toFile());
        }
    }

    @Test
    public void testCreateDownloaderOutputManifestDto()
    {
        // Initiate input parameters.
        List<String> subPartitionValues = Arrays.asList("subPartitionValue1", "subPartitionValue2", "subPartitionValue3", "subPartitionValue4");
        String s3KeyPrefix = "s3KeyPrefix";
        StorageUnit storageUnit = new StorageUnit();
        Storage storage = new Storage();
        storage.setName("storageName");
        storage.setStoragePlatformName(s3KeyPrefix);
        storageUnit.setStorage(storage);
        storageUnit.setStorageUnitStatus(StorageUnitStatusEntity.ENABLED);
        Attribute attribute = new Attribute();
        attribute.setName("name");
        attribute.setValue("value");
        BusinessObjectData businessObjectData = new BusinessObjectData();
        businessObjectData.setId(1234l);
        businessObjectData.setNamespace("businessObjectDefinitionNamespace");
        businessObjectData.setBusinessObjectFormatUsage("formatUsage");
        businessObjectData.setBusinessObjectFormatFileType("formatFileType");
        businessObjectData.setBusinessObjectDefinitionName("businessObjectDefinitionName");
        businessObjectData.setBusinessObjectFormatVersion(2345);
        businessObjectData.setPartitionKey("partitionKey");
        businessObjectData.setPartitionValue("partitionValue");
        businessObjectData.setSubPartitionValues(subPartitionValues);
        businessObjectData.setVersion(3456);
        businessObjectData.setLatestVersion(true);
        businessObjectData.setStatus(BusinessObjectDataStatusEntity.VALID);
        businessObjectData.setStorageUnits(Arrays.asList(storageUnit));
        businessObjectData.setAttributes(Arrays.asList(attribute));
        businessObjectData.setBusinessObjectDataParents(new ArrayList<>());
        businessObjectData.setBusinessObjectDataChildren(new ArrayList<>());
        businessObjectData.setBusinessObjectDataStatusHistory(new ArrayList<>());

        // Create a downloader output manifest DTO.
        DownloaderOutputManifestDto resultDto = downloaderController.createDownloaderOutputManifestDto(businessObjectData, storageUnit, s3KeyPrefix);

        // Validate the result DTO.
        assertEquals("businessObjectDefinitionNamespace", resultDto.getNamespace());
        assertEquals("businessObjectDefinitionName", resultDto.getBusinessObjectDefinitionName());
        assertEquals("formatUsage", resultDto.getBusinessObjectFormatUsage());
        assertEquals("formatFileType", resultDto.getBusinessObjectFormatFileType());
        assertEquals("2345", resultDto.getBusinessObjectFormatVersion());
        assertEquals("partitionKey", resultDto.getPartitionKey());
        assertEquals("partitionValue", resultDto.getPartitionValue());
        assertEquals(subPartitionValues, resultDto.getSubPartitionValues());
        assertEquals("3456", resultDto.getBusinessObjectDataVersion());
        assertEquals("storageName", resultDto.getStorageName());
        HashMap<String, String> expectedAttributes = new HashMap<>();
        expectedAttributes.put(attribute.getName(), attribute.getValue());
        assertEquals(expectedAttributes, resultDto.getAttributes());
        assertEquals(new ArrayList<>(), resultDto.getBusinessObjectDataParents());
        assertEquals(new ArrayList<>(), resultDto.getBusinessObjectDataChildren());
    }

    @Test
    public void testCreateDownloaderOutputManifestDtoAssertOutputFilesEmptyWhenStorageFilesNull()
    {
        BusinessObjectData businessObjectData = new BusinessObjectData();
        Storage storage = new Storage();
        storage.setStoragePlatformName("storageName");
        StorageUnit storageUnit = new StorageUnit();
        storageUnit.setStorage(storage);
        storageUnit.setStorageUnitStatus(StorageUnitStatusEntity.ENABLED);
        String s3KeyPrefix = "s3KeyPrefix";
        DownloaderOutputManifestDto actual = downloaderController.createDownloaderOutputManifestDto(businessObjectData, storageUnit, s3KeyPrefix);
        assertEquals(0, actual.getManifestFiles().size());
    }

    @Test
    public void testCreateDownloaderOutputManifestDtoAssertOutputAttributesSetWhenBdataAttributesSet()
    {
        BusinessObjectData businessObjectData = new BusinessObjectData();
        businessObjectData.setAttributes(new ArrayList<>());
        Attribute attribute = new Attribute();
        attribute.setName("name");
        attribute.setValue("value");
        businessObjectData.getAttributes().add(attribute);
        StorageUnit storageUnit = new StorageUnit();
        Storage storage = new Storage();
        storage.setName("storageName");
        storageUnit.setStorage(storage);
        storageUnit.setStorageUnitStatus(StorageUnitStatusEntity.ENABLED);
        String s3KeyPrefix = "s3KeyPrefix";
        DownloaderOutputManifestDto actual = downloaderController.createDownloaderOutputManifestDto(businessObjectData, storageUnit, s3KeyPrefix);
        assertEquals(1, actual.getAttributes().size());
        assertEquals("value", actual.getAttributes().get("name"));
    }

    @Test
    public void testLogLocalDirectoryContents() throws Exception
    {
        String appenderName = "TestWriterAppender";
        StringWriter stringWriter = addLoggingWriterAppender(appenderName);
        LogLevel originalLevel = getLogLevel(DownloaderController.class);
        setLogLevel(DownloaderController.class, LogLevel.INFO);

        /*
         * Create and inject mock objects
         */
        DownloaderWebClient mockDownloaderWebClient = mock(DownloaderWebClient.class);
        DownloaderWebClient originalDownloaderWebClient = (DownloaderWebClient) ReflectionTestUtils.getField(downloaderController, "downloaderWebClient");
        ReflectionTestUtils.setField(downloaderController, "downloaderWebClient", mockDownloaderWebClient);

        DownloaderManifestReader mockDownloaderManifestReader = mock(DownloaderManifestReader.class);
        DownloaderManifestReader originalDownloaderManifestReader =
            (DownloaderManifestReader) ReflectionTestUtils.getField(downloaderController, "manifestReader");
        ReflectionTestUtils.setField(downloaderController, "manifestReader", mockDownloaderManifestReader);

        S3Service mockS3Service = mock(S3Service.class);
        S3Service originalS3Service = (S3Service) ReflectionTestUtils.getField(downloaderController, "s3Service");
        ReflectionTestUtils.setField(downloaderController, "s3Service", mockS3Service);

        StorageFileHelper mockStorageFileHelper = mock(StorageFileHelper.class);
        StorageFileHelper originalStorageFileHelper = (StorageFileHelper) ReflectionTestUtils.getField(downloaderController, "storageFileHelper");
        ReflectionTestUtils.setField(downloaderController, "storageFileHelper", mockStorageFileHelper);

        /*
         * Start test
         */
        Path localPath = Files.createTempDirectory(null);
        try
        {
            String s3KeyPrefix = "s3KeyPrefix";
            String storageName = "S3_MANAGED";
            Path targetDirectoryPath = localPath.resolve(s3KeyPrefix);
            Path targetFilePath = targetDirectoryPath.resolve("file");

            DownloaderInputManifestDto downloaderInputManifestDto = new DownloaderInputManifestDto();
            BusinessObjectData businessObjectData = new BusinessObjectData();
            StorageUnit storageUnit = new StorageUnit();
            Storage storage = new Storage();
            storage.setName(storageName);
            Attribute attribute = new Attribute();
            attribute.setName(configurationHelper.getProperty(ConfigurationValue.S3_ATTRIBUTE_NAME_BUCKET_NAME));
            attribute.setValue("value");
            storage.setAttributes(Collections.singletonList(attribute));
            storageUnit.setStorage(storage);
            storageUnit.setStorageUnitStatus(StorageUnitStatusEntity.ENABLED);
            businessObjectData.setStorageUnits(Arrays.asList(storageUnit));
            S3KeyPrefixInformation s3KeyPrefixInformation = new S3KeyPrefixInformation();
            s3KeyPrefixInformation.setS3KeyPrefix(s3KeyPrefix);

            /*
             * Mock operations on mocked dependencies
             */
            when(mockDownloaderManifestReader.readJsonManifest(any())).thenReturn(downloaderInputManifestDto);
            when(mockDownloaderWebClient.getBusinessObjectData(any())).thenReturn(businessObjectData);
            when(mockDownloaderWebClient.getStorage(any())).thenReturn(storage);
            when(mockDownloaderWebClient.getS3KeyPrefix(any())).thenReturn(s3KeyPrefixInformation);
            when(mockS3Service.downloadDirectory(any())).then(new Answer<S3FileTransferResultsDto>()
            {
                @Override
                public S3FileTransferResultsDto answer(InvocationOnMock invocation) throws Throwable
                {
                    Files.createFile(targetFilePath);
                    return null;
                }
            });

            /*
             * Make the call to the method under test
             */
            RegServerAccessParamsDto regServerAccessParamsDto = null;
            File manifestPath = null;
            S3FileTransferRequestParamsDto s3FileTransferRequestParamsDto = new S3FileTransferRequestParamsDto();
            s3FileTransferRequestParamsDto.setLocalPath(localPath.toString());
            s3FileTransferRequestParamsDto.setMaxThreads(1);

            downloaderController.performDownload(regServerAccessParamsDto, manifestPath, s3FileTransferRequestParamsDto);

            // Assert that the proper delegate method is called with the expected params to retrieve credentials
            verify(mockDownloaderManifestReader).readJsonManifest(manifestPath);
            verify(mockDownloaderWebClient).getBusinessObjectData(downloaderInputManifestDto);
            verify(mockDownloaderWebClient).getS3KeyPrefix(businessObjectData);
            verify(mockS3Service).downloadDirectory(s3FileTransferRequestParamsDto);
        }
        finally
        {
            setLogLevel(DownloaderController.class, originalLevel);
            removeLoggingAppender(appenderName);

            /*
             * Restore mocked dependencies to their original implementation
             */
            ReflectionTestUtils.setField(downloaderController, "downloaderWebClient", originalDownloaderWebClient);
            ReflectionTestUtils.setField(downloaderController, "manifestReader", originalDownloaderManifestReader);
            ReflectionTestUtils.setField(downloaderController, "s3Service", originalS3Service);
            ReflectionTestUtils.setField(downloaderController, "storageFileHelper", originalStorageFileHelper);

            // Clean up any temporary files
            FileUtils.deleteDirectory(localPath.toFile());
        }
    }

    /**
     * Asserts that the controller is sending the proper implementation of credentials provider when calling S3.
     */
    @Test
    public void testPerformDownloadAssertCredentialsRetrieved() throws Exception
    {
        /*
         * Create and inject mock objects
         */
        DownloaderWebClient mockDownloaderWebClient = mock(DownloaderWebClient.class);
        DownloaderWebClient originalDownloaderWebClient = (DownloaderWebClient) ReflectionTestUtils.getField(downloaderController, "downloaderWebClient");
        ReflectionTestUtils.setField(downloaderController, "downloaderWebClient", mockDownloaderWebClient);

        DownloaderManifestReader mockDownloaderManifestReader = mock(DownloaderManifestReader.class);
        DownloaderManifestReader originalDownloaderManifestReader =
            (DownloaderManifestReader) ReflectionTestUtils.getField(downloaderController, "manifestReader");
        ReflectionTestUtils.setField(downloaderController, "manifestReader", mockDownloaderManifestReader);

        S3Service mockS3Service = mock(S3Service.class);
        S3Service originalS3Service = (S3Service) ReflectionTestUtils.getField(downloaderController, "s3Service");
        ReflectionTestUtils.setField(downloaderController, "s3Service", mockS3Service);

        StorageFileHelper mockStorageFileHelper = mock(StorageFileHelper.class);
        StorageFileHelper originalStorageFileHelper = (StorageFileHelper) ReflectionTestUtils.getField(downloaderController, "storageFileHelper");
        ReflectionTestUtils.setField(downloaderController, "storageFileHelper", mockStorageFileHelper);

        /*
         * Start test
         */
        Path localPath = Files.createTempDirectory(null);
        try
        {
            String s3KeyPrefix = "s3KeyPrefix";
            String storageName = "storageName";

            DownloaderInputManifestDto downloaderInputManifestDto = new DownloaderInputManifestDto();
            downloaderInputManifestDto.setStorageName(storageName);
            BusinessObjectData businessObjectData = new BusinessObjectData();
            StorageUnit storageUnit = new StorageUnit();
            Storage storage = new Storage();
            storage.setName(storageName);
            Attribute attribute = new Attribute();
            attribute.setName(configurationHelper.getProperty(ConfigurationValue.S3_ATTRIBUTE_NAME_BUCKET_NAME));
            attribute.setValue("value");
            storage.setAttributes(Collections.singletonList(attribute));
            storageUnit.setStorage(storage);
            storageUnit.setStorageUnitStatus(StorageUnitStatusEntity.ENABLED);
            businessObjectData.setStorageUnits(Arrays.asList(storageUnit));
            S3KeyPrefixInformation s3KeyPrefixInformation = new S3KeyPrefixInformation();
            s3KeyPrefixInformation.setS3KeyPrefix(s3KeyPrefix);

            /*
             * Mock operations on mocked dependencies
             */
            when(mockDownloaderManifestReader.readJsonManifest(any())).thenReturn(downloaderInputManifestDto);
            when(mockDownloaderWebClient.getBusinessObjectData(any())).thenReturn(businessObjectData);
            when(mockDownloaderWebClient.getStorage(any())).thenReturn(storage);
            when(mockDownloaderWebClient.getS3KeyPrefix(any())).thenReturn(s3KeyPrefixInformation);
            AwsCredential awsCredential = new AwsCredential();
            awsCredential.setAwsAccessKey("awsAccessKey");
            awsCredential.setAwsSecretKey("awsSecretKey");
            awsCredential.setAwsSessionToken("awsSessionToken");
            awsCredential.setAwsSessionExpirationTime(DateTime.now());
            StorageUnitDownloadCredential storageUnitDownloadCredential = new StorageUnitDownloadCredential();
            storageUnitDownloadCredential.setAwsCredential(awsCredential);
            when(mockDownloaderWebClient.getStorageUnitDownloadCredential(any(), any())).thenReturn(storageUnitDownloadCredential);
            when(mockS3Service.downloadDirectory(any())).then(new Answer<S3FileTransferResultsDto>()
            {
                @Override
                public S3FileTransferResultsDto answer(InvocationOnMock invocation) throws Throwable
                {
                    /*
                     * Call the providers' getAwsCredentials(), just like real implementation would.
                     */
                    S3FileTransferRequestParamsDto s3FileTransferRequestParamsDto = invocation.getArgument(0);
                    List<HerdAWSCredentialsProvider> additionalAwsCredentialsProviders = s3FileTransferRequestParamsDto.getAdditionalAwsCredentialsProviders();
                    for (HerdAWSCredentialsProvider herdAWSCredentialsProvider : additionalAwsCredentialsProviders)
                    {
                        herdAWSCredentialsProvider.getAwsCredential();
                    }
                    return null;
                }
            });

            /*
             * Make the call to the method under test
             */
            RegServerAccessParamsDto regServerAccessParamsDto = null;
            File manifestPath = null;
            S3FileTransferRequestParamsDto s3FileTransferRequestParamsDto = new S3FileTransferRequestParamsDto();
            s3FileTransferRequestParamsDto.setLocalPath(localPath.toString());
            s3FileTransferRequestParamsDto.setMaxThreads(1);

            downloaderController.performDownload(regServerAccessParamsDto, manifestPath, s3FileTransferRequestParamsDto);

            // Assert that the proper delegate method is called with the expected params to retrieve credentials
            verify(mockDownloaderWebClient, times(2)).getStorageUnitDownloadCredential(downloaderInputManifestDto, storageName);
        }
        finally
        {
            /*
             * Restore mocked dependencies to their original implementation
             */
            ReflectionTestUtils.setField(downloaderController, "downloaderWebClient", originalDownloaderWebClient);
            ReflectionTestUtils.setField(downloaderController, "manifestReader", originalDownloaderManifestReader);
            ReflectionTestUtils.setField(downloaderController, "s3Service", originalS3Service);
            ReflectionTestUtils.setField(downloaderController, "storageFileHelper", originalStorageFileHelper);

            // Clean up any temporary files
            FileUtils.deleteDirectory(localPath.toFile());
        }
    }

    @Test
    public void testGetStorageUnitByStorageName()
    {
        BusinessObjectData businessObjectData = new BusinessObjectData();
        businessObjectData.setStorageUnits(Arrays.asList(createStorageUnit("storageName1"), createStorageUnit("storageName2")));
        assertEquals("storageName1", downloaderController.getStorageUnitByStorageName(businessObjectData, "storageName1").getStorage().getName());
        assertEquals("storageName1", downloaderController.getStorageUnitByStorageName(businessObjectData, "StorageNAME1".toUpperCase()).getStorage().getName());
        assertEquals("storageName1", downloaderController.getStorageUnitByStorageName(businessObjectData, "storageName1".toLowerCase()).getStorage().getName());
    }

    @Test
    public void testGetStorageUnitByStorageNameNotExist()
    {
        BusinessObjectData businessObjectData = new BusinessObjectData();
        businessObjectData.setStorageUnits(Arrays.asList(createStorageUnit("storageName1"), createStorageUnit("storageName2")));
        try
        {
            downloaderController.getStorageUnitByStorageName(businessObjectData, "storageName3");
        }
        catch (IllegalStateException e)
        {
            assertEquals(String.format("Business object data has no storage unit with storage name \"%s\".", "storageName3"), e.getMessage());
        }
    }

    private StorageUnit createStorageUnit(String storageName)
    {
        StorageUnit storageUnit = new StorageUnit();
        Storage storage = new Storage();
        storage.setName(storageName);
        storageUnit.setStorage(storage);
        return storageUnit;
    }


    /**
     * Prepares test data and runs a normal download scenario using test output directory and minimum allowed number of threads.
     */
    protected void runDownload() throws Exception
    {
        runDownload(getTestDownloaderInputManifestDto(), LOCAL_TEMP_PATH_OUTPUT.toString(), DownloaderController.MIN_THREADS, null);
    }
}
