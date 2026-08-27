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

package org.wso2.dpdp.accelerator.complaint.mgt.service.notification;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;

/**
 * Notifies interested parties about complaint lifecycle events - a complaint being filed, or a
 * comment being added to one. {@link EmailNotificationClient} is the only implementation today,
 * routing through WSO2 IS's own email notification mechanism (see that class's javadoc for the
 * OSGi bridge mechanics) - this interface exists so a future channel (SMS, push, ...) could be
 * added as a sibling implementation without either caller (
 * {@code ComplaintServiceImpl}, {@code ComplaintEventServiceImpl}) changing at all.
 *
 * <p>Implementations must never let a notification failure propagate to the caller - every method
 * here is fire-and-forget by design, since a complaint or comment write must succeed independently
 * of whether anyone could be notified about it.
 */
public interface NotificationClient {

    /** Notifies the complaint officers (dpdp-consent-admin role members) that a complaint was filed. */
    void notifyComplaintCreated(Complaint complaint);

    /**
     * Notifies the other party of a new comment: complaint officers when a citizen comments, or
     * the complaint's original creator when an officer comments (see
     * {@code ComplaintNotificationHandler} for how the actor role decides this).
     */
    void notifyCommentAdded(Complaint complaint, ComplaintEvent event);
}
