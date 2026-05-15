package com.omni.framework.mybatis.proxy;

import com.omni.framework.jdbc.JdbcTemplate;
import com.omni.framework.mybatis.annotation.Select;


import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;

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
        Select select = method.getAnnotation(Select.class);
        if (select == null) {
            throw new UnsupportedOperationException("Mapper method must have @Select: " + method);
        }
        String sql = select.value();
        int timeout = select.timeout();          // 获取注解中的超时设置
        Object[] paramValues = args == null ? new Object[0] : args;
        Class<?> returnType = method.getReturnType();

        if (returnType == List.class) {
            return jdbcTemplate.query(sql, timeout, rs -> rs.getObject(1).toString(), paramValues);
        } else {
            return jdbcTemplate.queryOne(sql, timeout, rs -> {
                if (returnType == String.class) return rs.getString(1);
                else if (returnType == Integer.class) return rs.getInt(1);
                else return rs.getObject(1).toString();
            }, paramValues);
        }
    }
}