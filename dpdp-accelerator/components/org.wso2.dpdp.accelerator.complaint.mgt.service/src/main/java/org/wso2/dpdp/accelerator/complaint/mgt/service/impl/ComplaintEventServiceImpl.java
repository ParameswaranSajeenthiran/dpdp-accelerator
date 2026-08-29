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

import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintEventDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintActorRole;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintStatus;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCommentCreateResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintStatusUpdateResponseDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintErrorCode;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintServiceConstants;
import org.wso2.dpdp.accelerator.complaint.mgt.service.notification.EmailNotificationClient;
import org.wso2.dpdp.accelerator.complaint.mgt.service.notification.NotificationClient;
import org.wso2.dpdp.accelerator.complaint.mgt.service.util.StatusTransitionValidator;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintStatus.RESOLVED;

public class ComplaintEventServiceImpl implements ComplaintEventService {

    private final ComplaintEventDAO complaintEventDAO;
    private final ComplaintDAO complaintDAO;
    private final ComplaintService complaintService;
    private final NotificationClient notificationClient;

    public ComplaintEventServiceImpl(ComplaintEventDAO complaintEventDAO, ComplaintDAO complaintDAO,
            ComplaintService complaintService, NotificationClient notificationClient) {
        this.complaintEventDAO = complaintEventDAO;
        this.complaintDAO = complaintDAO;
        this.complaintService = complaintService;
        this.notificationClient = notificationClient;
    }

    @Override
    public List<ComplaintEvent> getTimeline(String orgId, String complaintId, Long since, Long until,
            Boolean isPublic, String order, int limit, int offset, int[] totalOut) {
        complaintService.requireComplaint(orgId, complaintId);
        return complaintEventDAO.listEvents(orgId, complaintId, since, until, isPublic, order, limit, offset,
                totalOut);
    }

