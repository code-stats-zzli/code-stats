package com.omni.framework.jdbc.metedata.service;

import com.omni.framework.pool.SimpleDataSource;

import java.sql.Connection;

/**
 * 基于指定数据源的动态元数据服务
 */
public class DynamicDatabaseMetadataService extends AbatractDatabaseMetadataService {

    private final SimpleDataSource dataSource;

    public DynamicDatabaseMetadataService(SimpleDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected Connection getConnection() throws Exception {
        return dataSource.getConnection();
    }

    // 以下方法复用父类逻辑，但父类中的 getConnection() 已被覆盖，无需额外修改
    // 如果需要优化性能，可以重写具体方法，但父类已足够
}