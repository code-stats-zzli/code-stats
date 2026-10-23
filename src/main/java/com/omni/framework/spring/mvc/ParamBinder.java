// 文件: src/main/java/com/omni/framework/spring/mvc/ParamBinder.java
package com.omni.framework.spring.mvc;

import com.omni.framework.spring.mvc.argument.ArgumentResolverComposite;
import com.omni.framework.spring.mvc.argument.ArgumentResolverFactory;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;

/**
 * 兼容旧 API 的参数绑定入口。
 * 实际解析逻辑已拆分到 com.omni.framework.spring.mvc.argument 包。
 */
public class ParamBinder {

    public static Object[] resolveParameters(Method method,
                                             HttpServletRequest request,
                                             HttpServletResponse response,
                                             Map<String, String> pathVars) throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];

        ArgumentResolverComposite composite = ArgumentResolverFactory.createDefault();

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (composite.supportsParameter(parameter)) {
                args[i] = composite.resolveArgument(parameter, request, response, pathVars);
            } else {
                args[i] = null;
            }
        }
        return args;
    }
}