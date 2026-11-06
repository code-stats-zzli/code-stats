package com.omni.framework.mybatis.proxy;

import com.omni.framework.jdbc.JdbcTemplate;
import com.omni.framework.mybatis.BaseMapper;
import com.omni.framework.mybatis.annotation.*;
import com.omni.framework.mybatis.parse.SqlParser;

import java.lang.reflect.*;
import java.util.*;

/**
 * Mapper 动态代理。
 * <p>★ L3-01：支持 BaseMapper 继承——{@code selectById / selectAll / insert / updateById / deleteById}
 * 自动生成 SQL（需子接口用 {@code @Table} 或类名推断表名，{@code @Id} 标注主键）。</p>
 */
public class MapperProxy implements InvocationHandler {
    private final JdbcTemplate jdbcTemplate;

    public MapperProxy(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) return method.invoke(this, args);

        // ==================== BaseMapper 继承方法 ====================
        if (method.getDeclaringClass() == BaseMapper.class) {
            return invokeBaseMapper(method, args);
        }

        // ==================== 显式 @Select/@Insert/@Update/@Delete ====================
        String sql = null;
        int timeout = 10;
        boolean isQuery = false;
        if (method.isAnnotationPresent(Select.class)) {
            sql = method.getAnnotation(Select.class).value();
            timeout = method.getAnnotation(Select.class).timeout();
            isQuery = true;
        } else if (method.isAnnotationPresent(Insert.class)) {
            sql = method.getAnnotation(Insert.class).value();
            timeout = method.getAnnotation(Insert.class).timeout();
        } else if (method.isAnnotationPresent(Update.class)) {
            sql = method.getAnnotation(Update.class).value();
            timeout = method.getAnnotation(Update.class).timeout();
        } else if (method.isAnnotationPresent(Delete.class)) {
            sql = method.getAnnotation(Delete.class).value();
            timeout = method.getAnnotation(Delete.class).timeout();
        } else {
            throw new UnsupportedOperationException("Mapper method must have @Select/@Insert/@Update/@Delete: " + method);
        }

        SqlParser.ParsedSql parsed = SqlParser.parse(sql);
        String parsedSql = parsed.getSql();
        List<String> paramNames = parsed.getParamNames();
        Object[] paramValues = buildParamValues(method, args, paramNames);
        Class<?> returnType = method.getReturnType();

        if (!isQuery) {
            int rows = jdbcTemplate.update(parsedSql, timeout, paramValues);
            if (returnType == int.class || returnType == Integer.class) return rows;
            if (returnType == void.class) return null;
            throw new UnsupportedOperationException("Insert/Update/Delete must return int or void");
        }

