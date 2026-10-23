// 文件: src/main/java/com/omni/framework/spring/mvc/argument/RequestAttributeArgumentResolver.java
package com.omni.framework.spring.mvc.argument;

import com.omni.framework.spring.annotation.RequestAttribute;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;

import java.lang.reflect.Parameter;
import java.util.Map;

public class RequestAttributeArgumentResolver extends AbstractNamedValueArgumentResolver {
    @Override
    public boolean supportsParameter(Parameter parameter) {
        return parameter.isAnnotationPresent(RequestAttribute.class);
    }

    @Override
    protected String getName(Parameter parameter) {
        return parameter.getAnnotation(RequestAttribute.class).value();
    }

    @Override
    protected boolean isRequired(Parameter parameter) {
        return parameter.getAnnotation(RequestAttribute.class).required();
    }

    @Override
    protected String getDefaultValue(Parameter parameter) {
        return parameter.getAnnotation(RequestAttribute.class).defaultValue();
    }

    @Override
    protected Object resolveValue(String name,
                                  Parameter parameter,
                                  HttpServletRequest request,
                                  HttpServletResponse response,
                                  Map<String, String> pathVariables) {
        return request.getAttribute(name);
    }
}