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

package org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentHistoryRecord;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentStatusAuditRecord;
import org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.dto.ConsentHistoryResponseDTO;
import org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.dto.StatusHistoryResponseDTO;
import org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.exception.ConsentHistoryEndpointException;
import org.wso2.dpdp.accelerator.consent.extensions.service.models.PagedResult;

import java.util.Collections;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class ConsentHistoryEndpointUtilTest {

    @Test
    public void validatePaginationAcceptsDefaults() {

        ConsentHistoryEndpointUtil.validatePagination(20, 0);
    }

    @Test
    public void validatePaginationRejectsLimitOutOfRange() {

        expectThrows(ConsentHistoryEndpointException.class,
                () -> ConsentHistoryEndpointUtil.validatePagination(0, 0));
        expectThrows(ConsentHistoryEndpointException.class,
                () -> ConsentHistoryEndpointUtil.validatePagination(101, 0));
    }

    @Test
    public void validatePaginationRejectsNegativeOffset() {

        expectThrows(ConsentHistoryEndpointException.class,
                () -> ConsentHistoryEndpointUtil.validatePagination(20, -1));
    }

    @Test
    public void requireHistoryExistsThrowsWhenCountIsZero() {

        expectThrows(ConsentHistoryEndpointException.class,
                () -> ConsentHistoryEndpointUtil.requireHistoryExists(0, "consent-1234"));
    }

    @Test
    public void requireHistoryExistsPassesWhenCountIsPositive() {

        ConsentHistoryEndpointUtil.requireHistoryExists(3, "consent-1234");
    }

    @Test
    public void isOwnerComparesCallerAgainstPrincipal() {

        assertTrue(ConsentHistoryEndpointUtil.isOwner("jdoe@carbon.super", "jdoe@carbon.super"));
        assertFalse(ConsentHistoryEndpointUtil.isOwner("jdoe@carbon.super", "other@carbon.super"));
        assertFalse(ConsentHistoryEndpointUtil.isOwner(null, "jdoe@carbon.super"));
    }

    @Test
    public void requireOwnerThrowsWhenCallerIsNotThePrincipal() {

        expectThrows(ConsentHistoryEndpointException.class,
                () -> ConsentHistoryEndpointUtil.requireOwner("jdoe@carbon.super", "other@carbon.super"));
    }

    @Test
    public void buildStatusHistoryResponseMapsRecordsAndPagination() {

        ConsentStatusAuditRecord record = new ConsentStatusAuditRecord();
        record.setPreviousStatus("PENDING");
        record.setCurrentStatus("ACTIVE");
        record.setActionType("AUTHORIZE_APPROVE");
        record.setActionBy("jdoe@carbon.super");
        record.setActionTime(1755504000000L);
        PagedResult<ConsentStatusAuditRecord> result = new PagedResult<>(Collections.singletonList(record), 1);

        StatusHistoryResponseDTO response = ConsentHistoryEndpointUtil.buildStatusHistoryResponse("consent-1234",
                result, 20, 0);

        assertEquals(response.getConsentId(), "consent-1234");
        assertEquals(response.getStatusHistory().size(), 1);
        assertEquals(response.getStatusHistory().get(0).getCurrentStatus(), "ACTIVE");
        assertEquals(response.getPagination().getTotalCount(), 1);
        assertEquals(response.getPagination().getLimit(), 20);
    }

    @Test
    public void buildConsentHistoryResponseParsesSnapshotJsonIntoATree() {

        ConsentHistoryRecord record = new ConsentHistoryRecord();
        record.setActionType("REVOKE");
        record.setActionBy("jdoe@carbon.super");
        record.setActionTime(1755504000000L);
        record.setSnapshot("{\"state\":\"ACTIVE\"}");
        PagedResult<ConsentHistoryRecord> result = new PagedResult<>(Collections.singletonList(record), 1);

        ConsentHistoryResponseDTO response = ConsentHistoryEndpointUtil.buildConsentHistoryResponse("consent-1234",
                result, 20, 0, new ObjectMapper());

        assertEquals(response.getHistory().size(), 1);
        JsonNode snapshot = (JsonNode) response.getHistory().get(0).getSnapshot();
        assertEquals(snapshot.get("state").asText(), "ACTIVE");
    }

    @Test
    public void buildConsentHistoryResponseThrowsOnUnparsableSnapshot() {

        ConsentHistoryRecord record = new ConsentHistoryRecord();
        record.setSnapshot("not valid json");
        PagedResult<ConsentHistoryRecord> result = new PagedResult<>(Collections.singletonList(record), 1);

        expectThrows(ConsentHistoryEndpointException.class, () -> ConsentHistoryEndpointUtil
                .buildConsentHistoryResponse("consent-1234", result, 20, 0, new ObjectMapper()));
    }

    @Test
    public void buildStatusHistoryResponseHandlesEmptyRecordList() {

        PagedResult<ConsentStatusAuditRecord> result = new PagedResult<>(Collections.<ConsentStatusAuditRecord>emptyList(), 0);

        StatusHistoryResponseDTO response = ConsentHistoryEndpointUtil.buildStatusHistoryResponse("consent-1234",
                result, 20, 0);

        assertTrue(response.getStatusHistory().isEmpty());
    }
}
