// 文件: src/main/java/com/omni/framework/spring/mvc/support/HandlerExecutionChain.java
package com.omni.framework.spring.mvc.support;

import java.util.Map;

public class HandlerExecutionChain {
    private final HandlerMethod handlerMethod;
    private final Map<String, String> pathVariables;

    public HandlerExecutionChain(HandlerMethod handlerMethod, Map<String, String> pathVariables) {
        this.handlerMethod = handlerMethod;
        this.pathVariables = pathVariables;
    }

    public HandlerMethod getHandlerMethod() {
        return handlerMethod;
    }

    public Map<String, String> getPathVariables() {
        return pathVariables;
    }
}