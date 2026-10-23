// 文件: src/main/java/com/omni/framework/spring/mvc/argument/CookieValueArgumentResolver.java
package com.omni.framework.spring.mvc.argument;

import com.omni.framework.spring.annotation.CookieValue;
import com.omni.framework.tomcat.connector.Request;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;

import java.lang.reflect.Parameter;
import java.util.Map;

public class CookieValueArgumentResolver extends AbstractNamedValueArgumentResolver {
    @Override
    public boolean supportsParameter(Parameter parameter) {
        return parameter.isAnnotationPresent(CookieValue.class);
    }

    @Override
    protected String getName(Parameter parameter) {
        return parameter.getAnnotation(CookieValue.class).value();
    }

    @Override
    protected boolean isRequired(Parameter parameter) {
        return parameter.getAnnotation(CookieValue.class).required();
    }

    @Override
    protected String getDefaultValue(Parameter parameter) {
        return parameter.getAnnotation(CookieValue.class).defaultValue();
    }

    @Override
    protected Object resolveValue(String name,
                                  Parameter parameter,
                                  HttpServletRequest request,
                                  HttpServletResponse response,
                                  Map<String, String> pathVariables) {
        Request.Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Request.Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}