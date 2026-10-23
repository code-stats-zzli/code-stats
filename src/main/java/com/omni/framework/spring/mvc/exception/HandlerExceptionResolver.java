// 文件: src/main/java/com/omni/framework/spring/mvc/exception/HandlerExceptionResolver.java
package com.omni.framework.spring.mvc.exception;

import com.omni.framework.spring.mvc.support.HandlerMethod;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;

public interface HandlerExceptionResolver {
    boolean resolveException(HttpServletRequest request,
                             HttpServletResponse response,
                             HandlerMethod handlerMethod,
                             Throwable ex);
}