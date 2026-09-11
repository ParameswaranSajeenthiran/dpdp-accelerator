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

package org.wso2.dpdp.accelerator.complaint.mgt.service;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;

import java.util.List;

public interface ComplaintEventService {

    /**
     * Lists timeline entries (status changes, comments, internal notes) for a complaint,
     * optionally filtered to entries after "since", at or before "until", and/or by isPublic,
     * ordered and paginated.
     *
     * @param orgId       tenant/organization the complaint belongs to
     * @param complaintId complaint whose timeline is being listed
     * @param since       optional filter; only entries with ACTION_TIME greater than this are
     *                    returned
     * @param until       optional filter; only entries with ACTION_TIME at or before this are
     *                    returned
     * @param isPublic    optional filter by visibility
     * @param order       sort order
     * @param limit       maximum number of rows to return
     * @param offset      number of matching rows to skip
     * @param totalOut    out-param - see ComplaintDAO#listComplaints for the convention; after
     *                    the call, {@code totalOut[0]} holds the total row count matching the
     *                    filters, igno
     *                    ring limit/offset
     * @return the page of timeline entries matching the filters
     */
    List<ComplaintEvent> getTimeline(String orgId, String complaintId, Long since, Long until, Boolean isPublic,
            String order, int limit, int offset, int[] totalOut);

    /**
     * Adds a COMMENT (isPublic=true) or an officer-internal note (isPublic=false) to the
     * complaint's timeline. If toStatus is non-null, the complaint is transitioned to that status
     * in the same call, subject to the same state-machine rules as {@link #updateStatus}.
     *
     * @param orgId         tenant/organization the complaint belongs to
     * @param complaintId   complaint to add the entry to
     * @param actorUserId   resolved, authenticated caller adding the entry
     * @param actorUserName display name of the caller
     * @param actorRole     resolved, authenticated caller's role; only COMPLAINT_OFFICER may set
     *                      isPublic=false
     * @param message       entry text
     * @param isPublic      whether the entry is visible to the Data Principal
     * @param toStatus      optional status to transition the complaint to as part of this call
     * @return the newly created timeline entry
     * @throws ComplaintException thrown with a 409 status (CO-4090) if toStatus is set and the
     *                            transition isn't valid from the complaint's current status
     */
    ComplaintEvent addComment(String orgId, String complaintId, String actorUserId,
            String actorUserName, String actorRole, String message, boolean isPublic, String toStatus);

    /**
     * Fetches a single timeline entry, used by the comment-attachment endpoint to verify
     * ownership.
     *
     * @param orgId            tenant/organization the complaint belongs to
     * @param complaintId      complaint the entry belongs to
     * @param complaintEventId timeline entry to fetch
     * @return the timeline entry
     */
    ComplaintEvent getTimelineEntry(String orgId, String complaintId, String complaintEventId);

    /**
     * Transitions a complaint to toStatus, recording a timeline entry for the change.
     *
     * @param orgId         tenant/organization the complaint belongs to
     * @param complaintId   complaint to transition
     * @param actorUserId   resolved, authenticated caller performing the transition
     * @param actorUserName display name of the caller
     * @param actorRole     resolved, authenticated caller's role
     * @param toStatus      status to transition to
     * @param note          required when toStatus is RESOLVED
     * @return the updated complaint
     * @throws ComplaintException thrown with a 409 status (CO-4090) if the transition isn't valid
     *                            from the complaint's current status - see
     *                            StatusTransitionValidator
     */
    Complaint updateStatus(String orgId, String complaintId, String actorUserId,
            String actorUserName, String actorRole, String toStatus, String note);
}
