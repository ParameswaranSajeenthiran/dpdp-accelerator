/**
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 * <p>
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.event.notifications.endpoint.exception;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.ErrorResponse;
import org.wso2.dpdp.accelerator.event.notifications.service.exception.ENFException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class ENFExceptionMapperTest {

    private ENFExceptionMapper mapper;

    @BeforeMethod
    public void setUp() {
        mapper = new ENFExceptionMapper();
    }

    @Test
    public void testToResponseNotFoundException() {
        ENFException ex = new ENFException("CS-4040", "Resource not found", "Topic ID not found.", 404);
        Response response = mapper.toResponse(ex);

        assertNotNull(response);
        assertEquals(response.getStatus(), 404);
        assertEquals(response.getMediaType(), MediaType.APPLICATION_JSON_TYPE);

        ErrorResponse entity = (ErrorResponse) response.getEntity();
        assertNotNull(entity);
        assertEquals(entity.getCode(), "CS-4040");
        assertEquals(entity.getMessage(), "Resource not found");
        assertEquals(entity.getDescription(), "Topic ID not found.");
    }

    @Test
    public void testToResponseConflictException() {
        ENFException ex = new ENFException("CS-4090", "Topic already exists", "Topic name conflict.", 409);
        Response response = mapper.toResponse(ex);

        assertNotNull(response);
        assertEquals(response.getStatus(), 409);
        assertTrue(response.getEntity() instanceof ErrorResponse);
    }

    @Test
    public void testToResponseValidationException() {
        ENFException ex = new ENFException("CS-4001", "Malformed request", "Org ID required.", 400);
        Response response = mapper.toResponse(ex);

        assertNotNull(response);
        assertEquals(response.getStatus(), 400);
    }
}
