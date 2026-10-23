// 文件: src/main/java/com/omni/framework/spring/mvc/returnvalue/VoidReturnValueHandler.java
package com.omni.framework.spring.mvc.returnvalue;

import com.omni.framework.spring.mvc.support.HandlerMethod;
import com.omni.framework.tomcat.servlet.HttpServletResponse;

public class VoidReturnValueHandler implements HandlerMethodReturnValueHandler {
    @Override
    public boolean supports(HandlerMethod handlerMethod, Object returnValue) {
        return !handlerMethod.isResponseBody() && returnValue == null;
    }

    @Override
    public void handleReturnValue(Object returnValue,
                                  HandlerMethod handlerMethod,
                                  HttpServletResponse response) {
        // no-op
    }
}