        if (returnType == List.class) {
            Type genericReturnType = method.getGenericReturnType();
            Class<?> elementType = Object.class;
            if (genericReturnType instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) genericReturnType;
                Type[] actualArgs = pt.getActualTypeArguments();
                if (actualArgs.length == 1 && actualArgs[0] instanceof Class) {
                    elementType = (Class<?>) actualArgs[0];
                }
            }
            if (isSimpleType(elementType)) {
                return jdbcTemplate.query(parsedSql, timeout, rs -> rs.getObject(1).toString(), paramValues);
            }
            return jdbcTemplate.queryForList(parsedSql, elementType, paramValues);
        } else {
            if (isSimpleType(returnType)) {
                return jdbcTemplate.queryOne(parsedSql, timeout, rs -> {
                    if (returnType == String.class) return rs.getString(1);
                    else if (returnType == Integer.class) return rs.getInt(1);
                    else if (returnType == Long.class) return rs.getLong(1);
                    else return rs.getObject(1);
                }, paramValues);
            }
            return jdbcTemplate.queryForObject(parsedSql, returnType, paramValues);
        }
    }

    // ==================== BaseMapper 自动 SQL ====================

    private Object invokeBaseMapper(Method method, Object[] args) throws Exception {
        String methodName = method.getName();
        // 从子接口拿实体类型
        Class<?> entityType = resolveEntityType(method.getDeclaringClass());
        String table = resolveTableName(entityType);
        String idColumn = resolveIdColumn(entityType);

        switch (methodName) {
            case "selectById": {
                String sql = "SELECT * FROM " + table + " WHERE " + idColumn + " = ?";
                return jdbcTemplate.queryForObject(sql, entityType, args[0]);
            }
            case "selectAll": {
                String sql = "SELECT * FROM " + table;
                return jdbcTemplate.queryForList(sql, entityType);
            }
            case "insert": {
                Map<String, Object> fields = beanToMap(args[0]);
                if (fields.isEmpty()) throw new IllegalArgumentException("entity 字段为空");
                List<String> cols = new ArrayList<>(fields.keySet());
                String colList = String.join(", ", cols);
                String placeholders = String.join(", ", Collections.nCopies(cols.size(), "?"));
                String sql = "INSERT INTO " + table + " (" + colList + ") VALUES (" + placeholders + ")";
                return jdbcTemplate.update(sql, fields.values().toArray());
            }
            case "updateById": {
                Map<String, Object> fields = beanToMap(args[0]);
                Object idVal = fields.remove(idColumn);
                if (idVal == null) throw new IllegalArgumentException("主键 " + idColumn + " 不能为空");
                if (fields.isEmpty()) return 0;
                StringBuilder setSb = new StringBuilder();
                for (String col : fields.keySet()) {
                    if (setSb.length() > 0) setSb.append(", ");
                    setSb.append(col).append(" = ?");
                }
                List<Object> params = new ArrayList<>(fields.values());
                params.add(idVal);
                String sql = "UPDATE " + table + " SET " + setSb + " WHERE " + idColumn + " = ?";
                return jdbcTemplate.update(sql, params.toArray());
            }
            case "deleteById": {
                String sql = "DELETE FROM " + table + " WHERE " + idColumn + " = ?";
                return jdbcTemplate.update(sql, args[0]);
            }
            default:
                throw new UnsupportedOperationException("BaseMapper method not supported: " + methodName);
        }
    }

    private Class<?> resolveEntityType(Class<?> mapperInterface) {
        Type[] types = mapperInterface.getGenericInterfaces();
        for (Type t : types) {
            if (t instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) t;
                if (pt.getRawType() == BaseMapper.class) {
                    Type arg = pt.getActualTypeArguments()[0];
                    if (arg instanceof Class) return (Class<?>) arg;
                }
            }
        }
        throw new IllegalStateException("无法解析 " + mapperInterface.getName() + " 的实体类型");
    }

    private String resolveTableName(Class<?> entityType) {
        try {
            Class<?> tableAnno = Class.forName("com.omni.framework.mybatis.annotation.Table");
            @SuppressWarnings("unchecked")
            java.lang.annotation.Annotation anno = entityType.getAnnotation((Class) tableAnno);
            if (anno != null) {
                Method value = tableAnno.getMethod("value");
                String v = (String) value.invoke(anno);
                if (v != null && !v.isEmpty()) return v;
            }
        } catch (Exception ignored) {}
        return camelToSnake(entityType.getSimpleName());
    }

    private String resolveIdColumn(Class<?> entityType) {
        for (Field f : entityType.getDeclaredFields()) {
            try {
                Class<?> idAnno = Class.forName("com.omni.framework.mybatis.annotation.Id");
                if (f.isAnnotationPresent((Class) idAnno)) {
                    return camelToSnake(f.getName());
                }
            } catch (Exception ignored) {}
        }
        return "id";
    }

    private static String camelToSnake(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c) && i > 0) sb.append('_');
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private Map<String, Object> beanToMap(Object bean) throws IllegalAccessException {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Field f : bean.getClass().getDeclaredFields()) {
            int mod = f.getModifiers();
            if (Modifier.isStatic(mod) || Modifier.isTransient(mod)) continue;
            f.setAccessible(true);
            Object v = f.get(bean);
            if (v == null) continue;
            map.put(camelToSnake(f.getName()), v);
        }
        return map;
    }

    // ==================== 显式注解的辅助 ====================

    private Object[] buildParamValues(Method method, Object[] args, List<String> paramNames) {
        if (args == null || args.length == 0 || paramNames.isEmpty()) return new Object[0];
        Map<String, Object> paramMap = new HashMap<>();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Param p = parameters[i].getAnnotation(Param.class);
            if (p != null) {
                paramMap.put(p.value(), args[i]);
            } else if (args.length == 1 && !isSimpleType(parameters[i].getType())) {
                paramMap.putAll(safeObjectToMap(args[i]));
            }
        }
        if (paramMap.isEmpty() && args.length == paramNames.size()) return args;

        Object[] result = new Object[paramNames.size()];
        for (int i = 0; i < paramNames.size(); i++) {
            String name = paramNames.get(i);
            Object val = paramMap.get(name);
            if (val == null && name.matches("\\d+")) {
                int idx = Integer.parseInt(name);
                if (idx < args.length) val = args[idx];
            }
            result[i] = val;
        }
        return result;
    }

    private Map<String, Object> safeObjectToMap(Object obj) {
        Map<String, Object> map = new HashMap<>();
        if (obj == null) return map;
        for (Field f : obj.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            try { map.put(f.getName(), f.get(obj)); } catch (IllegalAccessException ignored) {}
        }
        return map;
    }

    private boolean isSimpleType(Class<?> clazz) {
        return clazz == String.class
                || clazz == int.class || clazz == Integer.class
                || clazz == long.class || clazz == Long.class
                || clazz == boolean.class || clazz == Boolean.class
                || clazz == double.class || clazz == Double.class
                || clazz == float.class || clazz == Float.class
                || clazz == byte.class || clazz == Byte.class
                || clazz == short.class || clazz == Short.class;
    }
}