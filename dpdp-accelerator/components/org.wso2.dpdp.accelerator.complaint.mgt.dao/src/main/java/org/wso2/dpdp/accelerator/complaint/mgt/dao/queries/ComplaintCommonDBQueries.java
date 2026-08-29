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

package org.wso2.dpdp.accelerator.complaint.mgt.dao.queries;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintDBColumns;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintStatus;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants;

/**
 * Common ANSI SQL queries base provider for the DPDP Complaint Management feature. Dialect-specific
 * subclasses (see {@link ComplaintMysqlDBQueries}) override only the queries that actually diverge;
 * {@link ComplaintQueryFactory} resolves which one to use per connection. Mirrors
 * {@code org.wso2.dpdp.accelerator.event.notifications.dao.queries.EventNotificationCommonDBQueries}
 * and the Financial Services accelerator's own {@code ConsentMgtCommonDBQueries}.
 */
public class ComplaintCommonDBQueries {

    protected static final String SQL_STATUS_RESOLVED = "'" + ComplaintStatus.RESOLVED.name() + "'";

    // ---- COMPLAINT ----

    public String getAddComplaintQuery() {

        return "INSERT INTO " + DAOConstants.TABLE_COMPLAINT + " (" + ComplaintDBColumns.COMPLAINT_ID + ", " + ComplaintDBColumns.ORG_ID + ", "
                + ComplaintDBColumns.USER_ID + ", " + ComplaintDBColumns.USER_NAME + ", "
                + ComplaintDBColumns.REFERENCE_ID + ", " + ComplaintDBColumns.CATEGORY + ", "
                + ComplaintDBColumns.PRIORITY + ", " + ComplaintDBColumns.STATUS + ", "
                + ComplaintDBColumns.DESCRIPTION + ", " + ComplaintDBColumns.CREATED_TIME + ", "
                + ComplaintDBColumns.UPDATED_TIME + ", " + ComplaintDBColumns.STATUTORY_DUE_TIME + ") "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    public String getGetComplaintByIdQuery() {

        return "SELECT " + ComplaintDBColumns.COMPLAINT_ID + ", " + ComplaintDBColumns.ORG_ID + ", "
                + ComplaintDBColumns.USER_ID + ", " + ComplaintDBColumns.USER_NAME + ", "
                + ComplaintDBColumns.REFERENCE_ID + ", " + ComplaintDBColumns.CATEGORY + ", "
                + ComplaintDBColumns.PRIORITY + ", " + ComplaintDBColumns.STATUS + ", "
                + ComplaintDBColumns.DESCRIPTION + ", " + ComplaintDBColumns.CREATED_TIME + ", "
                + ComplaintDBColumns.UPDATED_TIME + ", " + ComplaintDBColumns.STATUTORY_DUE_TIME + " FROM " + DAOConstants.TABLE_COMPLAINT + " "
                + "WHERE " + ComplaintDBColumns.COMPLAINT_ID + " = ? AND " + ComplaintDBColumns.ORG_ID + " = ?";
    }

    public String getCountComplaintsForYearPrefixQuery() {

        return "SELECT COUNT(*) FROM " + DAOConstants.TABLE_COMPLAINT + " WHERE " + ComplaintDBColumns.ORG_ID + " = ? AND "
                + ComplaintDBColumns.REFERENCE_ID + " LIKE ?";
    }

    public String getUpdateComplaintStatusQuery() {

        return "UPDATE " + DAOConstants.TABLE_COMPLAINT + " SET " + ComplaintDBColumns.STATUS + " = ?, " + ComplaintDBColumns.UPDATED_TIME
                + " = ? WHERE " + ComplaintDBColumns.COMPLAINT_ID + " = ? AND " + ComplaintDBColumns.ORG_ID + " = ?";
    }

    /**
     * Base SELECT for paginated complaint listing/search - {@link ComplaintQueryBuilder} appends
     * the optional filter WHERE clause, ORDER BY, and LIMIT/OFFSET.
     */
    public String getListComplaintsBaseQuery() {

        return "SELECT " + ComplaintDBColumns.COMPLAINT_ID + ", " + ComplaintDBColumns.ORG_ID + ", "
                + ComplaintDBColumns.USER_ID + ", " + ComplaintDBColumns.USER_NAME + ", "
                + ComplaintDBColumns.REFERENCE_ID + ", " + ComplaintDBColumns.CATEGORY + ", "
                + ComplaintDBColumns.PRIORITY + ", " + ComplaintDBColumns.STATUS + ", "
                + ComplaintDBColumns.DESCRIPTION + ", " + ComplaintDBColumns.CREATED_TIME + ", "
                + ComplaintDBColumns.UPDATED_TIME + ", " + ComplaintDBColumns.STATUTORY_DUE_TIME
                + " FROM " + DAOConstants.TABLE_COMPLAINT + " WHERE " + ComplaintDBColumns.ORG_ID + " = ? ";
    }

    public String getCountComplaintsBaseQuery() {

        return "SELECT COUNT(*) FROM " + DAOConstants.TABLE_COMPLAINT + " WHERE " + ComplaintDBColumns.ORG_ID + " = ? ";
    }

    public String getCountComplaintsByStatusQuery() {

        return "SELECT " + ComplaintDBColumns.STATUS + ", COUNT(*) FROM " + DAOConstants.TABLE_COMPLAINT + " WHERE " + ComplaintDBColumns.ORG_ID
                + " = ? GROUP BY " + ComplaintDBColumns.STATUS;
    }

    public String getCountSlaBreachedComplaintsQuery() {

        return "SELECT COUNT(*) FROM " + DAOConstants.TABLE_COMPLAINT + " WHERE " + ComplaintDBColumns.ORG_ID + " = ? AND "
                + ComplaintDBColumns.STATUS + " != " + SQL_STATUS_RESOLVED + " AND "
                + ComplaintDBColumns.STATUTORY_DUE_TIME + " < ?";
    }

    // ---- COMPLAINT_EVENT ----
    // COMPLAINT_EVENT_ID links an attachment to the upload event created alongside it (see
    // ComplaintAttachmentServiceImpl.store), so the timeline can show attachments under the entry
    // that added them.

    public String getAddComplaintEventQuery() {

        return "INSERT INTO " + DAOConstants.TABLE_COMPLAINT_EVENT + " (" + ComplaintDBColumns.COMPLAINT_EVENT_ID + ", "
                + ComplaintDBColumns.ORG_ID + ", " + ComplaintDBColumns.COMPLAINT_ID + ", "
                + ComplaintDBColumns.ACTOR_USER_ID + ", " + ComplaintDBColumns.ACTOR_USER_NAME + ", "
                + ComplaintDBColumns.ACTOR_ROLE + ", " + ComplaintDBColumns.IS_PUBLIC + ", "
                + ComplaintDBColumns.COMMENT + ", " + ComplaintDBColumns.FROM_STATUS + ", "
                + ComplaintDBColumns.TO_STATUS + ", " + ComplaintDBColumns.ACTION_TIME + ") "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    public String getGetComplaintEventByIdQuery() {

        return "SELECT " + ComplaintDBColumns.COMPLAINT_EVENT_ID + ", " + ComplaintDBColumns.ORG_ID + ", "
                + ComplaintDBColumns.COMPLAINT_ID + ", " + ComplaintDBColumns.ACTOR_USER_ID + ", "
                + ComplaintDBColumns.ACTOR_USER_NAME + ", " + ComplaintDBColumns.ACTOR_ROLE + ", "
                + ComplaintDBColumns.IS_PUBLIC + ", " + ComplaintDBColumns.COMMENT + ", "
                + ComplaintDBColumns.FROM_STATUS + ", " + ComplaintDBColumns.TO_STATUS + ", "
                + ComplaintDBColumns.ACTION_TIME + " FROM " + DAOConstants.TABLE_COMPLAINT_EVENT + " WHERE " + ComplaintDBColumns.COMPLAINT_EVENT_ID
                + " = ? AND " + ComplaintDBColumns.ORG_ID + " = ? AND " + ComplaintDBColumns.COMPLAINT_ID + " = ?";
    }

    /**
     * Base SELECT for paginated event/timeline listing - {@link ComplaintEventQueryBuilder} appends
     * the optional filter WHERE clause, ORDER BY, and LIMIT/OFFSET.
     */
    public String getListComplaintEventsBaseQuery() {

        return "SELECT " + ComplaintDBColumns.COMPLAINT_EVENT_ID + ", " + ComplaintDBColumns.ORG_ID + ", "
                + ComplaintDBColumns.COMPLAINT_ID + ", " + ComplaintDBColumns.ACTOR_USER_ID + ", "
                + ComplaintDBColumns.ACTOR_USER_NAME + ", " + ComplaintDBColumns.ACTOR_ROLE + ", "
                + ComplaintDBColumns.IS_PUBLIC + ", " + ComplaintDBColumns.COMMENT + ", "
                + ComplaintDBColumns.FROM_STATUS + ", " + ComplaintDBColumns.TO_STATUS + ", "
                + ComplaintDBColumns.ACTION_TIME + " FROM " + DAOConstants.TABLE_COMPLAINT_EVENT + " WHERE " + ComplaintDBColumns.ORG_ID
                + " = ? AND " + ComplaintDBColumns.COMPLAINT_ID + " = ? ";
    }

    public String getCountComplaintEventsBaseQuery() {

        return "SELECT COUNT(*) FROM " + DAOConstants.TABLE_COMPLAINT_EVENT + " WHERE " + ComplaintDBColumns.ORG_ID + " = ? AND "
                + ComplaintDBColumns.COMPLAINT_ID + " = ? ";
    }

    // ---- COMPLAINT_ATTACHMENT ----

    public String getAddComplaintAttachmentQuery() {

        return "INSERT INTO " + DAOConstants.TABLE_COMPLAINT_ATTACHMENT + " (" + ComplaintDBColumns.ATTACHMENT_ID + ", "
                + ComplaintDBColumns.ORG_ID + ", " + ComplaintDBColumns.COMPLAINT_ID + ", "
                + ComplaintDBColumns.COMPLAINT_EVENT_ID + ", " + ComplaintDBColumns.FILE_NAME + ", "
                + ComplaintDBColumns.FILE_CONTENT_TYPE + ", " + ComplaintDBColumns.FILE_DATA + ", "
                + ComplaintDBColumns.IS_PUBLIC + ", " + ComplaintDBColumns.CREATED_TIME + ") "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    public String getGetAttachmentMetadataByIdQuery() {

        return "SELECT " + ComplaintDBColumns.ATTACHMENT_ID + ", " + ComplaintDBColumns.ORG_ID + ", "
                + ComplaintDBColumns.COMPLAINT_ID + ", " + ComplaintDBColumns.COMPLAINT_EVENT_ID + ", "
                + ComplaintDBColumns.FILE_NAME + ", " + ComplaintDBColumns.FILE_CONTENT_TYPE + ", "
                + "LENGTH(" + ComplaintDBColumns.FILE_DATA + ") AS " + ComplaintDBColumns.SIZE_BYTES + ", "
                + ComplaintDBColumns.IS_PUBLIC + ", " + ComplaintDBColumns.CREATED_TIME
                + " FROM " + DAOConstants.TABLE_COMPLAINT_ATTACHMENT + " WHERE " + ComplaintDBColumns.ATTACHMENT_ID + " = ? AND "
                + ComplaintDBColumns.ORG_ID + " = ? AND " + ComplaintDBColumns.COMPLAINT_ID + " = ?";
    }

    public String getGetAttachmentWithDataByIdQuery() {

        return "SELECT " + ComplaintDBColumns.ATTACHMENT_ID + ", " + ComplaintDBColumns.ORG_ID + ", "
                + ComplaintDBColumns.COMPLAINT_ID + ", " + ComplaintDBColumns.COMPLAINT_EVENT_ID + ", "
                + ComplaintDBColumns.FILE_NAME + ", " + ComplaintDBColumns.FILE_CONTENT_TYPE + ", "
                + ComplaintDBColumns.FILE_DATA + ", " + ComplaintDBColumns.IS_PUBLIC + ", "
                + ComplaintDBColumns.CREATED_TIME + " FROM " + DAOConstants.TABLE_COMPLAINT_ATTACHMENT + " WHERE "
                + ComplaintDBColumns.ATTACHMENT_ID + " = ? AND " + ComplaintDBColumns.ORG_ID + " = ? AND "
                + ComplaintDBColumns.COMPLAINT_ID + " = ?";
    }

    public String getListAttachmentMetadataByComplaintQuery() {

        return "SELECT " + ComplaintDBColumns.ATTACHMENT_ID + ", " + ComplaintDBColumns.ORG_ID + ", "
                + ComplaintDBColumns.COMPLAINT_ID + ", " + ComplaintDBColumns.COMPLAINT_EVENT_ID + ", "
                + ComplaintDBColumns.FILE_NAME + ", " + ComplaintDBColumns.FILE_CONTENT_TYPE + ", "
                + "LENGTH(" + ComplaintDBColumns.FILE_DATA + ") AS " + ComplaintDBColumns.SIZE_BYTES + ", "
                + ComplaintDBColumns.IS_PUBLIC + ", " + ComplaintDBColumns.CREATED_TIME
                + " FROM " + DAOConstants.TABLE_COMPLAINT_ATTACHMENT + " WHERE " + ComplaintDBColumns.ORG_ID + " = ? AND "
                + ComplaintDBColumns.COMPLAINT_ID + " = ? ORDER BY " + ComplaintDBColumns.CREATED_TIME + " ASC";
    }
}
