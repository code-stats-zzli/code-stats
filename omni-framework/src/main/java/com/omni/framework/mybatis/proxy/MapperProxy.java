// 文件路径: src/main/java/com/omni/framework/mybatis/proxy/MapperProxy.java
package com.omni.framework.mybatis.proxy;

import com.omni.framework.jdbc.JdbcTemplate;
import com.omni.framework.mybatis.annotation.*;
import com.omni.framework.mybatis.parse.SqlParser;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

public class MapperProxy implements InvocationHandler {
    private final JdbcTemplate jdbcTemplate;

    public MapperProxy(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        // 获取注解 SQL
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

        // 1. 解析 SQL 中的 #{xxx} 占位符
        SqlParser.ParsedSql parsed = SqlParser.parse(sql);
        String parsedSql = parsed.getSql();
        List<String> paramNames = parsed.getParamNames();

        // 2. 构建参数值列表
        Object[] paramValues = buildParamValues(method, args, paramNames);

        // 3. 根据方法返回类型执行操作
        Class<?> returnType = method.getReturnType();

        // 处理增删改
        if (!isQuery) {
            int rows = jdbcTemplate.update(parsedSql, timeout, paramValues);
            if (returnType == int.class || returnType == Integer.class) {
                return rows;
            } else if (returnType == void.class) {
                return null;
            } else {
                throw new UnsupportedOperationException("Insert/Update/Delete must return int or void");
            }
        }

        // 处理查询（Select）
        if (returnType == List.class) {
            // 获取 List 的泛型实际类型
            Type genericReturnType = method.getGenericReturnType();
            Class<?> elementType = Object.class;
            if (genericReturnType instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) genericReturnType;
                Type[] actualArgs = pt.getActualTypeArguments();
                if (actualArgs.length == 1 && actualArgs[0] instanceof Class) {
                    elementType = (Class<?>) actualArgs[0];
                } else if (actualArgs.length == 1 && actualArgs[0] instanceof ParameterizedType) {
                    // 处理 List<Map<String, Object>> 等复杂泛型，回退到 Object
                    elementType = Object.class;
                }
            }
            // 简单类型（String/Number/Boolean）使用单列映射
            if (isSimpleType(elementType)) {
                return jdbcTemplate.query(parsedSql, timeout, rs -> rs.getObject(1).toString(), paramValues);
            } else {
                // ★ 关键修复：传入 elementType，而不是 returnType
                return jdbcTemplate.queryForList(parsedSql, elementType, paramValues);
            }
        } else {
            // 单个对象（实体或简单类型）
            if (isSimpleType(returnType)) {
                return jdbcTemplate.queryOne(parsedSql, timeout, rs -> {
                    if (returnType == String.class) return rs.getString(1);
                    else if (returnType == Integer.class) return rs.getInt(1);
                    else if (returnType == Long.class) return rs.getLong(1);
                    else return rs.getObject(1);
                }, paramValues);
            } else {
                return jdbcTemplate.queryForObject(parsedSql, returnType, paramValues);
            }
        }
    }

    private Object[] buildParamValues(Method method, Object[] args, List<String> paramNames) {
        if (args == null || args.length == 0) {
            return new Object[0];
        }
        if (paramNames.isEmpty()) {
            return new Object[0];
        }

        Map<String, Object> paramMap = new HashMap<>();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter p = parameters[i];
            Object arg = args[i];
            Param paramAnn = p.getAnnotation(Param.class);
            if (paramAnn != null) {
                paramMap.put(paramAnn.value(), arg);
            } else {
                if (args.length == 1 && !isSimpleType(p.getType())) {
                    paramMap.putAll(objectToMap(arg));
                }
            }
        }

        if (paramMap.isEmpty() && !paramNames.isEmpty()) {
            if (args.length == paramNames.size()) {
                return args;
            }
        }

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

    private Map<String, Object> objectToMap(Object obj) {
        Map<String, Object> map = new HashMap<>();
        if (obj == null) return map;
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field f : fields) {
            f.setAccessible(true);
            try {
                map.put(f.getName(), f.get(obj));
            } catch (IllegalAccessException ignored) {}
        }
        return map;
    }

    private boolean isSimpleType(Class<?> clazz) {
        return clazz == String.class ||
                clazz == int.class || clazz == Integer.class ||
                clazz == long.class || clazz == Long.class ||
                clazz == boolean.class || clazz == Boolean.class ||
                clazz == double.class || clazz == Double.class ||
                clazz == float.class || clazz == Float.class ||
                clazz == byte.class || clazz == Byte.class ||
                clazz == short.class || clazz == Short.class;
    }
}