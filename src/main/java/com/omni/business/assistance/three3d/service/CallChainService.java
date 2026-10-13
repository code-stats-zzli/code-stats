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
import java.util.stream.Collectors;

@Service
public class CallChainService {

    private static final Logger log = LoggerFactory.getLogger(CallChainService.class);

    @Autowired
    private CodeAnalysisService codeAnalysisService;

    /**
     * 获取模块调用链数据（Controller → Service → Mapper）
     * @param params 分析参数（rootDir, extensions等）
     * @return 包含 nodes 和 links 的 Map
     */
    public Map<String, Object> getCallChainData(Map<String, String> params) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            // 1. 获取依赖分析结果
            DependencyResult depResult = codeAnalysisService.getDependencyResult(params);
            if (depResult == null || depResult.getClassInfoMap().isEmpty()) {
                return buildEmptyResult("未找到类信息，请检查分析参数");
            }

            // 2. 按类型分类节点（Controller / Service / Mapper）
            Map<String, ClassInfo> allClasses = depResult.getClassInfoMap();
            Map<String, String> nodeTypeMap = new HashMap<>(); // className -> type
            Set<String> controllerSet = new HashSet<>();
            Set<String> serviceSet = new HashSet<>();
            Set<String> mapperSet = new HashSet<>();

            for (Map.Entry<String, ClassInfo> entry : allClasses.entrySet()) {
                String className = entry.getKey();
                ClassInfo info = entry.getValue();
                String simpleName = info.getSimpleName().toLowerCase();
                String packageName = info.getPackageName().toLowerCase();

                // 根据包名或类名后缀判断类型
                if (packageName.contains(".controller") || simpleName.endsWith("controller")) {
                    controllerSet.add(className);
                    nodeTypeMap.put(className, "controller");
                } else if (packageName.contains(".service") || simpleName.endsWith("service")) {
                    serviceSet.add(className);
                    nodeTypeMap.put(className, "service");
                } else if (packageName.contains(".mapper") || simpleName.endsWith("mapper")) {
                    mapperSet.add(className);
                    nodeTypeMap.put(className, "mapper");
                }
            }

            // 如果分类结果太少，可能未命中，则回退：使用所有类并标记为默认类型
            if (controllerSet.isEmpty() && serviceSet.isEmpty() && mapperSet.isEmpty()) {
                log.warn("未识别到 Controller/Service/Mapper，将全部作为普通类");
                for (String className : allClasses.keySet()) {
                    nodeTypeMap.put(className, "class");
                }
            }

            // 3. 构建节点列表（包含 id, type, size = 入度）
            Map<String, Integer> inDegree = new HashMap<>();
            for (String className : nodeTypeMap.keySet()) {
                inDegree.put(className, 0);
            }

            // 统计入度（被依赖次数）
            DependencyGraph graph = depResult.getGraph();
            for (String src : graph.getAllClasses()) {
                for (String target : graph.getDependencies(src)) {
                    if (inDegree.containsKey(target)) {
                        inDegree.put(target, inDegree.get(target) + 1);
                    }
                }
            }

            List<Map<String, Object>> nodes = new ArrayList<>();
            for (Map.Entry<String, String> entry : nodeTypeMap.entrySet()) {
                String id = entry.getKey();
                String type = entry.getValue();
                int size = inDegree.getOrDefault(id, 0) + 1; // +1 避免零节点
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", id);
                node.put("type", type);
                node.put("size", Math.min(size, 20)); // 限制最大尺寸
                // 添加简短名称用于显示
                String shortName = id.contains(".") ? id.substring(id.lastIndexOf('.') + 1) : id;
                node.put("label", shortName);
                nodes.add(node);
            }

            // 4. 构建边（仅包含两端都在节点列表中的依赖）
            List<Map<String, String>> links = new ArrayList<>();
            for (String src : graph.getAllClasses()) {
                if (!nodeTypeMap.containsKey(src)) continue;
                for (String target : graph.getDependencies(src)) {
                    if (nodeTypeMap.containsKey(target)) {
                        Map<String, String> link = new LinkedHashMap<>();
                        link.put("source", src);
                        link.put("target", target);
                        links.add(link);
                    }
                }
            }

            result.put("nodes", nodes);
            result.put("links", links);
            result.put("totalNodes", nodes.size());
            result.put("totalEdges", links.size());

        } catch (Exception e) {
            log.error("生成调用链数据失败", e);
            return buildEmptyResult("生成调用链数据失败: " + e.getMessage());
        }
        return result;
    }

    private Map<String, Object> buildEmptyResult(String message) {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("nodes", Collections.emptyList());
        empty.put("links", Collections.emptyList());
        empty.put("error", message);
        return empty;
    }
}