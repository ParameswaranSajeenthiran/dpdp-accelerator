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

import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintAttachment;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.ComplaintTimelineEntryResponse;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto.TimelineListResponse;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.util.ComplaintDtoMapper;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared business logic behind both /me/complaints/{id}/timeline (Data Principal, isPublic=true
 * entries only) and /complaints/{id}/timeline (officer/admin, every entry). Every attachment is
 * bound to the upload event created alongside it (see ComplaintAttachmentServiceImpl), so each
 * timeline entry below carries the attachments uploaded under it.
 *
 * <p>The API spec's fromTime/toTime query params are a two-sided window, mapped directly to the
 * DAO/service layer's since/until params.
 */
public class ComplaintTimelineHandler {

    private final ComplaintService complaintService;
    private final ComplaintEventService complaintEventService;
    private final ComplaintAttachmentService complaintAttachmentService;

    public ComplaintTimelineHandler() {
        this.complaintService = getOSGiService(ComplaintService.class);
        this.complaintEventService = getOSGiService(ComplaintEventService.class);
        this.complaintAttachmentService = getOSGiService(ComplaintAttachmentService.class);
    }

    private static <T> T getOSGiService(Class<T> serviceClass) {
        T service = (T) PrivilegedCarbonContext.getThreadLocalCarbonContext().getOSGiService(serviceClass, null);
        if (service == null) {
            throw new IllegalStateException(serviceClass.getName() + " OSGi service not available");
        }
        return service;
    }

    public ComplaintTimelineHandler(ComplaintService complaintService, ComplaintEventService complaintEventService,
            ComplaintAttachmentService complaintAttachmentService) {
        this.complaintService = complaintService;
        this.complaintEventService = complaintEventService;
        this.complaintAttachmentService = complaintAttachmentService;
    }

    public TimelineListResponse getTimeline(String orgId, String complaintId, Long fromTime, Long toTime,
            String order, Integer limit, Integer offset) {
        return getTimeline(orgId, complaintId, fromTime, toTime, null, order, limit, offset);
    }

    public TimelineListResponse getOwnTimeline(String orgId, String complaintId, String ownerUserId,
            Long fromTime, Long toTime, String order, Integer limit, Integer offset) {
        complaintService.getOwnedComplaint(orgId, complaintId, ownerUserId);
        return getTimeline(orgId, complaintId, fromTime, toTime, true, order, limit, offset);
    }

    private TimelineListResponse getTimeline(String orgId, String complaintId, Long fromTime, Long toTime,
            Boolean isPublic, String order, Integer limit, Integer offset) {
        int lim = limit != null && limit > 0 ? Math.min(limit, 100) : 20;
        int off = offset != null && offset >= 0 ? offset : 0;
        int[] totalOut = new int[]{0};

        List<ComplaintEvent> entries = complaintEventService.getTimeline(orgId, complaintId, fromTime, toTime,
                isPublic, order, lim, off, totalOut);

        Map<String, List<ComplaintAttachment>> attachmentsByEventId = complaintAttachmentService
                .listAttachmentsForComplaint(orgId, complaintId)
                .stream()
                .filter(attachment -> attachment.getComplaintEventId() != null)
                .collect(Collectors.groupingBy(ComplaintAttachment::getComplaintEventId));

        List<ComplaintTimelineEntryResponse> timeline = new ArrayList<>();
        for (ComplaintEvent entry : entries) {
            List<ComplaintAttachment> attachments = attachmentsByEventId
                    .getOrDefault(entry.getComplaintEventId(), Collections.emptyList());
            timeline.add(ComplaintDtoMapper.toTimelineEntry(entry, attachments));
        }

        return new TimelineListResponse()
                .data(timeline)
                .metadata(ComplaintDtoMapper.toPage(totalOut[0], off, timeline.size(), lim));
    }
}
