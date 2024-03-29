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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import org.finra.herd.dao.BusinessObjectFormatDao;
import org.finra.herd.model.api.xml.Attribute;
import org.finra.herd.model.api.xml.AttributeDefinition;
import org.finra.herd.model.api.xml.BusinessObjectDataKey;
import org.finra.herd.model.api.xml.BusinessObjectFormat;
import org.finra.herd.model.api.xml.BusinessObjectFormatExternalInterfaceKey;
import org.finra.herd.model.api.xml.BusinessObjectFormatKey;
import org.finra.herd.model.api.xml.Schema;
import org.finra.herd.model.api.xml.SchemaColumn;
import org.finra.herd.model.jpa.BusinessObjectDataAttributeDefinitionEntity;
import org.finra.herd.model.jpa.BusinessObjectFormatAttributeEntity;
import org.finra.herd.model.jpa.BusinessObjectFormatEntity;
import org.finra.herd.model.jpa.BusinessObjectFormatExternalInterfaceEntity;
import org.finra.herd.model.jpa.SchemaColumnEntity;

/**
 * A helper class for BusinessObjectFormatService related code.
 */
@Component
public class BusinessObjectFormatHelper
{
    @Autowired
    private AlternateKeyHelper alternateKeyHelper;

    @Autowired
    private BusinessObjectFormatDao businessObjectFormatDao;

    @Autowired
    private BusinessObjectFormatExternalInterfaceHelper businessObjectFormatExternalInterfaceHelper;

    /**
     * Returns a string representation of the alternate key values for the business object format.
     *
     * @param businessObjectFormatEntity the business object format entity
     *
     * @return the string representation of the alternate key values for the business object format entity
     */
    public String businessObjectFormatEntityAltKeyToString(BusinessObjectFormatEntity businessObjectFormatEntity)
    {
        return String.format("namespace: \"%s\", businessObjectDefinitionName: \"%s\", businessObjectFormatUsage: \"%s\", " +
                "businessObjectFormatFileType: \"%s\", businessObjectFormatVersion: %d",
            businessObjectFormatEntity.getBusinessObjectDefinition().getNamespace().getCode(),
            businessObjectFormatEntity.getBusinessObjectDefinition().getName(), businessObjectFormatEntity.getUsage(),
            businessObjectFormatEntity.getFileType().getCode(), businessObjectFormatEntity.getBusinessObjectFormatVersion());
    }

    /**
     * Returns a string representation of the business object format key.
     *
     * @param businessObjectFormatKey the business object format key
     *
     * @return the string representation of the business object format key
     */
    public String businessObjectFormatKeyToString(BusinessObjectFormatKey businessObjectFormatKey)
    {
        return businessObjectFormatKeyToString(businessObjectFormatKey.getNamespace(), businessObjectFormatKey.getBusinessObjectDefinitionName(),
            businessObjectFormatKey.getBusinessObjectFormatUsage(), businessObjectFormatKey.getBusinessObjectFormatFileType(),
            businessObjectFormatKey.getBusinessObjectFormatVersion());
    }

