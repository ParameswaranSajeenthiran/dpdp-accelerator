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

package org.wso2.dpdp.accelerator.complaint.mgt.dao.constants;

/** Database column names shared by Complaint DAO row mappings and query builders. */
public final class ComplaintDBColumns {

    public static final String COMPLAINT_ID = "COMPLAINT_ID";
    public static final String ORG_ID = "ORG_ID";
    public static final String USER_ID = "USER_ID";
    public static final String USER_NAME = "USER_NAME";
    public static final String REFERENCE_ID = "REFERENCE_ID";
    public static final String CATEGORY = "CATEGORY";
    public static final String PRIORITY = "PRIORITY";
    public static final String STATUS = "STATUS";
    public static final String DESCRIPTION = "DESCRIPTION";
    public static final String CREATED_TIME = "CREATED_TIME";
    public static final String UPDATED_TIME = "UPDATED_TIME";
    public static final String STATUTORY_DUE_TIME = "STATUTORY_DUE_TIME";

    public static final String COMPLAINT_EVENT_ID = "COMPLAINT_EVENT_ID";
    public static final String ACTOR_USER_ID = "ACTOR_USER_ID";
    public static final String ACTOR_USER_NAME = "ACTOR_USER_NAME";
    public static final String ACTOR_ROLE = "ACTOR_ROLE";
    public static final String IS_PUBLIC = "IS_PUBLIC";
    public static final String COMMENT = "COMMENT";
    public static final String FROM_STATUS = "FROM_STATUS";
    public static final String TO_STATUS = "TO_STATUS";
    public static final String ACTION_TIME = "ACTION_TIME";

    public static final String ATTACHMENT_ID = "ATTACHMENT_ID";
    public static final String FILE_NAME = "FILE_NAME";
    public static final String FILE_CONTENT_TYPE = "FILE_CONTENT_TYPE";
    public static final String FILE_DATA = "FILE_DATA";
    public static final String SIZE_BYTES = "SIZE_BYTES";

    private ComplaintDBColumns() {
    }
}
