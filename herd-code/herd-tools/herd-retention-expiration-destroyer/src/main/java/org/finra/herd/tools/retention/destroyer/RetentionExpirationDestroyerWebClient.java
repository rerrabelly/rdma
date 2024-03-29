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

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.finra.herd.sdk.api.BusinessObjectDataApi;
import org.finra.herd.sdk.invoker.ApiException;
import org.finra.herd.sdk.model.BusinessObjectData;
import org.finra.herd.sdk.model.BusinessObjectDataKey;
import org.finra.herd.tools.common.ToolsDtoHelper;
import org.finra.herd.tools.common.databridge.DataBridgeWebClient;

@Component
public class RetentionExpirationDestroyerWebClient extends DataBridgeWebClient
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RetentionExpirationDestroyerWebClient.class);

    /**
     * Retrieves business object definition from the herd registration server.
     *
     * @param businessObjectDataKey the name of the business object data key
     * @param batchMode flag to indicate if herd should use S3 Batch Operations to destroy the business object data
     *
     * @return the business object definition
     *
     * @throws ApiException if an Api exception was encountered
     * @throws URISyntaxException if a URI syntax error was encountered
     * @throws KeyStoreException if a key store exception occurs
     * @throws NoSuchAlgorithmException if a no such algorithm exception occurs
     * @throws KeyManagementException if key management exception
     */
    public BusinessObjectData destroyBusinessObjectData(BusinessObjectDataKey businessObjectDataKey, Boolean batchMode)
        throws ApiException, URISyntaxException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException
    {
        BusinessObjectDataApi businessObjectDataApi = new BusinessObjectDataApi(createApiClient(regServerAccessParamsDto));

        LOGGER.info(String.format("Destroy business object data for: %s", ToolsDtoHelper.businessObjectDataKeyToString(businessObjectDataKey)));
        BusinessObjectData sdkResponse = businessObjectDataApi.businessObjectDataDestroyBusinessObjectData(businessObjectDataKey.getNamespace(),
            businessObjectDataKey.getBusinessObjectDefinitionName(), businessObjectDataKey.getBusinessObjectFormatUsage(),
            businessObjectDataKey.getBusinessObjectFormatFileType(), businessObjectDataKey.getBusinessObjectFormatVersion(),
            businessObjectDataKey.getPartitionValue(), businessObjectDataKey.getBusinessObjectDataVersion(),
            herdStringHelper.join(businessObjectDataKey.getSubPartitionValues(), "|", "\\"), batchMode);

        LOGGER.info("Successfully destroyed business object data from the registration server.");
        return sdkResponse;
    }
}
