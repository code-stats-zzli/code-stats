// 文件: src/main/java/com/omni/framework/spring/mvc/argument/ModelAttributeArgumentResolver.java
package com.omni.framework.spring.mvc.argument;

import com.omni.framework.spring.annotation.ModelAttribute;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;
import com.omni.framework.util.ReflectionUtil;
import com.omni.framework.util.TypeConverter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Map;

public class ModelAttributeArgumentResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(Parameter parameter) {
        return parameter.isAnnotationPresent(ModelAttribute.class);
    }

    @Override
    public Object resolveArgument(Parameter parameter,
                                  HttpServletRequest request,
                                  HttpServletResponse response,
                                  Map<String, String> pathVariables) {
        Class<?> type = parameter.getType();

        if (Map.class.isAssignableFrom(type)) {
            return request.getParameters();
        }

        Object bean = ReflectionUtil.newInstance(type);
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            String value = request.getParameter(field.getName());
            if (value == null) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object converted = TypeConverter.convert(value, field.getType());
                field.set(bean, converted);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "ModelAttribute bind failed: " + field.getName(), e);
            }
        }
        return bean;
    }
}