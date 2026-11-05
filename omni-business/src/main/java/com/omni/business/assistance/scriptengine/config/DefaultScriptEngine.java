package com.omni.business.assistance.scriptengine.config;

import com.omni.framework.scriptexec.engine.ScriptEngine;
import com.omni.framework.scriptexec.executor.ScriptExecutor;

import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class DefaultScriptEngine implements ScriptEngine {

    @Autowired
    private ScriptExecutorRegistry registry;

    @Override
    public String execute(Map<String, Object> params) throws Exception {
        String language = (String) params.get("language");
        String executorType = (String) params.get("executorType");
        ScriptExecutor executor = null;
        if (executorType != null && !executorType.isEmpty()) {
            executor = registry.getExecutor(executorType);
        } else if (language != null && !language.isEmpty()) {
            executor = registry.getExecutor(language);
        }
        if (executor == null) {
            throw new IllegalArgumentException("No executor found for language/type: " + (language != null ? language : executorType));
        }
        // 参数增强：支持 paramTemplate
        Map<String, Object> enriched = enrichParameters(params);
        return executor.execute((String) enriched.get("scriptContent"), enriched);
    }


    private Map<String, Object> enrichParameters(Map<String, Object> original) {
        Map<String, Object> enriched = new HashMap<>(original);
        Object templateObj = original.get("paramTemplate");
        if (templateObj instanceof Map) {
            Map<String, String> template = (Map<String, String>) templateObj;
            for (Map.Entry<String, String> entry : template.entrySet()) {
                String targetKey = entry.getKey();
                String sourcePath = entry.getValue();
                Object value = resolveParameterPath(sourcePath, original);
                if (value != null) enriched.put(targetKey, value);
            }
        }
        return enriched;
    }

    private Object resolveParameterPath(String path, Map<String, Object> params) {
        if (path == null || path.isEmpty()) return null;
        String[] parts = path.split("\\.");
        Map<String, Object> current = params;
        for (int i = 0; i < parts.length; i++) {
            if (i == parts.length - 1) {
                return current.get(parts[i]);
            } else {
                Object next = current.get(parts[i]);
                if (next instanceof Map) {
                    current = (Map<String, Object>) next;
                } else return null;
            }
        }
        return null;
    }
}