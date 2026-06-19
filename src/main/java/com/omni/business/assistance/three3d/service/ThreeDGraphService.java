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

/**
 * 3D 依赖图数据服务
 * 负责将 CodeAnalysisService 的依赖分析结果转换为前端 3D 力导向图所需的节点和边数据
 */
@Service
public class ThreeDGraphService {
    private static final Logger log = LoggerFactory.getLogger(ThreeDGraphService.class);

    @Autowired
    private CodeAnalysisService codeAnalysisService;

    /**
     * 获取 3D 图数据（节点和边）
     *
     * @param params 分析参数（与 CodeAnalysisService 一致）
     * @return Map 包含 nodes 和 links，以及附加统计信息
     */
    public Map<String, Object> getGraphData(Map<String, String> params) {
        try {
            // 1. 调用 CodeAnalysisService 获取依赖分析结果（含缓存）
            DependencyResult result = codeAnalysisService.getDependencyResult(params);

            // 2. 构建节点列表
            List<Map<String, String>> nodes = new ArrayList<>();
            for (Map.Entry<String, ClassInfo> entry : result.getClassInfoMap().entrySet()) {
                Map<String, String> node = new HashMap<>();
                node.put("id", entry.getKey());
                String type = entry.getValue().getType().name().toLowerCase();
                node.put("type", type); // class / interface / enum / annotation
                nodes.add(node);
            }

            // 3. 构建边列表
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

            // 4. 组装返回数据
            Map<String, Object> graphData = new LinkedHashMap<>();
            graphData.put("nodes", nodes);
            graphData.put("links", links);
            graphData.put("totalClasses", result.getTotalClasses());
            graphData.put("totalEdges", links.size());
            graphData.put("analysisTimeMs", result.getAnalysisTimeMs());

            log.info("3D 图数据生成成功，节点数={}，边数={}", nodes.size(), links.size());
            return graphData;
        } catch (Exception e) {
            log.error("生成 3D 图数据失败", e);
            // 返回空图，避免前端报错
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("nodes", Collections.emptyList());
            empty.put("links", Collections.emptyList());
            empty.put("error", e.getMessage());
            return empty;
        }
    }
}