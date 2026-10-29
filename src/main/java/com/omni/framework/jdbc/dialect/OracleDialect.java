package com.omni.framework.jdbc.dialect;

import com.omni.framework.spring.annotation.Component;

@Component
public class OracleDialect implements SqlDialect {
    @Override public String getName() { return "oracle"; }

    @Override
    public String buildPageSql(String baseSql, int offset, int pageSize) {
        int upper = offset + pageSize;
        return "SELECT * FROM (SELECT t.*, ROWNUM rn FROM (" + baseSql
                + ") t WHERE ROWNUM <= " + upper + ") WHERE rn > " + offset;
    }

    @Override
    public String quote(String identifier) {
        return "\"" + identifier + "\"";
    }
}