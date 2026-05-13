package com.framework.jdbc.metedata.service;

import com.framework.jdbc.metedata.datamodel.DataModel;
import com.framework.jdbc.metedata.model.ColumnInfo;
import com.framework.jdbc.metedata.model.DatabaseInfo;
import com.framework.jdbc.metedata.model.TableInfo;

import java.util.List;

public interface DatabaseMetadataService {

    // ==================== 通用查询（保留原有方法） ====================
    List<DatabaseInfo> getDatabases() throws Exception;
    List<TableInfo> getTables(String catalog, String schemaPattern, String tableNamePattern) throws Exception;
    List<ColumnInfo> getColumns(String catalog, String schemaPattern, String tableName) throws Exception;

    // ==================== 默认数据库便捷方法（保留原有方法） ====================
    DatabaseInfo getDefaultDatabase() throws Exception;
    List<TableInfo> getTables() throws Exception;
    List<ColumnInfo> getColumns(String tableName) throws Exception;

    // ==================== 新增：以 DataModel 形式返回结果 ====================

    /**
     * 获取所有数据库列表，以 DataModel 返回。
     * 列定义：DatabaseName
     */
    DataModel getDatabasesAsDataModel() throws Exception;

    /**
     * 获取指定数据库中的表信息，以 DataModel 返回。
     * 列定义：TABLE_CAT, TABLE_SCHEM, TABLE_NAME, TABLE_TYPE, REMARKS
     */
    DataModel getTablesAsDataModel(String catalog, String schemaPattern, String tableNamePattern) throws Exception;

    /**
     * 获取指定表的列信息，以 DataModel 返回。
     * 列定义：COLUMN_NAME, DATA_TYPE, TYPE_NAME, COLUMN_SIZE, DECIMAL_DIGITS,
     *        NULLABLE, REMARKS, COLUMN_DEF, ORDINAL_POSITION, IS_AUTOINCREMENT, IS_GENERATEDCOLUMN
     */
    DataModel getColumnsAsDataModel(String catalog, String schemaPattern, String tableName) throws Exception;

    /**
     * 获取默认数据库中的所有表，以 DataModel 返回。
     */
    DataModel getTablesAsDataModel() throws Exception;

    /**
     * 获取默认数据库中指定表的列信息，以 DataModel 返回。
     */
    DataModel getColumnsAsDataModel(String tableName) throws Exception;
}