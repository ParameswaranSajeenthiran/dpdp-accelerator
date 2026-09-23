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

import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentExpiryRecord;

import java.sql.Connection;
import java.util.List;

/**
 * Maintains {@code DPDP_CONSENT_EXPIRY_TRACKER} - the scheduling index used to detect when a
 * consent has lapsed. This service is deliberately DB-only: it never talks to
 * {@code carbon-consent-management} (that dependency lives in {@code identity.extensions}, which
 * calls into this service). {@code orgId}/{@code tenantDomain} is passed in explicitly by every
 * caller, same convention as {@link ConsentHistoryService}. Every method throws only the
 * unchecked {@link org.wso2.dpdp.accelerator.consent.extensions.dao.exceptions.ConsentExpiryDataAccessException}.
 */
public interface ConsentExpiryService {

    /**
     * Replaces any existing tracker row for this consent with one carrying
     * {@code expiryTimeMillis}. Unconditional - not gated by {@code ConsentExpiry.Enabled}, since
     * disabling the feature should stop generating {@code EXPIRE} history rows, not stop
     * bookkeeping the tracker table.
     */
    void trackExpiry(String orgId, String consentId, long expiryTimeMillis);

    void untrackExpiry(String orgId, String consentId);

    /** Claims the observed deadline using the caller's transaction; never commits or closes it. */
    boolean claimExpiryIfDue(Connection connection, ConsentExpiryRecord candidate, long nowMillis);

    /** Returns the tracked deadline for a listener invocation, or null when no row exists. */
    ConsentExpiryRecord findExpiry(String orgId, String consentId);

    /** Fetches a page strictly after the supplied cursor; null starts a new scan. */
    List<ConsentExpiryRecord> findDueExpiries(long nowMillis, int batchSize, ConsentExpiryRecord cursor);

    /** Updates only the observed tracker deadline, without owning the caller's transaction. */
    boolean reconcileExpiry(Connection connection, ConsentExpiryRecord candidate, long expiryTimeMillis);
}
