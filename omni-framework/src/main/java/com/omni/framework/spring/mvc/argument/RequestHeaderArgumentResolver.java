// 文件: src/main/java/com/omni/framework/spring/mvc/argument/RequestHeaderArgumentResolver.java
package com.omni.framework.spring.mvc.argument;

import com.omni.framework.spring.annotation.RequestHeader;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;

import java.lang.reflect.Parameter;
import java.util.Map;

public class RequestHeaderArgumentResolver extends AbstractNamedValueArgumentResolver {
    @Override
    public boolean supportsParameter(Parameter parameter) {
        return parameter.isAnnotationPresent(RequestHeader.class);
    }

    @Override
    protected String getName(Parameter parameter) {
        return parameter.getAnnotation(RequestHeader.class).value();
    }

    @Override
    protected boolean isRequired(Parameter parameter) {
        return parameter.getAnnotation(RequestHeader.class).required();
    }

    @Override
    protected String getDefaultValue(Parameter parameter) {
        return parameter.getAnnotation(RequestHeader.class).defaultValue();
    }

    @Override
    protected Object resolveValue(String name,
                                  Parameter parameter,
                                  HttpServletRequest request,
                                  HttpServletResponse response,
                                  Map<String, String> pathVariables) {
        return request.getHeader(name);
    }
}