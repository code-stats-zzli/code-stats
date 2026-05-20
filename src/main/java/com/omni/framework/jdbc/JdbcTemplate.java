// ======== D:\work\git\CodeStats\src\main\java\com\omni\framework\jdbc\JdbcTemplate.java ========
package com.omni.framework.jdbc;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.util.ReflectionUtil;
import com.omni.framework.util.TypeConverter;  // ★ 导入新工具类

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
 */
public class JdbcTemplate {

    private static final Logger log = LoggerFactory.getLogger(JdbcTemplate.class);

    // ==================== 核心模板方法 ====================

    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) throws IntrospectionException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        return query(sql, 0, rowMapper, args);
    }

    public <T> List<T> query(String sql, int timeoutSeconds, RowMapper<T> rowMapper, Object... args) throws IntrospectionException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        List<T> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (timeoutSeconds > 0) ps.setQueryTimeout(timeoutSeconds);
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

    public <T> T queryOne(String sql, RowMapper<T> rowMapper, Object... args) throws IntrospectionException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        return queryOne(sql, 0, rowMapper, args);
    }

    public <T> T queryOne(String sql, int timeoutSeconds, RowMapper<T> rowMapper, Object... args) throws IntrospectionException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        List<T> list = query(sql, timeoutSeconds, rowMapper, args);
        if (list.isEmpty()) return null;
        if (list.size() > 1) throw new RuntimeException("Expected one result, got " + list.size());
        return list.get(0);
    }

    public int update(String sql, Object... args) {
        return update(sql, 0, args);
    }

    public int update(String sql, int timeoutSeconds, Object... args) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (timeoutSeconds > 0) ps.setQueryTimeout(timeoutSeconds);
            setParameters(ps, args);
            return ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Update failed: {}", sql, e);
            throw new RuntimeException("Update failed: " + sql, e);
        }
    }

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

    // ==================== 扩展方法 ====================

    public <T> List<T> queryForList(String sql, Class<T> clazz, Object... args) throws IntrospectionException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        return query(sql, beanRowMapper(clazz), args);
    }

    public <T> T queryForObject(String sql, Class<T> clazz, Object... args) throws IntrospectionException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        return queryOne(sql, beanRowMapper(clazz), args);
    }

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

    public static <T> RowMapper<T> beanRowMapper(Class<T> clazz) {
        return rs -> {
            T bean = ReflectionUtil.newInstance(clazz);
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
                        // ★ 使用 TypeConverter 进行转换
                        Object converted = TypeConverter.convert(value, setter.getParameterTypes()[0]);
                        setter.invoke(bean, converted);
                    }
                }
            }
            return bean;
        };
    }

    public static <T> RowMapper<T> singleColumnRowMapper(Class<T> requiredType) {
        return rs -> {
            Object val = rs.getObject(1);
            if (val == null) return null;
            if (requiredType.isAssignableFrom(val.getClass())) {
                return requiredType.cast(val);
            }
            // ★ 使用 TypeConverter 进行转换
            return TypeConverter.convert(val, requiredType);
        };
    }

    // ==================== 辅助方法 ====================

    protected Connection getConnection() throws SQLException {
        return DataSourceUtils.getConnection();
    }

    protected void setParameters(PreparedStatement ps, Object[] args) throws SQLException {
        if (args == null) return;
        for (int i = 0; i < args.length; i++) {
            ps.setObject(i + 1, args[i]);
        }
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