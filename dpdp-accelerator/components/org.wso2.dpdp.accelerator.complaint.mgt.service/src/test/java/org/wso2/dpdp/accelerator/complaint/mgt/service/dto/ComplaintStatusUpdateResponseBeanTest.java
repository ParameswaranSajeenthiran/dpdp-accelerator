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

package org.wso2.dpdp.accelerator.complaint.mgt.service.dto;

import org.junit.jupiter.api.Test;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComplaintStatusUpdateResponseBeanTest {

    @Test
    void fromSetsAFixedConfirmationMessageAndMapsStatusAndUpdatedTime() {
        Complaint complaint = new Complaint("c1", "org1", "user1", "User One", "CMP-2026-00001", "DATA_BREACH",
                "CRITICAL", "IN_PROGRESS", "desc", 1L, 2L, 3L);

        ComplaintStatusUpdateResponseBean bean = ComplaintStatusUpdateResponseBean.from(complaint);

        assertEquals("Status transition confirmed", bean.getMessage());
        assertEquals("IN_PROGRESS", bean.getToStatus());
        assertEquals(2L, bean.getUpdatedAt());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        ComplaintStatusUpdateResponseBean bean = new ComplaintStatusUpdateResponseBean();
        bean.setMessage("custom message");
        bean.setToStatus("RESOLVED");
        bean.setUpdatedAt(1L);

        assertEquals("custom message", bean.getMessage());
        assertEquals("RESOLVED", bean.getToStatus());
        assertEquals(1L, bean.getUpdatedAt());
    }
}
