// 文件: src/main/java/com/omni/framework/spring/mvc/argument/SessionAttributeArgumentResolver.java
package com.omni.framework.spring.mvc.argument;

import com.omni.framework.spring.annotation.SessionAttribute;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;
import com.omni.framework.tomcat.session.HttpSession;

import java.lang.reflect.Parameter;
import java.util.Map;

public class SessionAttributeArgumentResolver extends AbstractNamedValueArgumentResolver {
    @Override
    public boolean supportsParameter(Parameter parameter) {
        return parameter.isAnnotationPresent(SessionAttribute.class);
    }

    @Override
    protected String getName(Parameter parameter) {
        return parameter.getAnnotation(SessionAttribute.class).value();
    }

    @Override
    protected boolean isRequired(Parameter parameter) {
        return parameter.getAnnotation(SessionAttribute.class).required();
    }

    @Override
    protected String getDefaultValue(Parameter parameter) {
        return parameter.getAnnotation(SessionAttribute.class).defaultValue();
    }

    @Override
    protected Object resolveValue(String name,
                                  Parameter parameter,
                                  HttpServletRequest request,
                                  HttpServletResponse response,
                                  Map<String, String> pathVariables) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        return session.getAttribute(name);
    }
}