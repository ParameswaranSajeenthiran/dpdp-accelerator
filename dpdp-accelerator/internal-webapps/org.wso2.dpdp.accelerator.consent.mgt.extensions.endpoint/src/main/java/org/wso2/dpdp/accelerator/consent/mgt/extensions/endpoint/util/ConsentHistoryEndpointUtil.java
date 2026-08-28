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

package org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentHistoryRecord;
import org.wso2.dpdp.accelerator.consent.extensions.dao.models.ConsentStatusAuditRecord;
import org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.dto.ActionType;
import org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.dto.ConsentHistoryEntryDTO;
import org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.dto.ConsentHistoryResponseDTO;
import org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.dto.PaginationDTO;
import org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.dto.StatusAuditEntryDTO;
import org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.dto.StatusHistoryResponseDTO;
import org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.error.ConsentHistoryErrorCodes;
import org.wso2.dpdp.accelerator.consent.mgt.extensions.endpoint.exception.ConsentHistoryEndpointException;
import org.wso2.dpdp.accelerator.consent.extensions.service.models.PagedResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response;

/**
 * Pure request/response-shaping logic, kept free of any {@code PrivilegedCarbonContext}/OSGi
 * call so it is directly unit-testable - the {@code api} resource classes stay thin orchestration
 * around this.
 */
public final class ConsentHistoryEndpointUtil {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;

    private ConsentHistoryEndpointUtil() {

    }

    public static void validatePagination(int limit, int offset) {

        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new ConsentHistoryEndpointException(Response.Status.BAD_REQUEST.getStatusCode(),
                    ConsentHistoryErrorCodes.INVALID_PARAMETER,
                    "limit must be between " + MIN_LIMIT + " and " + MAX_LIMIT + ".");
        }
        if (offset < 0) {
            throw new ConsentHistoryEndpointException(Response.Status.BAD_REQUEST.getStatusCode(),
                    ConsentHistoryErrorCodes.INVALID_PARAMETER, "offset must not be negative.");
        }
    }

    public static void requireHistoryExists(int totalCount, String consentId) {

        if (totalCount == 0) {
            throw new ConsentHistoryEndpointException(Response.Status.NOT_FOUND.getStatusCode(),
                    ConsentHistoryErrorCodes.NOT_FOUND, "No history exists for consent ID '" + consentId + "'.");
        }
    }

    public static boolean isOwner(String callerUsername, String piiPrincipalId) {

        return callerUsername != null && callerUsername.equals(piiPrincipalId);
    }

    public static void requireOwner(String callerUsername, String piiPrincipalId) {

        if (!isOwner(callerUsername, piiPrincipalId)) {
            throw new ConsentHistoryEndpointException(Response.Status.FORBIDDEN.getStatusCode(),
                    ConsentHistoryErrorCodes.FORBIDDEN_NOT_OWNER,
                    "The authenticated user is not the owner of this consent.");
        }
    }

    public static StatusHistoryResponseDTO buildStatusHistoryResponse(String consentId,
            PagedResult<ConsentStatusAuditRecord> result, int limit, int offset) {

        List<StatusAuditEntryDTO> entries = new ArrayList<>();
        for (ConsentStatusAuditRecord record : result.getRecords()) {
            entries.add(new StatusAuditEntryDTO()
                    .previousStatus(record.getPreviousStatus())
                    .currentStatus(record.getCurrentStatus())
                    .actionType(parseActionType(record.getActionType()))
                    .actionBy(record.getActionBy())
                    .actionTime(record.getActionTime()));
        }
        return new StatusHistoryResponseDTO()
                .consentId(consentId)
                .statusHistory(entries)
                .pagination(new PaginationDTO().limit(limit).offset(offset).totalCount(result.getTotalCount()));
    }

    public static ConsentHistoryResponseDTO buildConsentHistoryResponse(String consentId,
            PagedResult<ConsentHistoryRecord> result, int limit, int offset, ObjectMapper objectMapper) {

        List<ConsentHistoryEntryDTO> entries = new ArrayList<>();
        for (ConsentHistoryRecord record : result.getRecords()) {
            entries.add(new ConsentHistoryEntryDTO()
                    .actionType(parseActionType(record.getActionType()))
                    .actionBy(record.getActionBy())
                    .actionTime(record.getActionTime())
                    .snapshot(parseSnapshot(record.getSnapshot(), objectMapper)));
        }
        return new ConsentHistoryResponseDTO()
                .consentId(consentId)
                .history(entries)
                .pagination(new PaginationDTO().limit(limit).offset(offset).totalCount(result.getTotalCount()));
    }

    private static JsonNode parseSnapshot(String snapshotJson, ObjectMapper objectMapper) {

        try {
            return objectMapper.readTree(snapshotJson);
        } catch (IOException e) {
            throw new ConsentHistoryEndpointException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                    ConsentHistoryErrorCodes.SERVER_ERROR, "A stored history snapshot could not be parsed.");
        }
    }

    private static ActionType parseActionType(String actionType) {

        try {
            return ActionType.fromValue(actionType);
        } catch (IllegalArgumentException e) {
            throw new ConsentHistoryEndpointException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                    ConsentHistoryErrorCodes.SERVER_ERROR, "A stored history entry has an unrecognized action type.");
        }
    }
}
