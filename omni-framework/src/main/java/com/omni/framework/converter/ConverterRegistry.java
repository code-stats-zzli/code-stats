package com.omni.framework.converter;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Component;
import com.omni.framework.spring.context.SpringContextHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 转换器注册中心（懒加载）。
 * <p>通过 IoC 容器收集所有 {@link Converter} 实现，按源格式建立索引，按优先级降序排序。</p>
 */
@Component
public class ConverterRegistry {
    private static final Logger log = LoggerFactory.getLogger(ConverterRegistry.class);

    private volatile Map<String, List<Converter>> sourceIndex;

    private Map<String, List<Converter>> getIndex() {
        if (sourceIndex != null) return sourceIndex;
        synchronized (this) {
            if (sourceIndex != null) return sourceIndex;
            Map<String, List<Converter>> map = new ConcurrentHashMap<>();
            try {
                Map<String, Converter> beans = SpringContextHolder.getApplicationContext()
                        .getBeanFactory().getBeansOfType(Converter.class);
                for (Converter c : beans.values()) {
                    for (FormatType src : c.supportedSources()) {
                        map.computeIfAbsent(src.ext, k -> new ArrayList<>()).add(c);
                    }
                    log.info("注册转换器: {} ({}→{})", c.getName(),
                            c.supportedSources(), c.supportedTargets());
                }
                map.values().forEach(list ->
                        list.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority())));
            } catch (Exception e) {
                log.warn("收集 Converter 失败", e);
            }
            sourceIndex = Collections.unmodifiableMap(map);
            return sourceIndex;
        }
    }

    /** 找到第一个能处理 source → target 的转换器 */
    public Converter resolve(FormatType source, FormatType target) {
        List<Converter> candidates = getIndex().getOrDefault(source.ext, Collections.emptyList());
        for (Converter c : candidates) {
            if (c.supports(source, target)) return c;
        }
        return null;
    }

    /** 列出所有已注册的转换能力，供前端渲染 */
    public List<Map<String, Object>> listSupported() {
        List<Map<String, Object>> list = new ArrayList<>();
        Set<Converter> seen = new HashSet<>();
        for (List<Converter> group : getIndex().values()) {
            for (Converter c : group) {
                if (!seen.add(c)) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", c.getName());
                m.put("sources", c.supportedSources().stream()
                        .map(f -> f.ext).collect(Collectors.toList()));
                m.put("targets", c.supportedTargets().stream()
                        .map(f -> f.ext).collect(Collectors.toList()));
                m.put("priority", c.getPriority());
                list.add(m);
            }
        }
        return list;
    }
}