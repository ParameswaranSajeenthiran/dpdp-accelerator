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

package org.wso2.dpdp.accelerator.portal.webapp.util;

import org.testng.annotations.Test;

import javax.servlet.http.HttpServletRequest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * Covers {@link AuthUtil#resolveGroupId(HttpServletRequest)}: the BFF reads the
 * {@code group-id} header from the incoming SPA request and forwards it to the
 * event-notifications endpoint as-is. The helper trims whitespace and treats
 * the empty header the same as a missing one.
 */
public class AuthUtilTest {

    @Test
    public void resolveGroupId_returnsHeaderValue_whenPresent() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("group-id")).thenReturn("GROUP-001");

        assertEquals(AuthUtil.resolveGroupId(request), "GROUP-001");
    }

    @Test
    public void resolveGroupId_trimsWhitespace() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("group-id")).thenReturn("  GROUP-001  ");

        assertEquals(AuthUtil.resolveGroupId(request), "GROUP-001");
    }

    @Test
    public void resolveGroupId_returnsNull_whenHeaderMissing() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("group-id")).thenReturn(null);

        assertNull(AuthUtil.resolveGroupId(request));
    }

    @Test
    public void resolveGroupId_returnsNull_whenHeaderBlank() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("group-id")).thenReturn("   ");

        assertNull(AuthUtil.resolveGroupId(request));
    }

    @Test
    public void resolveGroupId_returnsNull_whenHeaderEmpty() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("group-id")).thenReturn("");

        assertNull(AuthUtil.resolveGroupId(request));
    }
}