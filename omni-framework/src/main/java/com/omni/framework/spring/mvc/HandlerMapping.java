package com.omni.framework.spring.mvc;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class HandlerMapping {
    private final Map<String, Handler> mappings = new HashMap<>();

    public void register(String url, Object controller, Method method, boolean isResponseBody) {
        mappings.put(url, new Handler(controller, method, isResponseBody));
    }

    public Handler get(String url) {
        return mappings.get(url);
    }

    public static class Handler {
        public final Object controller;
        public final Method method;
        public final boolean isResponseBody;

        public Handler(Object controller, Method method, boolean isResponseBody) {
            this.controller = controller;
            this.method = method;
            this.isResponseBody = isResponseBody;
        }
    }
}