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

package org.wso2.dpdp.accelerator.complaint.mgt.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintAttachmentDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintEventDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService.UploadedFile;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentDownloadResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;
import org.wso2.dpdp.common.config.ConfigProvider;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintAttachmentServiceImplTest {

    @Mock
    private ComplaintAttachmentDAO attachmentDAO;
    @Mock
    private ComplaintEventDAO complaintEventDAO;
    @Mock
    private ComplaintService complaintService;

    private ComplaintAttachmentServiceImpl attachmentService;

    @BeforeEach
    void setUp() {
        attachmentService = new ComplaintAttachmentServiceImpl(attachmentDAO, complaintEventDAO, complaintService);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("CO_MAX_ATTACHMENT_FILES_PER_UPLOAD");
        ConfigProvider.resetForTesting();
        System.clearProperty("deployment.config.path");
    }

    private UploadedFile pdfFile(String name, int size) {
        return new UploadedFile(name, "application/pdf", new byte[size]);
    }

    private void useMaxAttachmentSizeBytes(Path tempDir, String maxSizeBytes) throws IOException {
        Path tomlFile = tempDir.resolve("deployment.toml");
        try (Writer writer = Files.newBufferedWriter(tomlFile, StandardCharsets.UTF_8)) {
            writer.write("[attachment]\nmaxSizeBytes = \"" + maxSizeBytes + "\"\n");
        }
        System.setProperty("deployment.config.path", tomlFile.toString());
        ConfigProvider.resetForTesting();
    }

    // ---- uploadComplaintAttachments ----

    @Test
    void uploadComplaintAttachmentsRequiresComplaintToExist() {
        when(complaintService.requireComplaint("org1", "c1")).thenThrow(
                new ComplaintException("CO-4040", "Complaint not found", "desc", 404));

        assertThrows(ComplaintException.class, () -> attachmentService.uploadComplaintAttachments("org1", "c1",
                List.of(pdfFile("a.pdf", 10)), true, "user1", "User One", "USER"));

        verify(attachmentDAO, never()).addAttachment(any());
    }

    @Test
    void uploadComplaintAttachmentsThrowsWhenFileListIsEmpty() {
        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> attachmentService.uploadComplaintAttachments("org1", "c1", List.of(), true, "user1",
                        "User One", "USER"));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void uploadComplaintAttachmentsThrowsWhenFileDataIsEmpty() {
        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> attachmentService.uploadComplaintAttachments("org1", "c1",
                        List.of(pdfFile("empty.pdf", 0)), true, "user1", "User One", "USER"));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void uploadComplaintAttachmentsThrowsWhenContentTypeNotAllowed() {
        UploadedFile file = new UploadedFile("a.exe", "application/octet-stream", new byte[]{1});

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> attachmentService.uploadComplaintAttachments("org1", "c1", List.of(file), true, "user1",
                        "User One", "USER"));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void uploadComplaintAttachmentsThrowsWhenTooManyFilesInOneRequest() {
        System.setProperty("CO_MAX_ATTACHMENT_FILES_PER_UPLOAD", "2");

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> attachmentService.uploadComplaintAttachments("org1", "c1",
                        List.of(pdfFile("a.pdf", 10), pdfFile("b.pdf", 10), pdfFile("c.pdf", 10)), true, "user1",
                        "User One", "USER"));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void uploadComplaintAttachmentsThrowsWhenFileExceedsMaxSize(@TempDir Path tempDir) throws IOException {
        useMaxAttachmentSizeBytes(tempDir, "5");

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> attachmentService.uploadComplaintAttachments("org1", "c1",
                        List.of(pdfFile("big.pdf", 10)), true, "user1", "User One", "USER"));

        assertEquals("CO-4002", ex.getCode());
    }

    @Test
    void uploadComplaintAttachmentsThrowsWhenActorUserIdBlank() {
        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> attachmentService.uploadComplaintAttachments("org1", "c1", List.of(pdfFile("a.pdf", 10)), true,
                        "  ", "User One", "USER"));

        assertEquals("CO-4002", ex.getCode());
        verify(complaintEventDAO, never()).addEvent(any());
    }

    @Test
    void uploadComplaintAttachmentsThrowsWhenActorRoleInvalid() {
        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> attachmentService.uploadComplaintAttachments("org1", "c1", List.of(pdfFile("a.pdf", 10)), true,
                        "user1", "User One", "SYSTEM"));

        assertEquals("CO-4002", ex.getCode());
        verify(complaintEventDAO, never()).addEvent(any());
    }

    @Test
    void uploadComplaintAttachmentsStoresEachFileWithGivenIsPublic() {
        when(complaintEventDAO.addEvent(any(ComplaintEvent.class))).thenReturn(true);
        when(attachmentDAO.addAttachment(any(ComplaintAttachment.class))).thenReturn(true);

        List<ComplaintAttachmentResponseDTO> result = attachmentService.uploadComplaintAttachments("org1", "c1",
                List.of(pdfFile("a.pdf", 10), pdfFile("b.pdf", 20)), false, "officer1", "Officer One",
                "COMPLAINT_OFFICER");

        assertEquals(2, result.size());
        assertEquals(10, result.get(0).getSizeBytes());
        assertEquals(20, result.get(1).getSizeBytes());
        assertEquals(false, result.get(0).isPublic());
        assertEquals(false, result.get(1).isPublic());
    }

    @Test
    void uploadComplaintAttachmentsRecordsOneUploadEventAndLinksEveryAttachmentToIt() {
        when(complaintEventDAO.addEvent(any(ComplaintEvent.class))).thenReturn(true);
        when(attachmentDAO.addAttachment(any(ComplaintAttachment.class))).thenReturn(true);

        List<ComplaintAttachmentResponseDTO> result = attachmentService.uploadComplaintAttachments("org1", "c1",
                List.of(pdfFile("a.pdf", 10), pdfFile("b.pdf", 20)), true, "user1", "User One", "USER");

        ArgumentCaptor<ComplaintEvent> eventCaptor = ArgumentCaptor.forClass(ComplaintEvent.class);
        verify(complaintEventDAO).addEvent(eventCaptor.capture());
        ComplaintEvent event = eventCaptor.getValue();

        assertNotNull(event.getComplaintEventId());
        assertEquals("user1", event.getActorUserId());
        assertEquals("User One", event.getActorUserName());
        assertEquals("USER", event.getActorRole());
        assertTrue(event.isPublic());
        assertNull(event.getComment());

        assertEquals(event.getComplaintEventId(), result.get(0).getComplaintEventId());
        assertEquals(event.getComplaintEventId(), result.get(1).getComplaintEventId());
    }

    @Test
    void uploadComplaintAttachmentsThrowsInternalErrorWhenEventStoreFails() {
        when(complaintEventDAO.addEvent(any(ComplaintEvent.class))).thenReturn(false);

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> attachmentService.uploadComplaintAttachments("org1", "c1", List.of(pdfFile("a.pdf", 10)), true,
                        "user1", "User One", "USER"));

        assertEquals("CO-5000", ex.getCode());
        verify(attachmentDAO, never()).addAttachment(any());
    }

    @Test
    void uploadComplaintAttachmentsThrowsInternalErrorWhenPersistFails() {
        when(complaintEventDAO.addEvent(any(ComplaintEvent.class))).thenReturn(true);
        when(attachmentDAO.addAttachment(any(ComplaintAttachment.class))).thenReturn(false);

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> attachmentService.uploadComplaintAttachments("org1", "c1", List.of(pdfFile("a.pdf", 10)),
                        true, "user1", "User One", "USER"));

        assertEquals("CO-5000", ex.getCode());
    }

    // ---- listAttachmentsForComplaint ----

    @Test
    void listAttachmentsForComplaintMapsDaoResultsToMetadataDtos() {
        ComplaintAttachment attachment = new ComplaintAttachment();
        attachment.setAttachmentId("a1");
        attachment.setFileName("a.pdf");
        attachment.setContentType("application/pdf");
        attachment.setSizeBytesOverride(123L);
        when(attachmentDAO.listAttachmentsForComplaint("org1", "c1")).thenReturn(List.of(attachment));

        List<ComplaintAttachmentResponseDTO> result = attachmentService.listAttachmentsForComplaint("org1", "c1");

        assertEquals(1, result.size());
        assertEquals("a1", result.get(0).getAttachmentId());
        assertEquals(123L, result.get(0).getSizeBytes());
    }

    @Test
    void listAttachmentsForComplaintReturnsEmptyWhenNoneExist() {
        when(attachmentDAO.listAttachmentsForComplaint("org1", "c1")).thenReturn(List.of());

        List<ComplaintAttachmentResponseDTO> result = attachmentService.listAttachmentsForComplaint("org1", "c1");

        assertTrue(result.isEmpty());
    }

    // ---- downloadAttachment ----

    @Test
    void downloadAttachmentThrows404WhenNotFound() {
        when(attachmentDAO.getAttachmentWithDataById("a1", "org1", "c1")).thenReturn(Optional.empty());

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> attachmentService.downloadAttachment("org1", "c1", "a1", true));

        assertEquals("CO-4040", ex.getCode());
    }

    @Test
    void downloadAttachmentAllowsUnrestrictedAccessRegardlessOfIsPublic() {
        ComplaintAttachment attachment = new ComplaintAttachment("a1", "org1", "c1", "a.pdf",
                "application/pdf", new byte[]{1, 2, 3}, false, 100L);
        when(attachmentDAO.getAttachmentWithDataById("a1", "org1", "c1")).thenReturn(Optional.of(attachment));

        ComplaintAttachmentDownloadResponseDTO result = attachmentService.downloadAttachment("org1", "c1", "a1",
                false);

        assertEquals("a1", result.getAttachmentId());
        assertEquals(3, Base64.getDecoder().decode(result.getContent()).length);
    }

    @Test
    void downloadAttachmentDeniesRestrictedAccessToNonPublicAttachment() {
        ComplaintAttachment attachment = new ComplaintAttachment("a1", "org1", "c1", "a.pdf",
                "application/pdf", new byte[]{1}, false, 100L);
        when(attachmentDAO.getAttachmentWithDataById("a1", "org1", "c1")).thenReturn(Optional.of(attachment));

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> attachmentService.downloadAttachment("org1", "c1", "a1", true));

        assertEquals("CO-4030", ex.getCode());
    }

    @Test
    void downloadAttachmentAllowsRestrictedAccessToPublicAttachment() {
        ComplaintAttachment attachment = new ComplaintAttachment("a1", "org1", "c1", "a.pdf",
                "application/pdf", new byte[]{1}, true, 100L);
        when(attachmentDAO.getAttachmentWithDataById("a1", "org1", "c1")).thenReturn(Optional.of(attachment));

        ComplaintAttachmentDownloadResponseDTO result = attachmentService.downloadAttachment("org1", "c1", "a1",
                true);

        assertEquals("a1", result.getAttachmentId());
    }

    // ---- own* ownership defense-in-depth ----

    @Test
    void uploadOwnComplaintAttachmentsThrowsWhenComplaintIsNotOwnedByCallerAndNeverPersists() {
        when(complaintService.requireOwnedComplaint("org1", "c1", "user1"))
                .thenThrow(new ComplaintException("CO-4040", "not found", "desc", 404));

        assertThrows(ComplaintException.class, () -> attachmentService.uploadOwnComplaintAttachments("org1", "c1",
                "user1", "User One", List.of(pdfFile("a.pdf", 10))));

        verify(attachmentDAO, never()).addAttachment(any());
    }

    @Test
    void uploadOwnComplaintAttachmentsVerifiesOwnershipThenUploadsAsPublicUserRole() {
        when(complaintEventDAO.addEvent(any(ComplaintEvent.class))).thenReturn(true);
        when(attachmentDAO.addAttachment(any())).thenReturn(true);

        List<ComplaintAttachmentResponseDTO> result = attachmentService.uploadOwnComplaintAttachments("org1", "c1",
                "user1", "User One", List.of(pdfFile("a.pdf", 10)));

        assertEquals(1, result.size());
        assertTrue(result.get(0).isPublic());
        verify(complaintService).requireOwnedComplaint("org1", "c1", "user1");
        ArgumentCaptor<ComplaintEvent> captor = ArgumentCaptor.forClass(ComplaintEvent.class);
        verify(complaintEventDAO).addEvent(captor.capture());
        assertEquals("USER", captor.getValue().getActorRole());
    }

    @Test
    void downloadOwnAttachmentThrowsWhenComplaintIsNotOwnedByCallerAndNeverFetches() {
        when(complaintService.requireOwnedComplaint("org1", "c1", "user1"))
                .thenThrow(new ComplaintException("CO-4040", "not found", "desc", 404));

        assertThrows(ComplaintException.class,
                () -> attachmentService.downloadOwnAttachment("org1", "c1", "user1", "a1"));

        verify(attachmentDAO, never()).getAttachmentWithDataById(any(), any(), any());
    }

    @Test
    void downloadOwnAttachmentVerifiesOwnershipThenRestrictsToPublicAttachments() {
        ComplaintAttachment attachment = new ComplaintAttachment("a1", "org1", "c1", "a.pdf",
                "application/pdf", new byte[]{1}, false, 100L);
        when(attachmentDAO.getAttachmentWithDataById("a1", "org1", "c1")).thenReturn(Optional.of(attachment));

        ComplaintException ex = assertThrows(ComplaintException.class,
                () -> attachmentService.downloadOwnAttachment("org1", "c1", "user1", "a1"));

        assertEquals("CO-4030", ex.getCode());
        verify(complaintService).requireOwnedComplaint("org1", "c1", "user1");
    }
}
