/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 */
package org.wso2.dpdp.accelerator.common.util;

import org.testng.Assert;
import org.testng.annotations.Test;

public class QueryBuilderUtilsTest {

    @Test
    public void escapeLikePatternEscapesWildcardsAndTheEscapeCharacterItself() {
        Assert.assertEquals(QueryBuilderUtils.escapeLikePattern("100%_off!"), "100!%!_off!!");
        Assert.assertEquals(QueryBuilderUtils.escapeLikePattern(null), "");
    }

    @Test
    public void buildCaseInsensitiveContainsPatternLowercasesTrimsAndWrapsWithWildcards() {
        Assert.assertEquals(QueryBuilderUtils.buildCaseInsensitiveContainsPattern("  Acme_Corp  "),
                "%acme!_corp%");
        Assert.assertEquals(QueryBuilderUtils.buildCaseInsensitiveContainsPattern(null), "%%");
    }

    @Test
    public void buildEscapedLikePredicateAppendsLikeWithEscapeClause() {
        Assert.assertEquals(QueryBuilderUtils.buildEscapedLikePredicate("LOWER(NAME)"),
                "LOWER(NAME) LIKE ? ESCAPE '!'");
    }
}
