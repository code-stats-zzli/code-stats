package com.omni.business.assistance.scriptengine.config;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.scriptexec.executor.ScriptExecutor;
import com.omni.framework.spring.annotation.Component;
import com.omni.framework.spring.context.SpringContextHolder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 脚本执行器注册中心：懒加载所有 ScriptExecutor 实现。
 * 新增语言只需新增实现类（含 getLanguage），无需修改本类。
 * 注：原静态块中的 RestExecutor 因被注释掉而失效，现由 IoC 自动发现，只注册实际存在的 Bean。
 */
@Component
public class ScriptExecutorRegistry {
    private static final Logger log = LoggerFactory.getLogger(ScriptExecutorRegistry.class);

    private volatile Map<String, ScriptExecutor> executorMap;

    public ScriptExecutor getExecutor(String key) {
        if (key == null) return null;
        return getExecutorMap().get(key.toLowerCase());
    }

    public Map<String, ScriptExecutor> getAll() {
        return getExecutorMap();
    }

    private Map<String, ScriptExecutor> getExecutorMap() {
        if (executorMap != null) return executorMap;
        synchronized (this) {
            if (executorMap != null) return executorMap;
            Map<String, ScriptExecutor> map = new HashMap<>();
            try {
                Map<String, ScriptExecutor> beans = SpringContextHolder.getApplicationContext()
                        .getBeanFactory().getBeansOfType(ScriptExecutor.class);
                for (ScriptExecutor e : beans.values()) {
                    String lang = e.getLanguage();
                    if (lang != null) {
                        map.put(lang.toLowerCase(), e);
                        log.info("注册 ScriptExecutor: {}", lang);
                    }
                }
            } catch (Exception e) {
                log.warn("收集 ScriptExecutor 失败", e);
            }
            executorMap = Collections.unmodifiableMap(map);
            return executorMap;
        }
    }
}