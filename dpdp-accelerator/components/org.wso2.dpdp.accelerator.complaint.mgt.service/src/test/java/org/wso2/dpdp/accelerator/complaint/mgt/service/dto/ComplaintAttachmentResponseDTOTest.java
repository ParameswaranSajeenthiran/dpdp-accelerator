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

import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;

import static org.testng.Assert.assertEquals;

class ComplaintAttachmentResponseDTOTest {

    @Test
    void allArgsConstructorPopulatesEveryField() {
        ComplaintAttachmentResponseDTO bean =
                new ComplaintAttachmentResponseDTO("a1", "e1", "a.pdf", "application/pdf", 100L, true, 1L);

        assertEquals("a1", bean.getAttachmentId());
        assertEquals("e1", bean.getComplaintEventId());
        assertEquals("a.pdf", bean.getFileName());
        assertEquals("application/pdf", bean.getContentType());
        assertEquals(100L, bean.getSizeBytes());
        assertEquals(true, bean.isPublic());
        assertEquals(1L, bean.getUploadedTime());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        ComplaintAttachmentResponseDTO bean = new ComplaintAttachmentResponseDTO();
        bean.setAttachmentId("a2");
        bean.setComplaintEventId("e2");
        bean.setFileName("b.png");
        bean.setContentType("image/png");
        bean.setSizeBytes(200L);
        bean.setPublic(false);
        bean.setUploadedTime(2L);

        assertEquals("a2", bean.getAttachmentId());
        assertEquals("e2", bean.getComplaintEventId());
        assertEquals("b.png", bean.getFileName());
        assertEquals("image/png", bean.getContentType());
        assertEquals(200L, bean.getSizeBytes());
        assertEquals(false, bean.isPublic());
        assertEquals(2L, bean.getUploadedTime());
    }

    @Test
    void fromMapsEveryFieldFromTheDaoModel() {
        ComplaintAttachment attachment = new ComplaintAttachment("a1", "org1", "c1", "a.pdf", "application/pdf",
                new byte[]{1, 2, 3}, true, 100L);
        attachment.setComplaintEventId("e1");

        ComplaintAttachmentResponseDTO bean = ComplaintAttachmentResponseDTO.from(attachment);

        assertEquals("a1", bean.getAttachmentId());
        assertEquals("e1", bean.getComplaintEventId());
        assertEquals("a.pdf", bean.getFileName());
        assertEquals("application/pdf", bean.getContentType());
        assertEquals(3L, bean.getSizeBytes());
        assertEquals(true, bean.isPublic());
        assertEquals(100L, bean.getUploadedTime());
    }
}
