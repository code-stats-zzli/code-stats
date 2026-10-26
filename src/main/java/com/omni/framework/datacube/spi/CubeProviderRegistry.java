package com.omni.framework.datacube.spi;

import com.omni.framework.datacube.model.CubeType;
import com.omni.framework.spring.context.SpringContextHolder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CubeProviderRegistry {
    private static final Map<CubeType, CubeProvider> PROVIDERS = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;

    public static void register(CubeProvider p) {
        if (p != null && p.getType() != null) {
            PROVIDERS.put(p.getType(), p);
        }
    }

    /** 惰性加载：首次调用时从 Spring 容器拉取所有 CubeProvider Bean */
    public static void ensureLoaded() {
        if (loaded) return;
        synchronized (CubeProviderRegistry.class) {
            if (loaded) return;
            try {
                Map<String, CubeProvider> beans = SpringContextHolder
                        .getApplicationContext().getBeanFactory()
                        .getBeansOfType(CubeProvider.class);
                for (CubeProvider p : beans.values()) register(p);
                loaded = true;
            } catch (Exception ignored) {
                // 容器尚未就绪，下次再试
            }
        }
    }

    public static CubeProvider get(CubeType t) {
        ensureLoaded();
        return PROVIDERS.get(t);
    }

    public static Map<CubeType, CubeProvider> all() {
        ensureLoaded();
        return PROVIDERS;
    }
}