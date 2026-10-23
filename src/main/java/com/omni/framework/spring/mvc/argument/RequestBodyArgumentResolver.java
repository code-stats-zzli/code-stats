// 文件: src/main/java/com/omni/framework/spring/mvc/argument/RequestBodyArgumentResolver.java
package com.omni.framework.spring.mvc.argument;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.framework.spring.annotation.RequestBody;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;

import java.lang.reflect.Parameter;
import java.util.Map;

public class RequestBodyArgumentResolver implements HandlerMethodArgumentResolver {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean supportsParameter(Parameter parameter) {
        return parameter.isAnnotationPresent(RequestBody.class);
    }

    @Override
    public Object resolveArgument(Parameter parameter,
                                  HttpServletRequest request,
                                  HttpServletResponse response,
                                  Map<String, String> pathVariables) throws Exception {
        RequestBody requestBody = parameter.getAnnotation(RequestBody.class);
        String body = request.getBody();

        if ((body == null || body.trim().isEmpty()) && requestBody.required()) {
            throw new IllegalArgumentException("Missing request body");
        }
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        if (parameter.getType() == String.class) {
            return body;
        }
        return objectMapper.readValue(body, parameter.getType());
    }
}