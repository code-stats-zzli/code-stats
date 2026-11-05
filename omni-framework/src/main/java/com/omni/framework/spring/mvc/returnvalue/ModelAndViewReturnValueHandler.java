// 文件: src/main/java/com/omni/framework/spring/mvc/returnvalue/ModelAndViewReturnValueHandler.java
package com.omni.framework.spring.mvc.returnvalue;

import com.omni.framework.spring.mvc.support.HandlerMethod;
import com.omni.framework.tomcat.servlet.HttpServletResponse;

public class ModelAndViewReturnValueHandler implements HandlerMethodReturnValueHandler {
    @Override
    public boolean supports(HandlerMethod handlerMethod, Object returnValue) {
        return !handlerMethod.isResponseBody() && returnValue != null;
    }

    @Override
    public void handleReturnValue(Object returnValue,
                                  HandlerMethod handlerMethod,
                                  HttpServletResponse response) {
        response.write(returnValue.toString());
    }
}