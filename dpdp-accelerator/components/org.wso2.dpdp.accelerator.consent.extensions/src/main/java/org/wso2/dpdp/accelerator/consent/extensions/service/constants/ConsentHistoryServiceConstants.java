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

package org.wso2.dpdp.accelerator.consent.extensions.service.constants;

public final class ConsentHistoryServiceConstants {

    private ConsentHistoryServiceConstants() {

    }

    /**
     * What triggered a status-audit/history row. No {@code EXPIRE} value - expiry handling is a
     * separate, later task; {@code EXPIRED} is never persisted by carbon-consent-management and
     * there is no listener hook for it.
     */
    public enum ActionType {
        CREATE,
        UPDATE,
        REVOKE,
        AUTHORIZE,
        DELETE
    }
}
