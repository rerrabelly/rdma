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

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.listener.MessageListenerContainer;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.finra.herd.core.ApplicationContextHolder;
import org.finra.herd.core.helper.ConfigurationHelper;
import org.finra.herd.model.dto.ConfigurationValue;
import org.finra.herd.service.BusinessObjectDefinitionService;

/**
 * Search index update jms message listener
 */
@Component
public class SearchIndexUpdateJmsMessageListener
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchIndexUpdateJmsMessageListener.class);

    @Autowired
    private ConfigurationHelper configurationHelper;

    @Autowired
    private BusinessObjectDefinitionService businessObjectDefinitionService;

    /**
     * Periodically check the configuration and apply the action to the storage policy processor JMS message listener service, if needed.
     */
    @Scheduled(fixedDelay = 60000)
    public void controlSearchIndexUpdateJmsMessageListener()
    {
        try
        {
            // Get the configuration setting.
            Boolean jmsMessageListenerEnabled =
                Boolean.valueOf(configurationHelper.getProperty(ConfigurationValue.SEARCH_INDEX_UPDATE_JMS_LISTENER_ENABLED));

            // Get the registry bean.
            JmsListenerEndpointRegistry registry = ApplicationContextHolder.getApplicationContext()
                .getBean("org.springframework.jms.config.internalJmsListenerEndpointRegistry", JmsListenerEndpointRegistry.class);

            // Get the search index update JMS message listener container.
            MessageListenerContainer jmsMessageListenerContainer =
                registry.getListenerContainer(HerdJmsDestinationResolver.SQS_DESTINATION_SEARCH_INDEX_UPDATE_QUEUE);

            // Get the current JMS message listener status and the configuration value.
            LOGGER.debug("controlSearchIndexUpdateJmsMessageListener(): {}={} jmsMessageListenerContainer.isRunning()={}",
                ConfigurationValue.SEARCH_INDEX_UPDATE_JMS_LISTENER_ENABLED.getKey(), jmsMessageListenerEnabled, jmsMessageListenerContainer.isRunning());

            // Apply the relative action if needed.
            if (!jmsMessageListenerEnabled && jmsMessageListenerContainer.isRunning())
            {
                LOGGER.info("controlSearchIndexUpdateJmsMessageListener(): Stopping the search index update JMS message listener ...");
                jmsMessageListenerContainer.stop();
                LOGGER.info("controlSearchIndexUpdateJmsMessageListener(): Done");
            }
            else if (jmsMessageListenerEnabled && !jmsMessageListenerContainer.isRunning())
            {
                LOGGER.info("controlSearchIndexUpdateJmsMessageListener(): Starting the search index update JMS message listener ...");
                jmsMessageListenerContainer.start();
                LOGGER.info("controlSearchIndexUpdateJmsMessageListener(): Done");
            }
        }
        catch (Exception e)
        {
            LOGGER.error("controlSearchIndexUpdateJmsMessageListener(): Failed to control the search index update Jms message listener service.", e);
        }
    }

    /**
     * Processes a JMS message.
     *
     * @param payload the message payload
     * @param allHeaders the JMS headers
     */
    @JmsListener(id = HerdJmsDestinationResolver.SQS_DESTINATION_SEARCH_INDEX_UPDATE_QUEUE,
        containerFactory = "jmsListenerContainerFactory",
        destination = HerdJmsDestinationResolver.SQS_DESTINATION_SEARCH_INDEX_UPDATE_QUEUE)
    public void processMessage(String payload, @Headers Map<Object, Object> allHeaders)
    {
        LOGGER.info("Message received from the JMS queue. jmsQueueName=\"{}\" jmsMessageHeaders=\"{}\" jmsMessagePayload={}",
            HerdJmsDestinationResolver.SQS_DESTINATION_SEARCH_INDEX_UPDATE_QUEUE, allHeaders, payload);

        businessObjectDefinitionService.updateSearchIndexDocumentBusinessObjectDefinition(payload);
    }
}