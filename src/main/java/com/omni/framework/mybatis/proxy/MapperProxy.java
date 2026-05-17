// ======== com/omni/framework/mybatis/proxy/MapperProxy.java ========
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
        Annotation sqlAnnotation = null;
        if (method.isAnnotationPresent(Select.class)) {
            sql = method.getAnnotation(Select.class).value();
            timeout = method.getAnnotation(Select.class).timeout();
            isQuery = true;
            sqlAnnotation = method.getAnnotation(Select.class);
        } else if (method.isAnnotationPresent(Insert.class)) {
            sql = method.getAnnotation(Insert.class).value();
            timeout = method.getAnnotation(Insert.class).timeout();
            sqlAnnotation = method.getAnnotation(Insert.class);
        } else if (method.isAnnotationPresent(Update.class)) {
            sql = method.getAnnotation(Update.class).value();
            timeout = method.getAnnotation(Update.class).timeout();
            sqlAnnotation = method.getAnnotation(Update.class);
        } else if (method.isAnnotationPresent(Delete.class)) {
            sql = method.getAnnotation(Delete.class).value();
            timeout = method.getAnnotation(Delete.class).timeout();
            sqlAnnotation = method.getAnnotation(Delete.class);
        } else {
            throw new UnsupportedOperationException("Mapper method must have @Select/@Insert/@Update/@Delete: " + method);
        }

        // 1. 解析 SQL 中的 #{xxx} 占位符，得到参数名列表
        SqlParser.ParsedSql parsed = SqlParser.parse(sql);
        String parsedSql = parsed.getSql();
        List<String> paramNames = parsed.getParamNames();

        // 2. 构建参数值列表（按占位符顺序）
        Object[] paramValues = buildParamValues(method, args, paramNames);

        // 3. 根据方法返回类型执行相应操作
        Class<?> returnType = method.getReturnType();

        // 处理增删改（返回 int 或 void）
        if (!isQuery) {
            int rows = jdbcTemplate.update(parsedSql, paramValues);
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
                }
            }
            // 如果是简单类型（String/Number/Boolean）则调用原有 query 逻辑
            if (isSimpleType(elementType)) {
                // 兼容老逻辑：返回第一列值的 List<String>
                return jdbcTemplate.query(parsedSql, timeout, rs -> rs.getObject(1).toString(), paramValues);
            } else {
                // 复杂对象，使用 Bean 映射
                JdbcTemplate.RowMapper<?> mapper = JdbcTemplate.beanRowMapper(elementType);
                return jdbcTemplate.queryForList(parsedSql, returnType, paramValues);
            }
        } else {
            // 单个对象（实体或简单类型）
            if (isSimpleType(returnType)) {
                // 简单类型，返回第一列值
                return jdbcTemplate.queryOne(parsedSql, timeout, rs -> {
                    if (returnType == String.class) return rs.getString(1);
                    else if (returnType == Integer.class) return rs.getInt(1);
                    else if (returnType == Long.class) return rs.getLong(1);
                    else return rs.getObject(1);
                }, paramValues);
            } else {
                // 实体对象
                JdbcTemplate.RowMapper<?> mapper = JdbcTemplate.beanRowMapper(returnType);
                return jdbcTemplate.queryForObject(parsedSql, returnType, paramValues);
            }
        }
    }

    private Object[] buildParamValues(Method method, Object[] args, List<String> paramNames) {
        if (args == null || args.length == 0) {
            return new Object[0];
        }
        // 如果 SQL 中没有占位符，直接返回空数组
        if (paramNames.isEmpty()) {
            return new Object[0];
        }

        // 构建参数名 -> 实际值的映射
        Map<String, Object> paramMap = new HashMap<>();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter p = parameters[i];
            Object arg = args[i];
            Param paramAnn = p.getAnnotation(Param.class);
            if (paramAnn != null) {
                paramMap.put(paramAnn.value(), arg);
            } else {
                // 如果只有一个参数且不是简单类型，则将其视为参数对象，解析其属性
                if (args.length == 1 && !isSimpleType(p.getType())) {
                    // 将对象的所有属性值放入 map（属性名作为 key）
                    paramMap.putAll(objectToMap(arg));
                } else {
                    // 无名参数，使用 index 作为备选（按位置绑定，占位符必须按顺序）
                    // 这里为了支持 #{0} #{1} 风格，暂不支持，建议使用 @Param
                    // 对于没有 @Param 且多参数的情况，直接按顺序绑定
                }
            }
        }

        // 如果 paramMap 为空，尝试按顺序绑定（arg 数组）
        if (paramMap.isEmpty() && paramNames.isEmpty() == false) {
            // 若方法参数数量等于占位符数量且未指定 @Param，则按顺序
            if (args.length == paramNames.size()) {
                return args;
            }
        }

        // 根据 paramNames 顺序取值
        Object[] result = new Object[paramNames.size()];
        for (int i = 0; i < paramNames.size(); i++) {
            String name = paramNames.get(i);
            Object val = paramMap.get(name);
            // 如果没找到，尝试从方法参数顺序中获取（支持 #{0} #{1}）
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
            } catch (IllegalAccessException e) {
                // ignore
            }
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