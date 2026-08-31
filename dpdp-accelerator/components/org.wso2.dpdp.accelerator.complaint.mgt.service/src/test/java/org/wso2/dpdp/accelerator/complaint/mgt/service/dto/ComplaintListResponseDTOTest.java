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

package org.wso2.dpdp.accelerator.complaint.mgt.service.dto;

import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

class ComplaintListResponseDTOTest {

    @Test
    void allArgsConstructorPopulatesEveryField() {
        List<ComplaintRecordDTO> data = List.of(new ComplaintRecordDTO());
        PageMetadataDTO metadata = new PageMetadataDTO(1, 0, 1, 10);

        ComplaintListResponseDTO bean = new ComplaintListResponseDTO(data, metadata);

        assertSame(data, bean.getData());
        assertSame(metadata, bean.getMetadata());
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        ComplaintListResponseDTO bean = new ComplaintListResponseDTO();
        List<ComplaintRecordDTO> data = List.of();
        PageMetadataDTO metadata = new PageMetadataDTO();
        bean.setData(data);
        bean.setMetadata(metadata);

        assertEquals(data, bean.getData());
        assertSame(metadata, bean.getMetadata());
    }
}
