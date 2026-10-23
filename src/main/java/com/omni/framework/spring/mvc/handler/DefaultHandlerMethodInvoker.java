// 文件: src/main/java/com/omni/framework/spring/mvc/handler/DefaultHandlerMethodInvoker.java
package com.omni.framework.spring.mvc.handler;

import com.omni.framework.spring.mvc.argument.ArgumentResolverComposite;
import com.omni.framework.spring.mvc.exception.HandlerExceptionResolver;
import com.omni.framework.spring.mvc.returnvalue.ReturnValueHandlerComposite;
import com.omni.framework.spring.mvc.support.HandlerMethod;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;

import java.lang.reflect.Parameter;
import java.util.Map;

public class DefaultHandlerMethodInvoker extends AbstractHandlerMethodInvoker {
    private final ArgumentResolverComposite argumentResolverComposite;
    private final ReturnValueHandlerComposite returnValueHandlerComposite;
    private final HandlerExceptionResolver exceptionResolver;

    public DefaultHandlerMethodInvoker(ArgumentResolverComposite argumentResolverComposite,
                                       ReturnValueHandlerComposite returnValueHandlerComposite,
                                       HandlerExceptionResolver exceptionResolver) {
        this.argumentResolverComposite = argumentResolverComposite;
        this.returnValueHandlerComposite = returnValueHandlerComposite;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected Object[] resolveArguments(HandlerMethod handlerMethod,
                                        HttpServletRequest request,
                                        HttpServletResponse response,
                                        Map<String, String> pathVariables) throws Exception {
        Parameter[] parameters = handlerMethod.getMethod().getParameters();
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (argumentResolverComposite.supportsParameter(parameter)) {
                args[i] = argumentResolverComposite.resolveArgument(
                        parameter, request, response, pathVariables);
            } else {
                args[i] = null;
            }
        }
        return args;
    }

    @Override
    protected Object invokeHandlerMethod(HandlerMethod handlerMethod, Object[] args) throws Exception {
        return handlerMethod.getMethod().invoke(handlerMethod.getController(), args);
    }

    @Override
    protected void handleReturnValue(Object returnValue,
                                     HandlerMethod handlerMethod,
                                     HttpServletResponse response) throws Exception {
        returnValueHandlerComposite.handleReturnValue(returnValue, handlerMethod, response);
    }

    @Override
    protected void handleException(HttpServletRequest request,
                                   HttpServletResponse response,
                                   HandlerMethod handlerMethod,
                                   Throwable ex) {
        exceptionResolver.resolveException(request, response, handlerMethod, ex);
    }
}