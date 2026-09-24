package com.omni.business.assistance.codestat.service;


import com.omni.framework.codestats.dependency.algorithm.CallTreeBuilder;
import com.omni.framework.codestats.dependency.export.PlantUmlExporter;
import com.omni.framework.codestats.dependency.extractor.MethodExtractorBatch;
import com.omni.framework.codestats.dependency.model.CallTreeNode;
import com.omni.framework.codestats.dependency.model.MethodCallGraph;
import com.omni.framework.codestats.dependency.model.MethodInfo;
import com.omni.framework.spring.annotation.Service;
import com.omni.framework.spring.config.ConfigLoader;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 方法级调用链分析服务（带配置读取与缓存）。
 *
 * <h3>配置项</h3>
 * <ul>
 *   <li>method.scan.rootDir      默认扫描根目录</li>
 *   <li>method.scan.defaultMaxDepth  调用树默认深度</li>
 *   <li>method.scan.excludeTest   是否排除 test 目录</li>
 *   <li>method.scan.excludePaths  排除的路径片段（逗号分隔）</li>
 *   <li>method.scan.cacheTtlMs    缓存有效期（毫秒），0 表示永不失效</li>
 * </ul>
 */
@Service
public class MethodAnalysisService {

    // ==================== 配置 ====================
    private final String defaultRootDir;
    private final int defaultMaxDepth;
    private final boolean excludeTest;
    private final List<String> excludePaths;
    private final long cacheTtlMs;

    // ==================== 缓存 ====================
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private static class CacheEntry {
        final MethodCallGraph graph;
        final long timestamp;
        CacheEntry(MethodCallGraph g) {
            this.graph = g;
            this.timestamp = System.currentTimeMillis();
        }
        boolean isExpired(long ttl) {
            return ttl > 0 && System.currentTimeMillis() - timestamp > ttl;
        }
    }

    public MethodAnalysisService() {
        this.defaultRootDir = ConfigLoader.getProperty("method.scan.rootDir", "./src/main/java");
        this.defaultMaxDepth = parseInt(
                ConfigLoader.getProperty("method.scan.defaultMaxDepth", "5"), 5);
        this.excludeTest = "true".equalsIgnoreCase(
                ConfigLoader.getProperty("method.scan.excludeTest", "true"));
        String excludeStr = ConfigLoader.getProperty("method.scan.excludePaths",
                "target,node_modules,.git,.idea,build,dist");
        this.excludePaths = new ArrayList<>();
        for (String s : excludeStr.split(",")) {
            if (s != null && !s.trim().isEmpty()) excludePaths.add(s.trim());
        }
        this.cacheTtlMs = parseLong(
                ConfigLoader.getProperty("method.scan.cacheTtlMs", "300000"), 300000L);
    }

    // ==================== 配置读取器（供 Controller 暴露给前端） ====================
    public String getDefaultRootDir() { return defaultRootDir; }
    public int getDefaultMaxDepth() { return defaultMaxDepth; }
    public boolean isExcludeTest() { return excludeTest; }
    public List<String> getExcludePaths() { return excludePaths; }
    public long getCacheTtlMs() { return cacheTtlMs; }

    // ==================== 图构建 ====================

    /**
     * 获取或构建图。rootDir 为空时使用配置文件里的默认值。
     */
    public MethodCallGraph getOrBuild(String rootDir) throws Exception {
        String useDir = (rootDir == null || rootDir.trim().isEmpty())
                ? defaultRootDir : rootDir.trim();

        Path src = Paths.get(useDir).toAbsolutePath().normalize();
        String key = src.toString();

        CacheEntry entry = cache.get(key);
        if (entry != null && !entry.isExpired(cacheTtlMs)) {
            return entry.graph;
        }

        synchronized (this) {
            entry = cache.get(key);
            if (entry != null && !entry.isExpired(cacheTtlMs)) {
                return entry.graph;
            }
            MethodCallGraph g = MethodExtractorBatch.extractAll(
                    src, excludePaths, excludeTest);
            cache.put(key, new CacheEntry(g));
            return g;
        }
    }

    public void clearCache() { cache.clear(); }

    // ==================== 对外输出 ====================

