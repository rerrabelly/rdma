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
package org.finra.herd.service.helper.notification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang3.BooleanUtils;
import org.finra.herd.core.helper.ConfigurationHelper;
import org.finra.herd.model.dto.ConfigurationValue;
import org.finra.herd.model.jpa.BusinessObjectFormatEntity;
import org.finra.herd.service.helper.ConfigurationDaoHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.finra.herd.model.api.xml.BusinessObjectDataKey;
import org.finra.herd.model.dto.BusinessObjectDataStatusChangeNotificationEvent;
import org.finra.herd.model.dto.NotificationEvent;
import org.finra.herd.model.jpa.BusinessObjectDataAttributeDefinitionEntity;
import org.finra.herd.model.jpa.BusinessObjectDataAttributeEntity;
import org.finra.herd.model.jpa.BusinessObjectDataEntity;
import org.finra.herd.service.helper.BusinessObjectDataDaoHelper;
import org.finra.herd.service.helper.BusinessObjectFormatHelper;
import org.springframework.util.Assert;

/**
 * The builder that knows how to build Business Object Data Status Change notification messages
 */
@Component
public class BusinessObjectDataStatusChangeMessageBuilder extends AbstractNotificationMessageBuilder implements NotificationMessageBuilder
{
    @Autowired
    private BusinessObjectDataDaoHelper businessObjectDataDaoHelper;

    @Autowired
    private BusinessObjectFormatHelper businessObjectFormatHelper;

    @Autowired
    private ConfigurationHelper configurationHelper;

    /**
     * Returns Velocity context map of additional keys and values to place in the velocity context.
     *
     * @param notificationEvent the notification event
     *
     * @return the Velocity context map
     */
    @Override
    @SuppressFBWarnings(value = "BC_UNCONFIRMED_CAST", justification =
        "The NotificationEvent is cast to a BusinessObjectDataStatusChangeNotificationEvent which is always the case since" +
            " we manage the event type to a builder in a map defined in NotificationMessageManager")
    public Map<String, Object> getNotificationMessageVelocityContextMap(NotificationEvent notificationEvent)
    {
        BusinessObjectDataStatusChangeNotificationEvent event = (BusinessObjectDataStatusChangeNotificationEvent) notificationEvent;

        // Create a context map of values that can be used when building the message.
        Map<String, Object> velocityContextMap = new HashMap<>();
        addObjectPropertyToContext(velocityContextMap, "businessObjectDataKey", event.getBusinessObjectDataKey(),
            escapeJsonBusinessObjectDataKey(event.getBusinessObjectDataKey()), escapeXmlBusinessObjectDataKey(event.getBusinessObjectDataKey()));
        addStringPropertyToContext(velocityContextMap, "newBusinessObjectDataStatus", event.getNewBusinessObjectDataStatus());
        addStringPropertyToContext(velocityContextMap, "oldBusinessObjectDataStatus", event.getOldBusinessObjectDataStatus());

        // Retrieve business object data entity and business object data id to the context.
        BusinessObjectDataEntity businessObjectDataEntity = businessObjectDataDaoHelper.getBusinessObjectDataEntity(event.getBusinessObjectDataKey());
        velocityContextMap.put("businessObjectDataId", businessObjectDataEntity.getId());

        BusinessObjectFormatEntity businessObjectFormatEntity = businessObjectDataEntity.getBusinessObjectFormat();

        // Load all attribute definitions for this business object data in a map for easy access.
        Map<String, BusinessObjectDataAttributeDefinitionEntity> attributeDefinitionEntityMap =
            businessObjectFormatHelper.getAttributeDefinitionEntities(businessObjectFormatEntity);

        // Build an ordered map of business object data attributes that are flagged to be published in notification messages.
        Map<String, String> businessObjectDataAttributes = new LinkedHashMap<>();
        Map<String, String> businessObjectDataAttributesWithJson = new LinkedHashMap<>();
        Map<String, String> businessObjectDataAttributesWithXml = new LinkedHashMap<>();
        // Get notification header key for filter attribute value
        String filterAttributeKey = configurationHelper.getRequiredProperty(ConfigurationValue.MESSAGE_HEADER_KEY_FILTER_ATTRIBUTE_VALUE);

        if (!attributeDefinitionEntityMap.isEmpty())
        {
            for (BusinessObjectDataAttributeEntity attributeEntity : businessObjectDataEntity.getAttributes())
            {
                if (attributeDefinitionEntityMap.containsKey(attributeEntity.getName().toUpperCase()))
                {
                    BusinessObjectDataAttributeDefinitionEntity attributeDefinitionEntity =
                        attributeDefinitionEntityMap.get(attributeEntity.getName().toUpperCase());

                    if (BooleanUtils.isTrue(attributeDefinitionEntity.getPublish()))
                    {
                        businessObjectDataAttributes.put(attributeEntity.getName(), attributeEntity.getValue());
                        businessObjectDataAttributesWithJson.put(escapeJson(attributeEntity.getName()), escapeJson(attributeEntity.getValue()));
                        businessObjectDataAttributesWithXml.put(escapeXml(attributeEntity.getName()), escapeXml(attributeEntity.getValue()));
                    }

                    if (BooleanUtils.isTrue(attributeDefinitionEntity.getPublishForFilter()))
                    {
                        if (velocityContextMap.containsKey(filterAttributeKey))
                        {
                            throw new IllegalStateException(String.format("Multiple attributes are marked as publishForFilter for business object format {%s}.",
                                    businessObjectFormatHelper.businessObjectFormatEntityAltKeyToString(businessObjectFormatEntity)));
                        }
                        addStringPropertyToContext(velocityContextMap, filterAttributeKey, attributeEntity.getValue());
                    }
                }
            }
        }

        // Add the map of business object data attributes to the context.
        addObjectPropertyToContext(velocityContextMap, "businessObjectDataAttributes", businessObjectDataAttributes, businessObjectDataAttributesWithJson,
            businessObjectDataAttributesWithXml);

        // Add the namespace Velocity property for the header.
        addStringPropertyToContext(velocityContextMap, "namespace", event.getBusinessObjectDataKey().getNamespace());

        return velocityContextMap;
    }

