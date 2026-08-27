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

package org.wso2.dpdp.accelerator.event.notifications.dao;

import org.h2.tools.RunScript;
import org.testng.annotations.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/** Verifies that the shipped H2 schema retains production database invariants. */
public class H2SchemaParityTest {

    private static final String SCHEMA_PATH =
            "accelerators/dpdp-is/carbon-home/dbscripts/dpdp-accelerator/event-notification/h2.sql";

    @Test
    public void shippedSchemaEnforcesDeliveryIntegrityAndIndexes() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:enf_schema_parity;DB_CLOSE_DELAY=-1");
                Reader schema = Files.newBufferedReader(findSchema(), StandardCharsets.UTF_8)) {
            RunScript.execute(connection, schema);

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO TOPIC "
                        + "(TOPIC_ID, ORG_ID, NAME, STATUS, INITIATED_BY) "
                        + "VALUES ('topic-1', 'org-1', 'accounts', 'active', 'USER')");
                statement.executeUpdate("INSERT INTO EVENT "
                        + "(EVENT_ID, ORG_ID, GROUP_ID, TOPIC_ID, PAYLOAD) "
                        + "VALUES ('event-1', 'org-1', 'group-1', 'topic-1', '{}')");
                statement.executeUpdate("INSERT INTO SUBSCRIPTION "
                        + "(SUBSCRIPTION_ID, ORG_ID, GROUP_ID, TOPIC_ID, STATUS, PURPOSE_FILTER_MODE, "
                        + "PURPOSE_SET_HASH, DELIVERY_MODE, UPDATED_AT) VALUES "
                        + "('sub-1', 'org-1', 'group-1', 'topic-1', 'active', 'all', '', 'webhook', "
                        + "TIMESTAMP '2000-01-01 00:00:00')");
                statement.executeUpdate("INSERT INTO WEBHOOK_DELIVERY "
                        + "(DELIVERY_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS, UPDATED_AT) VALUES "
                        + "('delivery-1', 'sub-1', 'event-1', 'pending', TIMESTAMP '2000-01-01 00:00:00')");

                expectThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO WEBHOOK_DELIVERY "
                        + "(DELIVERY_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS) "
                        + "VALUES ('delivery-2', 'sub-1', 'event-1', 'pending')"));
                expectThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO WEBHOOK_DELIVERY "
                        + "(DELIVERY_ID, SUBSCRIPTION_ID, EVENT_ID, STATUS) "
                        + "VALUES ('delivery-3', 'sub-1', 'missing-event', 'pending')"));
                expectThrows(SQLException.class, () -> statement.executeUpdate("UPDATE WEBHOOK_DELIVERY "
                        + "SET STATUS = 'invalid' WHERE DELIVERY_ID = 'delivery-1'"));

                statement.executeUpdate("UPDATE SUBSCRIPTION SET STATUS = 'pending' "
                        + "WHERE SUBSCRIPTION_ID = 'sub-1'");
                statement.executeUpdate("UPDATE WEBHOOK_DELIVERY SET STATUS = 'in_flight' "
                        + "WHERE DELIVERY_ID = 'delivery-1'");
            }

            assertTimestampUpdated(connection, "SUBSCRIPTION", "SUBSCRIPTION_ID", "sub-1");
            assertTimestampUpdated(connection, "WEBHOOK_DELIVERY", "DELIVERY_ID", "delivery-1");
            assertRequiredIndexes(connection);
        }
    }

    private static void assertTimestampUpdated(Connection connection, String table, String idColumn,
            String id) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT UPDATED_AT FROM " + table
                        + " WHERE " + idColumn + " = '" + id + "'")) {
            assertTrue(result.next());
            assertTrue(result.getTimestamp(1).after(Timestamp.valueOf("2000-01-01 00:00:00")));
        }
    }

    private static void assertRequiredIndexes(Connection connection) throws SQLException {
        Set<String> indexes = new HashSet<>();
        for (String table : new String[] {"EVENT", "EVENT_PURPOSE", "SUBSCRIPTION", "SUBSCRIPTION_PURPOSE",
                "WEBHOOK_DELIVERY", "POLL_DELIVERY"}) {
            try (ResultSet result = connection.getMetaData().getIndexInfo(null, null, table, false, false)) {
                while (result.next()) {
                    indexes.add(result.getString("INDEX_NAME"));
                }
            }
        }
        for (String required : new String[] {"IDX_EVENT_ORG_GROUP_TOPIC_CREATED", "IDX_EVENT_PURPOSE_NAME",
                "IDX_SUB_STATUS_UPDATED", "IDX_SUB_MATCHING", "IDX_SUB_PURPOSE_NAME", "IDX_EDP_SUB",
                "IDX_EDPL_SUB"}) {
            assertTrue(indexes.contains(required), "Missing H2 index: " + required);
        }
    }

    private static Path findSchema() {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(SCHEMA_PATH);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate " + SCHEMA_PATH);
    }
}
