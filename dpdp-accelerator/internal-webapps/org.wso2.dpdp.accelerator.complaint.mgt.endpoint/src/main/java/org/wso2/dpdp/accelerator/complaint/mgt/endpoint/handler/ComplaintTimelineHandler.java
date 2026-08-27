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

import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintTimelineEntryResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.PageMetadataBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.TimelineListResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.service.impl.ComplaintAttachmentServiceImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.service.impl.ComplaintEventServiceImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.service.impl.ComplaintServiceImpl;

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
        this.complaintService = new ComplaintServiceImpl();
        this.complaintEventService = new ComplaintEventServiceImpl();
        this.complaintAttachmentService = new ComplaintAttachmentServiceImpl(complaintService);
    }

    public ComplaintTimelineHandler(ComplaintService complaintService, ComplaintEventService complaintEventService,
            ComplaintAttachmentService complaintAttachmentService) {
        this.complaintService = complaintService;
        this.complaintEventService = complaintEventService;
        this.complaintAttachmentService = complaintAttachmentService;
    }

    public TimelineListResponseBean getTimeline(String orgId, String complaintId, Long fromTime, Long toTime,
            String order, Integer limit, Integer offset) {
        return getTimeline(orgId, complaintId, fromTime, toTime, null, order, limit, offset);
    }

    public TimelineListResponseBean getOwnTimeline(String orgId, String complaintId, String ownerUserId,
            Long fromTime, Long toTime, String order, Integer limit, Integer offset) {
        complaintService.requireOwnedComplaint(orgId, complaintId, ownerUserId);
        return getTimeline(orgId, complaintId, fromTime, toTime, true, order, limit, offset);
    }

    private TimelineListResponseBean getTimeline(String orgId, String complaintId, Long fromTime, Long toTime,
            Boolean isPublic, String order, Integer limit, Integer offset) {
        int lim = limit != null && limit > 0 ? Math.min(limit, 100) : 20;
        int off = offset != null && offset >= 0 ? offset : 0;
        int[] totalOut = new int[]{0};

        List<ComplaintEvent> entries = complaintEventService.getTimeline(orgId, complaintId, fromTime, toTime,
                isPublic, order, lim, off, totalOut);

        Map<String, List<ComplaintAttachmentResponseBean>> attachmentsByEventId = complaintAttachmentService
                .listAttachmentsForComplaint(orgId, complaintId)
                .stream()
                .filter(attachment -> attachment.getComplaintEventId() != null)
                .collect(Collectors.groupingBy(ComplaintAttachmentResponseBean::getComplaintEventId));

        List<ComplaintTimelineEntryResponseBean> beanList = new ArrayList<>();
        for (ComplaintEvent entry : entries) {
            List<ComplaintAttachmentResponseBean> attachments = attachmentsByEventId
                    .getOrDefault(entry.getComplaintEventId(), Collections.emptyList());
            beanList.add(ComplaintTimelineEntryResponseBean.from(entry, attachments));
        }

        PageMetadataBean metadata = new PageMetadataBean(totalOut[0], off, beanList.size(), lim);
        return new TimelineListResponseBean(beanList, metadata);
    }
}
