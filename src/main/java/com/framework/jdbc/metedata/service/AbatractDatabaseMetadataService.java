package com.framework.jdbc.metedata.service;

import com.framework.jdbc.DataSourceUtils;
import com.framework.jdbc.metedata.datamodel.ColumnDef;
import com.framework.jdbc.metedata.datamodel.DataModel;
import com.framework.jdbc.metedata.datamodel.Row;
import com.framework.jdbc.metedata.datamodel.SimpleRow;
import com.framework.jdbc.metedata.model.ColumnInfo;
import com.framework.jdbc.metedata.model.DatabaseInfo;
import com.framework.jdbc.metedata.model.TableInfo;
import com.framework.jdbc.metedata.util.MetadataConverter;
import com.framework.spring.config.ConfigLoader;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class AbatractDatabaseMetadataService implements DatabaseMetadataService {

    // ==================== 基础通用实现（保持不变） ====================

    @Override
    public List<DatabaseInfo> getDatabases() throws Exception {
        List<DatabaseInfo> databases = new ArrayList<>();
        try (Connection conn = DataSourceUtils.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getCatalogs()) {
                while (rs.next()) {
                    databases.add(MetadataConverter.toDatabaseInfo(rs));
                }
            }
        }
        return databases;
    }

    @Override
    public List<TableInfo> getTables(String catalog, String schemaPattern, String tableNamePattern) throws Exception {
        List<TableInfo> tables = new ArrayList<>();
        try (Connection conn = DataSourceUtils.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getTables(catalog, schemaPattern, tableNamePattern, null)) {
                while (rs.next()) {
                    tables.add(MetadataConverter.toTableInfo(rs));
                }
            }
        }
        return tables;
    }

    @Override
    public List<ColumnInfo> getColumns(String catalog, String schemaPattern, String tableName) throws Exception {
        List<ColumnInfo> columns = new ArrayList<>();
        try (Connection conn = DataSourceUtils.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getColumns(catalog, schemaPattern, tableName, null)) {
                while (rs.next()) {
                    columns.add(MetadataConverter.toColumnInfo(rs));
                }
            }
        }
        return columns;
    }

    // ==================== 默认数据库便捷实现（保持不变） ====================

    @Override
    public DatabaseInfo getDefaultDatabase() {
        String dbName = resolveDefaultCatalog();
        return new DatabaseInfo(dbName);
    }

    @Override
    public List<TableInfo> getTables() throws Exception {
        String catalog = resolveDefaultCatalog();
        return getTables(catalog, null, "%");
    }

    @Override
    public List<ColumnInfo> getColumns(String tableName) throws Exception {
        String catalog = resolveDefaultCatalog();
        return getColumns(catalog, null, tableName);
    }

    // ==================== 新增：DataModel 转换实现 ====================

    @Override
    public DataModel getDatabasesAsDataModel() throws Exception {
        List<ColumnDef> columnDefs = new ArrayList<>();
        columnDefs.add(new ColumnDef("DatabaseName", "VARCHAR", Types.VARCHAR, true, "数据库名", 255, 0));

        List<Row> rows = new ArrayList<>();
        // 先构建一个只包含列定义的临时模型，供 SimpleRow 使用
        DataModel tempModel = new DataModel(columnDefs, rows);
        for (DatabaseInfo db : getDatabases()) {
            List<Object> values = new ArrayList<>();
            values.add(db.getName());
            rows.add(new SimpleRow(tempModel, values));
        }
        return new DataModel(columnDefs, rows);
    }

    @Override
    public DataModel getTablesAsDataModel(String catalog, String schemaPattern, String tableNamePattern) throws Exception {
        List<ColumnDef> columnDefs = new ArrayList<>();
        columnDefs.add(new ColumnDef("TABLE_CAT", "VARCHAR", Types.VARCHAR, true, "数据库名", 255, 0));
        columnDefs.add(new ColumnDef("TABLE_SCHEM", "VARCHAR", Types.VARCHAR, true, "模式名", 255, 0));
        columnDefs.add(new ColumnDef("TABLE_NAME", "VARCHAR", Types.VARCHAR, false, "表名", 255, 0));
        columnDefs.add(new ColumnDef("TABLE_TYPE", "VARCHAR", Types.VARCHAR, false, "表类型", 50, 0));
        columnDefs.add(new ColumnDef("REMARKS", "VARCHAR", Types.VARCHAR, true, "备注", 500, 0));

        List<Row> rows = new ArrayList<>();
        DataModel tempModel = new DataModel(columnDefs, rows);
        for (TableInfo table : getTables(catalog, schemaPattern, tableNamePattern)) {
            List<Object> values = new ArrayList<>();
            values.add(table.getCatalog());
            values.add(table.getSchema());
            values.add(table.getName());
            values.add(table.getType());
            values.add(table.getRemarks());
            rows.add(new SimpleRow(tempModel, values));
        }
        return new DataModel(columnDefs, rows);
    }

    @Override
    public DataModel getColumnsAsDataModel(String catalog, String schemaPattern, String tableName) throws Exception {
        List<ColumnDef> columnDefs = new ArrayList<>();
        columnDefs.add(new ColumnDef("COLUMN_NAME", "VARCHAR", Types.VARCHAR, false, "列名", 255, 0));
        columnDefs.add(new ColumnDef("DATA_TYPE", "INTEGER", Types.INTEGER, false, "SQL类型", 10, 0));
        columnDefs.add(new ColumnDef("TYPE_NAME", "VARCHAR", Types.VARCHAR, false, "数据库类型名", 50, 0));
        columnDefs.add(new ColumnDef("COLUMN_SIZE", "INTEGER", Types.INTEGER, false, "列大小", 10, 0));
        columnDefs.add(new ColumnDef("DECIMAL_DIGITS", "INTEGER", Types.INTEGER, true, "小数位数", 10, 0));
        columnDefs.add(new ColumnDef("NULLABLE", "BOOLEAN", Types.BOOLEAN, false, "是否可为空", 0, 0));
        columnDefs.add(new ColumnDef("REMARKS", "VARCHAR", Types.VARCHAR, true, "备注", 500, 0));
        columnDefs.add(new ColumnDef("COLUMN_DEF", "VARCHAR", Types.VARCHAR, true, "默认值", 255, 0));
        columnDefs.add(new ColumnDef("ORDINAL_POSITION", "INTEGER", Types.INTEGER, false, "列序号", 10, 0));
        columnDefs.add(new ColumnDef("IS_AUTOINCREMENT", "BOOLEAN", Types.BOOLEAN, false, "是否自增", 0, 0));
        columnDefs.add(new ColumnDef("IS_GENERATEDCOLUMN", "BOOLEAN", Types.BOOLEAN, false, "是否生成列", 0, 0));

        List<Row> rows = new ArrayList<>();
        DataModel tempModel = new DataModel(columnDefs, rows);
        for (ColumnInfo col : getColumns(catalog, schemaPattern, tableName)) {
            List<Object> values = new ArrayList<>();
            values.add(col.getColumnName());
            values.add(col.getDataType());
            values.add(col.getTypeName());
            values.add(col.getColumnSize());
            values.add(col.getDecimalDigits());
            values.add(col.isNullable());
            values.add(col.getRemarks());
            values.add(col.getDefaultValue());
            values.add(col.getOrdinalPosition());
            values.add(col.isAutoIncrement());
            values.add(col.isGenerated());
            rows.add(new SimpleRow(tempModel, values));
        }
        return new DataModel(columnDefs, rows);
    }

    // 便捷方法：使用默认数据库

    @Override
    public DataModel getTablesAsDataModel() throws Exception {
        String catalog = resolveDefaultCatalog();
        return getTablesAsDataModel(catalog, null, "%");
    }

    @Override
    public DataModel getColumnsAsDataModel(String tableName) throws Exception {
        String catalog = resolveDefaultCatalog();
        return getColumnsAsDataModel(catalog, null, tableName);
    }

    // ==================== 工具方法（保持不变） ====================

    private String resolveDefaultCatalog() {
        String url = ConfigLoader.getProperty("db.url");
        if (url == null || url.isEmpty()) {
            return "";
        }
        int slashIndex = url.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < url.length() - 1) {
            String dbPart = url.substring(slashIndex + 1);
            int queryIndex = dbPart.indexOf('?');
            if (queryIndex >= 0) {
                return dbPart.substring(0, queryIndex);
            } else {
                return dbPart;
            }
        }
        return "";
    }
}