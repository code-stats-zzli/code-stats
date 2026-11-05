package com.omni.business.assistance.agent.service.backend;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Component;
import com.omni.framework.spring.context.SpringContextHolder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * ChatBackend 注册中心：懒加载所有 ChatBackend 实现。
 * <p>注：本项目的 IoC 容器不支持 {@code @Autowired List<ChatBackend>} 泛型注入，
 * 因此这里通过 SpringContextHolder 主动收集。</p>
 */
@Component
public class ChatBackendRegistry {
    private static final Logger log = LoggerFactory.getLogger(ChatBackendRegistry.class);

    private volatile Map<String, ChatBackend> backends;

    public ChatBackend get(String name) {
        if (name == null) return null;
        return getBackends().get(name);
    }

    public Map<String, ChatBackend> getAll() {
        return getBackends();
    }

    private Map<String, ChatBackend> getBackends() {
        if (backends != null) return backends;
        synchronized (this) {
            if (backends != null) return backends;
            Map<String, ChatBackend> map = new HashMap<>();
            try {
                Map<String, ChatBackend> beans = SpringContextHolder.getApplicationContext()
                        .getBeanFactory().getBeansOfType(ChatBackend.class);
                for (ChatBackend b : beans.values()) {
                    map.put(b.getName(), b);
                    log.info("注册 ChatBackend: {}", b.getName());
                }
            } catch (Exception e) {
                log.warn("收集 ChatBackend 失败", e);
            }
            backends = Collections.unmodifiableMap(map);
            return backends;
        }
    }
}