package com.omni.business.assistance.autotest.core.engine;

import java.util.HashMap;
import java.util.Map;

public class StepExecutorRegistry {
    private static final Map<String, StepExecutor> executors = new HashMap<>();

    public static void register(String actionType, StepExecutor executor) {
        executors.put(actionType, executor);
    }

    public static StepExecutor getExecutor(String actionType) {
        return executors.get(actionType);
    }

    static {
        // 注册所有动作类型对应的执行器（默认使用 DefaultStepExecutor）
        register("goto", new DefaultStepExecutor("goto"));
        register("click", new DefaultStepExecutor("click"));
        register("input", new DefaultStepExecutor("input"));
        register("wait", new DefaultStepExecutor("wait"));
        // 可继续添加更多
    }
}