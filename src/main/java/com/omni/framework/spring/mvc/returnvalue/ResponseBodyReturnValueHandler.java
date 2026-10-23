// 文件: src/main/java/com/omni/framework/spring/mvc/returnvalue/ResponseBodyReturnValueHandler.java
package com.omni.framework.spring.mvc.returnvalue;

import com.omni.framework.spring.mvc.support.HandlerMethod;
import com.omni.framework.tomcat.servlet.HttpServletResponse;
import com.omni.framework.util.JsonUtil;

public class ResponseBodyReturnValueHandler implements HandlerMethodReturnValueHandler {
    @Override
    public boolean supports(HandlerMethod handlerMethod, Object returnValue) {
        return handlerMethod.isResponseBody();
    }

    @Override
    public void handleReturnValue(Object returnValue,
                                  HandlerMethod handlerMethod,
                                  HttpServletResponse response) {
        String out;
        if (returnValue == null) {
            out = "";
        } else if (returnValue instanceof String) {
            out = (String) returnValue;
        } else {
            out = JsonUtil.toJson(returnValue);
        }
        response.setContentType("application/json; charset=utf-8");
        response.write(out);
    }
}