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

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ComplaintAttachmentDownloadResponseDTOTest {

    @Test
    void constructorBase64EncodesNonNullContent() {
        byte[] raw = "hello".getBytes();

        ComplaintAttachmentDownloadResponseDTO bean =
                new ComplaintAttachmentDownloadResponseDTO("a1", "a.pdf", "application/pdf", raw);

        assertEquals("a1", bean.getAttachmentId());
        assertEquals("a.pdf", bean.getFileName());
        assertEquals("application/pdf", bean.getContentType());
        assertEquals(Base64.getEncoder().encodeToString(raw), bean.getContent());
    }

    @Test
    void constructorLeavesContentNullWhenRawContentIsNull() {
        ComplaintAttachmentDownloadResponseDTO bean =
                new ComplaintAttachmentDownloadResponseDTO("a1", "a.pdf", "application/pdf", null);

        assertNull(bean.getContent());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        ComplaintAttachmentDownloadResponseDTO bean = new ComplaintAttachmentDownloadResponseDTO();
        bean.setAttachmentId("a2");
        bean.setFileName("b.png");
        bean.setContentType("image/png");
        bean.setContent("base64content");

        assertEquals("a2", bean.getAttachmentId());
        assertEquals("b.png", bean.getFileName());
        assertEquals("image/png", bean.getContentType());
        assertEquals("base64content", bean.getContent());
    }
}
