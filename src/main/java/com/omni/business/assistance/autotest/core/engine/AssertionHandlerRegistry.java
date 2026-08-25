package com.omni.business.assistance.autotest.core.engine;

import com.omni.business.assistance.autotest.core.engine.assertions.AssertTextHandler;
import com.omni.business.assistance.autotest.core.engine.assertions.AssertUrlHandler;

import java.util.HashMap;
import java.util.Map;

public class AssertionHandlerRegistry {
    private static final Map<String, AssertionHandler> handlers = new HashMap<>();

    public static void register(String assertType, AssertionHandler handler) {
        handlers.put(assertType, handler);
    }

    public static AssertionHandler getHandler(String assertType) {
        return handlers.get(assertType);
    }

    static {
        register("text", new AssertTextHandler());
        register("url", new AssertUrlHandler());
    }
}