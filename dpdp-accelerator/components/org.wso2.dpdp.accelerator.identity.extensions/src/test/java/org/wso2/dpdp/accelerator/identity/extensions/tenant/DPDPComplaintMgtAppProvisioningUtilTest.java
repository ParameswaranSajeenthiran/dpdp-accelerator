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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class DPDPComplaintMgtAppProvisioningUtilTest {

    private static final String TENANT_DOMAIN = "tenant-a.com";
    private static final String APPLICATION_ID = "app-1234";
    private static final String RESOURCE_ID = "res-complaints";

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
    public void authorizeComplaintManagementAPIRegistersTheResourceWhenMissing() throws Exception {

        Scope readSelf = mockScope(DPDPComplaintMgtAppProvisioningUtil.SCOPE_READ_SELF);
        Scope writeSelf = mockScope(DPDPComplaintMgtAppProvisioningUtil.SCOPE_WRITE_SELF);
        Scope readAny = mockScope(DPDPComplaintMgtAppProvisioningUtil.SCOPE_READ_ANY);
        Scope writeAny = mockScope(DPDPComplaintMgtAppProvisioningUtil.SCOPE_WRITE_ANY);
        APIResource registered = mockResource(RESOURCE_ID,
                Arrays.asList(readSelf, writeSelf, readAny, writeAny));

        when(apiResourceManager.getAPIResourceByIdentifier(DPDPComplaintMgtAppProvisioningUtil.API_RESOURCE_IDENTIFIER,
                TENANT_DOMAIN)).thenReturn(null);
        when(apiResourceManager.addAPIResource(any(APIResource.class), eq(TENANT_DOMAIN))).thenReturn(registered);

        List<String> scopes = DPDPComplaintMgtAppProvisioningUtil
                .authorizeComplaintManagementAPI(APPLICATION_ID, TENANT_DOMAIN);

        assertEquals(scopes, Arrays.asList(DPDPComplaintMgtAppProvisioningUtil.SCOPE_READ_SELF,
                DPDPComplaintMgtAppProvisioningUtil.SCOPE_WRITE_SELF,
                DPDPComplaintMgtAppProvisioningUtil.SCOPE_READ_ANY,
                DPDPComplaintMgtAppProvisioningUtil.SCOPE_WRITE_ANY));

        ArgumentCaptor<APIResource> resourceCaptor = ArgumentCaptor.forClass(APIResource.class);
        verify(apiResourceManager).addAPIResource(resourceCaptor.capture(), eq(TENANT_DOMAIN));
        assertEquals(resourceCaptor.getValue().getIdentifier(),
                DPDPComplaintMgtAppProvisioningUtil.API_RESOURCE_IDENTIFIER);
        assertEquals(resourceCaptor.getValue().getScopes().size(), 4);

        verify(authorizedAPIManagementService).addAuthorizedAPI(eq(APPLICATION_ID), any(AuthorizedAPI.class),
                eq(TENANT_DOMAIN));
    }

    @Test
    public void authorizeComplaintManagementAPISkipsRegistrationWhenResourceAlreadyExists() throws Exception {

        APIResource existing = mockResource(RESOURCE_ID,
                Arrays.asList(mockScope(DPDPComplaintMgtAppProvisioningUtil.SCOPE_READ_SELF)));
        when(apiResourceManager.getAPIResourceByIdentifier(DPDPComplaintMgtAppProvisioningUtil.API_RESOURCE_IDENTIFIER,
                TENANT_DOMAIN)).thenReturn(existing);

        DPDPComplaintMgtAppProvisioningUtil.authorizeComplaintManagementAPI(APPLICATION_ID, TENANT_DOMAIN);

        verify(apiResourceManager, never()).addAPIResource(any(APIResource.class), eq(TENANT_DOMAIN));
        verify(authorizedAPIManagementService, times(1)).addAuthorizedAPI(eq(APPLICATION_ID),
                any(AuthorizedAPI.class), eq(TENANT_DOMAIN));
    }

    @Test
    public void authorizeComplaintManagementAPISkipsReauthorizingAnAlreadyAuthorizedApplication() throws Exception {

        Scope readSelf = mockScope(DPDPComplaintMgtAppProvisioningUtil.SCOPE_READ_SELF);
        APIResource existingResource = mockResource(RESOURCE_ID, Arrays.asList(readSelf));
        when(apiResourceManager.getAPIResourceByIdentifier(DPDPComplaintMgtAppProvisioningUtil.API_RESOURCE_IDENTIFIER,
                TENANT_DOMAIN)).thenReturn(existingResource);

        AuthorizedAPI existingAuthorization = mock(AuthorizedAPI.class);
        when(existingAuthorization.getScopes()).thenReturn(Arrays.asList(readSelf));
        when(authorizedAPIManagementService.getAuthorizedAPI(APPLICATION_ID, RESOURCE_ID, TENANT_DOMAIN))
                .thenReturn(existingAuthorization);

        List<String> scopes = DPDPComplaintMgtAppProvisioningUtil
                .authorizeComplaintManagementAPI(APPLICATION_ID, TENANT_DOMAIN);

        assertEquals(scopes, Arrays.asList(DPDPComplaintMgtAppProvisioningUtil.SCOPE_READ_SELF));
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
        when(resource.getIdentifier()).thenReturn(DPDPComplaintMgtAppProvisioningUtil.API_RESOURCE_IDENTIFIER);
        when(resource.getType()).thenReturn("BUSINESS");
        when(resource.getScopes()).thenReturn(scopes);
        return resource;
    }
}
