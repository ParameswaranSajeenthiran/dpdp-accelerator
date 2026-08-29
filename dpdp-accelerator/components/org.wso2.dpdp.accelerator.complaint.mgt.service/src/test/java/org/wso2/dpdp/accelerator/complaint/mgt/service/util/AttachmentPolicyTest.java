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

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationServiceImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.service.internal.ComplaintServiceDataHolder;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Outside a real Carbon environment (no dpdp-accelerator.xml on disk), DPDPConfigurationService
 * always falls back to its own default - see DPDPConfigParserTest for coverage of the
 * configured-value/validation path itself, which lives entirely in that class now.
 */
public class AttachmentPolicyTest {

    @BeforeClass
    public void seedConfigurationService() {
        // Normally bound by ComplaintServiceComponent's OSGi @Reference; AttachmentPolicy reads
        // it via ComplaintServiceDataHolder, so a test running outside a live Carbon environment
        // must seed it itself.
        ComplaintServiceDataHolder.getInstance().setConfigurationService(new DPDPConfigurationServiceImpl());
    }

    @AfterClass
    public void clearConfigurationService() {
        ComplaintServiceDataHolder.getInstance().setConfigurationService(null);
    }

    @DataProvider(name = "documentedContentTypes")
    public Object[][] documentedContentTypes() {
        return new Object[][]{
                {"application/pdf"},
                {"application/vnd.openxmlformats-officedocument.wordprocessingml.document"},
                {"image/png"},
                {"image/jpeg"}
        };
    }

    @Test(dataProvider = "documentedContentTypes")
    public void allowsEachDocumentedContentType(String contentType) {
        assertTrue(AttachmentPolicy.isAllowedContentType(contentType));
    }

    @Test
    public void rejectsUnknownOrNullContentType() {
        assertFalse(AttachmentPolicy.isAllowedContentType("application/zip"));
        assertFalse(AttachmentPolicy.isAllowedContentType(null));
    }

    @Test
    public void isAllowedContentTypeTrimsWhitespace() {
        assertTrue(AttachmentPolicy.isAllowedContentType("  image/png  "));
    }

    @Test
    public void defaultsToTenMegabytesWhenNoDpdpAcceleratorXmlIsAvailable() {
        assertEquals(AttachmentPolicy.getMaxSizeBytes(), 10L * 1024 * 1024);
    }
}
