// 文件: src/main/java/com/omni/framework/spring/mvc/argument/ArgumentResolverComposite.java
package com.omni.framework.spring.mvc.argument;

import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;

import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ArgumentResolverComposite {
    private final List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();

    public ArgumentResolverComposite addResolver(HandlerMethodArgumentResolver resolver) {
        if (resolver != null) {
            resolvers.add(resolver);
        }
        return this;
    }

    public boolean supportsParameter(Parameter parameter) {
        for (HandlerMethodArgumentResolver resolver : resolvers) {
            if (resolver.supportsParameter(parameter)) {
                return true;
            }
        }
        return false;
    }

    public Object resolveArgument(Parameter parameter,
                                  HttpServletRequest request,
                                  HttpServletResponse response,
                                  Map<String, String> pathVariables) throws Exception {
        for (HandlerMethodArgumentResolver resolver : resolvers) {
            if (resolver.supportsParameter(parameter)) {
                return resolver.resolveArgument(parameter, request, response, pathVariables);
            }
        }
        return null;
    }
}