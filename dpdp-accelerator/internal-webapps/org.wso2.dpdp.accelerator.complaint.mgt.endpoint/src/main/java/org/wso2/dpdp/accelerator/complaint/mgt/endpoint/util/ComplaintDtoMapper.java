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

import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintQueueStats;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.CategoryListResponse;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintActorRole;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintAttachmentDownloadResponse;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintAttachmentResponse;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintCategory;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintCategoryInfo;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintCommentCreateResponse;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintCreateResponse;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintPriority;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintQueueStatsResponse;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintRecord;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintStatus;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintStatusUpdateResponse;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintTimelineEntryResponse;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintTimelineEntryType;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.PageMetadata;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Builds the generated REST models from the DAO models the service layer returns. Neither the
 * service nor the DAO module depends on the generated models.
 */
public final class ComplaintDtoMapper {

    static final String STATUS_UPDATED_MESSAGE = "Status transition confirmed";

    private ComplaintDtoMapper() {
    }

    public static ComplaintCreateResponse toCreateResponse(Complaint source) {
        if (source == null) {
            return null;
        }
        // userName is deliberately not copied: the create response has never carried it, even
        // though the spec declares it.
        return new ComplaintCreateResponse()
                .id(source.getComplaintId())
                .referenceId(source.getReferenceId())
                .subjectCategory(category(source.getCategory()))
                .priority(priority(source.getPriority()))
                .status(status(source.getStatus()))
                .userId(source.getUserId())
                .description(source.getDescription())
                .submittedAt(source.getCreatedTime())
                .updatedAt(source.getUpdatedTime())
                .statutoryDueDate(source.getStatutoryDueTime());
    }

    public static ComplaintRecord toRecord(Complaint source, List<ComplaintAttachment> attachments) {
        if (source == null) {
            return null;
        }
        return new ComplaintRecord()
                .id(source.getComplaintId())
                .referenceId(source.getReferenceId())
                .subjectCategory(category(source.getCategory()))
                .priority(priority(source.getPriority()))
                .status(status(source.getStatus()))
                .userId(source.getUserId())
                .userName(source.getUserName())
                .description(source.getDescription())
                .attachments(attachments == null ? new ArrayList<>() : toAttachments(attachments))
                .submittedAt(source.getCreatedTime())
                .updatedAt(source.getUpdatedTime())
                .statutoryDueDate(source.getStatutoryDueTime());
    }

    public static ComplaintQueueStatsResponse toQueueStats(ComplaintQueueStats source) {
        if (source == null) {
            return null;
        }
        return new ComplaintQueueStatsResponse()
                .openCount(source.getOpenCount())
                .awaitingInternalReviewCount(source.getAwaitingInternalReviewCount())
                .resolvedCount(source.getResolvedCount())
                .slaBreachedCount(source.getSlaBreachedCount());
    }

    /** One entry per category, ordered by the map's own iteration order. */
    public static CategoryListResponse toCategories(Map<String, String> categoryPriorities) {
        List<ComplaintCategoryInfo> data = new ArrayList<>();
        for (Map.Entry<String, String> entry : categoryPriorities.entrySet()) {
            data.add(new ComplaintCategoryInfo()
                    .category(category(entry.getKey()))
                    .priority(priority(entry.getValue())));
        }
        return new CategoryListResponse().data(data);
    }

    public static ComplaintStatusUpdateResponse toStatusUpdateResponse(Complaint source) {
        if (source == null) {
            return null;
        }
        return new ComplaintStatusUpdateResponse()
                .message(STATUS_UPDATED_MESSAGE)
                .toStatus(status(source.getStatus()))
                .updatedAt(source.getUpdatedTime());
    }

    public static ComplaintCommentCreateResponse toCommentResponse(ComplaintEvent source) {
        if (source == null) {
            return null;
        }
        return new ComplaintCommentCreateResponse()
                .id(source.getComplaintEventId())
                .actorUserId(source.getActorUserId())
                .actorRole(actorRole(source.getActorRole()))
                .message(source.getComment())
                .isPublic(source.isPublic())
                .fromStatus(status(source.getFromStatus()))
                .toStatus(status(source.getToStatus()))
                .createdTime(source.getActionTime());
    }

    public static ComplaintTimelineEntryResponse toTimelineEntry(ComplaintEvent source,
            List<ComplaintAttachment> attachments) {
        if (source == null) {
            return null;
        }
        return new ComplaintTimelineEntryResponse()
                .id(source.getComplaintEventId())
                .type(ComplaintTimelineEntryType.fromValue(source.deriveEntryType()))
                .isPublic(source.isPublic())
                .actorUserId(source.getActorUserId())
                .actorUserName(source.getActorUserName())
                .actorRole(actorRole(source.getActorRole()))
                .message(source.getComment())
                .fromStatus(status(source.getFromStatus()))
                .toStatus(status(source.getToStatus()))
                .createdTime(source.getActionTime())
                .attachments(toAttachments(attachments == null ? Collections.emptyList() : attachments));
    }

    public static ComplaintAttachmentResponse toAttachment(ComplaintAttachment source) {
        if (source == null) {
            return null;
        }
        return new ComplaintAttachmentResponse()
                .attachmentId(source.getAttachmentId())
                .complaintEventId(source.getComplaintEventId())
                .fileName(source.getFileName())
                .contentType(source.getContentType())
                .sizeBytes(source.getSizeBytes())
                .isPublic(source.isPublic())
                .uploadedTime(source.getCreatedTime());
    }

    public static List<ComplaintAttachmentResponse> toAttachments(List<ComplaintAttachment> source) {
        if (source == null) {
            return null;
        }
        List<ComplaintAttachmentResponse> target = new ArrayList<>(source.size());
        for (ComplaintAttachment attachment : source) {
            target.add(toAttachment(attachment));
        }
        return target;
    }

    public static ComplaintAttachmentDownloadResponse toDownload(ComplaintAttachment source) {
        if (source == null) {
            return null;
        }
        // uploadedTime is deliberately not copied: the download response has never carried it,
        // even though the spec declares it.
        return new ComplaintAttachmentDownloadResponse()
                .attachmentId(source.getAttachmentId())
                .fileName(source.getFileName())
                .contentType(source.getContentType())
                .content(source.getFileData() == null ? null
                        : Base64.getEncoder().encodeToString(source.getFileData()));
    }

    public static PageMetadata toPage(int total, int offset, int count, int limit) {
        return new PageMetadata().total(total).offset(offset).count(count).limit(limit);
    }

    /** The service layer takes enum values as their wire strings. */
    public static String value(Enum<?> source) {
        return source == null ? null : source.toString();
    }

    private static ComplaintCategory category(String value) {
        return value == null ? null : ComplaintCategory.fromValue(value);
    }

    private static ComplaintPriority priority(String value) {
        return value == null ? null : ComplaintPriority.fromValue(value);
    }

    private static ComplaintStatus status(String value) {
        return value == null ? null : ComplaintStatus.fromValue(value);
    }

    private static ComplaintActorRole actorRole(String value) {
        return value == null ? null : ComplaintActorRole.fromValue(value);
    }
}
