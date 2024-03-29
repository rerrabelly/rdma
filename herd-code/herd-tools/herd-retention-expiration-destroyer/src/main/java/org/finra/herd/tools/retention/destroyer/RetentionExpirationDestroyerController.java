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
package org.finra.herd.tools.retention.destroyer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import org.finra.herd.sdk.model.BusinessObjectDataKey;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.finra.herd.dao.helper.JsonHelper;
import org.finra.herd.model.dto.RegServerAccessParamsDto;

@Component
public class RetentionExpirationDestroyerController
{
    private static final List<String> BUSINESS_OBJECT_DATA_HEADERS = Arrays
        .asList("Namespace", "Business Object Definition Name", "Business Object Format Usage", "Business Object Format File Type",
            "Business Object Format Version", "Primary Partition Value", "Sub-Partition Value 1", "Sub-Partition Value 2", "Sub-Partition Value 3",
            "Sub-Partition Value 4", "Business Object Data Version");


    private static final Logger LOGGER = LoggerFactory.getLogger(RetentionExpirationDestroyerController.class);

    @Autowired
    private JsonHelper jsonHelper;

    @Autowired
    private RetentionExpirationDestroyerWebClient retentionExpirationDestroyerWebClient;

    /**
     * Executes the retention expiration destroyer workflow.
     *
     * @param localInputFile           the local input file
     * @param regServerAccessParamsDto the DTO for the parameters required to communicate with the registration server
     * @param batchMode                flag to indicate if herd should use S3 Batch Operations to destroy the business object data
     * @throws Exception if any problems were encountered
     */
    public void performRetentionExpirationDestruction(File localInputFile, RegServerAccessParamsDto regServerAccessParamsDto, Boolean batchMode)
        throws Exception
    {
        // Read business object data keys from the input Excel file.
        List<BusinessObjectDataKey> businessObjectDataKeys = getBusinessObjectDataKeys(localInputFile);

        // Initialize the web client.
        retentionExpirationDestroyerWebClient.setRegServerAccessParamsDto(regServerAccessParamsDto);

        // Process business object data keys one by one.
        LOGGER.info("Processing {} business object data instances for destruction.", CollectionUtils.size(businessObjectDataKeys));
        for (BusinessObjectDataKey businessObjectDataKey : businessObjectDataKeys)
        {
            retentionExpirationDestroyerWebClient.destroyBusinessObjectData(businessObjectDataKey, batchMode);
            LOGGER.info("Successfully marked for destruction. Business object data {}", jsonHelper.objectToJson(businessObjectDataKey));
        }

        LOGGER.info("Successfully processed {} business object data instances for destruction.", CollectionUtils.size(businessObjectDataKeys));
    }

    /**
     * Extracts business object data key from a Excel file line. This method also validates the format of the line.
     *
     * @param line         the input line
     * @param lineNumber   the input line number
     * @param inputExcelFile the input Excel file
     * @return the business object data key
     */
    protected BusinessObjectDataKey getBusinessObjectDataKey(List<String> line, int lineNumber, File inputExcelFile)
    {
        if (line.size() != BUSINESS_OBJECT_DATA_HEADERS.size())
        {
            throw new IllegalArgumentException(
                String.format("Line number %d of input file \"%s\" does not match the expected format.", lineNumber, inputExcelFile.toString()));
        }

        Integer businessObjectFormatVersion;
        Integer businessObjectDataVersion;

        try
        {
            businessObjectFormatVersion = Integer.valueOf(line.get(4));
        }
        catch (NumberFormatException e)
        {
            throw new IllegalArgumentException(
                String.format("Line number %d of input file \"%s\" does not match the expected format. Business object format version must be an integer.",
                    lineNumber, inputExcelFile.toString()), e);
        }

        try
        {
            businessObjectDataVersion = Integer.valueOf(line.get(10));
        }
        catch (NumberFormatException e)
        {
            throw new IllegalArgumentException(
                String.format("Line number %d of input file \"%s\" does not match the expected format. Business object data version must be an integer.",
                    lineNumber, inputExcelFile.toString()), e);
        }

        // Build a list of optional sub-partition values.
        List<String> subPartitionValues = new ArrayList<>();
        for (String subPartitionValue : Arrays.asList(line.get(6), line.get(7), line.get(8), line.get(9)))
        {
            if (StringUtils.isNotBlank(subPartitionValue))
            {
                subPartitionValues.add(subPartitionValue);
            }
            else
            {
                break;
            }
        }

        return buildBusinessObjectDataKey(line.get(0), line.get(1), line.get(2), line.get(3), businessObjectFormatVersion, line.get(5), subPartitionValues,
            businessObjectDataVersion);
    }

