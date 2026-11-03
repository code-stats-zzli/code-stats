package com.omni.framework.unzip.writer;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Component;
import com.omni.framework.spring.context.SpringContextHolder;
import com.omni.framework.unzip.ArchiveType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class ArchiveWriterRegistry {
    private static final Logger log = LoggerFactory.getLogger(ArchiveWriterRegistry.class);
    private volatile Map<ArchiveType, ArchiveWriter> writers;

    public ArchiveWriter get(ArchiveType type) {
        if (type == null) return null;
        return getWriters().get(type);
    }
    public Map<ArchiveType, ArchiveWriter> getAll() { return getWriters(); }

    private Map<ArchiveType, ArchiveWriter> getWriters() {
        if (writers != null) return writers;
        synchronized (this) {
            if (writers != null) return writers;
            Map<ArchiveType, ArchiveWriter> map = new HashMap<>();
            try {
                Map<String, ArchiveWriter> beans = SpringContextHolder.getApplicationContext()
                        .getBeanFactory().getBeansOfType(ArchiveWriter.class);
                for (ArchiveWriter w : beans.values()) {
                    map.put(w.getType(), w);
                    log.info("注册 ArchiveWriter: {}", w.getType());
                }
            } catch (Exception e) {
                log.warn("收集 ArchiveWriter 失败", e);
            }
            writers = Collections.unmodifiableMap(map);
            return writers;
        }
    }
}