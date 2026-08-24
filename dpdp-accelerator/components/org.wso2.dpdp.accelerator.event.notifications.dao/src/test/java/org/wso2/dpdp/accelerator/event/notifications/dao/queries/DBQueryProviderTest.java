/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 */
package org.wso2.dpdp.accelerator.event.notifications.dao.queries;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.lang.reflect.Method;

public class DBQueryProviderTest {

    @Test
    public void queryProvidersExposeNonEmptyQueries() throws Exception {
        for (EventNotificationCommonDBQueries provider : new EventNotificationCommonDBQueries[] {
                new EventNotificationCommonDBQueries(), new EventNotificationMysqlDBQueries(),
                new EventNotificationPostgresDBQueries(), new EventNotificationSqliteDBQueries() }) {
            for (Method method : EventNotificationCommonDBQueries.class.getMethods()) {
                if (method.getDeclaringClass() == EventNotificationCommonDBQueries.class
                        && method.getReturnType() == String.class && method.getParameterCount() == 0) {
                    Assert.assertFalse(((String) method.invoke(provider)).trim().isEmpty(), method.getName());
                }
            }
        }
        Assert.assertTrue(EventNotificationQueryFactory.getQueryProvider("postgres")
                instanceof EventNotificationPostgresDBQueries);
        Assert.assertTrue(EventNotificationQueryFactory.getQueryProvider("sqlite")
                instanceof EventNotificationSqliteDBQueries);
    }
}
