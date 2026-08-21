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

package org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.consent.mgt.core.PrivilegedConsentManager;
import org.wso2.carbon.consent.mgt.core.exception.ConsentManagementException;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.dpdp.accelerator.consent.extensions.dao.exceptions.ConsentHistoryDataRetrievalException;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentHistoryRecord;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentStatusAuditRecord;
import org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.dto.ConsentHistoryResponseDTO;
import org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.dto.StatusHistoryResponseDTO;
import org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.error.ConsentHistoryErrorCodes;
import org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.exception.ConsentHistoryEndpointException;
import org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.util.ConsentHistoryEndpointUtil;
import org.wso2.dpdp.accelerator.consent.extensions.service.ConsentHistoryService;
import org.wso2.dpdp.accelerator.consent.extensions.service.models.PagedResult;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Self-service consent read isn't gated by a dedicated IS-native scope - any authenticated user
 * (scope {@code internal_login}) can reach these; ownership is enforced entirely here, in code,
 * matching how the portal's own self-service consent read already works.
 */
@Path("/me/consents")
public class ConsentHistorySelfApi {

    private static final Log LOG = LogFactory.getLog(ConsentHistorySelfApi.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @GET
    @Path("/{consentId}/status-history")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStatusHistory(@PathParam("consentId") String consentId,
            @QueryParam("limit") @DefaultValue("20") int limit, @QueryParam("offset") @DefaultValue("0") int offset) {

        ConsentHistoryEndpointUtil.validatePagination(limit, offset);
        String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
        requireCallerOwnsConsent(consentId);
        try {
            PagedResult<ConsentStatusAuditRecord> result = getConsentHistoryService()
                    .getStatusAuditHistory(tenantDomain, consentId, limit, offset);
            ConsentHistoryEndpointUtil.requireHistoryExists(result.getTotalCount(), consentId);
            StatusHistoryResponseDTO response = ConsentHistoryEndpointUtil.buildStatusHistoryResponse(consentId,
                    result, limit, offset);
            return Response.ok(response).build();
        } catch (ConsentHistoryDataRetrievalException e) {
            LOG.error("Error retrieving status-audit history for consent: " + sanitize(consentId), e);
            throw new ConsentHistoryEndpointException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                    ConsentHistoryErrorCodes.SERVER_ERROR, "Could not retrieve the status-audit history.");
        }
    }

    @GET
    @Path("/{consentId}/history")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getHistory(@PathParam("consentId") String consentId,
            @QueryParam("limit") @DefaultValue("20") int limit, @QueryParam("offset") @DefaultValue("0") int offset) {

        ConsentHistoryEndpointUtil.validatePagination(limit, offset);
        String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
        requireCallerOwnsConsent(consentId);
        try {
            PagedResult<ConsentHistoryRecord> result = getConsentHistoryService()
                    .getConsentHistory(tenantDomain, consentId, limit, offset);
            ConsentHistoryEndpointUtil.requireHistoryExists(result.getTotalCount(), consentId);
            ConsentHistoryResponseDTO response = ConsentHistoryEndpointUtil.buildConsentHistoryResponse(consentId,
                    result, limit, offset, OBJECT_MAPPER);
            return Response.ok(response).build();
        } catch (ConsentHistoryDataRetrievalException e) {
            LOG.error("Error retrieving history for consent: " + sanitize(consentId), e);
            throw new ConsentHistoryEndpointException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                    ConsentHistoryErrorCodes.SERVER_ERROR, "Could not retrieve the history.");
        }
    }

    private void requireCallerOwnsConsent(String consentId) {

        String callerUsername = PrivilegedCarbonContext.getThreadLocalCarbonContext().getUsername();
        try {
            String piiPrincipalId = getPrivilegedConsentManager().getReceiptWithExtendedSchema(consentId)
                    .getPiiPrincipalId();
            ConsentHistoryEndpointUtil.requireOwner(callerUsername, piiPrincipalId);
        } catch (ConsentManagementException e) {
            LOG.debug("Could not resolve consent " + sanitize(consentId) + " for the ownership check.", e);
            throw new ConsentHistoryEndpointException(Response.Status.NOT_FOUND.getStatusCode(),
                    ConsentHistoryErrorCodes.NOT_FOUND, "No history exists for consent ID '" + consentId + "'.");
        }
    }

    private ConsentHistoryService getConsentHistoryService() {

        return (ConsentHistoryService) PrivilegedCarbonContext.getThreadLocalCarbonContext()
                .getOSGiService(ConsentHistoryService.class, null);
    }

    private PrivilegedConsentManager getPrivilegedConsentManager() {

        return (PrivilegedConsentManager) PrivilegedCarbonContext.getThreadLocalCarbonContext()
                .getOSGiService(PrivilegedConsentManager.class, null);
    }

    private static String sanitize(String value) {

        return value == null ? null : value.replaceAll("[\r\n]", "");
    }
}
