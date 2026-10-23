// 文件: src/main/java/com/omni/framework/spring/mvc/handler/AbstractHandlerMethodInvoker.java
package com.omni.framework.spring.mvc.handler;

import com.omni.framework.spring.mvc.support.HandlerMethod;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;

import java.util.Map;

public abstract class AbstractHandlerMethodInvoker {

    public final void invoke(HandlerMethod handlerMethod,
                             HttpServletRequest request,
                             HttpServletResponse response,
                             Map<String, String> pathVariables) {
        try {
            Object[] args = resolveArguments(handlerMethod, request, response, pathVariables);
            Object result = invokeHandlerMethod(handlerMethod, args);
            handleReturnValue(result, handlerMethod, response);
        } catch (Throwable ex) {
            handleException(request, response, handlerMethod, ex);
        }
    }

    protected abstract Object[] resolveArguments(HandlerMethod handlerMethod,
                                                 HttpServletRequest request,
                                                 HttpServletResponse response,
                                                 Map<String, String> pathVariables) throws Exception;

    protected abstract Object invokeHandlerMethod(HandlerMethod handlerMethod,
                                                  Object[] args) throws Exception;

    protected abstract void handleReturnValue(Object returnValue,
                                              HandlerMethod handlerMethod,
                                              HttpServletResponse response) throws Exception;

    protected abstract void handleException(HttpServletRequest request,
                                            HttpServletResponse response,
                                            HandlerMethod handlerMethod,
                                            Throwable ex);
}