    /**
     * Creates an XML escaped copy of the specified business object data key.
     *
     * @param businessObjectDataKey the business object data key
     *
     * @return the XML escaped business object data key
     */
    private BusinessObjectDataKey escapeXmlBusinessObjectDataKey(final BusinessObjectDataKey businessObjectDataKey)
    {
        // Escape sub-partition values, if they are present.
        List<String> escapedSubPartitionValues = null;
        if (businessObjectDataKey.getSubPartitionValues() != null)
        {
            escapedSubPartitionValues = new ArrayList<>();
            for (String subPartitionValue : businessObjectDataKey.getSubPartitionValues())
            {
                escapedSubPartitionValues.add(escapeXml(subPartitionValue));
            }
        }

        // Build and return an XML escaped business object data key.
        return new BusinessObjectDataKey(escapeXml(businessObjectDataKey.getNamespace()), escapeXml(businessObjectDataKey.getBusinessObjectDefinitionName()),
            escapeXml(businessObjectDataKey.getBusinessObjectFormatUsage()), escapeXml(businessObjectDataKey.getBusinessObjectFormatFileType()),
            businessObjectDataKey.getBusinessObjectFormatVersion(), escapeXml(businessObjectDataKey.getPartitionValue()), escapedSubPartitionValues,
            businessObjectDataKey.getBusinessObjectDataVersion());
    }

    /**
     * Creates a JSON escaped copy of the specified business object data key.
     *
     * @param businessObjectDataKey the business object data key
     *
     * @return the JSON escaped business object data key
     */
    private BusinessObjectDataKey escapeJsonBusinessObjectDataKey(final BusinessObjectDataKey businessObjectDataKey)
    {
        // Escape sub-partition values, if they are present.
        List<String> escapedSubPartitionValues = null;
        if (businessObjectDataKey.getSubPartitionValues() != null)
        {
            escapedSubPartitionValues = new ArrayList<>();
            for (String subPartitionValue : businessObjectDataKey.getSubPartitionValues())
            {
                escapedSubPartitionValues.add(escapeJson(subPartitionValue));
            }
        }

        // Build and return a JSON escaped business object data key.
        return new BusinessObjectDataKey(escapeJson(businessObjectDataKey.getNamespace()), escapeJson(businessObjectDataKey.getBusinessObjectDefinitionName()),
            escapeJson(businessObjectDataKey.getBusinessObjectFormatUsage()), escapeJson(businessObjectDataKey.getBusinessObjectFormatFileType()),
            businessObjectDataKey.getBusinessObjectFormatVersion(), escapeJson(businessObjectDataKey.getPartitionValue()), escapedSubPartitionValues,
            businessObjectDataKey.getBusinessObjectDataVersion());
    }

}
