// ======== D:\space\git\CodeStats\src\main\java\com\omni\framework\jdbc\JdbcTemplate.java ========
package com.omni.framework.jdbc;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC 模板类（模板模式）
 * 提供通用的数据库操作模板，包括查询（单条/列表）、更新（增删改）、结果集映射等。
 * 子类可通过重写特定方法扩展行为（如连接获取、异常处理）。
 */
public class JdbcTemplate {

    private static final Logger log = LoggerFactory.getLogger(JdbcTemplate.class);

    // ==================== 核心模板方法 ====================

    /**
     * 执行查询，返回结果列表（通过 RowMapper 映射）
     */
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) throws IntrospectionException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        return query(sql, 0, rowMapper, args);
    }

    /**
     * 执行查询，支持超时设置
     */
    public <T> List<T> query(String sql, int timeoutSeconds, RowMapper<T> rowMapper, Object... args) throws IntrospectionException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        List<T> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (timeoutSeconds > 0) {
                ps.setQueryTimeout(timeoutSeconds);
            }
            setParameters(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(rowMapper.mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Query failed: {}", sql, e);
            throw new RuntimeException("Query failed: " + sql, e);
        }
        return results;
    }

    /**
     * 查询单条记录，若无结果返回 null，多于一条抛异常
     */
    public <T> T queryOne(String sql, RowMapper<T> rowMapper, Object... args) throws IntrospectionException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        return queryOne(sql, 0, rowMapper, args);
    }

    public <T> T queryOne(String sql, int timeoutSeconds, RowMapper<T> rowMapper, Object... args) throws IntrospectionException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        List<T> list = query(sql, timeoutSeconds, rowMapper, args);
        if (list.isEmpty()) return null;
        if (list.size() > 1) {
            throw new RuntimeException("Expected one result, got " + list.size());
        }
        return list.get(0);
    }

    /**
     * 执行更新（INSERT / UPDATE / DELETE），返回影响行数
     */
    public int update(String sql, Object... args) {
        return update(sql, 0, args);
    }

    public int update(String sql, int timeoutSeconds, Object... args) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (timeoutSeconds > 0) {
                ps.setQueryTimeout(timeoutSeconds);
            }
            setParameters(ps, args);
            return ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Update failed: {}", sql, e);
            throw new RuntimeException("Update failed: " + sql, e);
        }
    }

    /**
     * 批量更新
     */
    public int[] batchUpdate(String sql, List<Object[]> batchArgs) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] args : batchArgs) {
                setParameters(ps, args);
                ps.addBatch();
            }
            return ps.executeBatch();
        } catch (SQLException e) {
            log.error("Batch update failed: {}", sql, e);
            throw new RuntimeException("Batch update failed", e);
        }
    }

    // ==================== 扩展方法：对象映射 ====================

    /**
     * 查询返回实体对象列表（自动映射列名->属性，下划线转驼峰）
     */
    public <T> List<T> queryForList(String sql, Class<T> clazz, Object... args) throws IntrospectionException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        return query(sql, beanRowMapper(clazz), args);
    }

    /**
     * 查询返回单个实体对象
     */
    public <T> T queryForObject(String sql, Class<T> clazz, Object... args) throws IntrospectionException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        return queryOne(sql, beanRowMapper(clazz), args);
    }

    /**
     * 查询返回 Map（列名 -> 值），适用于动态列
     */
    public List<Map<String, Object>> queryForMapList(String sql, Object... args) throws IntrospectionException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        return query(sql, rs -> {
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            Map<String, Object> row = new HashMap<>();
            for (int i = 1; i <= colCount; i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            return row;
        }, args);
    }

    // ==================== RowMapper 工具方法 ====================

    /**
     * 通用 Bean 属性 RowMapper：自动将列名（下划线转驼峰）映射到 JavaBean 属性
     */
    public static <T> RowMapper<T> beanRowMapper(Class<T> clazz) {
        return rs -> {
            T bean = clazz.getDeclaredConstructor().newInstance();
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            PropertyDescriptor[] props = Introspector.getBeanInfo(clazz).getPropertyDescriptors();
            Map<String, Method> writeMethods = new HashMap<>();
            for (PropertyDescriptor pd : props) {
                if (pd.getWriteMethod() != null) {
                    writeMethods.put(pd.getName(), pd.getWriteMethod());
                }
            }
            for (int i = 1; i <= columnCount; i++) {
                String columnName = meta.getColumnLabel(i);
                String propName = underscoreToCamel(columnName);
                Method setter = writeMethods.get(propName);
                if (setter != null) {
                    Object value = rs.getObject(i);
                    if (value != null && setter.getParameterTypes()[0].isAssignableFrom(value.getClass())) {
                        setter.invoke(bean, value);
                    } else if (value != null) {
                        // 类型转换简单处理，可扩展
                        setter.invoke(bean, convert(value, setter.getParameterTypes()[0]));
                    }
                }
            }
            return bean;
        };
    }

    /**
     * 单列 RowMapper（返回第一列值）
     */
    public static <T> RowMapper<T> singleColumnRowMapper(Class<T> requiredType) {
        return rs -> {
            Object val = rs.getObject(1);
            if (val == null) return null;
            if (requiredType.isAssignableFrom(val.getClass())) {
                return requiredType.cast(val);
            }
            return convert(val, requiredType);
        };
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取数据库连接（可被子类重写以支持不同数据源）
     */
    protected Connection getConnection() throws SQLException {
        return DataSourceUtils.getConnection();
    }

    /**
     * 设置 PreparedStatement 参数
     */
    protected void setParameters(PreparedStatement ps, Object[] args) throws SQLException {
        if (args == null) return;
        for (int i = 0; i < args.length; i++) {
            ps.setObject(i + 1, args[i]);
        }
    }

    /**
     * 简单类型转换（仅支持基本类型和字符串）
     */
    @SuppressWarnings("unchecked")
    private static <T> T convert(Object value, Class<T> targetType) {
        if (value == null) return null;
        if (targetType.isAssignableFrom(value.getClass())) return (T) value;
        if (targetType == String.class) return (T) value.toString();
        if (targetType == Integer.class || targetType == int.class) return (T) Integer.valueOf(value.toString());
        if (targetType == Long.class || targetType == long.class) return (T) Long.valueOf(value.toString());
        if (targetType == Boolean.class || targetType == boolean.class) return (T) Boolean.valueOf(value.toString());
        if (targetType == Double.class || targetType == double.class) return (T) Double.valueOf(value.toString());
        if (targetType == Float.class || targetType == float.class) return (T) Float.valueOf(value.toString());
        throw new IllegalArgumentException("Unsupported conversion: " + value.getClass() + " -> " + targetType);
    }

    private static String underscoreToCamel(String name) {
        if (name == null || name.isEmpty()) return name;
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (char c : name.toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else {
                sb.append(nextUpper ? Character.toUpperCase(c) : c);
                nextUpper = false;
            }
        }
        return sb.toString();
    }

    // ==================== 函数式接口 ====================

    @FunctionalInterface
    public interface RowMapper<T> {
        T mapRow(ResultSet rs) throws SQLException, IntrospectionException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException;
    }
}