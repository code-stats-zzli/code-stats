// 文件: src/main/java/com/omni/framework/spring/mvc/argument/AbstractNamedValueArgumentResolver.java
package com.omni.framework.spring.mvc.argument;

import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;
import com.omni.framework.util.TypeConverter;

import java.lang.reflect.Parameter;
import java.util.Map;

public abstract class AbstractNamedValueArgumentResolver implements HandlerMethodArgumentResolver {

    protected abstract String getName(Parameter parameter);

    protected abstract boolean isRequired(Parameter parameter);

    protected abstract String getDefaultValue(Parameter parameter);

    protected abstract Object resolveValue(String name,
                                           Parameter parameter,
                                           HttpServletRequest request,
                                           HttpServletResponse response,
                                           Map<String, String> pathVariables) throws Exception;

    @Override
    public final Object resolveArgument(Parameter parameter,
                                        HttpServletRequest request,
                                        HttpServletResponse response,
                                        Map<String, String> pathVariables) throws Exception {
        String name = getName(parameter);
        if (name == null || name.trim().isEmpty()) {
            name = parameter.getName();
        }

        Object value = resolveValue(name, parameter, request, response, pathVariables);

        if (value == null || "".equals(value)) {
            String defaultValue = getDefaultValue(parameter);
            if (defaultValue != null && !defaultValue.isEmpty()) {
                value = defaultValue;
            }
        }

        if (value == null || "".equals(value)) {
            if (isRequired(parameter)) {
                throw new IllegalArgumentException("Missing required parameter: " + name);
            }
            return getDefaultForType(parameter.getType());
        }

        return convert(value, parameter.getType());
    }

    protected Object convert(Object value, Class<?> targetType) {
        if (value == null) {
            return getDefaultForType(targetType);
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        if (targetType.isEnum()) {
            @SuppressWarnings("unchecked")
            Class<? extends Enum> enumType = (Class<? extends Enum>) targetType;
            return Enum.valueOf(enumType, value.toString());
        }
        try {
            return TypeConverter.convert(value.toString(), targetType);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Cannot convert value '" + value + "' to type " + targetType.getName(), e);
        }
    }

    protected Object getDefaultForType(Class<?> type) {
        if (type == String.class) {
            return "";
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0.0D;
        }
        if (type == float.class) {
            return 0.0F;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }
}