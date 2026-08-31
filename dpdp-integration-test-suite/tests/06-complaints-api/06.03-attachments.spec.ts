/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import { test, expect } from '../../fixtures/auth.fixtures'
import type { ComplaintApiClient, UploadFile } from '../../clients/ComplaintApiClient'
import { uniqueMarker } from '../../utils/testData'

// AttachmentPolicy.isAllowedContentType only checks the declared multipart contentType string, not
// the actual bytes (verified by reading ComplaintAttachmentServiceImpl#validateFiles) - so a
// synthetic in-memory buffer declared as one of the four allowed types is indistinguishable, from
// the server's point of view, from a real PDF/PNG/JPEG/docx.
function fakeFile(name: string, mimeType: string, sizeBytes = 128): UploadFile {
  return { name, mimeType, buffer: Buffer.from(`fake-content-${uniqueMarker('file')}`.padEnd(sizeBytes, '.')) }
}

async function createOpenComplaint(api: ComplaintApiClient, label: string): Promise<string> {
  const response = await api.createMyComplaint({
    subjectCategory: 'OTHER',
    description: `Automated regression test: ${uniqueMarker(label)}`,
  })
  const { id } = await response.json()
  return id as string
}

/**
 * Attachment upload/download on both surfaces - ComplaintAttachmentHandler.java (shared logic
 * behind /me/complaints/{id}/attachments and /complaints/{id}/attachments), AttachmentPolicy.java
 * for the count/size/type limits.
 */
