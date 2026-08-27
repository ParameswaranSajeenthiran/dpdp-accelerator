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

import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentDownloadResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService.UploadedFile;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;
import org.wso2.dpdp.common.config.ConfigProvider;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintAttachmentHandlerTest {

    private static final String ORG_ID = DAOConstants.DEFAULT_ORG_ID;

    @Mock
    private ComplaintService complaintService;
    @Mock
    private ComplaintAttachmentService complaintAttachmentService;
    @Mock
    private FormDataBodyPart filePart;
    @Mock
    private ContentDisposition contentDisposition;

    private ComplaintAttachmentHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ComplaintAttachmentHandler(complaintService, complaintAttachmentService);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("CO_MAX_ATTACHMENT_FILES_PER_UPLOAD");
        ConfigProvider.resetForTesting();
        System.clearProperty("deployment.config.path");
    }

    private ComplaintAttachment attachment(String id, boolean isPublic) {
        return new ComplaintAttachment(id, ORG_ID, "c1", "a.pdf", "application/pdf", new byte[]{1}, isPublic, 1L);
    }

    private ComplaintAttachmentResponseDTO attachmentBean(String id, boolean isPublic) {
        return ComplaintAttachmentResponseDTO.from(attachment(id, isPublic));
    }

    private ComplaintAttachmentDownloadResponseDTO downloadBean(ComplaintAttachment attachment) {
        return new ComplaintAttachmentDownloadResponseDTO(attachment.getAttachmentId(), attachment.getFileName(),
                attachment.getContentType(), attachment.getFileData());
    }

    private void useMaxAttachmentSizeBytes(Path tempDir, String maxSizeBytes) throws IOException {
        Path tomlFile = tempDir.resolve("deployment.toml");
        try (Writer writer = Files.newBufferedWriter(tomlFile, StandardCharsets.UTF_8)) {
            writer.write("[attachment]\nmaxSizeBytes = \"" + maxSizeBytes + "\"\n");
        }
        System.setProperty("deployment.config.path", tomlFile.toString());
        ConfigProvider.resetForTesting();
    }

    // ---- officer/admin ----

    @Test
    void uploadComplaintAttachmentsReadsFilePartsAndDelegatesToServiceWithGivenIsPublic() {
        byte[] data = "file-content".getBytes();
        when(filePart.getValueAs(java.io.InputStream.class)).thenReturn(new ByteArrayInputStream(data));
        when(filePart.getMediaType()).thenReturn(MediaType.valueOf("application/pdf"));
        when(filePart.getContentDisposition()).thenReturn(contentDisposition);
        when(contentDisposition.getFileName()).thenReturn("a.pdf");
        when(complaintAttachmentService.uploadComplaintAttachments(eq(ORG_ID), eq("c1"), any(), eq(false),
                eq("officer1"), eq("Officer One"), eq("COMPLAINT_OFFICER")))
                .thenReturn(List.of(attachmentBean("att1", false)));

        List<ComplaintAttachmentResponseDTO> result = handler.uploadComplaintAttachments(ORG_ID, "c1",
                List.of(filePart), false, "officer1", "Officer One");

        assertEquals(1, result.size());
        assertEquals("att1", result.get(0).getAttachmentId());

        ArgumentCaptor<List<UploadedFile>> captor = ArgumentCaptor.forClass(List.class);
        verify(complaintAttachmentService).uploadComplaintAttachments(eq(ORG_ID), eq("c1"), captor.capture(),
                eq(false), eq("officer1"), eq("Officer One"), eq("COMPLAINT_OFFICER"));
        UploadedFile uploaded = captor.getValue().get(0);
        assertEquals("a.pdf", uploaded.getFileName());
        assertEquals("application/pdf", uploaded.getContentType());
        assertEquals(data.length, uploaded.getData().length);
    }

    @Test
    void uploadComplaintAttachmentsDefaultsNullIsPublicToTrue() {
        when(complaintAttachmentService.uploadComplaintAttachments(eq(ORG_ID), eq("c1"), any(), eq(true),
                eq("officer1"), eq("Officer One"), eq("COMPLAINT_OFFICER"))).thenReturn(List.of());

        handler.uploadComplaintAttachments(ORG_ID, "c1", null, null, "officer1", "Officer One");

        verify(complaintAttachmentService).uploadComplaintAttachments(eq(ORG_ID), eq("c1"), any(), eq(true),
                eq("officer1"), eq("Officer One"), eq("COMPLAINT_OFFICER"));
    }

    @Test
    void uploadComplaintAttachmentsDefaultsContentTypeToOctetStreamWhenMediaTypeMissing() {
        when(filePart.getValueAs(java.io.InputStream.class))
                .thenReturn(new ByteArrayInputStream("x".getBytes()));
        when(filePart.getMediaType()).thenReturn(null);
        when(filePart.getContentDisposition()).thenReturn(null);
        when(complaintAttachmentService.uploadComplaintAttachments(eq(ORG_ID), eq("c1"), any(), eq(true),
                eq("officer1"), eq("Officer One"), eq("COMPLAINT_OFFICER"))).thenReturn(List.of());

        handler.uploadComplaintAttachments(ORG_ID, "c1", List.of(filePart), true, "officer1", "Officer One");

        ArgumentCaptor<List<UploadedFile>> captor = ArgumentCaptor.forClass(List.class);
        verify(complaintAttachmentService).uploadComplaintAttachments(eq(ORG_ID), eq("c1"), captor.capture(),
                eq(true), eq("officer1"), eq("Officer One"), eq("COMPLAINT_OFFICER"));
        UploadedFile uploaded = captor.getValue().get(0);
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, uploaded.getContentType());
        assertNull(uploaded.getFileName());
    }

    @Test
    void uploadComplaintAttachmentsThrowsWhenTooManyFilePartsProvidedWithoutReadingAny() {
        System.setProperty("CO_MAX_ATTACHMENT_FILES_PER_UPLOAD", "2");
        List<FormDataBodyPart> parts = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            parts.add(mock(FormDataBodyPart.class));
        }

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> handler.uploadComplaintAttachments(ORG_ID, "c1", parts, true, "officer1", "Officer One"));

        assertEquals("CO-4002", ex.getCode());
        // The count must be checked before any part is read - otherwise the exact memory-exhaustion
        // vector this cap exists to close (many parts, each read into heap before being rejected)
        // would still occur.
        for (FormDataBodyPart part : parts) {
            verifyNoInteractions(part);
        }
        verifyNoInteractions(complaintAttachmentService);
    }

    @Test
    void uploadComplaintAttachmentsThrowsWhenFileExceedsMaxSizeWithoutBufferingItWhole(@TempDir Path tempDir)
            throws IOException {
        useMaxAttachmentSizeBytes(tempDir, "5");
        byte[] oversized = "this is way more than five bytes".getBytes();
        when(filePart.getValueAs(java.io.InputStream.class)).thenReturn(new ByteArrayInputStream(oversized));
        when(filePart.getMediaType()).thenReturn(MediaType.valueOf("application/pdf"));
        when(filePart.getContentDisposition()).thenReturn(contentDisposition);
        when(contentDisposition.getFileName()).thenReturn("big.pdf");

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> handler.uploadComplaintAttachments(ORG_ID, "c1", List.of(filePart), true, "officer1",
                        "Officer One"));

        assertEquals("CO-4002", ex.getCode());
        assertTrue(ex.getDescription().contains("big.pdf"));
        verifyNoInteractions(complaintAttachmentService);
    }

    @Test
    void downloadAttachmentIsUnrestricted() {
        byte[] content = "hello".getBytes();
        ComplaintAttachment attachment = new ComplaintAttachment("att1", ORG_ID, "c1", "a.pdf", "application/pdf",
                content, false, 100L);
        when(complaintAttachmentService.downloadAttachment(ORG_ID, "c1", "att1", false))
                .thenReturn(downloadBean(attachment));

        ComplaintAttachmentDownloadResponseDTO response = handler.downloadAttachment(ORG_ID, "c1", "att1");

        assertEquals("att1", response.getAttachmentId());
        assertEquals(Base64.getEncoder().encodeToString(content), response.getContent());
    }

    // ---- Data Principal ----

    @Test
    void uploadOwnComplaintAttachmentsDelegatesOwnershipEnforcementToTheService() {
        // Ownership is enforced by the service (uploadOwnComplaintAttachments), not the handler -
        // see ComplaintAttachmentServiceImpl for the defense-in-depth check.
        when(complaintAttachmentService.uploadOwnComplaintAttachments(eq(ORG_ID), eq("c1"), eq("user1"),
                eq("User One"), any())).thenReturn(List.of(attachmentBean("att1", true)));

        List<ComplaintAttachmentResponseDTO> result =
                handler.uploadOwnComplaintAttachments(ORG_ID, "c1", "user1", "User One", List.of());

        assertEquals(1, result.size());
        verify(complaintAttachmentService).uploadOwnComplaintAttachments(eq(ORG_ID), eq("c1"), eq("user1"),
                eq("User One"), any());
    }

    @Test
    void downloadOwnAttachmentDelegatesOwnershipEnforcementToTheService() {
        // Ownership is enforced by the service (downloadOwnAttachment), not the handler - see
        // ComplaintAttachmentServiceImpl for the defense-in-depth check.
        ComplaintAttachment attachment = attachment("att1", true);
        when(complaintAttachmentService.downloadOwnAttachment(ORG_ID, "c1", "user1", "att1"))
                .thenReturn(downloadBean(attachment));

        ComplaintAttachmentDownloadResponseDTO response =
                handler.downloadOwnAttachment(ORG_ID, "c1", "user1", "att1");

        assertEquals("att1", response.getAttachmentId());
        verify(complaintAttachmentService).downloadOwnAttachment(ORG_ID, "c1", "user1", "att1");
    }

    @Test
    void noArgsConstructorWiresRealServiceImplementations() {
        assertNotNull(new ComplaintAttachmentHandler());
    }
}
