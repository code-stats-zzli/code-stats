package com.omni.business.assistance.scriptengine.config;

import com.omni.framework.scriptexec.executor.ScriptExecutor;
import com.omni.framework.scriptexec.executor.impl.*;
import com.omni.framework.spring.annotation.Component;


import java.util.HashMap;
import java.util.Map;

@Component
public class ScriptExecutorRegistry {
    private final static Map<String, ScriptExecutor> executorMap = new HashMap<>();

    static  {
        executorMap.put("python", new PythonExecutor());
        executorMap.put("java", new JavaExecutor());
        executorMap.put("c", new CExecutor());
        executorMap.put("cpp", new CppExecutor());

    }

    public ScriptExecutor getExecutor(String key) {
        return executorMap.get(key);
    }

    public Map<String, ScriptExecutor> getAll() {
        return executorMap;
    }
}