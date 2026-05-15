// 文件路径: com.framework.spring.mvc.ResponseContextHolder

package com.omni.framework.spring.mvc;


import com.omni.framework.tomcat.servlet.HttpServletResponse;

public class ResponseContextHolder {
    private static final ThreadLocal<HttpServletResponse> responseHolder = new ThreadLocal<>();

    public static void setResponse(HttpServletResponse response) {
        responseHolder.set(response);
    }

    public static HttpServletResponse getResponse() {
        return responseHolder.get();
    }

    public static void clear() {
        responseHolder.remove();
    }
}