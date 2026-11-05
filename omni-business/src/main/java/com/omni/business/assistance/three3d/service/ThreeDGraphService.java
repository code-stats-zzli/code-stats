package com.omni.business.assistance.three3d.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.business.assistance.codestat.service.CodeAnalysisService;
import com.omni.framework.codestats.dependency.model.ClassInfo;
import com.omni.framework.codestats.dependency.model.DependencyGraph;
import com.omni.framework.codestats.dependency.model.DependencyResult;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Service;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.util.*;

@Service
public class ThreeDGraphService {
    private static final Logger log = LoggerFactory.getLogger(ThreeDGraphService.class);

    @Autowired
    private CodeAnalysisService codeAnalysisService;

    @Autowired
    private CallChainService callChainService;

    public Map<String, Object> getCallChainData(Map<String, String> params) {
        return callChainService.getCallChainData(params);
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---------- 依赖图相关方法 ----------
    public Map<String, Object> getGraphData(Map<String, String> params) {
        try {
            DependencyResult fullResult = codeAnalysisService.getDependencyResult(params);
            String className = params.get("className");
            if (className != null && !className.trim().isEmpty()) {
                return buildSubGraph(fullResult, className.trim());
            } else {
                return buildFullGraph(fullResult);
            }
        } catch (Exception e) {
            log.error("生成 3D 图数据失败", e);
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("nodes", Collections.emptyList());
            empty.put("links", Collections.emptyList());
            empty.put("error", e.getMessage());
            return empty;
        }
    }

    public List<String> getClassNames(Map<String, String> params) {
        try {
            DependencyResult result = codeAnalysisService.getDependencyResult(params);
            return new ArrayList<>(result.getClassInfoMap().keySet());
        } catch (Exception e) {
            log.error("获取类名列表失败", e);
            return Collections.emptyList();
        }
    }

    private Map<String, Object> buildFullGraph(DependencyResult result) {
        List<Map<String, String>> nodes = new ArrayList<>();
        for (Map.Entry<String, ClassInfo> entry : result.getClassInfoMap().entrySet()) {
            Map<String, String> node = new HashMap<>();
            node.put("id", entry.getKey());
            String type = entry.getValue().getType().name().toLowerCase();
            node.put("type", type);
            nodes.add(node);
        }
        List<Map<String, String>> links = new ArrayList<>();
        DependencyGraph graph = result.getGraph();
        for (String src : graph.getAllClasses()) {
            for (String target : graph.getDependencies(src)) {
                Map<String, String> link = new HashMap<>();
                link.put("source", src);
                link.put("target", target);
                links.add(link);
            }
        }
        Map<String, Object> graphData = new LinkedHashMap<>();
        graphData.put("nodes", nodes);
        graphData.put("links", links);
        graphData.put("totalClasses", result.getTotalClasses());
        graphData.put("totalEdges", links.size());
        graphData.put("analysisTimeMs", result.getAnalysisTimeMs());
        return graphData;
    }

    private Map<String, Object> buildSubGraph(DependencyResult fullResult, String className) {
        Map<String, ClassInfo> classInfoMap = fullResult.getClassInfoMap();
        DependencyGraph fullGraph = fullResult.getGraph();
        if (!classInfoMap.containsKey(className)) {
            throw new IllegalArgumentException("类不存在: " + className);
        }
        Set<String> involvedClasses = new HashSet<>();
        involvedClasses.add(className);
        Set<String> outgoing = fullGraph.getDependencies(className);
        involvedClasses.addAll(outgoing);
        Set<String> incoming = new HashSet<>(fullGraph.getInverseDependencies(className));
        involvedClasses.addAll(incoming);

        List<Map<String, String>> nodes = new ArrayList<>();
        for (String cls : involvedClasses) {
            ClassInfo info = classInfoMap.get(cls);
            if (info == null) continue;
            Map<String, String> node = new HashMap<>();
            node.put("id", cls);
            String type = info.getType().name().toLowerCase();
            node.put("type", type);
            nodes.add(node);
        }

        List<Map<String, String>> links = new ArrayList<>();
        for (String target : outgoing) {
            Map<String, String> link = new HashMap<>();
            link.put("source", className);
            link.put("target", target);
            links.add(link);
        }
        for (String source : incoming) {
            Map<String, String> link = new HashMap<>();
            link.put("source", source);
            link.put("target", className);
            links.add(link);
        }

        Map<String, Object> graphData = new LinkedHashMap<>();
        graphData.put("nodes", nodes);
        graphData.put("links", links);
        graphData.put("totalClasses", nodes.size());
        graphData.put("totalEdges", links.size());
        graphData.put("analysisTimeMs", fullResult.getAnalysisTimeMs());
        graphData.put("selectedClass", className);
        return graphData;
    }

    // ==================== 柱状图数据 ====================
    public Map<String, Object> getBarData(Map<String, String> params) {
        try {
            String resultJson = codeAnalysisService.analyze(params);
            Map<String, Object> resultMap = objectMapper.readValue(resultJson, Map.class);
            Map<String, Object> languages = (Map<String, Object>) resultMap.get("languages");
            if (languages == null || languages.isEmpty()) {
                return buildEmptyBarData();
            }
            List<String> categories = new ArrayList<>();
            List<Number> series = new ArrayList<>();
            for (Map.Entry<String, Object> entry : languages.entrySet()) {
                String lang = entry.getKey();
                Map<String, Object> stats = (Map<String, Object>) entry.getValue();
                categories.add(lang);
                Number codeLines = (Number) stats.get("code");
                if (codeLines == null) codeLines = 0;
                series.add(codeLines);
            }
            Map<String, Object> barData = new LinkedHashMap<>();
            barData.put("categories", categories);
            barData.put("series", series);
            return barData;
        } catch (Exception e) {
            log.error("获取柱状图数据失败", e);
            return buildEmptyBarData();
        }
    }

    private Map<String, Object> buildEmptyBarData() {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("categories", Collections.emptyList());
        empty.put("series", Collections.emptyList());
        return empty;
    }

    // ==================== 新增：获取内置系统全链路数据 ====================
    public Map<String, Object> getSystemCallChainData() {
        return SystemCallChainData.getFixedData();
    }
}