    /**
     * Creates the business object format from the persisted entity.
     *
     * @param businessObjectFormatEntity the newly persisted business object format entity.
     * @param checkLatestVersion need to check latest version
     *
     * @return the business object format.
     */
    public BusinessObjectFormat createBusinessObjectFormatFromEntity(BusinessObjectFormatEntity businessObjectFormatEntity, Boolean checkLatestVersion)
    {
        BusinessObjectFormat businessObjectFormat = new BusinessObjectFormat();
        businessObjectFormat.setId(businessObjectFormatEntity.getId());
        businessObjectFormat.setNamespace(businessObjectFormatEntity.getBusinessObjectDefinition().getNamespace().getCode());
        businessObjectFormat.setBusinessObjectDefinitionName(businessObjectFormatEntity.getBusinessObjectDefinition().getName());
        businessObjectFormat.setBusinessObjectFormatUsage(businessObjectFormatEntity.getUsage());
        businessObjectFormat.setBusinessObjectFormatFileType(businessObjectFormatEntity.getFileType().getCode());
        businessObjectFormat.setBusinessObjectFormatVersion(businessObjectFormatEntity.getBusinessObjectFormatVersion());
        businessObjectFormat.setLatestVersion(businessObjectFormatEntity.getLatestVersion());
        businessObjectFormat.setPartitionKey(businessObjectFormatEntity.getPartitionKey());
        businessObjectFormat.setDescription(businessObjectFormatEntity.getDescription());
        businessObjectFormat.setDocumentSchema(businessObjectFormatEntity.getDocumentSchema());
        businessObjectFormat.setDocumentSchemaUrl(businessObjectFormatEntity.getDocumentSchemaUrl());

        // Add in the attributes.
        List<Attribute> attributes = new ArrayList<>();
        businessObjectFormat.setAttributes(attributes);
        for (BusinessObjectFormatAttributeEntity attributeEntity : businessObjectFormatEntity.getAttributes())
        {
            Attribute attribute = new Attribute();
            attributes.add(attribute);
            attribute.setName(attributeEntity.getName());
            attribute.setValue(attributeEntity.getValue());
        }

        // Add in the attribute definitions.
        List<AttributeDefinition> attributeDefinitions = new ArrayList<>();
        businessObjectFormat.setAttributeDefinitions(attributeDefinitions);

        for (BusinessObjectDataAttributeDefinitionEntity attributeDefinitionEntity : businessObjectFormatEntity.getAttributeDefinitions())
        {
            AttributeDefinition attributeDefinition = new AttributeDefinition();
            attributeDefinitions.add(attributeDefinition);
            attributeDefinition.setName(attributeDefinitionEntity.getName());
            attributeDefinition.setPublish(attributeDefinitionEntity.getPublish());
            attributeDefinition.setPublishForFilter(attributeDefinitionEntity.getPublishForFilter());
        }

        // Add in the business object format flag that enables business object data published attributes change event notification.
        businessObjectFormat.setEnableBusinessObjectDataPublishedAttributesChangeEventNotification(
            businessObjectFormatEntity.isEnableBusinessObjectDataPublishedAttributesChangeEventNotification());

        // Only add schema information if this format has any schema columns defined.
        if (!businessObjectFormatEntity.getSchemaColumns().isEmpty())
        {
            Schema schema = new Schema();
            businessObjectFormat.setSchema(schema);
            schema.setNullValue(businessObjectFormatEntity.getNullValue());
            schema.setDelimiter(businessObjectFormatEntity.getDelimiter());
            schema.setCollectionItemsDelimiter(businessObjectFormatEntity.getCollectionItemsDelimiter());
            schema.setMapKeysDelimiter(businessObjectFormatEntity.getMapKeysDelimiter());
            schema.setEscapeCharacter(businessObjectFormatEntity.getEscapeCharacter());
            schema.setCustomRowFormat(businessObjectFormatEntity.getCustomRowFormat());
            schema.setCustomClusteredBy(businessObjectFormatEntity.getCustomClusteredBy());
            schema.setCustomTblProperties(businessObjectFormatEntity.getCustomTblProperties());
            schema.setPartitionKeyGroup(
                businessObjectFormatEntity.getPartitionKeyGroup() != null ? businessObjectFormatEntity.getPartitionKeyGroup().getPartitionKeyGroupName() :
                    null);

            // Create two lists of schema column entities: one for the data columns and one for the partition columns.
            List<SchemaColumnEntity> dataSchemaColumns = new ArrayList<>();
            List<SchemaColumnEntity> partitionSchemaColumns = new ArrayList<>();
            for (SchemaColumnEntity schemaColumnEntity : businessObjectFormatEntity.getSchemaColumns())
            {
                // We can determine which list (or both) a column entity belongs to depending on whether it has a position and/or partition level set.
                if (schemaColumnEntity.getPosition() != null)
                {
                    dataSchemaColumns.add(schemaColumnEntity);
                }
                if (schemaColumnEntity.getPartitionLevel() != null)
                {
                    partitionSchemaColumns.add(schemaColumnEntity);
                }
            }

            // Sort the data schema columns on the position.
            dataSchemaColumns.sort(new SchemaColumnPositionComparator());

            // Sort the partition schema columns on the partition level.
            partitionSchemaColumns.sort(new SchemaColumnPartitionLevelComparator());

            // Add in the data schema columns.
            List<SchemaColumn> schemaColumns = new ArrayList<>();
            schema.setColumns(schemaColumns);
            for (SchemaColumnEntity schemaColumnEntity : dataSchemaColumns)
            {
                schemaColumns.add(createSchemaColumn(schemaColumnEntity));
            }

            // Add in the partition schema columns.
            // We need to only add partitions when we have data. Otherwise an empty list will cause JAXB to generate a partitions wrapper tag with no
            // columns which isn't valid from an XSD standpoint.
            if (partitionSchemaColumns.size() > 0)
            {
                schemaColumns = new ArrayList<>();
                schema.setPartitions(schemaColumns);
                for (SchemaColumnEntity schemaColumnEntity : partitionSchemaColumns)
                {
                    schemaColumns.add(createSchemaColumn(schemaColumnEntity));
                }
            }
        }

        BusinessObjectFormatEntity latestVersionBusinessObjectFormatEntity = businessObjectFormatEntity;
        //need to check if the business object format entity is the latest version
        //use the latest version if it is not
        if (checkLatestVersion)
        {
            BusinessObjectFormatKey businessObjectFormatKey = getBusinessObjectFormatKey(businessObjectFormatEntity);
            businessObjectFormatKey.setBusinessObjectFormatVersion(null);
            latestVersionBusinessObjectFormatEntity = businessObjectFormatDao.getBusinessObjectFormatByAltKey(businessObjectFormatKey);
        }

        // Add in the parents.
        List<BusinessObjectFormatKey> businessObjectFormatParents = new ArrayList<>();
        businessObjectFormat.setBusinessObjectFormatParents(businessObjectFormatParents);
        for (BusinessObjectFormatEntity businessObjectFormatEntityParent : latestVersionBusinessObjectFormatEntity.getBusinessObjectFormatParents())
        {
            BusinessObjectFormatKey businessObjectFormatParent = getBusinessObjectFormatKey(businessObjectFormatEntityParent);
            businessObjectFormatParent.setBusinessObjectFormatVersion(null);
            businessObjectFormatParents.add(businessObjectFormatParent);
        }

        // Add in the children.
        List<BusinessObjectFormatKey> businessObjectFormatChildren = new ArrayList<>();
        businessObjectFormat.setBusinessObjectFormatChildren(businessObjectFormatChildren);
        for (BusinessObjectFormatEntity businessObjectFormatEntityChild : latestVersionBusinessObjectFormatEntity.getBusinessObjectFormatChildren())
        {
            BusinessObjectFormatKey businessObjectFormatChild = getBusinessObjectFormatKey(businessObjectFormatEntityChild);
            businessObjectFormatChild.setBusinessObjectFormatVersion(null);
            businessObjectFormatChildren.add(businessObjectFormatChild);
        }

        // Add in the external interface mappings.
        List<BusinessObjectFormatExternalInterfaceKey> businessObjectFormatExternalInterfaceKeys = new ArrayList<>();
        businessObjectFormat.setBusinessObjectFormatExternalInterfaces(businessObjectFormatExternalInterfaceKeys);
        for (BusinessObjectFormatExternalInterfaceEntity businessObjectFormatExternalInterfaceEntity : latestVersionBusinessObjectFormatEntity
            .getBusinessObjectFormatExternalInterfaces())
        {
            businessObjectFormatExternalInterfaceKeys.add(businessObjectFormatExternalInterfaceHelper
                .createBusinessObjectFormatExternalInterfaceKeyFromEntity(businessObjectFormatExternalInterfaceEntity));
        }

        // Add in the retention information.
        businessObjectFormat.setRecordFlag(latestVersionBusinessObjectFormatEntity.isRecordFlag());
        businessObjectFormat.setRetentionPeriodInDays(latestVersionBusinessObjectFormatEntity.getRetentionPeriodInDays());
        if (latestVersionBusinessObjectFormatEntity.getRetentionType() != null)
        {
            businessObjectFormat.setRetentionType(latestVersionBusinessObjectFormatEntity.getRetentionType().getCode());
        }

        // Add in the business object format schema backwards compatibility changes flag.
        businessObjectFormat.setAllowNonBackwardsCompatibleChanges(latestVersionBusinessObjectFormatEntity.isAllowNonBackwardsCompatibleChanges());

        // Add in relational table related fields
        businessObjectFormat.setRelationalSchemaName(latestVersionBusinessObjectFormatEntity.getRelationalSchemaName());
        businessObjectFormat.setRelationalTableName(latestVersionBusinessObjectFormatEntity.getRelationalTableName());

        return businessObjectFormat;
    }

