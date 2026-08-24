/**
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 */
package org.wso2.dpdp.accelerator.event.notifications.dao.queries;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class QueryBuilderTest {

    @Test
    public void testSharedResultAndLikeUtility() {
        SubscriptionQueryBuilder subscriptionBuilder = new SubscriptionQueryBuilder("org1")
                .setSearch("a_%");
        QueryResult subscriptionResult = subscriptionBuilder.buildSelectQuery(null);
        QueryResult eventResult = new EventQueryBuilder("org1")
                .setSearch("a_%")
                .buildSelectQuery("SELECT 1 WHERE 1 = 1", null);

        assertTrue(subscriptionResult.getSql().contains("LIKE ?"));
        assertTrue(eventResult.getSql().contains("LIKE ?"));
        assertEquals(QueryBuilderUtils.escapeLikePattern("a_%"), "a\\_\\%");
        assertEquals(EventQueryBuilder.escapeLikePattern("a_%"),
                SubscriptionQueryBuilder.escapeLikePattern("a_%"));
    }

    @Test
    public void testTopicBuilderBuildsParameterizedQueries() {
        TopicQueryBuilder builder = new TopicQueryBuilder("org1")
                .setStatus("active")
                .setSearch("accounts")
                .setSort("-name");
        QueryResult result = builder.buildSelectQuery(" ORDER BY NAME DESC LIMIT ? OFFSET ?");

        assertTrue(result.getSql().contains("WHERE ORG_ID = ?"));
        assertTrue(result.getSql().contains("LOWER(STATUS) = LOWER(?)"));
        assertEquals(result.getParameters().size(), 5);
        assertEquals(builder.resolveSortColumn(), "NAME DESC");
    }
}
