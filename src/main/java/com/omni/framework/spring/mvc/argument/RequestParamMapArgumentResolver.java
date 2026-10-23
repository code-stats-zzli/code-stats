// 文件: src/main/java/com/omni/framework/spring/mvc/argument/RequestParamMapArgumentResolver.java
package com.omni.framework.spring.mvc.argument;

import com.omni.framework.spring.annotation.RequestParam;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;

import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

public class RequestParamMapArgumentResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(Parameter parameter) {
        return parameter.isAnnotationPresent(RequestParam.class)
                && Map.class.isAssignableFrom(parameter.getType());
    }

    @Override
    public Object resolveArgument(Parameter parameter,
                                  HttpServletRequest request,
                                  HttpServletResponse response,
                                  Map<String, String> pathVariables) {
        return new HashMap<>(request.getParameters());
    }
}