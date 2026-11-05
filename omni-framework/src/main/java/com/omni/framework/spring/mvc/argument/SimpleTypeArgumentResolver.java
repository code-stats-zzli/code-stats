// 文件: src/main/java/com/omni/framework/spring/mvc/argument/SimpleTypeArgumentResolver.java
package com.omni.framework.spring.mvc.argument;

import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;
import com.omni.framework.util.TypeConverter;

import java.lang.reflect.Parameter;
import java.util.Map;

public class SimpleTypeArgumentResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(Parameter parameter) {
        return parameter.getAnnotations().length == 0 && isSimpleType(parameter.getType());
    }

    @Override
    public Object resolveArgument(Parameter parameter,
                                  HttpServletRequest request,
                                  HttpServletResponse response,
                                  Map<String, String> pathVariables) {
        String value = request.getParameter(parameter.getName());
        if (value == null) {
            return getDefaultForType(parameter.getType());
        }
        try {
            return TypeConverter.convert(value, parameter.getType());
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Cannot convert parameter '" + parameter.getName() + "'", e);
        }
    }

    private boolean isSimpleType(Class<?> type) {
        return type == String.class
                || type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == boolean.class || type == Boolean.class
                || type == double.class || type == Double.class
                || type == float.class || type == Float.class
                || type == short.class || type == Short.class
                || type == byte.class || type == Byte.class
                || type == char.class || type == Character.class;
    }

    private Object getDefaultForType(Class<?> type) {
        if (type == String.class) return "";
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == boolean.class) return false;
        if (type == double.class) return 0.0D;
        if (type == float.class) return 0.0F;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return '\0';
        return null;
    }
}