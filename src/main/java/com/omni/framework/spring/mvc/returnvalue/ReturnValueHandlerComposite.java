// 文件: src/main/java/com/omni/framework/spring/mvc/returnvalue/ReturnValueHandlerComposite.java
package com.omni.framework.spring.mvc.returnvalue;

import com.omni.framework.spring.mvc.support.HandlerMethod;
import com.omni.framework.tomcat.servlet.HttpServletResponse;

import java.util.ArrayList;
import java.util.List;

public class ReturnValueHandlerComposite {
    private final List<HandlerMethodReturnValueHandler> handlers = new ArrayList<>();

    public ReturnValueHandlerComposite add(HandlerMethodReturnValueHandler handler) {
        if (handler != null) {
            handlers.add(handler);
        }
        return this;
    }

    public void handleReturnValue(Object returnValue,
                                  HandlerMethod handlerMethod,
                                  HttpServletResponse response) throws Exception {
        for (HandlerMethodReturnValueHandler handler : handlers) {
            if (handler.supports(handlerMethod, returnValue)) {
                handler.handleReturnValue(returnValue, handlerMethod, response);
                return;
            }
        }
    }
}