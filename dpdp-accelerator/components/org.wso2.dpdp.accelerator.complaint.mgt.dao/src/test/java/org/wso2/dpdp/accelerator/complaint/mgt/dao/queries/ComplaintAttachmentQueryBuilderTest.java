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

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class ComplaintAttachmentQueryBuilderTest {

    private final ComplaintCommonDBQueries queries = new ComplaintCommonDBQueries();

    @Test
    public void testListMetadataByComplaintsBindsOnePlaceholderPerComplaintId() {
        QueryResult query = new ComplaintAttachmentQueryBuilder("org1", queries)
                .setComplaintIds(List.of("c1", "c2", "c3"))
                .buildListMetadataByComplaintsQuery();

        assertTrue(query.getSql().contains("WHERE ORG_ID = ? AND COMPLAINT_ID IN (?, ?, ?)"));
        assertTrue(query.getSql().endsWith("ORDER BY COMPLAINT_ID ASC, CREATED_TIME ASC"));
        assertTrue(!query.getSql().contains("FILE_DATA,"), "blob must not be selected, only its length");
        assertEquals(query.getParameters(), List.of("org1", "c1", "c2", "c3"));
    }

    @Test
    public void testListMetadataByComplaintsRejectsEmptyIdList() {
        ComplaintAttachmentQueryBuilder builder = new ComplaintAttachmentQueryBuilder("org1", queries)
                .setComplaintIds(null);

        expectThrows(IllegalStateException.class, builder::buildListMetadataByComplaintsQuery);
    }
}
