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

package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintQueueStats;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintStatus;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/** Pins the JSON each generated response serializes to, built from the DAO models. */
public class ComplaintDtoMapperTest {

    private static final String ATTACHMENT_JSON = "{\"attachmentId\":\"a1\",\"complaintEventId\":\"e1\","
            + "\"fileName\":\"evidence.pdf\",\"contentType\":\"application/pdf\",\"sizeBytes\":3,"
            + "\"isPublic\":true,\"uploadedTime\":1712345678901}";

    private final ObjectMapper json = new ObjectMapper();

    // JsonNode.equals ignores field order (TestNG's assertEquals would iterate the values in order).
    // Both sides are parsed from text so numbers compare by value, not by Int/LongNode type.
    private void assertJson(Object actual, String expected) throws Exception {
        JsonNode expectedNode = json.readTree(expected);
        JsonNode actualNode = json.readTree(json.writeValueAsString(actual));
        assertTrue(expectedNode.equals(actualNode), "expected " + expectedNode + " but was " + actualNode);
    }

    private static Complaint complaint() {
        return new Complaint("c1", "org1", "user1", "User One", "CMP-0001", "DATA_BREACH", "CRITICAL",
                "WAITING_ON_CLIENT", "leak", 1L, 2L, 3L);
    }

    private static ComplaintAttachment attachment(String id, boolean isPublic) {
        ComplaintAttachment attachment = new ComplaintAttachment(id, "org1", "c1", "evidence.pdf", "application/pdf",
                new byte[]{1, 2, 3}, isPublic, 1712345678901L);
        attachment.setComplaintEventId("e1");
        return attachment;
    }

    private static ComplaintEvent event(String id, String fromStatus, String toStatus, boolean isPublic) {
        return new ComplaintEvent(id, "org1", "c1", "officer1", "Officer One", "COMPLAINT_OFFICER", isPublic, "note",
                fromStatus, toStatus, 5L);
    }

    @Test
    public void recordCarriesAttachmentsAndUserName() throws Exception {
        assertJson(ComplaintDtoMapper.toRecord(complaint(), Collections.singletonList(attachment("a1", true))),
                "{\"id\":\"c1\",\"referenceId\":\"CMP-0001\",\"subjectCategory\":\"DATA_BREACH\","
                        + "\"priority\":\"CRITICAL\",\"status\":\"WAITING_ON_CLIENT\",\"userId\":\"user1\","
                        + "\"userName\":\"User One\",\"description\":\"leak\",\"attachments\":[" + ATTACHMENT_JSON
                        + "],\"submittedAt\":1,\"updatedAt\":2,\"statutoryDueDate\":3}");
        assertEquals(ComplaintDtoMapper.toRecord(complaint(), null).getAttachments(), Collections.emptyList());
    }

    @Test
    public void createResponseCarriesUserName() throws Exception {
        assertJson(ComplaintDtoMapper.toCreateResponse(complaint()),
                "{\"id\":\"c1\",\"referenceId\":\"CMP-0001\",\"subjectCategory\":\"DATA_BREACH\","
                        + "\"priority\":\"CRITICAL\",\"status\":\"WAITING_ON_CLIENT\",\"userId\":\"user1\","
                        + "\"userName\":\"User One\",\"description\":\"leak\",\"submittedAt\":1,\"updatedAt\":2,"
                        + "\"statutoryDueDate\":3}");
    }

    @Test
    public void timelineEntryDerivesItsTypeAndNestsAttachments() throws Exception {
        assertJson(ComplaintDtoMapper.toTimelineEntry(event("e1", null, null, false),
                        Collections.singletonList(attachment("a1", true))),
                "{\"id\":\"e1\",\"type\":\"INTERNAL_NOTE\",\"isPublic\":false,\"actorUserId\":\"officer1\","
                        + "\"actorUserName\":\"Officer One\",\"actorRole\":\"COMPLAINT_OFFICER\",\"message\":\"note\","
                        + "\"fromStatus\":null,\"toStatus\":null,\"createdTime\":5,\"attachments\":["
                        + ATTACHMENT_JSON + "]}");
        assertEquals(ComplaintDtoMapper.toTimelineEntry(event("e2", "OPEN", "IN_PROGRESS", true), null)
                .getType().toString(), "STATUS_CHANGE");
        assertEquals(ComplaintDtoMapper.toTimelineEntry(event("e3", null, null, true), null)
                .getAttachments(), Collections.emptyList());
    }

