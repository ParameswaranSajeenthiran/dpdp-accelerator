/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 */
package org.wso2.dpdp.accelerator.common.persistence;

import java.sql.Connection;

/**
 * Operation executed within a connection-owned transaction.
 *
 * @param <T> result type
 */
@FunctionalInterface
public interface TransactionCallback<T> {

    T execute(Connection connection) throws Exception;
}
