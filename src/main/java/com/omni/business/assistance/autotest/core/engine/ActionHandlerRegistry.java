package com.omni.business.assistance.autotest.core.engine;

import com.omni.business.assistance.autotest.core.engine.actions.ClickAction;
import com.omni.business.assistance.autotest.core.engine.actions.GotoAction;
import com.omni.business.assistance.autotest.core.engine.actions.InputAction;
import com.omni.business.assistance.autotest.core.engine.actions.WaitAction;

import java.util.HashMap;
import java.util.Map;

public class ActionHandlerRegistry {
    private static final Map<String, ActionHandler> handlers = new HashMap<>();

    public static void register(String actionType, ActionHandler handler) {
        handlers.put(actionType, handler);
    }

    public static ActionHandler getHandler(String actionType) {
        return handlers.get(actionType);
    }

    static {
        // 手动注册（实际可用注解扫描）
        register("goto", new GotoAction());
        register("click", new ClickAction());
        register("input", new InputAction());
        register("wait", new WaitAction());
    }
}