    /**
     * Creates the business object format from the persisted entity.
     *
     * @param businessObjectFormatEntity the newly persisted business object format entity.
     *
     * @return the business object format.
     */
    public BusinessObjectFormat createBusinessObjectFormatFromEntity(BusinessObjectFormatEntity businessObjectFormatEntity)
    {
        return createBusinessObjectFormatFromEntity(businessObjectFormatEntity, false);
    }

    /**
     * Returns a map with attribute names in uppercase mapped to the relative attribute definition entities.
     *
     * @param businessObjectFormatEntity the business object format entity
     *
     * @return the attribute definition entities loaded in a map
     */
    public Map<String, BusinessObjectDataAttributeDefinitionEntity> getAttributeDefinitionEntities(BusinessObjectFormatEntity businessObjectFormatEntity)
    {
        Map<String, BusinessObjectDataAttributeDefinitionEntity> result = new HashMap<>();

        for (BusinessObjectDataAttributeDefinitionEntity businessObjectDataAttributeDefinitionEntity : businessObjectFormatEntity.getAttributeDefinitions())
        {
            result.put(businessObjectDataAttributeDefinitionEntity.getName().toUpperCase(), businessObjectDataAttributeDefinitionEntity);
        }

        return result;
    }

    /**
     * Creates a business object format key from specified business object format entity.
     *
     * @param businessObjectFormatEntity the business object format entity
     *
     * @return the business object format key
     */
    public BusinessObjectFormatKey getBusinessObjectFormatKey(BusinessObjectFormatEntity businessObjectFormatEntity)
    {
        BusinessObjectFormatKey businessObjectFormatKey = new BusinessObjectFormatKey();

        businessObjectFormatKey.setNamespace(businessObjectFormatEntity.getBusinessObjectDefinition().getNamespace().getCode());
        businessObjectFormatKey.setBusinessObjectDefinitionName(businessObjectFormatEntity.getBusinessObjectDefinition().getName());
        businessObjectFormatKey.setBusinessObjectFormatUsage(businessObjectFormatEntity.getUsage());
        businessObjectFormatKey.setBusinessObjectFormatFileType(businessObjectFormatEntity.getFileType().getCode());
        businessObjectFormatKey.setBusinessObjectFormatVersion(businessObjectFormatEntity.getBusinessObjectFormatVersion());

        return businessObjectFormatKey;
    }

