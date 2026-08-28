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

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.consent.extensions.dao.ConsentHistoryDAO;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentHistoryRecord;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentStatusAuditRecord;
import org.wso2.dpdp.accelerator.consent.extensions.internal.DPDPConsentExtensionDataHolder;
import org.wso2.dpdp.accelerator.consent.extensions.service.constants.ConsentHistoryServiceConstants.ActionType;
import org.wso2.dpdp.accelerator.consent.extensions.service.models.PagedResult;

import java.sql.Connection;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class ConsentHistoryServiceImplTest {

    private static final String TENANT_DOMAIN = "tenant-a.com";
    private static final String CONSENT_ID = "consent-1234";

    @Mock
    private ConsentHistoryDAO consentHistoryDAO;

    @Mock
    private DPDPConfigurationService configurationService;

    private ConsentHistoryServiceImpl consentHistoryService;

    @BeforeMethod
    public void setUp() {

        MockitoAnnotations.openMocks(this);
        DPDPConsentExtensionDataHolder.getInstance().setConfigurationService(configurationService);
        when(configurationService.isConsentHistorySnapshotEnabled()).thenReturn(true);
        consentHistoryService = new ConsentHistoryServiceImpl(consentHistoryDAO, () -> mock(Connection.class),
                connection -> { }, connection -> { });
    }

    @Test
    public void recordStatusAuditInsertsAndCommits() throws Exception {

        consentHistoryService.recordStatusAudit(TENANT_DOMAIN, CONSENT_ID, "PENDING", "ACTIVE",
                ActionType.AUTHORIZE_APPROVE, "jdoe@carbon.super");

        ArgumentCaptor<ConsentStatusAuditRecord> captor = ArgumentCaptor.forClass(ConsentStatusAuditRecord.class);
        verify(consentHistoryDAO).insertStatusAudit(any(Connection.class), captor.capture());
        ConsentStatusAuditRecord record = captor.getValue();
        assertEquals(record.getConsentId(), CONSENT_ID);
        assertEquals(record.getOrgId(), TENANT_DOMAIN);
        assertEquals(record.getPreviousStatus(), "PENDING");
        assertEquals(record.getCurrentStatus(), "ACTIVE");
        assertEquals(record.getActionType(), "AUTHORIZE_APPROVE");
        assertEquals(record.getActionBy(), "jdoe@carbon.super");
    }

    @Test
    public void recordStatusAuditSkipsWhenStatusIsUnchanged() throws Exception {

        consentHistoryService.recordStatusAudit(TENANT_DOMAIN, CONSENT_ID, "ACTIVE", "ACTIVE", ActionType.UPDATE,
                "jdoe@carbon.super");

        verify(consentHistoryDAO, never()).insertStatusAudit(any(Connection.class), any());
    }

    @Test
    public void recordStatusAuditInsertsWhenPreviousStatusIsNull() throws Exception {

        consentHistoryService.recordStatusAudit(TENANT_DOMAIN, CONSENT_ID, null, "ACTIVE", ActionType.CREATE,
                "jdoe@carbon.super");

        ArgumentCaptor<ConsentStatusAuditRecord> captor = ArgumentCaptor.forClass(ConsentStatusAuditRecord.class);
        verify(consentHistoryDAO).insertStatusAudit(any(Connection.class), captor.capture());
        assertEquals(captor.getValue().getPreviousStatus(), null);
        assertEquals(captor.getValue().getCurrentStatus(), "ACTIVE");
    }

    @Test
    public void recordHistorySnapshotInsertsWhenEnabled() throws Exception {

        consentHistoryService.recordHistorySnapshot(TENANT_DOMAIN, CONSENT_ID, ActionType.REVOKE,
                "{\"state\":\"ACTIVE\"}", "jdoe@carbon.super");

        ArgumentCaptor<ConsentHistoryRecord> captor = ArgumentCaptor.forClass(ConsentHistoryRecord.class);
        verify(consentHistoryDAO).insertHistorySnapshot(any(Connection.class), captor.capture());
        assertEquals(captor.getValue().getSnapshot(), "{\"state\":\"ACTIVE\"}");
        assertEquals(captor.getValue().getActionType(), "REVOKE");
    }

    @Test
    public void recordHistorySnapshotSkipsWhenDisabled() throws Exception {

        when(configurationService.isConsentHistorySnapshotEnabled()).thenReturn(false);

        consentHistoryService.recordHistorySnapshot(TENANT_DOMAIN, CONSENT_ID, ActionType.REVOKE,
                "{\"state\":\"ACTIVE\"}", "jdoe@carbon.super");

        verify(consentHistoryDAO, never()).insertHistorySnapshot(any(Connection.class), any());
    }

    @Test
    public void getStatusAuditHistoryReturnsRecordsAndTotalCount() throws Exception {

        List<ConsentStatusAuditRecord> records = Collections.singletonList(new ConsentStatusAuditRecord());
        when(consentHistoryDAO.getStatusAuditHistory(any(Connection.class), eq(TENANT_DOMAIN), eq(CONSENT_ID),
                eq(20), eq(0))).thenReturn(records);
        when(consentHistoryDAO.getStatusAuditHistoryCount(any(Connection.class), eq(TENANT_DOMAIN), eq(CONSENT_ID)))
                .thenReturn(1);

        PagedResult<ConsentStatusAuditRecord> result = consentHistoryService.getStatusAuditHistory(TENANT_DOMAIN,
                CONSENT_ID, 20, 0);

        assertEquals(result.getRecords(), records);
        assertEquals(result.getTotalCount(), 1);
    }

    @Test
    public void getConsentHistoryReturnsRecordsAndTotalCount() throws Exception {

        List<ConsentHistoryRecord> records = Collections.singletonList(new ConsentHistoryRecord());
        when(consentHistoryDAO.getConsentHistory(any(Connection.class), eq(TENANT_DOMAIN), eq(CONSENT_ID), anyInt(),
                anyInt())).thenReturn(records);
        when(consentHistoryDAO.getConsentHistoryCount(any(Connection.class), eq(TENANT_DOMAIN), eq(CONSENT_ID)))
                .thenReturn(1);

        PagedResult<ConsentHistoryRecord> result = consentHistoryService.getConsentHistory(TENANT_DOMAIN, CONSENT_ID,
                20, 0);

        assertEquals(result.getRecords(), records);
        assertEquals(result.getTotalCount(), 1);
    }

    @Test
    public void resolveOrgIdDefaultsWhenTenantDomainIsNull() throws Exception {

        when(consentHistoryDAO.getStatusAuditHistory(any(Connection.class), eq("carbon.super"), eq(CONSENT_ID),
                anyInt(), anyInt())).thenReturn(Collections.emptyList());

        consentHistoryService.getStatusAuditHistory(null, CONSENT_ID, 20, 0);

        verify(consentHistoryDAO).getStatusAuditHistory(any(Connection.class), eq("carbon.super"), eq(CONSENT_ID),
                eq(20), eq(0));
    }

    @Test(expectedExceptions = org.wso2.dpdp.accelerator.consent.extensions.dao.exceptions.ConsentHistoryDataInsertionException.class)
    public void recordStatusAuditRollsBackAndRethrowsOnDaoFailure() throws Exception {

        org.wso2.dpdp.accelerator.consent.extensions.dao.exceptions.ConsentHistoryDataInsertionException failure =
                new org.wso2.dpdp.accelerator.consent.extensions.dao.exceptions.ConsentHistoryDataInsertionException(
                        "boom", null);
        org.mockito.Mockito.doThrow(failure).when(consentHistoryDAO)
                .insertStatusAudit(any(Connection.class), any(ConsentStatusAuditRecord.class));

        consentHistoryService.recordStatusAudit(TENANT_DOMAIN, CONSENT_ID, "PENDING", "ACTIVE", ActionType.AUTHORIZE_APPROVE,
                "jdoe@carbon.super");
    }
}
