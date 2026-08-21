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

package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.CategoryListResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintCategoryBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintCreateRequestBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintCreateResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintListResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintRecordBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintStatusUpdateRequestBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintStatusUpdateResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.MeComplaintCreateRequestBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.MeComplaintStatusUpdateRequestBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintHandlerTest {

    private static final String ORG_ID = DAOConstants.DEFAULT_ORG_ID;

    @Mock
    private ComplaintService complaintService;
    @Mock
    private ComplaintEventService complaintEventService;
    @Mock
    private ComplaintAttachmentService complaintAttachmentService;

    private ComplaintHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ComplaintHandler(complaintService, complaintEventService, complaintAttachmentService);
    }

    private Complaint sampleComplaint(String id, String userId, String status) {
        return new Complaint(id, ORG_ID, userId, userId + "-name", "CMP-2026-00001", "DATA_BREACH", "CRITICAL",
                status, "desc", 1L, 2L, 3L);
    }

    private ComplaintAttachment attachment(String id, boolean isPublic) {
        return new ComplaintAttachment(id, ORG_ID, "c1", "f.pdf", "application/pdf", new byte[]{1}, isPublic, 1L);
    }

    // ---- officer/admin ----

    @Test
    void createComplaintPassesRequestFieldsThroughToService() {
        ComplaintCreateRequestBean request = new ComplaintCreateRequestBean();
        request.setUserId("user1");
        request.setSubjectCategory("DATA_BREACH");
        request.setDescription("desc");
        when(complaintService.createComplaint(ORG_ID, "user1", null, "DATA_BREACH", "desc"))
                .thenReturn(sampleComplaint("c1", "user1", "OPEN"));

        ComplaintCreateResponseBean response = handler.createComplaint(ORG_ID, request);

        assertEquals("c1", response.getId());
        assertEquals("OPEN", response.getStatus());
    }

    @Test
    void createComplaintToleratesNullRequestBody() {
        when(complaintService.createComplaint(eq(ORG_ID), eq(null), eq(null), eq(null), eq(null)))
                .thenReturn(sampleComplaint("c1", "user1", "OPEN"));

        ComplaintCreateResponseBean response = handler.createComplaint(ORG_ID, null);

        assertEquals("c1", response.getId());
    }

    @Test
    void getComplaintComposesRecordWithAllAttachments() {
        when(complaintService.getComplaint(ORG_ID, "c1")).thenReturn(sampleComplaint("c1", "user1", "OPEN"));
        when(complaintAttachmentService.listAttachmentsForComplaint(ORG_ID, "c1"))
                .thenReturn(List.of(attachment("a1", false)));

        ComplaintRecordBean bean = handler.getComplaint(ORG_ID, "c1");

        assertEquals("c1", bean.getId());
        assertEquals(1, bean.getAttachments().size());
    }

    @Test
    void listComplaintsDefaultsLimitTo10AndOffsetTo0WhenNotProvided() {
        when(complaintService.listComplaints(eq(ORG_ID), any(), any(), any(), eq(10), eq(0), any(), any()))
                .thenReturn(List.of());

        ComplaintListResponseBean response = handler.listComplaints(ORG_ID, null, null, null, null, null, null);

        assertEquals(10, response.getMetadata().getLimit());
        assertEquals(0, response.getMetadata().getOffset());
    }

    @Test
    void listComplaintsCapsLimitAt100() {
        when(complaintService.listComplaints(eq(ORG_ID), any(), any(), any(), eq(100), eq(0), any(), any()))
                .thenReturn(List.of());

        ComplaintListResponseBean response = handler.listComplaints(ORG_ID, null, null, null, 500, null, null);

        assertEquals(100, response.getMetadata().getLimit());
    }

    @Test
    void listComplaintsAttachesAttachmentsAndReportsAccuratePageMetadata() {
        when(complaintService.listComplaints(eq(ORG_ID), any(), any(), any(), eq(10), eq(0), any(), any()))
                .thenAnswer(invocation -> {
                    int[] totalOut = invocation.getArgument(7);
                    totalOut[0] = 42;
                    return List.of(sampleComplaint("c1", "user1", "OPEN"), sampleComplaint("c2", "user1",
                            "IN_PROGRESS"));
                });
        when(complaintAttachmentService.listAttachmentsForComplaint(eq(ORG_ID), anyString())).thenReturn(List.of());

        ComplaintListResponseBean response = handler.listComplaints(ORG_ID, null, null, null, null, null, null);

        assertEquals(2, response.getData().size());
        assertEquals(42, response.getMetadata().getTotal());
        assertEquals(2, response.getMetadata().getCount());
    }

    @Test
    void getCategoriesReturnsEveryKnownCategoryWithItsPriority() {
        CategoryListResponseBean response = handler.getCategories();

        assertEquals(10, response.getData().size());
        boolean foundDataBreach = false;
        for (ComplaintCategoryBean bean : response.getData()) {
            if ("DATA_BREACH".equals(bean.getCategory())) {
                assertEquals("CRITICAL", bean.getPriority());
                foundDataBreach = true;
            }
        }
        assertTrue(foundDataBreach);
    }

    @Test
    void updateStatusPassesRequestFieldsThroughToEventService() {
        ComplaintStatusUpdateRequestBean request = new ComplaintStatusUpdateRequestBean();
        request.setToStatus("IN_PROGRESS");
        request.setNote("note");
        when(complaintEventService.updateStatus(ORG_ID, "c1", "officer1", "Officer One", "COMPLAINT_OFFICER",
                "IN_PROGRESS", "note")).thenReturn(sampleComplaint("c1", "user1", "IN_PROGRESS"));

        ComplaintStatusUpdateResponseBean response =
                handler.updateStatus(ORG_ID, "c1", "officer1", "Officer One", "COMPLAINT_OFFICER", request);

        assertEquals("IN_PROGRESS", response.getToStatus());
    }

    // ---- Data Principal ----

    @Test
    void createOwnComplaintUsesCallerAsOwnerRegardlessOfRequestBody() {
        MeComplaintCreateRequestBean request = new MeComplaintCreateRequestBean();
        request.setSubjectCategory("DATA_BREACH");
        request.setDescription("desc");
        when(complaintService.createComplaint(ORG_ID, "user1", "User One", "DATA_BREACH", "desc"))
                .thenReturn(sampleComplaint("c1", "user1", "OPEN"));

        ComplaintCreateResponseBean response = handler.createOwnComplaint(ORG_ID, "user1", "User One", request);

        assertEquals("c1", response.getId());
    }

    @Test
    void getOwnComplaintFiltersToPublicAttachmentsOnly() {
        when(complaintService.requireOwnedComplaint(ORG_ID, "c1", "user1"))
                .thenReturn(sampleComplaint("c1", "user1", "OPEN"));
        when(complaintAttachmentService.listAttachmentsForComplaint(ORG_ID, "c1"))
                .thenReturn(List.of(attachment("a1", true), attachment("a2", false)));

        ComplaintRecordBean bean = handler.getOwnComplaint(ORG_ID, "c1", "user1");

        assertEquals(1, bean.getAttachments().size());
        assertEquals("a1", bean.getAttachments().get(0).getAttachmentId());
    }

    @Test
    void listOwnComplaintsScopesToOwnerAndFiltersPrivateAttachments() {
        when(complaintService.listComplaints(eq(ORG_ID), any(), any(), eq("user1"), eq(10), eq(0), any(), any()))
                .thenReturn(List.of(sampleComplaint("c1", "user1", "OPEN")));
        when(complaintAttachmentService.listAttachmentsForComplaint(ORG_ID, "c1"))
                .thenReturn(List.of(attachment("a1", false)));

        ComplaintListResponseBean response = handler.listOwnComplaints(ORG_ID, "user1", null, null, null, null);

        assertEquals(1, response.getData().size());
        assertEquals(0, response.getData().get(0).getAttachments().size());
    }

    @Test
    void updateOwnStatusVerifiesOwnershipAndForcesUserRole() {
        when(complaintService.requireOwnedComplaint(ORG_ID, "c1", "user1"))
                .thenReturn(sampleComplaint("c1", "user1", "OPEN"));
        MeComplaintStatusUpdateRequestBean request = new MeComplaintStatusUpdateRequestBean();
        request.setToStatus("RESOLVED");
        when(complaintEventService.updateStatus(ORG_ID, "c1", "user1", "User One", "USER", "RESOLVED", null))
                .thenReturn(sampleComplaint("c1", "user1", "RESOLVED"));

        ComplaintStatusUpdateResponseBean response =
                handler.updateOwnStatus(ORG_ID, "c1", "user1", "User One", request);

        assertEquals("RESOLVED", response.getToStatus());
        verify(complaintService).requireOwnedComplaint(ORG_ID, "c1", "user1");
    }

    @Test
    void noArgsConstructorWiresRealServiceImplementations() {
        assertNotNull(new ComplaintHandler());
    }
}
