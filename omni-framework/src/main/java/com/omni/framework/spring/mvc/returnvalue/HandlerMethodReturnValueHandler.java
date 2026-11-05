// 文件: src/main/java/com/omni/framework/spring/mvc/returnvalue/HandlerMethodReturnValueHandler.java
package com.omni.framework.spring.mvc.returnvalue;

import com.omni.framework.spring.mvc.support.HandlerMethod;
import com.omni.framework.tomcat.servlet.HttpServletResponse;

public interface HandlerMethodReturnValueHandler {
    boolean supports(HandlerMethod handlerMethod, Object returnValue);

    void handleReturnValue(Object returnValue,
                           HandlerMethod handlerMethod,
                           HttpServletResponse response) throws Exception;
}