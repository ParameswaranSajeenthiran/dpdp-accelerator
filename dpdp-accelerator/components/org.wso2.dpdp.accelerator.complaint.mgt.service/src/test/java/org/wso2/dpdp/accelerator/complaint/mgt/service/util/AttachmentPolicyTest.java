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

package org.wso2.dpdp.accelerator.complaint.mgt.service.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Outside a real Carbon environment (no dpdp-accelerator.xml on disk), DPDPConfigurationService
 * always falls back to its own default - see DPDPConfigurationServiceImplTest for coverage of the
 * configured-value/validation path itself, which lives entirely in that class now.
 */
class AttachmentPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "image/png",
            "image/jpeg"
    })
    void allowsEachDocumentedContentType(String contentType) {
        assertTrue(AttachmentPolicy.isAllowedContentType(contentType));
    }

    @Test
    void rejectsUnknownOrNullContentType() {
        assertFalse(AttachmentPolicy.isAllowedContentType("application/zip"));
        assertFalse(AttachmentPolicy.isAllowedContentType(null));
    }

    @Test
    void isAllowedContentTypeTrimsWhitespace() {
        assertTrue(AttachmentPolicy.isAllowedContentType("  image/png  "));
    }

    @Test
    void defaultsToTenMegabytesWhenNoDpdpAcceleratorXmlIsAvailable() {
        assertEquals(10L * 1024 * 1024, AttachmentPolicy.getMaxSizeBytes());
    }
}
