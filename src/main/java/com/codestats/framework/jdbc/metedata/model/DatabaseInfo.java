package com.codestats.framework.jdbc.metedata.model;

/**
 * 数据库基本信息。
 */
public class DatabaseInfo {
    private final String name;   // 在 MySQL 中对应数据库名

    public DatabaseInfo(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "Database{name='" + name + "'}";
    }
}