    /**
     * Returns a business object format key for the business object format.
     *
     * @param businessObjectFormat the business object format
     *
     * @return the business object format key
     */
    public BusinessObjectFormatKey getBusinessObjectFormatKey(BusinessObjectFormat businessObjectFormat)
    {
        return new BusinessObjectFormatKey(businessObjectFormat.getNamespace(), businessObjectFormat.getBusinessObjectDefinitionName(),
            businessObjectFormat.getBusinessObjectFormatUsage(), businessObjectFormat.getBusinessObjectFormatFileType(),
            businessObjectFormat.getBusinessObjectFormatVersion());
    }

    /**
     * Gets business object format key from the specified business object data key.
     *
     * @param businessObjectDataKey the business object data key
     *
     * @return the business object format key
     */
    public BusinessObjectFormatKey getBusinessObjectFormatKey(BusinessObjectDataKey businessObjectDataKey)
    {
        return new BusinessObjectFormatKey(businessObjectDataKey.getNamespace(), businessObjectDataKey.getBusinessObjectDefinitionName(),
            businessObjectDataKey.getBusinessObjectFormatUsage(), businessObjectDataKey.getBusinessObjectFormatFileType(),
            businessObjectDataKey.getBusinessObjectFormatVersion());
    }

    /**
     * Returns partition key to partition level mapping. Please note that this method expects the list of partition keys not to be empty and main list of
     * partition levels contain a list of levels for each partition key. Those second (inner) lists can be empty. The relative mapping for partition key is only
     * added to result map if its partition level values are the same. Please note partition level values in the database schema use 1-based numbering.
     *
     * @param partitionKeys the list of partition keys, not empty
     * @param partitionLevels the list of partition level lists with the main list not empty and having one inner list for each partition key
     *
     * @return the partition key to partition level mapping
     */
    public Map<String, Integer> getPartitionKeyToPartitionLevelMapping(List<String> partitionKeys, List<List<Integer>> partitionLevels)
    {
        // Declare a map to store partition levels for partition keys.
        Map<String, Integer> partitionKeyToPartitionLevel = new HashMap<>();

        // Process each partition key in the list. The list is expected to contain at least one partition key.
        for (int index = 0; index < partitionKeys.size(); index++)
        {
            // If partition key has the same partition level across all target business object formats, we add that key to the result map.
            HashSet<Integer> uniquePartitionLevels = new HashSet<>(partitionLevels.get(index));
            if (uniquePartitionLevels.size() == 1)
            {
                partitionKeyToPartitionLevel.put(partitionKeys.get(index), uniquePartitionLevels.iterator().next());
            }
        }

        return partitionKeyToPartitionLevel;
    }