    @Test
    public void commentAndStatusUpdateResponses() throws Exception {
        assertJson(ComplaintDtoMapper.toCommentResponse(event("e1", "OPEN", "IN_PROGRESS", true)),
                "{\"id\":\"e1\",\"actorUserId\":\"officer1\",\"actorRole\":\"COMPLAINT_OFFICER\","
                        + "\"message\":\"note\",\"isPublic\":true,\"fromStatus\":\"OPEN\","
                        + "\"toStatus\":\"IN_PROGRESS\",\"createdTime\":5}");
        assertJson(ComplaintDtoMapper.toStatusUpdateResponse(complaint()),
                "{\"message\":\"Status transition confirmed\",\"toStatus\":\"WAITING_ON_CLIENT\",\"updatedAt\":2}");
    }

    @Test
    public void statsCategoriesAndPageMetadata() throws Exception {
        assertJson(ComplaintDtoMapper.toQueueStats(new ComplaintQueueStats(1, 2, 3, 4)),
                "{\"openCount\":1,\"awaitingInternalReviewCount\":2,\"resolvedCount\":3,\"slaBreachedCount\":4}");

        Map<String, String> categories = new LinkedHashMap<>();
        categories.put("DATA_BREACH", "CRITICAL");
        categories.put("OTHER", "LOW");
        assertJson(ComplaintDtoMapper.toCategories(categories),
                "{\"data\":[{\"category\":\"DATA_BREACH\",\"priority\":\"CRITICAL\"},"
                        + "{\"category\":\"OTHER\",\"priority\":\"LOW\"}]}");

        assertJson(ComplaintDtoMapper.toPage(11, 10, 1, 10), "{\"total\":11,\"offset\":10,\"count\":1,\"limit\":10}");
    }

    @Test
    public void attachmentsAndDownloadContent() throws Exception {
        List<ComplaintAttachment> uploaded = Arrays.asList(attachment("a1", true), attachment("a2", false));
        assertEquals(ComplaintDtoMapper.toAttachments(uploaded).size(), 2);
        assertJson(ComplaintDtoMapper.toAttachment(uploaded.get(0)), ATTACHMENT_JSON);

        ComplaintAttachment listed = attachment("a3", true);
        listed.setFileData(null);
        listed.setSizeBytesOverride(2048L);
        assertEquals(ComplaintDtoMapper.toAttachment(listed).getSizeBytes(), Long.valueOf(2048L));

        ComplaintAttachment download = attachment("a1", true);
        download.setFileData(new byte[]{1, 2, 3, (byte) 0xff});
        assertJson(ComplaintDtoMapper.toDownload(download), "{\"attachmentId\":\"a1\",\"fileName\":\"evidence.pdf\","
                + "\"contentType\":\"application/pdf\",\"uploadedTime\":1712345678901,\"content\":\"AQID/w==\"}");
        download.setFileData(null);
        assertNull(ComplaintDtoMapper.toDownload(download).getContent());
    }

    @Test
    public void nullsPassThrough() {
        assertNull(ComplaintDtoMapper.toCreateResponse(null));
        assertNull(ComplaintDtoMapper.toRecord(null, null));
        assertNull(ComplaintDtoMapper.toQueueStats(null));
        assertNull(ComplaintDtoMapper.toStatusUpdateResponse(null));
        assertNull(ComplaintDtoMapper.toCommentResponse(null));
        assertNull(ComplaintDtoMapper.toTimelineEntry(null, null));
        assertNull(ComplaintDtoMapper.toAttachment(null));
        assertNull(ComplaintDtoMapper.toAttachments(null));
        assertNull(ComplaintDtoMapper.toDownload(null));
        assertNull(ComplaintDtoMapper.value(null));
        assertEquals(ComplaintDtoMapper.value(ComplaintStatus.WAITING_ON_CLIENT), "WAITING_ON_CLIENT");
    }
}