    /** 方法列表（摘要） */
    public List<Map<String, Object>> listMethods(MethodCallGraph graph) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (MethodInfo m : graph.getAllMethods()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", m.getId());
            r.put("displayName", m.getDisplayName());
            r.put("className", m.getClassName());
            r.put("methodName", m.getMethodName());
            r.put("lineNumber", m.getLineNumber());
            r.put("paramCount", m.getParameterTypes().size());
            r.put("isStatic", m.isStatic());
            r.put("calleeCount", graph.getCallees(m.getId()).size());
            r.put("callerCount", graph.getCallers(m.getId()).size());
            list.add(r);
        }
        list.sort((a, b) -> ((String) a.get("id")).compareTo((String) b.get("id")));
        return list;
    }

    /** 入口方法（无入向依赖，且非 JDK、非 getter/setter） */
    public List<Map<String, Object>> listEntryPoints(MethodCallGraph graph) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (MethodInfo m : graph.findEntryPoints()) {
            if (m.getClassName() == null) continue;
            if (m.getClassName().startsWith("java.")) continue;
            String name = m.getMethodName();
            if (name == null) continue;
            // 过滤简单 getter / setter
            if (name.startsWith("get") && m.getParameterTypes().isEmpty()) continue;
            if (name.startsWith("set") && m.getParameterTypes().size() == 1) continue;

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", m.getId());
            r.put("displayName", m.getDisplayName());
            r.put("className", m.getClassName());
            list.add(r);
        }
        list.sort((a, b) ->
                ((String) a.get("displayName")).compareTo((String) b.get("displayName")));
        if (list.size() > 200) list = list.subList(0, 200);
        return list;
    }

    /** 调用树 JSON */
    public Map<String, Object> buildCallTreeJson(MethodCallGraph graph, String entry, int maxDepth) {
        CallTreeNode root = new CallTreeBuilder(graph, maxDepth).build(entry);
        if (root == null) {
            return Collections.singletonMap("error", "方法不存在: " + entry);
        }
        return toJson(root);
    }

    private Map<String, Object> toJson(CallTreeNode node) {
        Map<String, Object> m = new LinkedHashMap<>();
        MethodInfo info = node.getInfo();
        m.put("id", node.getMethodId());
        if (info != null) {
            m.put("displayName", info.getDisplayName());
            m.put("className", info.getClassName());
            m.put("methodName", info.getMethodName());
            m.put("lineNumber", info.getLineNumber());
        }
        m.put("cycleCut", node.isCycleCut());
        m.put("depthCut", node.isDepthCut());
        List<Map<String, Object>> children = new ArrayList<>();
        for (CallTreeNode c : node.getChildren()) children.add(toJson(c));
        m.put("children", children);
        return m;
    }

    /** PlantUML 时序图文本 */
    public String generateSequence(MethodCallGraph graph, String entry, int maxDepth) {
        CallTreeNode root = new CallTreeBuilder(graph, maxDepth).build(entry);
        if (root == null || root.getInfo() == null) {
            return "@startuml\nnote \"方法不存在\"\n@enduml";
        }
        return PlantUmlExporter.toSequenceDiagram(root, root.getInfo().getDisplayName());
    }

    /** 某方法的直接邻居（出向 + 入向） */
    public Map<String, Object> getNeighbors(MethodCallGraph graph, String methodId) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("methodId", methodId);

        List<Map<String, Object>> callees = new ArrayList<>();
        for (String id : graph.getCallees(methodId)) {
            callees.add(describe(graph.getMethod(id), id));
        }
        List<Map<String, Object>> callers = new ArrayList<>();
        for (String id : graph.getCallers(methodId)) {
            callers.add(describe(graph.getMethod(id), id));
        }
        r.put("callees", callees);
        r.put("callers", callers);
        return r;
    }

    private Map<String, Object> describe(MethodInfo m, String fallbackId) {
        Map<String, Object> x = new LinkedHashMap<>();
        if (m == null) {
            x.put("id", fallbackId);
            x.put("displayName", fallbackId);
        } else {
            x.put("id", m.getId());
            x.put("displayName", m.getDisplayName());
            x.put("className", m.getClassName());
        }
        return x;
    }

    // ==================== 工具 ====================

    private static int parseInt(String s, int def) {
        if (s == null || s.isEmpty()) return def;
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
    private static long parseLong(String s, long def) {
        if (s == null || s.isEmpty()) return def;
        try { return Long.parseLong(s); } catch (Exception e) { return def; }
    }
}