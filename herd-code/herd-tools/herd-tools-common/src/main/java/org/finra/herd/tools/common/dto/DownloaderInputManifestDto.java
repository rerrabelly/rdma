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
package org.finra.herd.tools.common.dto;

import org.finra.herd.tools.common.dto.DataBridgeBaseManifestDto;

/**
 * An input manifest for the downloader.
 */
public class DownloaderInputManifestDto extends DataBridgeBaseManifestDto
{
    private String businessObjectDataVersion;

    public String getBusinessObjectDataVersion()
    {
        return businessObjectDataVersion;
    }

    public void setBusinessObjectDataVersion(String businessObjectDataVersion)
    {
        this.businessObjectDataVersion = businessObjectDataVersion;
    }
}
