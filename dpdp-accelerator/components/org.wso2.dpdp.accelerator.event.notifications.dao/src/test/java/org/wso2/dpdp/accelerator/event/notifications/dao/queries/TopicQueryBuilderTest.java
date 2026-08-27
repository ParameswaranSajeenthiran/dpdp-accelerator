/**
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 * <p>
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 */
package org.wso2.dpdp.accelerator.event.notifications.dao.queries;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TopicQueryBuilderTest {

    @Test
    public void testBaseQueriesAndSorting() {
        TopicQueryBuilder builder = new TopicQueryBuilder("org1").setSort("-name");

        QueryResult select = builder.buildSelectQuery(" ORDER BY NAME DESC LIMIT ? OFFSET ?");
        QueryResult count = builder.buildCountQuery();

        assertTrue(select.getSql().contains("WHERE ORG_ID = ?"));
        assertTrue(select.getSql().contains("ORDER BY NAME DESC LIMIT ? OFFSET ?"));
        assertEquals(select.getParameters().size(), 1);
        assertEquals(select.getParameters().get(0), "org1");
        assertTrue(count.getSql().startsWith("SELECT COUNT(*) FROM TOPIC"));
        assertEquals(builder.resolveSortColumn(), "NAME DESC");
    }

    @Test
    public void testStatusAndEscapedSearchFilters() {
        TopicQueryBuilder builder = new TopicQueryBuilder("org1")
                .setStatus("active")
                .setSearch("accounts_%");

        QueryResult result = builder.buildSelectQuery(null);

        assertTrue(result.getSql().contains("LOWER(STATUS) = LOWER(?)"));
        assertTrue(result.getSql().contains("LOWER(TOPIC_ID) LIKE ? ESCAPE '!'"));
        assertEquals(result.getParameters().size(), 5);
        assertEquals(result.getParameters().get(0), "org1");
        assertEquals(result.getParameters().get(1), "active");
        assertEquals(result.getParameters().get(2), "%accounts!_!%%");
    }

    @Test
    public void testDefaultAndStatusSorts() {
        assertEquals(new TopicQueryBuilder("org").setSort("status").resolveSortColumn(), "STATUS ASC");
        assertEquals(new TopicQueryBuilder("org").setSort("-status").resolveSortColumn(), "STATUS DESC");
        assertEquals(new TopicQueryBuilder("org").setSort("invalid").resolveSortColumn(), "NAME ASC");
    }
}
