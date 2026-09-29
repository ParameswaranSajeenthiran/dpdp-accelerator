/**
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 * <p>
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.event.notifications.dao.queries;

/**
 * MySQL dialect query provider for DPDP Event Notification Framework.
 */
public class EventNotificationMysqlDBQueries extends EventNotificationCommonDBQueries {

    /**
     * Returns a query that locks exactly the one active TOPIC row identified by
     * {@code (ORG_ID, LOWER(name))} without acquiring gap locks on neighbouring rows.
     *
     * <h3>Why this override is necessary</h3>
     * The common implementation filters on {@code LOWER(NAME) = LOWER(?)}, which MySQL cannot
     * satisfy with the {@code UQ_TOPIC_ORG_ACTIVE_NAME (ORG_ID, ACTIVE_NAME)} index because the
     * predicate is applied to a function call rather than to the stored generated column. MySQL
     * therefore falls back to a range scan over {@code UQ_TOPIC_ORG_ID} and, in InnoDB's default
     * REPEATABLE READ isolation, acquires next-key (gap + record) locks on <em>every</em> topic row
     * in the org's range.
     *
     * <p>When a concurrent subscription-creation transaction inserts into {@code SUBSCRIPTION_TOPIC}
     * it needs a shared lock on the referenced {@code TOPIC} row (via {@code FK_ST_TOPIC}). If the
     * subscription transaction holds that shared lock while the publish transaction holds the range
     * lock and is waiting to upgrade it, InnoDB detects an AB-BA deadlock and rolls back one of the
     * transactions — surfaced to the caller as {@code EN-5001 "Event publish failed"}.
     *
     * <h3>The fix</h3>
     * By filtering on {@code ACTIVE_NAME = LOWER(?)} instead, MySQL resolves the predicate against
     * the stored generated column and uses {@code UQ_TOPIC_ORG_ACTIVE_NAME} for a single-row index
     * seek. Only the one matching row is locked; no gap locks are acquired; the deadlock cannot
     * occur.
     *
     * <p>The {@code STATUS = 'active'} guard is still present because {@code ACTIVE_NAME} is
     * {@code NULL} for non-active topics (see the DDL's {@code CASE WHEN STATUS = 'active' THEN
     * LOWER(NAME) ELSE NULL END}), so the predicate already implies active status. The explicit
     * guard is kept for clarity and defence-in-depth.
     *
     * @return MySQL-specific {@code SELECT … FOR UPDATE} query that performs a point lookup on
     *         {@code UQ_TOPIC_ORG_ACTIVE_NAME}.
     */
    @Override
    public String getActiveTopicByOrgAndNameForUpdateQuery() {
        return "SELECT TOPIC_ID, ORG_ID, NAME, DESCRIPTION, STATUS, INITIATED_BY " +
                "FROM TOPIC WHERE ORG_ID = ? AND ACTIVE_NAME = LOWER(?) AND STATUS = " +
                SQL_TOPIC_ACTIVE + " FOR UPDATE";
    }
}