    /**
     * Get business object data keys from the input Excel tile. This method also validates the input file format.
     *
     * @param inputExcelFile the input excel file
     *
     * @return the list of business object data keys
     * @throws IOException if any problems were encountered
     * @throws InvalidFormatException if the input format is invalid
     */
    protected List<BusinessObjectDataKey> getBusinessObjectDataKeys(File inputExcelFile) throws IOException, InvalidFormatException
    {
        List<BusinessObjectDataKey> businessObjectDataKeyList = new ArrayList<>();

        // Read the input Excel file and populate business object data key list.
        try (XSSFWorkbook workbook = new XSSFWorkbook(inputExcelFile))
        {
            // Get business object data sheet
            XSSFSheet dataSheet = workbook.getSheetAt(1);

            // Get the business object data headers.
            XSSFRow row = dataSheet.getRow(0);
            List<String> dataHeaders = new ArrayList<>();
            for (Cell cell : row)
            {
                dataHeaders.add(cell.getStringCellValue());
            }

            // Validate required header of the Excel input file.
            if ((CollectionUtils.isEmpty(dataHeaders) || !CollectionUtils.isEqualCollection(dataHeaders, BUSINESS_OBJECT_DATA_HEADERS)))
            {
                throw new IllegalArgumentException(
                    String.format("Input file \"%s\" does not contain the expected Excel file header.", inputExcelFile.toString()));
            }

            // Process the input Excel file line by line.
            int totalLines = dataSheet.getLastRowNum() - dataSheet.getFirstRowNum() + 1;
            for (int lineCount = 1; lineCount < totalLines; lineCount++)
            {
                List<String> line = new ArrayList<>();
                row = dataSheet.getRow(lineCount);
                for (Cell cell : row)
                {
                    line.add(cell.getStringCellValue());
                }
                businessObjectDataKeyList.add(getBusinessObjectDataKey(line, lineCount, inputExcelFile));
            }
        }

        return businessObjectDataKeyList;
    }

    /**
     * Build Business Object Data Key object
     *
     * @param namespace                    the namespace
     * @param businessObjectDefinitionName the business object definition name
     * @param businessObjectFormatUsage    the business object format usage
     * @param businessObjectFormatFileType the business object format file type
     * @param businessObjectFormatVersion  the business objcet format version
     * @param partitionValue               the partition value
     * @param subPartitionValues           the sub partition values
     * @param businessObjectDataVersion    the business object data version
     * @return the built business object data key object
     */
    BusinessObjectDataKey buildBusinessObjectDataKey(final String namespace, final String businessObjectDefinitionName, final String businessObjectFormatUsage,
        final String businessObjectFormatFileType, final Integer businessObjectFormatVersion, final String partitionValue,
        final List<String> subPartitionValues, final Integer businessObjectDataVersion)
    {
        BusinessObjectDataKey businessObjectDataKey = new BusinessObjectDataKey();

        businessObjectDataKey.setNamespace(namespace);
        businessObjectDataKey.setBusinessObjectDefinitionName(businessObjectDefinitionName);
        businessObjectDataKey.setBusinessObjectFormatUsage(businessObjectFormatUsage);
        businessObjectDataKey.setBusinessObjectFormatFileType(businessObjectFormatFileType);
        businessObjectDataKey.setBusinessObjectFormatVersion(businessObjectFormatVersion);
        businessObjectDataKey.setPartitionValue(partitionValue);
        businessObjectDataKey.setSubPartitionValues(subPartitionValues);
        businessObjectDataKey.setBusinessObjectDataVersion(businessObjectDataVersion);
        return businessObjectDataKey;
    }
}
