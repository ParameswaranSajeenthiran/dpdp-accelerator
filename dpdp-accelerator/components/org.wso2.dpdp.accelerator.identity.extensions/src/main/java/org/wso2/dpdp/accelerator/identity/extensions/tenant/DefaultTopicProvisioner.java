/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.identity.extensions.tenant;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.stratos.common.exception.StratosException;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DefaultTopic;
import org.wso2.dpdp.accelerator.event.notifications.service.TopicService;

import java.util.ArrayList;
import java.util.List;

/**
 * Reconciles the system-defined Event Notification topics for one tenant.
 */
class DefaultTopicProvisioner {

    private static final Log LOG = LogFactory.getLog(DefaultTopicProvisioner.class);

    private final TopicService topicService;

    DefaultTopicProvisioner(TopicService topicService) {
        if (topicService == null) {
            throw new IllegalArgumentException("TopicService cannot be null.");
        }
        this.topicService = topicService;
    }

    void provision(String orgId) throws StratosException {
        String sanitizedOrgId = orgId == null ? null : orgId.replaceAll("[\r\n]", "");
        List<String> failedTopics = new ArrayList<>();
        Exception firstFailure = null;

        for (DefaultTopic defaultTopic : DefaultTopic.values()) {
            try {
                topicService.ensureSystemTopic(orgId, defaultTopic.getName(), defaultTopic.getDescription());
                LOG.debug("Reconciled system topic '" + defaultTopic.getName()
                        + "' for tenant: " + sanitizedOrgId);
            } catch (Exception e) {
                LOG.error("Failed to reconcile system topic '" + defaultTopic.getName()
                        + "' for tenant: " + sanitizedOrgId, e);
                failedTopics.add(defaultTopic.getName());
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }

        if (!failedTopics.isEmpty()) {
            throw new StratosException("Failed to provision DPDP system topics " + failedTopics
                    + " for tenant: " + sanitizedOrgId, firstFailure);
        }
    }
}
