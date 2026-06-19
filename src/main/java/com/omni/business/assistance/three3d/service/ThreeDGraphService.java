package com.omni.business.assistance.three3d.service;

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

    /**
     * 获取 3D 图数据，若指定 className 则只返回该类的直接依赖和被依赖子图
     */
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

    /**
     * 获取所有类名列表（用于前端下拉）
     */
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

        // 收集涉及的类名
        Set<String> involvedClasses = new HashSet<>();
        involvedClasses.add(className);
        // 出向依赖
        Set<String> outgoing = fullGraph.getDependencies(className);
        involvedClasses.addAll(outgoing);
        // 入向依赖
        Set<String> incoming = new HashSet<>(fullGraph.getInverseDependencies(className));
        involvedClasses.addAll(incoming);

        // 构建节点
        List<Map<String, String>> nodes = new ArrayList<>();
        for (String cls : involvedClasses) {
            ClassInfo info = classInfoMap.get(cls);
            if (info == null) continue; // 理论上不应发生
            Map<String, String> node = new HashMap<>();
            node.put("id", cls);
            String type = info.getType().name().toLowerCase();
            node.put("type", type);
            nodes.add(node);
        }

        // 构建边：只包含与 className 直接相关的边
        List<Map<String, String>> links = new ArrayList<>();
        // 出向边
        for (String target : outgoing) {
            Map<String, String> link = new HashMap<>();
            link.put("source", className);
            link.put("target", target);
            links.add(link);
        }
        // 入向边（从依赖者指向 className）
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
}