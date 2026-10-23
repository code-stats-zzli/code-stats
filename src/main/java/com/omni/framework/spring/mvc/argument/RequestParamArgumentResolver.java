// 文件: src/main/java/com/omni/framework/spring/mvc/argument/RequestParamArgumentResolver.java
package com.omni.framework.spring.mvc.argument;

import com.omni.framework.spring.annotation.RequestParam;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;

import java.lang.reflect.Parameter;
import java.util.Map;

public class RequestParamArgumentResolver extends AbstractNamedValueArgumentResolver {
    @Override
    public boolean supportsParameter(Parameter parameter) {
        return parameter.isAnnotationPresent(RequestParam.class)
                && !Map.class.isAssignableFrom(parameter.getType());
    }

    @Override
    protected String getName(Parameter parameter) {
        return parameter.getAnnotation(RequestParam.class).value();
    }

    @Override
    protected boolean isRequired(Parameter parameter) {
        return parameter.getAnnotation(RequestParam.class).required();
    }

    @Override
    protected String getDefaultValue(Parameter parameter) {
        return parameter.getAnnotation(RequestParam.class).defaultValue();
    }

    @Override
    protected Object resolveValue(String name,
                                  Parameter parameter,
                                  HttpServletRequest request,
                                  HttpServletResponse response,
                                  Map<String, String> pathVariables) {
        return request.getParameter(name);
    }
}