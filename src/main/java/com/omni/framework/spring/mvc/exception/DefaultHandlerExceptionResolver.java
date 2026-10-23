// 文件: src/main/java/com/omni/framework/spring/mvc/exception/DefaultHandlerExceptionResolver.java
package com.omni.framework.spring.mvc.exception;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.mvc.support.HandlerMethod;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;

public class DefaultHandlerExceptionResolver implements HandlerExceptionResolver {
    private static final Logger log = LoggerFactory.getLogger(DefaultHandlerExceptionResolver.class);

    @Override
    public boolean resolveException(HttpServletRequest request,
                                    HttpServletResponse response,
                                    HandlerMethod handlerMethod,
                                    Throwable ex) {
        log.error("Handler method execution failed", ex);

        if (response.isCommitted()) {
            return true;
        }

        try {
            response.setStatus(500, "Internal Server Error");
            response.write("<h1>500 Internal Server Error</h1><pre>"
                    + escape(ex.toString()) + "</pre>");
            response.send();
        } catch (Exception sendError) {
            log.error("Send error response failed", sendError);
        }
        return true;
    }

    private String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}