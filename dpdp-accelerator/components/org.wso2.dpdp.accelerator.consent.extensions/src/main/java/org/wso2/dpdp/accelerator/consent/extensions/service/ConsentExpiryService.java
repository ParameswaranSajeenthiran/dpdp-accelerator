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

package org.wso2.dpdp.accelerator.consent.extensions.service;

import org.wso2.dpdp.accelerator.consent.extensions.dao.exceptions.ConsentExpiryDataAccessException;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentExpiryRecord;

import java.util.List;

/**
 * Maintains {@code DPDP_CONSENT_EXPIRY_TRACKER} - the scheduling index used to detect when a
 * consent has lapsed. This service is deliberately DB-only: it never talks to
 * {@code carbon-consent-management} (that dependency lives in {@code identity.extensions}, which
 * calls into this service). {@code orgId}/{@code tenantDomain} is passed in explicitly by every
 * caller, same convention as {@link ConsentHistoryService}.
 */
public interface ConsentExpiryService {

    /**
     * Replaces any existing tracker row for this consent with one carrying
     * {@code expiryTimeMillis}. Unconditional - not gated by {@code ConsentExpiry.Enabled}, since
     * disabling the feature should stop generating {@code EXPIRE} history rows, not stop
     * bookkeeping the tracker table.
     */
    void trackExpiry(String orgId, String consentId, long expiryTimeMillis) throws ConsentExpiryDataAccessException;

    void untrackExpiry(String orgId, String consentId) throws ConsentExpiryDataAccessException;

    /**
     * Atomically claims a due tracker row for this consent - deletes it only if it exists and its
     * expiry time has passed. Returns whether this call won the claim; {@code false} means
     * nothing was due, or another caller (another node, or the scheduled job) already claimed it.
     */
    boolean claimExpiryIfDue(String orgId, String consentId, long nowMillis) throws ConsentExpiryDataAccessException;

    /**
     * Batch of tracker rows whose expiry time is already due, oldest first.
     */
    List<ConsentExpiryRecord> findDueExpiries(long nowMillis, int batchSize) throws ConsentExpiryDataAccessException;
}