    @Override
    public ComplaintCommentCreateResponseDTO addComment(String orgId, String complaintId, String actorUserId,
            String actorUserName, String actorRole, String message, boolean isPublic, String toStatus) {
        Complaint complaint = complaintService.requireComplaint(orgId, complaintId);

        if (message == null || message.trim().isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.MESSAGE_REQUIRED_ERROR);
        }
        if (message.length() > ComplaintServiceConstants.MAX_MESSAGE_LENGTH) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.MESSAGE_TOO_LONG_ERROR);
        }
        if (actorUserId == null || actorUserId.trim().isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.ACTOR_USER_ID_REQUIRED_ERROR);
        }
        // SYSTEM is deliberately excluded - only ever written by the server itself, never accepted from a caller.
        if (!ComplaintActorRole.USER.name().equals(actorRole)
                && !ComplaintActorRole.COMPLAINT_OFFICER.name().equals(actorRole)) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.ACTOR_ROLE_INVALID_ERROR);
        }
        if (!isPublic && !ComplaintActorRole.COMPLAINT_OFFICER.name().equals(actorRole)) {
            throw new ComplaintException(ComplaintErrorCode.FORBIDDEN,
                    String.format(ComplaintServiceConstants.INTERNAL_NOTE_FORBIDDEN_ERROR, actorRole));
        }

        boolean hasToStatus = toStatus != null && !toStatus.trim().isEmpty();
        String fromStatus = null;
        if (hasToStatus) {
            if (!ComplaintStatus.isValid(toStatus)) {
                throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                        String.format(ComplaintServiceConstants.INVALID_STATUS_VALUE_ERROR, toStatus));
            }
            fromStatus = complaint.getStatus();
            if (!StatusTransitionValidator.isValidTransition(fromStatus, toStatus)) {
                throw new ComplaintException(ComplaintErrorCode.INVALID_STATE_TRANSITION,
                        String.format(ComplaintServiceConstants.INVALID_STATUS_TRANSITION_ERROR, fromStatus,
                                toStatus));
            }
        }

        String complaintEventId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        ComplaintEvent event = new ComplaintEvent(complaintEventId, orgId, complaintId, actorUserId.trim(),
                actorUserName, actorRole, isPublic, message.trim(), fromStatus, hasToStatus ? toStatus : null, now);

        if (hasToStatus) {
            // The comment and the status change it carries must land together - otherwise a
            // failure between the two writes could leave a status-changing comment recorded
            // against a complaint whose status never actually moved, or vice versa.
            Connection conn = DatabaseUtils.getDBConnection();
            try {
                if (!complaintEventDAO.addEvent(conn, event)) {
                    throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                            ComplaintServiceConstants.ADD_COMMENT_FAILED_ERROR);
                }
                if (!complaintDAO.updateStatus(conn, complaintId, orgId, toStatus, now)) {
                    throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                            ComplaintServiceConstants.STATUS_UPDATE_FAILED_ERROR);
                }
                DatabaseUtils.commitTransaction(conn);
            } catch (RuntimeException e) {
                DatabaseUtils.rollbackTransaction(conn);
                throw e;
            } catch (SQLException e) {
                DatabaseUtils.rollbackTransaction(conn);
                throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                        ComplaintServiceConstants.ADD_COMMENT_FAILED_ERROR, e);
            } finally {
                DatabaseUtils.closeConnection(conn);
            }
        } else {
            boolean added = complaintEventDAO.addEvent(event);
            if (!added) {
                throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                        ComplaintServiceConstants.ADD_COMMENT_FAILED_ERROR);
            }
        }

        if (hasToStatus) {
            // complaint was fetched before the DB status update above; without this, the
            // notification would carry the complaint's pre-transition status.
            complaint.setStatus(toStatus);
            complaint.setUpdatedTime(now);
        }
        if (isPublic) {
            // An internal note (isPublic=false, officer-only per the check above) is never shown
            // to the citizen in the timeline - notifying them about it would leak its existence.
            notificationClient.notifyCommentAdded(complaint, event);
        }
        return event;
        if (hasToStatus) {
            // complaint was fetched before the DB status update above; without this, the
            // notification would carry the complaint's pre-transition status.
            complaint.setStatus(toStatus);
            complaint.setUpdatedTime(now);
        }
        if (isPublic) {
            // An internal note (isPublic=false, officer-only per the check above) is never shown
            // to the citizen in the timeline - notifying them about it would leak its existence.
            notificationClient.notifyCommentAdded(complaint, event);
        }
        return ComplaintCommentCreateResponseDTO.from(event);
    }

    @Override
    public ComplaintEvent getTimelineEntry(String orgId, String complaintId, String complaintEventId) {
        complaintService.requireComplaint(orgId, complaintId);
        Optional<ComplaintEvent> eventOpt = complaintEventDAO.getEventById(complaintEventId, orgId, complaintId);
        if (eventOpt.isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.COMMENT_NOT_FOUND,
                    String.format(ComplaintServiceConstants.TIMELINE_ENTRY_NOT_FOUND_ERROR, complaintEventId));
        }
        return eventOpt.get();
    }

    @Override
    public ComplaintStatusUpdateResponseDTO updateStatus(String orgId, String complaintId, String actorUserId,
            String actorUserName, String actorRole, String toStatus, String note) {
        Complaint complaint = complaintService.requireComplaint(orgId, complaintId);

        if (actorUserId == null || actorUserId.trim().isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.ACTOR_USER_ID_REQUIRED_ERROR);
        }
        // SYSTEM is deliberately excluded - only ever written by the server itself, never accepted from a caller.
        if (!ComplaintActorRole.USER.name().equals(actorRole)
                && !ComplaintActorRole.COMPLAINT_OFFICER.name().equals(actorRole)) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.ACTOR_ROLE_INVALID_ERROR);
        }
        if (toStatus == null || toStatus.trim().isEmpty()) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.TO_STATUS_REQUIRED_ERROR);
        }
        if (!ComplaintStatus.isValid(toStatus)) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    String.format(ComplaintServiceConstants.INVALID_STATUS_VALUE_ERROR, toStatus));
        }
        if (RESOLVED.name().equals(toStatus) && (note == null || note.trim().isEmpty())) {
            throw new ComplaintException(ComplaintErrorCode.VALIDATION_FAILED,
                    ComplaintServiceConstants.NOTE_REQUIRED_FOR_RESOLVED_ERROR);
        }

        String fromStatus = complaint.getStatus();
        if (!StatusTransitionValidator.isValidTransition(fromStatus, toStatus)) {
            throw new ComplaintException(ComplaintErrorCode.INVALID_STATE_TRANSITION,
                    String.format(ComplaintServiceConstants.INVALID_STATUS_TRANSITION_ERROR, fromStatus, toStatus));
        }

        long now = System.currentTimeMillis();
        String complaintEventId = UUID.randomUUID().toString();
        ComplaintEvent event = new ComplaintEvent(complaintEventId, orgId, complaintId, actorUserId, actorUserName,
                actorRole, true, note, fromStatus, toStatus, now);

        // Status update and its audit event must land together. Both writes are checked and made
        // to fail the whole transaction (not just skip a write) so a partial failure can never
        // leave the status changed with no record of why, or vice versa.
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            if (!complaintDAO.updateStatus(conn, complaintId, orgId, toStatus, now)) {
                throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                        ComplaintServiceConstants.STATUS_UPDATE_FAILED_ERROR);
            }
            if (!complaintEventDAO.addEvent(conn, event)) {
                throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                        ComplaintServiceConstants.ADD_COMMENT_FAILED_ERROR);
            }
            DatabaseUtils.commitTransaction(conn);
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } catch (SQLException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw new ComplaintException(ComplaintErrorCode.INTERNAL_ERROR,
                    ComplaintServiceConstants.STATUS_UPDATE_FAILED_ERROR, e);
        } finally {
            DatabaseUtils.closeConnection(conn);
        }

        complaint.setStatus(toStatus);
        complaint.setUpdatedTime(now);
        return ComplaintStatusUpdateResponseDTO.from(complaint);
    }
}
