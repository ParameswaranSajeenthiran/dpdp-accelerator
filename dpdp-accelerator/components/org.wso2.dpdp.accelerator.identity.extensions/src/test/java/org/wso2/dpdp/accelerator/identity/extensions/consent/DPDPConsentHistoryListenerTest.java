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

package org.wso2.dpdp.accelerator.identity.extensions.consent;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Only {@code isEnable()}/{@code getDefaultOrderId()} are tested here - every hook method touches
 * {@code PrivilegedCarbonContext} and OSGi-injected consent-mgt services, neither reproducible in
 * a plain unit test (see the module's pom.xml jacoco exclusions).
 */
public class DPDPConsentHistoryListenerTest {

    @Mock
    private DPDPConfigurationService configurationService;

    private DPDPConsentHistoryListener listener;

    @BeforeMethod
    public void setUp() {

        MockitoAnnotations.openMocks(this);
        DPDPIdentityExtensionDataHolder.getInstance().setConfigurationService(configurationService);
        listener = new DPDPConsentHistoryListener();
    }

    @Test
    public void isEnableDelegatesToConfigurationService() {

        when(configurationService.isConsentHistoryEnabled()).thenReturn(true);
        assertTrue(listener.isEnable());

        when(configurationService.isConsentHistoryEnabled()).thenReturn(false);
        assertFalse(listener.isEnable());
    }

    @Test
    public void getDefaultOrderIdReturnsAFixedValue() {

        assertEquals(listener.getDefaultOrderId(), 100);
    }
}
