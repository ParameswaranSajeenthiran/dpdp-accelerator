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

package org.wso2.dpdp.accelerator.complaint.mgt.service.util;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintStatus.WAITING_ON_CLIENT;
import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintStatus.AWAITING_INTERNAL_REVIEW;
import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintStatus.IN_PROGRESS;
import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintStatus.OPEN;
import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.ComplaintStatus.RESOLVED;

/**
 * Valid ComplaintStatus transitions.
 *   OPEN                        -> IN_PROGRESS, WAITING_ON_CLIENT
 *   IN_PROGRESS                 -> WAITING_ON_CLIENT, RESOLVED
 *   WAITING_ON_CLIENT           -> AWAITING_INTERNAL_REVIEW
 *   AWAITING_INTERNAL_REVIEW    -> IN_PROGRESS, WAITING_ON_CLIENT, RESOLVED
 *   RESOLVED                    -> AWAITING_INTERNAL_REVIEW
 *
 * A complaint can only be RESOLVED after having gone through IN_PROGRESS or AWAITING_INTERNAL_REVIEW,
 * so OPEN -> RESOLVED directly is rejected (this matches the 409 example in the API spec).
 *
 * An officer must mark a complaint IN_PROGRESS before it can go to internal review - OPEN ->
 * AWAITING_INTERNAL_REVIEW directly is rejected so every reviewed complaint has been triaged first.
 *
 * Once a complaint is WAITING_ON_CLIENT, the complainant's reply routes it to internal
 * review rather than back into IN_PROGRESS directly - AWAITING_INTERNAL_REVIEW is the only way out.
 *
 * A RESOLVED complaint is locked for the officer (no manual transition out of it); it can only be
 * reopened when the complainant replies, which automatically routes it to AWAITING_INTERNAL_REVIEW
 * for an officer to re-triage.
 */
public class StatusTransitionValidator {

    private static final Map<ComplaintStatus, Set<ComplaintStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(ComplaintStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OPEN, EnumSet.of(IN_PROGRESS, WAITING_ON_CLIENT));
        ALLOWED_TRANSITIONS.put(IN_PROGRESS, EnumSet.of(WAITING_ON_CLIENT, RESOLVED));
        ALLOWED_TRANSITIONS.put(WAITING_ON_CLIENT, EnumSet.of(AWAITING_INTERNAL_REVIEW));
        ALLOWED_TRANSITIONS.put(AWAITING_INTERNAL_REVIEW, EnumSet.of(IN_PROGRESS, WAITING_ON_CLIENT, RESOLVED));
        ALLOWED_TRANSITIONS.put(RESOLVED, EnumSet.of(AWAITING_INTERNAL_REVIEW));
    }

    private StatusTransitionValidator() {
    }

    /** fromStatus/toStatus are the raw column/API values; unknown values (not a ComplaintStatus) are rejected. */
    public static boolean isValidTransition(String fromStatus, String toStatus) {
        if (!ComplaintStatus.isValid(fromStatus) || !ComplaintStatus.isValid(toStatus)) {
            return false;
        }
        Set<ComplaintStatus> allowedTargets = ALLOWED_TRANSITIONS.get(ComplaintStatus.valueOf(fromStatus));
        return allowedTargets.contains(ComplaintStatus.valueOf(toStatus));
    }
}
