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

package org.wso2.dpdp.accelerator.consent.extensions.dao.queries;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class ConsentExpiryQueryFactoryTest {

    @Test
    public void oracleAndSqlServerUseOffsetFetchForBothDueExpiryQueries() {

        assertOffsetFetchQueries(new ConsentExpiryOracleDBQueries());
        assertOffsetFetchQueries(new ConsentExpirySqlServerDBQueries());
    }

    @Test
    public void factorySelectsOracleAndSqlServerProviders() {

        assertTrue(ConsentExpiryQueryFactory.getQueryProvider("Oracle") instanceof ConsentExpiryOracleDBQueries);
        assertTrue(ConsentExpiryQueryFactory.getQueryProvider("Microsoft SQL Server")
                instanceof ConsentExpirySqlServerDBQueries);
        assertTrue(ConsentExpiryQueryFactory.getQueryProvider("mssql") instanceof ConsentExpirySqlServerDBQueries);
    }

    @Test
    public void resolvesH2ProviderForH2Dialect() {

        assertTrue(ConsentExpiryQueryFactory.getQueryProvider("H2") instanceof ConsentExpiryH2DBQueries);
    }

    @Test
    public void fallsBackToAnsiBaselineForUnrecognizedDialects() {

        assertEquals(ConsentExpiryQueryFactory.getQueryProvider("Derby").getClass(), ConsentExpiryDBQueries.class);
    }

    @Test
    public void blankAndNullDialectsResolveToTheH2Provider() {

        assertSame(ConsentExpiryQueryFactory.getQueryProvider((String) null), ConsentExpiryQueryFactory.getQueryProvider());
        assertSame(ConsentExpiryQueryFactory.getQueryProvider("  "), ConsentExpiryQueryFactory.getQueryProvider());
        assertTrue(ConsentExpiryQueryFactory.getQueryProvider() instanceof ConsentExpiryH2DBQueries);
    }

    private void assertOffsetFetchQueries(ConsentExpiryDBQueries queries) {

        assertOffsetFetch(queries.getFindDueExpiriesQuery());
        assertOffsetFetch(queries.getFindDueExpiriesAfterQuery());
    }

    private void assertOffsetFetch(String query) {

        assertTrue(query.contains("ORDER BY EXPIRY_TIME ASC, CONSENT_ID ASC"));
        assertTrue(query.endsWith("OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY"));
        assertFalse(query.contains("LIMIT ?"));
    }
}
