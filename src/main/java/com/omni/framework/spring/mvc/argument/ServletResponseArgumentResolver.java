// 文件: src/main/java/com/omni/framework/spring/mvc/argument/ServletResponseArgumentResolver.java
package com.omni.framework.spring.mvc.argument;

import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;

import java.lang.reflect.Parameter;
import java.util.Map;

public class ServletResponseArgumentResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(Parameter parameter) {
        return HttpServletResponse.class.isAssignableFrom(parameter.getType());
    }

    @Override
    public Object resolveArgument(Parameter parameter,
                                  HttpServletRequest request,
                                  HttpServletResponse response,
                                  Map<String, String> pathVariables) {
        return response;
    }
}