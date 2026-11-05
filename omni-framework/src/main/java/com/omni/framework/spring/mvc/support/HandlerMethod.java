// 文件: src/main/java/com/omni/framework/spring/mvc/support/HandlerMethod.java
package com.omni.framework.spring.mvc.support;

import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Pattern;

public class HandlerMethod {
    private final Pattern pattern;
    private final List<String> varNames;
    private final Object controller;
    private final Method method;
    private final boolean responseBody;
    private final String pathPattern;

    public HandlerMethod(Pattern pattern, List<String> varNames,
                         Object controller, Method method,
                         boolean responseBody, String pathPattern) {
        this.pattern = pattern;
        this.varNames = varNames;
        this.controller = controller;
        this.method = method;
        this.responseBody = responseBody;
        this.pathPattern = pathPattern;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public List<String> getVarNames() {
        return varNames;
    }

    public Object getController() {
        return controller;
    }

    public Method getMethod() {
        return method;
    }

    public boolean isResponseBody() {
        return responseBody;
    }

    public String getPathPattern() {
        return pathPattern;
    }
}