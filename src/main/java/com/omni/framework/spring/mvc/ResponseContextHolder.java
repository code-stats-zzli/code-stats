// 文件路径: com.framework.spring.mvc.ResponseContextHolder

package com.omni.framework.spring.mvc;

import com.omni.framework.tomcat.connector.Response;

public class ResponseContextHolder {
    private static final ThreadLocal<Response> responseHolder = new ThreadLocal<>();

    public static void setResponse(Response response) {
        responseHolder.set(response);
    }

    public static Response getResponse() {
        return responseHolder.get();
    }

    public static void clear() {
        responseHolder.remove();
    }
}