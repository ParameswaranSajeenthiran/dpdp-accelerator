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

package org.wso2.dpdp.accelerator.complaint.mgt.dao.queries;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class ComplaintQueryBuilderTest {

    private final ComplaintCommonDBQueries queries = new ComplaintCommonDBQueries();

    @Test
    public void testBaseSelectAndCountQueries() {
        ComplaintQueryBuilder builder = new ComplaintQueryBuilder("org1", queries);

        QueryResult select = builder.buildSelectQuery(10, 0);
        QueryResult count = builder.buildCountQuery();

        assertTrue(select.getSql().contains("WHERE ORG_ID = ?"));
        assertTrue(select.getSql().contains("ORDER BY UPDATED_TIME DESC LIMIT ? OFFSET ?"));
        assertEquals(select.getParameters(), java.util.List.of("org1", 10, 0));
        assertTrue(count.getSql().startsWith("SELECT COUNT(*) FROM COMPLAINT"));
        assertEquals(count.getParameters(), java.util.List.of("org1"));
    }

    @Test
    public void testStatusPriorityAndUserIdFiltersAreExactMatch() {
        ComplaintQueryBuilder builder = new ComplaintQueryBuilder("org1", queries)
                .setStatus("OPEN")
                .setPriority("HIGH")
                .setUserId("user1");

        QueryResult select = builder.buildSelectQuery(10, 0);

        assertTrue(select.getSql().contains("AND STATUS = ? "));
        assertTrue(select.getSql().contains("AND PRIORITY = ? "));
        assertTrue(select.getSql().contains("AND USER_ID = ? "));
        assertEquals(select.getParameters(), java.util.List.of("org1", "OPEN", "HIGH", "user1", 10, 0));
    }

    @Test
    public void testSearchFilterMatchesReferenceIdUserNameOrUserIdCaseInsensitively() {
        ComplaintQueryBuilder builder = new ComplaintQueryBuilder("org1", queries).setSearch("Acc_%");

        QueryResult select = builder.buildSelectQuery(10, 0);

        assertTrue(select.getSql().contains("LOWER(REFERENCE_ID) LIKE ? ESCAPE '!'"));
        assertTrue(select.getSql().contains("LOWER(USER_NAME) LIKE ? ESCAPE '!'"));
        assertTrue(select.getSql().contains("LOWER(USER_ID) LIKE ? ESCAPE '!'"));
        java.util.List<Object> params = select.getParameters();
        // org1, then the same escaped/lowercased term repeated for each of the 3 OR'd columns, then limit/offset.
        assertEquals(params.get(0), "org1");
        assertEquals(params.get(1), "%acc!_!%%");
        assertEquals(params.get(2), "%acc!_!%%");
        assertEquals(params.get(3), "%acc!_!%%");
        assertEquals(params.get(4), 10);
        assertEquals(params.get(5), 0);
    }

    @Test
    public void testBlankSearchIsIgnored() {
        ComplaintQueryBuilder builder = new ComplaintQueryBuilder("org1", queries).setSearch("   ");

        QueryResult select = builder.buildSelectQuery(10, 0);

        assertEquals(select.getParameters(), java.util.List.of("org1", 10, 0));
    }

    @Test
    public void testSortResolvesKnownColumnsAndDirection() {
        assertTrue(new ComplaintQueryBuilder("org1", queries).setSort("submittedTime").buildSelectQuery(10, 0)
                .getSql().contains("ORDER BY CREATED_TIME ASC"));
        assertTrue(new ComplaintQueryBuilder("org1", queries).setSort("-statutoryDueTime").buildSelectQuery(10, 0)
                .getSql().contains("ORDER BY STATUTORY_DUE_TIME DESC"));
        // An unrecognized field still falls back to the UPDATED_TIME column, but the "-" prefix
        // (absent here) is what decides direction - so this is ASC, not the blank-sort DESC default.
        assertTrue(new ComplaintQueryBuilder("org1", queries).setSort("unknownField").buildSelectQuery(10, 0)
                .getSql().contains("ORDER BY UPDATED_TIME ASC"));
    }
}
