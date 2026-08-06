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

package org.wso2.dpdp.accelerator.event.notifications.service.queries;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class SubscriptionQueryBuilderTest {

    @Test
    public void testBuildWithOrgIdOnly() {
        SubscriptionQueryBuilder builder = SubscriptionQueryBuilder.build("org1", null, null, null, 10, 0, null);
        assertNotNull(builder);

        String countSql = builder.getCountSql();
        String selectSql = builder.getSelectSql();

        assertTrue(countSql.contains("WHERE ORG_ID = ?"));
        assertTrue(selectSql.contains("WHERE ORG_ID = ?"));
        assertTrue(selectSql.contains("LIMIT ? OFFSET ?"));
        assertEquals(builder.getParameters().size(), 1);
        assertEquals(builder.getParameters().get(0), "org1");
    }

    @Test
    public void testBuildWithStatusAndSearch() {
        SubscriptionQueryBuilder builder = SubscriptionQueryBuilder.build("org1", "active", null, "consent", 20, 10, "asc");
        assertNotNull(builder);

        String selectSql = builder.getSelectSql();
        assertTrue(selectSql.contains("AND LOWER(STATUS) = ?"));
        assertTrue(selectSql.contains("AND (LOWER(SUBSCRIPTION_ID) LIKE ?"));
        assertTrue(selectSql.contains("ORDER BY CREATED_AT ASC"));

        // 1 (orgId) + 1 (status) + 4 (search term fields) = 6 parameters
        assertEquals(builder.getParameters().size(), 6);
        assertEquals(builder.getParameters().get(0), "org1");
        assertEquals(builder.getParameters().get(1), "active");
    }

    @Test
    public void testBuildWithPurposes() {
        SubscriptionQueryBuilder builder = SubscriptionQueryBuilder.build("org1", null, "marketing,analytics", null, 10, 0, null);
        assertNotNull(builder);

        String selectSql = builder.getSelectSql();
        assertTrue(selectSql.contains("SUBSCRIPTION_PURPOSE"));
        // 1 (orgId) + 2 (purposes) = 3 parameters
        assertEquals(builder.getParameters().size(), 3);
        assertEquals(builder.getParameters().get(1), "marketing");
        assertEquals(builder.getParameters().get(2), "analytics");
    }
}
