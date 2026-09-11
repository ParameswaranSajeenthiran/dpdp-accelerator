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

package org.wso2.dpdp.accelerator.complaint.mgt.dao;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintQueueStats;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/**
 * Every method takes the {@link Connection} as its first parameter and throws only the unchecked
 * {@link org.wso2.dpdp.accelerator.complaint.mgt.dao.exception.ComplaintDAOException} (never a
 * checked {@link java.sql.SQLException}) - reads and writes are handled identically, so a caller
 * composing several calls into one transaction (e.g. via
 * {@link org.wso2.dpdp.accelerator.common.util.DatabaseUtils#executeInTransaction}) never has to
 * special-case which of them declare a checked exception.
 */
public interface ComplaintDAO {

    /**
     * Persists a new complaint row.
     *
     * @param conn      connection to execute against
     * @param complaint complaint to persist
     * @return true if a row was inserted
     */
    boolean addComplaint(Connection conn, Complaint complaint);

    /**
     * Fetches a single complaint scoped to its org.
     *
     * @param conn        connection to execute against
     * @param complaintId complaint to fetch
     * @param orgId       tenant/organization the complaint belongs to
     * @return the complaint, or empty if none exists for this id/org
     */
    Optional<Complaint> getComplaintById(Connection conn, String complaintId, String orgId);

    /**
     * Count of complaints for this org whose REFERENCE_ID already uses the given year prefix
     * (e.g. "CMP-2026-%"). Used by ReferenceIdGenerator to pick the next sequence number when
     * minting a new complaint's REFERENCE_ID.
     *
     * @param conn                   connection to execute against
     * @param orgId                  tenant/organization to count within
     * @param referenceIdLikePattern SQL LIKE pattern matching the year prefix
     * @return the matching row count
     */
    int countByReferenceIdPrefix(Connection conn, String orgId, String referenceIdLikePattern);

    /**
     * Updates STATUS and UPDATED_TIME for a complaint.
     *
     * @param conn        connection to execute against
     * @param complaintId complaint to update
     * @param orgId       tenant/organization the complaint belongs to
     * @param newStatus   status to set
     * @param updatedTime timestamp to record as UPDATED_TIME
     * @return true if a row was updated
     */
    boolean updateStatus(Connection conn, String complaintId, String orgId, String newStatus, long updatedTime);

    /**
     * Lists complaints for an org with optional status/priority/userId filters, sorting, and
     * limit/offset pagination.
     *
     * @param conn     connection to execute against
     * @param orgId    tenant/organization to list complaints for
     * @param status   optional status filter
     * @param priority optional priority filter
     * @param userId   optional Data Principal filter
     * @param limit    maximum number of rows to return
     * @param offset   number of matching rows to skip
     * @param sort     sort order
     * @param totalOut out-param: Java has no multi-return, so the caller passes {@code new
     *                 int[1]} and, after the call, {@code totalOut[0]} holds the total row count
     *                 matching the filters (ignoring limit/offset) - needed by the endpoint layer
     *                 to populate pagination metadata (e.g. total pages) alongside the page of
     *                 results actually returned. Pass {@code null} or a zero-length array to skip
     *                 the count query.
     * @return the page of complaints matching the filters
     */
    List<Complaint> listComplaints(Connection conn, String orgId, String status, String priority, String userId,
            int limit, int offset, String sort, int[] totalOut);

    /**
     * Org-wide counts for the officer/admin queue's summary tiles - open (OPEN/IN_PROGRESS
     * combined), awaiting internal review (AWAITING_INTERNAL_REVIEW), resolved, and SLA-breached
     * (STATUTORY_DUE_TIME already passed on a still-open complaint, judged against {@code now}).
     * WAITING_ON_CLIENT has no dedicated tile, so it isn't counted in any bucket here. Always
     * unfiltered by status/priority - the tiles summarize the whole queue regardless of whatever
     * filter is currently applied to the paginated list beside them.
     *
     * @param conn  connection to execute against
     * @param orgId tenant/organization to compute stats for
     * @param now   current time, passed in rather than read here (same as every other DAO
     *              write's timestamp) so the whole call is deterministic and testable
     * @return the queue stats
     */
    ComplaintQueueStats getQueueStats(Connection conn, String orgId, long now);
}