test.describe('Complaint attachments (API)', () => {
  test.describe('Data Principal (/me)', () => {
    test('06.03.01 - Uploading a single supported file returns it as isPublic=true', async ({ userComplaintApi }) => {
      const complaintId = await createOpenComplaint(userComplaintApi, 'attach-single')
      const file = fakeFile('evidence.pdf', 'application/pdf')

      const response = await userComplaintApi.uploadMyAttachments(complaintId, [file])
      expect(response.status()).toBe(201)
      const [attachment] = await response.json()
      expect(attachment.fileName).toBe('evidence.pdf')
      expect(attachment.contentType).toBe('application/pdf')
      expect(attachment.isPublic).toBe(true)
      expect(attachment.sizeBytes).toBe(file.buffer.length)
    })

    test('06.03.02 - Uploading the maximum of 5 files in one request succeeds', async ({ userComplaintApi }) => {
      const complaintId = await createOpenComplaint(userComplaintApi, 'attach-max-files')
      const files = Array.from({ length: 5 }, (_, index) => fakeFile(`evidence-${index}.png`, 'image/png'))

      const response = await userComplaintApi.uploadMyAttachments(complaintId, files)
      expect(response.status()).toBe(201)
      const attachments = await response.json()
      expect(attachments).toHaveLength(5)
    })

    test('06.03.03 - Uploading 6 files in one request is rejected with 422 (too many files)', async ({
      userComplaintApi,
    }) => {
      const complaintId = await createOpenComplaint(userComplaintApi, 'attach-too-many-files')
      const files = Array.from({ length: 6 }, (_, index) => fakeFile(`evidence-${index}.png`, 'image/png'))

      const response = await userComplaintApi.uploadMyAttachments(complaintId, files)
      expect(response.status()).toBe(422)
      const body = await response.json()
      expect(body.code).toBe('CO-4002')
      expect(body.description).toMatch(/at most 5/i)
    })

    test('06.03.04 - An unsupported content type is rejected with 422', async ({ userComplaintApi }) => {
      const complaintId = await createOpenComplaint(userComplaintApi, 'attach-bad-type')
      const response = await userComplaintApi.uploadMyAttachments(complaintId, [
        fakeFile('notes.txt', 'text/plain'),
      ])
      expect(response.status()).toBe(422)
      const body = await response.json()
      expect(body.code).toBe('CO-4002')
      expect(body.description).toMatch(/not one of the supported types/i)
    })

    test('06.03.05 - A file over the configured max size is rejected with 422', async ({ userComplaintApi }) => {
      test.slow() // uploads ~10MB over the wire against a real deployment
      const complaintId = await createOpenComplaint(userComplaintApi, 'attach-too-large')
      // AttachmentPolicy's default cap is 10 MiB (CO_MAX_ATTACHMENT_SIZE_BYTES) - this assumes the
      // target deployment hasn't overridden it lower or dramatically higher.
      const oversized: UploadFile = {
        name: 'huge.png',
        mimeType: 'image/png',
        buffer: Buffer.alloc(10 * 1024 * 1024 + 1024, 'a'),
      }
      const response = await userComplaintApi.uploadMyAttachments(complaintId, [oversized])
      expect(response.status()).toBe(422)
      const body = await response.json()
      expect(body.code).toBe('CO-4002')
      expect(body.description).toMatch(/exceeds the maximum allowed size/i)
    })

    test('06.03.06 - Downloading an uploaded attachment returns its content', async ({ userComplaintApi }) => {
      const complaintId = await createOpenComplaint(userComplaintApi, 'attach-download')
      const file = fakeFile('evidence.jpg', 'image/jpeg')
      const uploaded = await userComplaintApi.uploadMyAttachments(complaintId, [file])
      const [{ attachmentId }] = await uploaded.json()

      const response = await userComplaintApi.downloadMyAttachment(complaintId, attachmentId)
      expect(response.ok()).toBe(true)
      const body = await response.json()
      expect(body.fileName).toBe('evidence.jpg')
      expect(Buffer.from(body.content, 'base64').equals(file.buffer)).toBe(true)
    })

    test('06.03.07 - Downloading an unknown attachment id returns 404', async ({ userComplaintApi }) => {
      const complaintId = await createOpenComplaint(userComplaintApi, 'attach-download-missing')
      const response = await userComplaintApi.downloadMyAttachment(complaintId, '00000000-0000-0000-0000-000000000000')
      expect(response.status()).toBe(404)
    })

    test("06.03.08 - Uploaded attachments show up on the complaint's own record", async ({ userComplaintApi }) => {
      const complaintId = await createOpenComplaint(userComplaintApi, 'attach-on-record')
      await userComplaintApi.uploadMyAttachments(complaintId, [fakeFile('evidence.pdf', 'application/pdf')])

      const complaint = await (await userComplaintApi.getMyComplaint(complaintId)).json()
      expect(complaint.attachments).toHaveLength(1)
      expect(complaint.attachments[0].fileName).toBe('evidence.pdf')
    })
  })

  test.describe('Officer (/complaints)', () => {
    test('06.03.09 - An officer upload defaults to isPublic=true when omitted', async ({ officerComplaintApi }) => {
      const complaint = await officerComplaintApi.createComplaintForUser({
        userId: uniqueMarker('attach-officer-default-user'),
        subjectCategory: 'OTHER',
        description: `Automated regression test: ${uniqueMarker('attach-officer-default')}`,
      })
      const { id } = await complaint.json()

      const response = await officerComplaintApi.uploadAttachments(id, [fakeFile('report.pdf', 'application/pdf')])
      expect(response.status()).toBe(201)
      expect((await response.json())[0].isPublic).toBe(true)
    })

    test('06.03.10 - An officer can mark an upload internal (isPublic=false), and still download it', async ({
      officerComplaintApi,
    }) => {
      const complaint = await officerComplaintApi.createComplaintForUser({
        userId: uniqueMarker('attach-officer-internal-user'),
        subjectCategory: 'OTHER',
        description: `Automated regression test: ${uniqueMarker('attach-officer-internal')}`,
      })
      const { id } = await complaint.json()

      const upload = await officerComplaintApi.uploadAttachments(id, [fakeFile('internal.pdf', 'application/pdf')], false)
      expect(upload.status()).toBe(201)
      const [{ attachmentId, isPublic }] = await upload.json()
      expect(isPublic).toBe(false)

      // Officers have full access regardless of isPublic (complaint-server-API.yaml).
      const download = await officerComplaintApi.downloadAttachment(id, attachmentId)
      expect(download.ok()).toBe(true)
    })

    test('06.03.11 - A citizen cannot download an officer-uploaded attachment marked isPublic=false, but can download a public one', async ({
      userComplaintApi,
      officerComplaintApi,
    }) => {
      const complaintId = await createOpenComplaint(userComplaintApi, 'attach-visibility')

      const internalUpload = await officerComplaintApi.uploadAttachments(
        complaintId,
        [fakeFile('internal-only.pdf', 'application/pdf')],
        false,
      )
      const [{ attachmentId: internalAttachmentId }] = await internalUpload.json()

      const publicUpload = await officerComplaintApi.uploadAttachments(
        complaintId,
        [fakeFile('shared-with-citizen.pdf', 'application/pdf')],
        true,
      )
      const [{ attachmentId: publicAttachmentId }] = await publicUpload.json()

      const deniedDownload = await userComplaintApi.downloadMyAttachment(complaintId, internalAttachmentId)
      expect(deniedDownload.status()).toBe(403)
      expect((await deniedDownload.json()).code).toBe('CO-4030')

      const allowedDownload = await userComplaintApi.downloadMyAttachment(complaintId, publicAttachmentId)
      expect(allowedDownload.ok()).toBe(true)

      // GET .../{complaintId} (self surface) only lists the isPublic=true one (drift #3 in the
      // README - the record includes attachments at all, filtered here to public-only).
      const ownRecord = await (await userComplaintApi.getMyComplaint(complaintId)).json()
      const ownAttachmentIds = ownRecord.attachments.map((a: { attachmentId: string }) => a.attachmentId)
      expect(ownAttachmentIds).toContain(publicAttachmentId)
      expect(ownAttachmentIds).not.toContain(internalAttachmentId)

      // The officer's own view of the same complaint sees both.
      const officerRecord = await (await officerComplaintApi.getComplaint(complaintId)).json()
      const officerAttachmentIds = officerRecord.attachments.map((a: { attachmentId: string }) => a.attachmentId)
      expect(officerAttachmentIds).toEqual(expect.arrayContaining([publicAttachmentId, internalAttachmentId]))
    })
  })
})
