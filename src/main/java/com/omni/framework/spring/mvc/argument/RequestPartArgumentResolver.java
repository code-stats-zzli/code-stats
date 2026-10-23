// 文件: src/main/java/com/omni/framework/spring/mvc/argument/RequestPartArgumentResolver.java
package com.omni.framework.spring.mvc.argument;

import com.omni.framework.spring.annotation.RequestPart;
import com.omni.framework.spring.mvc.FileItem;
import com.omni.framework.spring.mvc.MultipartResolver;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;

import java.lang.reflect.Parameter;
import java.util.Map;

public class RequestPartArgumentResolver extends AbstractNamedValueArgumentResolver {
    @Override
    public boolean supportsParameter(Parameter parameter) {
        return parameter.isAnnotationPresent(RequestPart.class);
    }

    @Override
    protected String getName(Parameter parameter) {
        return parameter.getAnnotation(RequestPart.class).value();
    }

    @Override
    protected boolean isRequired(Parameter parameter) {
        return parameter.getAnnotation(RequestPart.class).required();
    }

    @Override
    protected String getDefaultValue(Parameter parameter) {
        return "";
    }

    @Override
    protected Object resolveValue(String name,
                                  Parameter parameter,
                                  HttpServletRequest request,
                                  HttpServletResponse response,
                                  Map<String, String> pathVariables) {
        MultipartResolver resolver = request.getMultipartResolver();
        if (resolver == null) {
            return null;
        }
        FileItem item = resolver.getFileItem(name);
        return item;
    }
}