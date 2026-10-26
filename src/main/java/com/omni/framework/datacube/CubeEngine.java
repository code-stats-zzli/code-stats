package com.omni.framework.datacube;

import com.omni.common.cache.Cache;
import com.omni.common.cache.CacheFactory;
import com.omni.framework.datacube.model.CubeQueryResult;
import com.omni.framework.datacube.model.CubeType;
import com.omni.framework.datacube.model.DataCube;
import com.omni.framework.datacube.spi.CubeProvider;
import com.omni.framework.datacube.spi.CubeProviderRegistry;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** 执行引擎：缓存 → Provider → 组装结果 */
@Service
public class CubeEngine {
    private static final Logger log = LoggerFactory.getLogger(CubeEngine.class);

    private final Cache<String, CubeQueryResult> cache = CacheFactory.createDefaultCache();

    public CubeQueryResult execute(DataCube cube, Map<String, Object> params) {
        if (cube == null) return CubeQueryResult.error("cube 为空");

        CubeType type = cube.getType();
        CubeProvider provider = CubeProviderRegistry.get(type);
        if (provider == null) {
            return CubeQueryResult.error("未注册的 Provider: " + type);
        }

        boolean cacheEnabled = cube.getCache() != null
                && Boolean.TRUE.equals(cube.getCache().get("enabled"));
        long ttl = 60_000L;
        if (cube.getCache() != null && cube.getCache().get("ttlMs") != null) {
            try { ttl = Long.parseLong(cube.getCache().get("ttlMs").toString()); }
            catch (Exception ignored) {}
        }

        Map<String, Object> merged = mergeParams(cube.getParams(), params);
        String key = cacheKey(cube, merged);

        if (cacheEnabled) {
            CubeQueryResult hit = cache.get(key);
            if (hit != null) return hit;
        }

        long start = System.currentTimeMillis();
        try {
            CubeQueryResult result = provider.query(cube, merged);
            if (result.getMeta() == null) result.setMeta(new LinkedHashMap<>());
            result.getMeta().putIfAbsent("durationMs", System.currentTimeMillis() - start);
            if (cacheEnabled && result.isSuccess()) cache.put(key, result, ttl);
            return result;
        } catch (Exception e) {
            log.error("Cube 执行失败: {}", cube.getId(), e);
            return CubeQueryResult.error(e.getMessage());
        }
    }

    public CubeQueryResult describe(DataCube cube) {
        if (cube == null) return CubeQueryResult.error("cube 为空");
        CubeProvider provider = CubeProviderRegistry.get(cube.getType());
        if (provider == null) return CubeQueryResult.error("未注册的 Provider: " + cube.getType());
        try {
            provider.describe(cube);   // 触发探测（结果由外部自行获取）
            return CubeQueryResult.ok(null, null, null);
        } catch (Exception e) {
            return CubeQueryResult.error(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeParams(Map<String, Object> defaults, Map<String, Object> req) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (defaults != null) out.putAll(defaults);
        if (req != null) out.putAll(req);
        return out;
    }

    private String cacheKey(DataCube cube, Map<String, Object> params) {
        return cube.getId() + "|" + params.toString();
    }
}