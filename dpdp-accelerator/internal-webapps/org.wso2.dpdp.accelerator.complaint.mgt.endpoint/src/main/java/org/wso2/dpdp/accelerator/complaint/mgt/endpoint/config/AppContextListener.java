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

package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * Runs the deployment.toml loading + DB schema init on Tomcat's servlet lifecycle.
 */
public class AppContextListener implements ServletContextListener {

    private static final Log LOG = LogFactory.getLog(AppContextListener.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        LOG.info("Initializing WSO2 DPDP Complaint Server webapp...");
        AppBootstrap.loadDeploymentConfig();
        AppBootstrap.initDatabase();
        LOG.info("WSO2 DPDP Complaint Server webapp initialized.");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // No resources to release; DBUtil opens/closes a connection per request.
    }
}