    /**
     * Validates the business object format key. This method also trims the key parameters.
     *
     * @param key the business object format key
     *
     * @throws IllegalArgumentException if any validation errors were found
     */
    public void validateBusinessObjectFormatKey(BusinessObjectFormatKey key) throws IllegalArgumentException
    {
        validateBusinessObjectFormatKey(key, true);
    }

    /**
     * Validates the business object format key. This method also trims the key parameters.
     *
     * @param key the business object format key
     * @param businessObjectFormatVersionRequired specifies if business object format version parameter is required or not
     *
     * @throws IllegalArgumentException if any validation errors were found
     */
    public void validateBusinessObjectFormatKey(BusinessObjectFormatKey key, Boolean businessObjectFormatVersionRequired) throws IllegalArgumentException
    {
        Assert.notNull(key, "A business object format key must be specified.");
        key.setNamespace(alternateKeyHelper.validateStringParameter("namespace", key.getNamespace()));
        key.setBusinessObjectDefinitionName(
            alternateKeyHelper.validateStringParameter("business object definition name", key.getBusinessObjectDefinitionName()));
        key.setBusinessObjectFormatUsage(alternateKeyHelper.validateStringParameter("business object format usage", key.getBusinessObjectFormatUsage()));
        key.setBusinessObjectFormatFileType(
            alternateKeyHelper.validateStringParameter("business object format file type", key.getBusinessObjectFormatFileType()));
        if (businessObjectFormatVersionRequired)
        {
            Assert.notNull(key.getBusinessObjectFormatVersion(), "A business object format version must be specified.");
        }
    }

    /**
     * Returns a string representation of the business object format key.
     *
     * @param namespace the namespace
     * @param businessObjectDefinitionName the business object definition name
     * @param businessObjectFormatUsage the business object format usage
     * @param businessObjectFormatFileType the business object format file type
     * @param businessObjectFormatVersion the business object formation version
     *
     * @return the string representation of the business object format key
     */
    private String businessObjectFormatKeyToString(String namespace, String businessObjectDefinitionName, String businessObjectFormatUsage,
        String businessObjectFormatFileType, Integer businessObjectFormatVersion)
    {
        return String.format("namespace: \"%s\", businessObjectDefinitionName: \"%s\", businessObjectFormatUsage: \"%s\", " +
                "businessObjectFormatFileType: \"%s\", businessObjectFormatVersion: %d", namespace, businessObjectDefinitionName, businessObjectFormatUsage,
            businessObjectFormatFileType, businessObjectFormatVersion);
    }

    /**
     * Creates a schema column from a schema column entity.
     *
     * @param schemaColumnEntity the schema column entity.
     *
     * @return the newly created schema column.
     */
    private SchemaColumn createSchemaColumn(SchemaColumnEntity schemaColumnEntity)
    {
        SchemaColumn schemaColumn = new SchemaColumn();
        schemaColumn.setName(schemaColumnEntity.getName());
        schemaColumn.setType(schemaColumnEntity.getType());
        schemaColumn.setSize(schemaColumnEntity.getSize());
        schemaColumn.setRequired(schemaColumnEntity.getRequired());
        schemaColumn.setDefaultValue(schemaColumnEntity.getDefaultValue());
        schemaColumn.setDescription(schemaColumnEntity.getDescription());
        return schemaColumn;
    }

    /**
     * A schema column "partitionLevel" comparator. A static named inner class was created as opposed to an anonymous inner class since it has no dependencies
     * on it's containing class and is therefore more efficient.
     */
    private static class SchemaColumnPartitionLevelComparator implements Comparator<SchemaColumnEntity>, Serializable
    {
        private static final long serialVersionUID = -6222033387743498432L;

        @Override
        public int compare(SchemaColumnEntity entity1, SchemaColumnEntity entity2)
        {
            return entity1.getPartitionLevel().compareTo(entity2.getPartitionLevel());
        }
    }

    /**
     * A schema column "position" comparator. A static named inner class was created as opposed to an anonymous inner class since it has no dependencies on it's
     * containing class and is therefore more efficient.
     */
    private static class SchemaColumnPositionComparator implements Comparator<SchemaColumnEntity>, Serializable
    {
        private static final long serialVersionUID = -5860079250619473538L;

        @Override
        public int compare(SchemaColumnEntity entity1, SchemaColumnEntity entity2)
        {
            return entity1.getPosition().compareTo(entity2.getPosition());
        }
    }
}
