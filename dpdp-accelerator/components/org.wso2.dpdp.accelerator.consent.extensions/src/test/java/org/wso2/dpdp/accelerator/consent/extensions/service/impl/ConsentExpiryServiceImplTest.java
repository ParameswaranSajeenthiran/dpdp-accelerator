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
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.expectThrows;

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



    @Test(expectedExceptions = ConsentExpiryDataAccessException.class)
    public void trackExpiryRollsBackAndRethrowsOnDaoFailure() throws Exception {

        ConsentExpiryDataAccessException failure = new ConsentExpiryDataAccessException("boom", null);
        doThrow(failure).when(consentExpiryTrackerDAO).upsertExpiry(any(Connection.class), eq(ORG_ID),
                eq(CONSENT_ID), eq(5000L));

        consentExpiryService.trackExpiry(ORG_ID, CONSENT_ID, 5000L);
    }
    @Test
    public void callerOwnedOperationsDoNotCommitOrCloseTheConnection() throws Exception {

        Connection connection = mock(Connection.class);
        ConsentExpiryRecord candidate = new ConsentExpiryRecord();
        when(consentExpiryTrackerDAO.claimDueExpiry(connection, candidate, 1000)).thenReturn(true);
        when(consentExpiryTrackerDAO.reconcileExpiry(connection, candidate, 2000)).thenReturn(true);
        assertTrue(consentExpiryService.claimExpiryIfDue(connection, candidate, 1000));
        assertTrue(consentExpiryService.reconcileExpiry(connection, candidate, 2000));
        verifyNoInteractions(connection);
        ConsentExpiryDataAccessException failure = new ConsentExpiryDataAccessException("failed", null);
        when(consentExpiryTrackerDAO.reconcileExpiry(connection, candidate, 2000)).thenThrow(failure);
        assertSame(expectThrows(ConsentExpiryDataAccessException.class,
                () -> consentExpiryService.reconcileExpiry(connection, candidate, 2000)), failure);
        verifyNoInteractions(connection);
    }

    @Test
    public void lookupAndCursorFetchCloseTheirConnectionsEvenOnFailure() throws Exception {

        Connection connection = mock(Connection.class);
        ConsentExpiryServiceImpl service = new ConsentExpiryServiceImpl(consentExpiryTrackerDAO, () -> connection,
                c -> { }, c -> { });
        ConsentExpiryRecord candidate = new ConsentExpiryRecord();
        when(consentExpiryTrackerDAO.findExpiry(connection, ORG_ID, CONSENT_ID)).thenReturn(candidate);
        assertSame(service.findExpiry(ORG_ID, CONSENT_ID), candidate);
        List<ConsentExpiryRecord> page = Collections.singletonList(candidate);
        when(consentExpiryTrackerDAO.findDueExpiries(connection, 1000, 10, candidate)).thenReturn(page);
        assertEquals(service.findDueExpiries(1000, 10, candidate), page);
        ConsentExpiryDataAccessException failure = new ConsentExpiryDataAccessException("failed", null);
        when(consentExpiryTrackerDAO.findExpiry(connection, ORG_ID, CONSENT_ID)).thenThrow(failure);
        when(consentExpiryTrackerDAO.findDueExpiries(connection, 1000, 10, candidate)).thenThrow(failure);
        expectThrows(ConsentExpiryDataAccessException.class,
                () -> service.findExpiry(ORG_ID, CONSENT_ID));
        expectThrows(ConsentExpiryDataAccessException.class,
                () -> service.findDueExpiries(1000, 10, candidate));
        verify(connection, times(4)).close();
        verify(connection, never()).commit();
    }

    @Test
    public void standaloneFailuresRollbackAndCloseWithoutCommit() throws Exception {

        Connection connection = mock(Connection.class);
        AtomicInteger rollbacks = new AtomicInteger();
        ConsentExpiryServiceImpl service = new ConsentExpiryServiceImpl(consentExpiryTrackerDAO, () -> connection,
                c -> {
                    throw new AssertionError("must not commit");
                }, c -> rollbacks.incrementAndGet());
        ConsentExpiryDataAccessException failure = new ConsentExpiryDataAccessException("failed", null);
        doThrow(failure).when(consentExpiryTrackerDAO).deleteExpiry(connection, CONSENT_ID);
        expectThrows(ConsentExpiryDataAccessException.class,
                () -> service.untrackExpiry(ORG_ID, CONSENT_ID));
        assertEquals(rollbacks.get(), 1);
        verify(connection).close();
    }

}
