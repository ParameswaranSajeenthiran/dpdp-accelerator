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

package org.wso2.dpdp.accelerator.identity.extensions.tenant;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.api.resource.mgt.APIResourceManager;
import org.wso2.carbon.identity.application.common.model.APIResource;
import org.wso2.carbon.identity.application.common.model.AuthorizedAPI;
import org.wso2.carbon.identity.application.common.model.Scope;
import org.wso2.carbon.identity.application.mgt.AuthorizedAPIManagementService;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertEqualsNoOrder;

public class DPDPConsentHistoryApiProvisioningUtilTest {

    private static final String TENANT_DOMAIN = "tenant-a.com";
    private static final String APPLICATION_ID = "app-1234";
    private static final String API_IDENTIFIER = "/api/dpdp/consent-mgt/v1";
    private static final String API_RESOURCE_ID = "res-consent-history";

    @Mock
    private APIResourceManager apiResourceManager;

    @Mock
    private AuthorizedAPIManagementService authorizedAPIManagementService;

    @BeforeMethod
    public void setUp() {

        MockitoAnnotations.openMocks(this);
        DPDPIdentityExtensionDataHolder.getInstance().setApiResourceManager(apiResourceManager);
        DPDPIdentityExtensionDataHolder.getInstance()
                .setAuthorizedAPIManagementService(authorizedAPIManagementService);
    }

    @Test
    public void registerApiResourceSkipsWhenAlreadyRegistered() throws Exception {

        when(apiResourceManager.getAPIResourceByIdentifier(API_IDENTIFIER, TENANT_DOMAIN))
                .thenReturn(mock(APIResource.class));

        DPDPConsentHistoryApiProvisioningUtil.registerApiResource(TENANT_DOMAIN);

        verify(apiResourceManager, never()).addAPIResource(any(APIResource.class), eq(TENANT_DOMAIN));
    }

    @Test
    public void registerApiResourceCreatesItWithAllFourScopes() throws Exception {

        when(apiResourceManager.getAPIResourceByIdentifier(API_IDENTIFIER, TENANT_DOMAIN)).thenReturn(null);

        DPDPConsentHistoryApiProvisioningUtil.registerApiResource(TENANT_DOMAIN);

        ArgumentCaptor<APIResource> resourceCaptor = ArgumentCaptor.forClass(APIResource.class);
        verify(apiResourceManager).addAPIResource(resourceCaptor.capture(), eq(TENANT_DOMAIN));
        APIResource resource = resourceCaptor.getValue();
        assertEquals(resource.getIdentifier(), API_IDENTIFIER);
        assertEquals(resource.getType(), "BUSINESS");

        List<String> scopeNames = new java.util.ArrayList<>();
        for (Scope scope : resource.getScopes()) {
            scopeNames.add(scope.getName());
        }
        assertEqualsNoOrder(scopeNames.toArray(), new String[]{
                DPDPConsentHistoryApiProvisioningUtil.STATUS_HISTORY_VIEW_ANY,
                DPDPConsentHistoryApiProvisioningUtil.STATUS_HISTORY_VIEW_SELF,
                DPDPConsentHistoryApiProvisioningUtil.HISTORY_VIEW_ANY,
                DPDPConsentHistoryApiProvisioningUtil.HISTORY_VIEW_SELF
        });
    }

    @Test
    public void authorizeApiForApplicationAuthorizesAndReturnsScopeNamesWhenNotYetAuthorized() throws Exception {

        Scope anyScope = mockScope(DPDPConsentHistoryApiProvisioningUtil.STATUS_HISTORY_VIEW_ANY);
        Scope selfScope = mockScope(DPDPConsentHistoryApiProvisioningUtil.STATUS_HISTORY_VIEW_SELF);
        APIResource apiResource = mockResource(API_RESOURCE_ID, Arrays.asList(anyScope, selfScope));
        when(apiResourceManager.getAPIResourceByIdentifier(API_IDENTIFIER, TENANT_DOMAIN)).thenReturn(apiResource);
        when(authorizedAPIManagementService.getAuthorizedAPI(APPLICATION_ID, API_RESOURCE_ID, TENANT_DOMAIN))
                .thenReturn(null);

        List<String> scopeNames = DPDPConsentHistoryApiProvisioningUtil.authorizeApiForApplication(APPLICATION_ID,
                TENANT_DOMAIN);

        assertEquals(scopeNames, Arrays.asList(DPDPConsentHistoryApiProvisioningUtil.STATUS_HISTORY_VIEW_ANY,
                DPDPConsentHistoryApiProvisioningUtil.STATUS_HISTORY_VIEW_SELF));
        verify(authorizedAPIManagementService).addAuthorizedAPI(eq(APPLICATION_ID), any(AuthorizedAPI.class),
                eq(TENANT_DOMAIN));
    }

    @Test
    public void authorizeApiForApplicationSkipsWhenAlreadyAuthorized() throws Exception {

        Scope anyScope = mockScope(DPDPConsentHistoryApiProvisioningUtil.STATUS_HISTORY_VIEW_ANY);
        APIResource apiResource = mockResource(API_RESOURCE_ID, Arrays.asList(anyScope));
        when(apiResourceManager.getAPIResourceByIdentifier(API_IDENTIFIER, TENANT_DOMAIN)).thenReturn(apiResource);

        AuthorizedAPI existingAuthorization = mock(AuthorizedAPI.class);
        when(existingAuthorization.getScopes()).thenReturn(Arrays.asList(anyScope));
        when(authorizedAPIManagementService.getAuthorizedAPI(APPLICATION_ID, API_RESOURCE_ID, TENANT_DOMAIN))
                .thenReturn(existingAuthorization);

        List<String> scopeNames = DPDPConsentHistoryApiProvisioningUtil.authorizeApiForApplication(APPLICATION_ID,
                TENANT_DOMAIN);

        assertEquals(scopeNames, Arrays.asList(DPDPConsentHistoryApiProvisioningUtil.STATUS_HISTORY_VIEW_ANY));
        verify(authorizedAPIManagementService, never()).addAuthorizedAPI(eq(APPLICATION_ID), any(AuthorizedAPI.class),
                eq(TENANT_DOMAIN));
    }

    private static Scope mockScope(String name) {

        Scope scope = mock(Scope.class);
        when(scope.getName()).thenReturn(name);
        return scope;
    }

    private static APIResource mockResource(String id, List<Scope> scopes) {

        APIResource resource = mock(APIResource.class);
        when(resource.getId()).thenReturn(id);
        when(resource.getType()).thenReturn("BUSINESS");
        when(resource.getScopes()).thenReturn(scopes);
        return resource;
    }
}
