/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 */
package org.wso2.dpdp.accelerator.common.persistence;

import java.sql.Connection;

/** Executes an operation with a centrally acquired JDBC connection. */
@FunctionalInterface
public interface ConnectionCallback<T> {

    T execute(Connection connection) throws Exception;
}
