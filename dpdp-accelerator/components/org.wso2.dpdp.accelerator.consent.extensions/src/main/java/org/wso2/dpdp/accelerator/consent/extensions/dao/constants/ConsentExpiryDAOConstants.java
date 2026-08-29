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

package org.wso2.dpdp.accelerator.consent.extensions.dao.constants;

/**
 * Table and column names for {@code DPDP_CONSENT_EXPIRY_TRACKER}.
 */
public final class ConsentExpiryDAOConstants {

    public static final String EXPIRY_TRACKER_TABLE = "DPDP_CONSENT_EXPIRY_TRACKER";

    public static final String COLUMN_CONSENT_ID = "CONSENT_ID";
    public static final String COLUMN_ORG_ID = "ORG_ID";
    public static final String COLUMN_EXPIRY_TIME = "EXPIRY_TIME";

    public static final String DEFAULT_ORG_ID = "carbon.super";

    private ConsentExpiryDAOConstants() {

    }
}
