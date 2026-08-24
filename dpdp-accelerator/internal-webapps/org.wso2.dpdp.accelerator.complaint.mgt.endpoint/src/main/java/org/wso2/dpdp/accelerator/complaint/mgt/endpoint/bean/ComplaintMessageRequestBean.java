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

package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of POST /complaints/{complaintId}/comments (officer/admin) - CmComplaintMessageRequest in
 * the API spec. No actorUserId/actorRole field: the acting officer/system's identity and role are
 * resolved server-side from the caller's bearer token, same as the /me endpoints.
 *
 * <p>isPublic is a boxed {@link Boolean}, not a primitive: the spec marks it required, and a
 * primitive would silently default a missing field to {@code false} (an internal note) instead of
 * letting the handler reject it with a 422 - in a system whose whole job is gating visibility
 * correctly, that default is exactly the wrong direction to fail silently in.
 */
public class ComplaintMessageRequestBean {

    private String message;
    private Boolean isPublic;
    private String toStatus;

    public ComplaintMessageRequestBean() {
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @JsonProperty("isPublic")
    public Boolean isPublic() {
        return isPublic;
    }

    @JsonProperty("isPublic")
    public void setPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }
}
