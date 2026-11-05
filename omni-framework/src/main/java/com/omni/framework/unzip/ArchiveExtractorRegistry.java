package com.omni.framework.unzip;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Component;
import com.omni.framework.spring.context.SpringContextHolder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class ArchiveExtractorRegistry {
    private static final Logger log = LoggerFactory.getLogger(ArchiveExtractorRegistry.class);
    private volatile Map<ArchiveType, ArchiveExtractor> extractors;

    public ArchiveExtractor get(ArchiveType type) {
        if (type == null) return null;
        return getExtractors().get(type);
    }

    public ArchiveExtractor resolve(ArchiveType type, byte[] head, String fileName) {
        ArchiveExtractor e = get(type);
        if (e != null && e.supports(head, fileName)) return e;
        for (ArchiveExtractor ex : getExtractors().values()) {
            if (ex.supports(head, fileName)) return ex;
        }
        return null;
    }

    public Map<ArchiveType, ArchiveExtractor> getAll() { return getExtractors(); }

    private Map<ArchiveType, ArchiveExtractor> getExtractors() {
        if (extractors != null) return extractors;
        synchronized (this) {
            if (extractors != null) return extractors;
            Map<ArchiveType, ArchiveExtractor> map = new HashMap<>();
            try {
                Map<String, ArchiveExtractor> beans = SpringContextHolder.getApplicationContext()
                        .getBeanFactory().getBeansOfType(ArchiveExtractor.class);
                for (ArchiveExtractor e : beans.values()) {
                    map.put(e.getType(), e);
                    log.info("注册 ArchiveExtractor: {}", e.getType());
                }
            } catch (Exception e) {
                log.warn("收集 ArchiveExtractor 失败", e);
            }
            extractors = Collections.unmodifiableMap(map);
            return extractors;
        }
    }
}