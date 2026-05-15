package com.codestats.framework.jdbc.metedata.util;



import com.codestats.framework.jdbc.metedata.model.ColumnInfo;
import com.codestats.framework.jdbc.metedata.model.DatabaseInfo;
import com.codestats.framework.jdbc.metedata.model.TableInfo;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 将 JDBC 元数据 ResultSet 转换为领域模型对象。
 */
public class MetadataConverter {

    public static DatabaseInfo toDatabaseInfo(ResultSet rs) throws SQLException {
        // TABLE_CAT 即为 catalog（数据库名）
        String name = rs.getString("TABLE_CAT");
        return new DatabaseInfo(name);
    }

    public static TableInfo toTableInfo(ResultSet rs) throws SQLException {
        String catalog = rs.getString("TABLE_CAT");
        String schema = rs.getString("TABLE_SCHEM");
        String name = rs.getString("TABLE_NAME");
        String type = rs.getString("TABLE_TYPE");
        String remarks = rs.getString("REMARKS");
        return new TableInfo(catalog, schema, name, type, remarks);
    }

    public static ColumnInfo toColumnInfo(ResultSet rs) throws SQLException {
        String columnName = rs.getString("COLUMN_NAME");
        int dataType = rs.getInt("DATA_TYPE");
        String typeName = rs.getString("TYPE_NAME");
        int columnSize = rs.getInt("COLUMN_SIZE");
        int decimalDigits = rs.getInt("DECIMAL_DIGITS");
        boolean nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
        String remarks = rs.getString("REMARKS");
        String defaultValue = rs.getString("COLUMN_DEF");
        int ordinalPosition = rs.getInt("ORDINAL_POSITION");
        boolean autoIncrement = "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT"));
        boolean generated = "YES".equalsIgnoreCase(rs.getString("IS_GENERATEDCOLUMN"));
        return new ColumnInfo(columnName, dataType, typeName, columnSize, decimalDigits,
                nullable, remarks, defaultValue, ordinalPosition, autoIncrement, generated);
    }
}