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

package org.wso2.dpdp.accelerator.consent.extensions.service.impl;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.consent.extensions.dao.ConsentExpiryTrackerDAO;
import org.wso2.dpdp.accelerator.consent.extensions.dao.exceptions.ConsentExpiryDataAccessException;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentExpiryRecord;

import java.sql.Connection;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ConsentExpiryServiceImplTest {

    private static final String ORG_ID = "tenant-a.com";
    private static final String CONSENT_ID = "consent-1234";

    @Mock
    private ConsentExpiryTrackerDAO consentExpiryTrackerDAO;

    private ConsentExpiryServiceImpl consentExpiryService;

    @BeforeMethod
    public void setUp() {

        MockitoAnnotations.openMocks(this);
        consentExpiryService = new ConsentExpiryServiceImpl(consentExpiryTrackerDAO, () -> mock(Connection.class),
                connection -> { }, connection -> { });
    }

    @Test
    public void trackExpiryUpsertsAndCommits() throws Exception {

        consentExpiryService.trackExpiry(ORG_ID, CONSENT_ID, 5000L);

        verify(consentExpiryTrackerDAO).upsertExpiry(any(Connection.class), eq(ORG_ID), eq(CONSENT_ID), eq(5000L));
    }

    @Test
    public void untrackExpiryDeletesAndCommits() throws Exception {

        consentExpiryService.untrackExpiry(ORG_ID, CONSENT_ID);

        verify(consentExpiryTrackerDAO).deleteExpiry(any(Connection.class), eq(CONSENT_ID));
    }

    @Test
    public void claimExpiryIfDueReturnsTrueWhenDaoClaims() throws Exception {

        when(consentExpiryTrackerDAO.claimDueExpiry(any(Connection.class), eq(CONSENT_ID), eq(10_000L)))
                .thenReturn(true);

        assertTrue(consentExpiryService.claimExpiryIfDue(ORG_ID, CONSENT_ID, 10_000L));
    }

    @Test
    public void claimExpiryIfDueReturnsFalseWhenDaoDoesNotClaim() throws Exception {

        when(consentExpiryTrackerDAO.claimDueExpiry(any(Connection.class), eq(CONSENT_ID), eq(10_000L)))
                .thenReturn(false);

        assertFalse(consentExpiryService.claimExpiryIfDue(ORG_ID, CONSENT_ID, 10_000L));
    }

    @Test
    public void findDueExpiriesReturnsDaoResult() throws Exception {

        List<ConsentExpiryRecord> records = Collections.singletonList(new ConsentExpiryRecord());
        when(consentExpiryTrackerDAO.findDueExpiries(any(Connection.class), eq(10_000L), eq(100)))
                .thenReturn(records);

        List<ConsentExpiryRecord> result = consentExpiryService.findDueExpiries(10_000L, 100);

        assertEquals(result, records);
    }

    @Test(expectedExceptions = ConsentExpiryDataAccessException.class)
    public void trackExpiryRollsBackAndRethrowsOnDaoFailure() throws Exception {

        ConsentExpiryDataAccessException failure = new ConsentExpiryDataAccessException("boom", null);
        doThrow(failure).when(consentExpiryTrackerDAO).upsertExpiry(any(Connection.class), eq(ORG_ID),
                eq(CONSENT_ID), eq(5000L));

        consentExpiryService.trackExpiry(ORG_ID, CONSENT_ID, 5000L);
    }
}
