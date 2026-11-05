// 文件: src/main/java/com/omni/framework/spring/mvc/argument/HandlerMethodArgumentResolver.java
package com.omni.framework.spring.mvc.argument;

import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;

import java.lang.reflect.Parameter;
import java.util.Map;

public interface HandlerMethodArgumentResolver {
    boolean supportsParameter(Parameter parameter);

    Object resolveArgument(Parameter parameter,
                           HttpServletRequest request,
                           HttpServletResponse response,
                           Map<String, String> pathVariables) throws Exception;
}