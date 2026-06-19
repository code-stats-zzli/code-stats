// 完整文件路径: com.omni.business.assistance.codestat.service.CodeAnalysisService
package com.omni.business.assistance.codestat.service;

import com.omni.common.cache.Cache;
import com.omni.common.cache.CacheFactory;
import com.omni.framework.codestats.config.Config;
import com.omni.framework.codestats.config.ConfigLoader;
import com.omni.framework.codestats.core.FileInfo;
import com.omni.framework.codestats.core.StatisticsResult;
import com.omni.framework.codestats.dependency.model.ClassInfo;
import com.omni.framework.codestats.dependency.model.DependencyGraph;
import com.omni.framework.codestats.dependency.model.DependencyResult;
import com.omni.framework.codestats.filter.CompositeFilter;
import com.omni.framework.codestats.filter.ExcludePathFilter;
import com.omni.framework.codestats.filter.ExtensionFilter;
import com.omni.framework.codestats.filter.GitIgnoreFilter;
import com.omni.framework.codestats.pipeline.CollectStage;
import com.omni.framework.codestats.pipeline.DependencyAnalysisStage;
import com.omni.framework.codestats.pipeline.MergeFilesStage;
import com.omni.framework.codestats.pipeline.ParallelAnalyzeStage;
import com.omni.framework.codestats.sorter.SorterRegistry;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Service;
import com.omni.framework.util.JsonUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CodeAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(CodeAnalysisService.class);

    // 依赖分析结果缓存
    private final Cache<String, DependencyResult> dependencyCache = CacheFactory.createDefaultCache();

    // ==================== 原有代码统计功能 ====================

    public Map<String, Object> getDefaultConfig() {
        Config config = ConfigLoader.load(new String[0]);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("rootDir", config.getRootDir() != null ? config.getRootDir().toString() : System.getProperty("user.dir"));
        map.put("extensions", config.getExtensions() != null ? String.join(",", config.getExtensions()) : "java");
        map.put("excludePaths", config.getExcludePaths() != null ? String.join(",", config.getExcludePaths()) : "");
        map.put("excludeComments", config.isExcludeComments());
        map.put("threadCount", config.getThreadCount());
        map.put("sortBy", config.getSortBy());
        map.put("mergeEnabled", true);
        String defaultMergePath = config.getDefaultOutputPath() != null ?
                config.getDefaultOutputPath().toString() :
                Paths.get(System.getProperty("user.dir"), "merged_outputs", "merged_" +
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt").toString();
        map.put("mergeOutputFile", defaultMergePath);
        map.put("extraPaths", config.getExtraPaths() != null ? String.join(",", config.getExtraPaths()) : "");
        return map;
    }

    public String getDefaultConfigJson() {
        return JsonUtil.toJson(getDefaultConfig());
    }

    public String analyze(Map<String, String> params) {
        try {
            Config config = buildConfig(params);
            long startTime = System.currentTimeMillis();

            CompositeFilter filterChain = new CompositeFilter();
            if (config.getExtensions() != null && !config.getExtensions().isEmpty()) {
                filterChain.add(new ExtensionFilter(config.getExtensions()));
            }
            if (config.getExcludePaths() != null && !config.getExcludePaths().isEmpty()) {
                filterChain.add(new ExcludePathFilter(config.getExcludePaths()));
            }
            if (config.isUseGitIgnore()) {
                filterChain.add(new GitIgnoreFilter(config.getRootDir()));
            }

            CollectStage collectStage = new CollectStage(filterChain);
            List<Path> files = collectStage.process(config.getRootDir());

            String extraPathsStr = params.get("extraPaths");
            if (extraPathsStr != null && !extraPathsStr.trim().isEmpty()) {
                String[] extraPaths = extraPathsStr.split(",");
                Set<Path> extraFileSet = new LinkedHashSet<>();
                for (String extraPath : extraPaths) {
                    Path path = Paths.get(extraPath.trim());
                    if (Files.isDirectory(path)) {
                        Files.walk(path)
                                .filter(Files::isRegularFile)
                                .forEach(extraFileSet::add);
                    } else if (Files.isRegularFile(path)) {
                        extraFileSet.add(path);
                    }
                }
                if (!extraFileSet.isEmpty()) {
                    Set<Path> mergedSet = new LinkedHashSet<>(files);
                    mergedSet.addAll(extraFileSet);
                    files = new ArrayList<>(mergedSet);
                }
            }

            ParallelAnalyzeStage analyzeStage = new ParallelAnalyzeStage(
                    config.getThreadCount(), config.isExcludeComments(), null);
            List<FileInfo> fileInfos = analyzeStage.process(files);

            fileInfos.sort(SorterRegistry.getComparator(config.getSortBy()));

            StatisticsResult result = new StatisticsResult();
            for (FileInfo info : fileInfos) {
                result.addFileInfo(info);
            }
            result.setDurationMs(System.currentTimeMillis() - startTime);

            boolean mergeEnabled = !"false".equalsIgnoreCase(params.get("mergeEnabled"));
            String mergeOutputFile = params.get("mergeOutputFile");
            if (mergeEnabled && (mergeOutputFile == null || mergeOutputFile.trim().isEmpty())) {
                Path defaultOut = Paths.get(System.getProperty("user.dir"), "merged_outputs",
                        "merged_" + System.currentTimeMillis() + ".txt");
                mergeOutputFile = defaultOut.toString();
                if (defaultOut.getParent() != null && !Files.exists(defaultOut.getParent())) {
                    Files.createDirectories(defaultOut.getParent());
                }
            }
            if (mergeEnabled && mergeOutputFile != null && !mergeOutputFile.isEmpty()) {
                Path mergePath = Paths.get(mergeOutputFile);
                try {
                    new MergeFilesStage(mergePath).process(files);
                } catch (Exception e) {
                    log.error("合并文件失败: {}", mergeOutputFile, e);
                }
            }

            return buildResponseJson(fileInfos, result, mergeEnabled, mergeOutputFile);
        } catch (Exception e) {
            log.error("代码分析失败", e);
            Map<String, String> err = new LinkedHashMap<>();
            err.put("error", e.getMessage());
            return JsonUtil.toJson(err);
        }
    }

    private Config buildConfig(Map<String, String> params) {
        Config.Builder builder = new Config.Builder();
        String rootDir = params.get("rootDir");
        if (rootDir != null && !rootDir.isEmpty()) {
            builder.rootDir(Paths.get(rootDir));
        } else {
            builder.rootDir(Paths.get("."));
        }
        String exts = params.get("extensions");
        if (exts != null && !exts.isEmpty()) {
            builder.extensions(Arrays.asList(exts.split(",")));
        }
        String exclude = params.get("excludePaths");
        if (exclude != null && !exclude.isEmpty()) {
            builder.excludePaths(Arrays.asList(exclude.split(",")));
        }
        builder.excludeComments("true".equalsIgnoreCase(params.get("excludeComments")));
        String threads = params.get("threadCount");
        if (threads != null && !threads.isEmpty()) {
            builder.threadCount(Integer.parseInt(threads));
        }
        String sort = params.get("sortBy");
        if (sort != null && !sort.isEmpty()) {
            builder.sortBy(sort);
        }
        String extraPathsStr = params.get("extraPaths");
        if (extraPathsStr != null && !extraPathsStr.isEmpty()) {
            builder.extraPaths(Arrays.asList(extraPathsStr.split(",")));
        }
        builder.useGitIgnore(true);
        return builder.build();
    }

    private String buildResponseJson(List<FileInfo> fileInfos, StatisticsResult result,
                                     boolean mergeEnabled, String mergeOutputFile) {
        Map<String, Object> response = new LinkedHashMap<>();
        List<Map<String, Object>> fileList = fileInfos.stream().map(info -> {
            Map<String, Object> fileMap = new LinkedHashMap<>();
            fileMap.put("path", info.getPath().toString());
            fileMap.put("lines", info.getLines());
            fileMap.put("codeLines", info.getCodeLines());
            fileMap.put("commentLines", info.getCommentLines());
            fileMap.put("blankLines", info.getBlankLines());
            fileMap.put("language", info.getLanguage().name());
            fileMap.put("complexity", info.getComplexity());
            return fileMap;
        }).collect(Collectors.toList());
        response.put("files", fileList);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalFiles", result.getFileInfos().size());
        summary.put("totalLines", result.getTotalLines());
        summary.put("totalCodeLines", result.getTotalCodeLines());
        summary.put("totalCommentLines", result.getTotalCommentLines());
        summary.put("totalBlankLines", result.getTotalBlankLines());
        summary.put("durationMs", result.getDurationMs());
        response.put("summary", summary);
        Map<String, Map<String, Long>> langSummary = new LinkedHashMap<>();
        result.getLanguageSummary().forEach((lang, ls) -> {
            Map<String, Long> langMap = new LinkedHashMap<>();
            langMap.put("files", ls.getFiles());
            langMap.put("lines", ls.getLines());
            langMap.put("code", ls.getCode());
            langMap.put("comments", ls.getComments());
            langMap.put("blanks", ls.getBlanks());
            langSummary.put(lang.name(), langMap);
        });
        response.put("languages", langSummary);
        if (mergeEnabled && mergeOutputFile != null && !mergeOutputFile.isEmpty()) {
            response.put("mergeOutput", mergeOutputFile);
        }
        return JsonUtil.toJson(response);
    }

    // ==================== 依赖分析功能 ====================

    /**
     * 生成依赖分析的缓存 Key
     */
    private String generateDependencyCacheKey(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        sb.append(params.getOrDefault("rootDir", "")).append("|");
        sb.append(params.getOrDefault("extensions", "java")).append("|");
        sb.append(params.getOrDefault("excludePaths", "")).append("|");
        sb.append(params.getOrDefault("extraPaths", "")).append("|");
        sb.append(params.getOrDefault("threadCount", "0")).append("|");
        sb.append(params.getOrDefault("excludeComments", "true"));
        return Integer.toHexString(sb.toString().hashCode());
    }

    /**
     * 执行依赖分析（带缓存）
     */
    public String analyzeDependencies(Map<String, String> params) {
        long startTime = System.currentTimeMillis();
        String cacheKey = generateDependencyCacheKey(params);

        DependencyResult cached = dependencyCache.get(cacheKey);
        if (cached != null) {
            log.info("依赖分析结果命中缓存，key={}", cacheKey);
            return buildDependencyResponseJson(cached, System.currentTimeMillis() - startTime);
        }

        try {
            DependencyResult result = doAnalyzeDependencies(params);
            dependencyCache.put(cacheKey, result, 3600000L); // TTL 1小时
            return buildDependencyResponseJson(result, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("依赖分析失败", e);
            Map<String, String> err = new LinkedHashMap<>();
            err.put("error", e.getMessage());
            return JsonUtil.toJson(err);
        }
    }

    /**
     * 实际执行依赖分析（无缓存）
     */
    private DependencyResult doAnalyzeDependencies(Map<String, String> params) throws Exception {
        Config config = buildConfigForDependency(params);

        CompositeFilter filterChain = new CompositeFilter();
        if (config.getExtensions() != null && !config.getExtensions().isEmpty()) {
            filterChain.add(new ExtensionFilter(config.getExtensions()));
        }
        if (config.getExcludePaths() != null && !config.getExcludePaths().isEmpty()) {
            filterChain.add(new ExcludePathFilter(config.getExcludePaths()));
        }
        if (config.isUseGitIgnore()) {
            filterChain.add(new GitIgnoreFilter(config.getRootDir()));
        }

        CollectStage collectStage = new CollectStage(filterChain);
        List<Path> files = collectStage.process(config.getRootDir());

        String extraPathsStr = params.get("extraPaths");
        if (extraPathsStr != null && !extraPathsStr.trim().isEmpty()) {
            String[] extraPaths = extraPathsStr.split(",");
            Set<Path> extraFileSet = new LinkedHashSet<>();
            for (String extraPath : extraPaths) {
                Path path = Paths.get(extraPath.trim());
                if (Files.isDirectory(path)) {
                    Files.walk(path).filter(Files::isRegularFile).forEach(extraFileSet::add);
                } else if (Files.isRegularFile(path)) {
                    extraFileSet.add(path);
                }
            }
            if (!extraFileSet.isEmpty()) {
                Set<Path> mergedSet = new LinkedHashSet<>(files);
                mergedSet.addAll(extraFileSet);
                files = new ArrayList<>(mergedSet);
            }
        }

        if (files.isEmpty()) {
            return new DependencyResult(Collections.emptyMap(), new DependencyGraph(), 0L);
        }

        DependencyAnalysisStage stage = new DependencyAnalysisStage(config.getThreadCount());
        return stage.process(files);
    }

    private Config buildConfigForDependency(Map<String, String> params) {
        Config.Builder builder = new Config.Builder();
        String rootDir = params.get("rootDir");
        if (rootDir != null && !rootDir.isEmpty()) {
            builder.rootDir(Paths.get(rootDir));
        } else {
            builder.rootDir(Paths.get("."));
        }
        String exts = params.get("extensions");
        if (exts != null && !exts.isEmpty()) {
            builder.extensions(Arrays.asList(exts.split(",")));
        } else {
            builder.extensions(Collections.singletonList("java"));
        }
        String exclude = params.get("excludePaths");
        if (exclude != null && !exclude.isEmpty()) {
            builder.excludePaths(Arrays.asList(exclude.split(",")));
        }
        builder.excludeComments("true".equalsIgnoreCase(params.get("excludeComments")));
        String threads = params.get("threadCount");
        if (threads != null && !threads.isEmpty()) {
            builder.threadCount(Integer.parseInt(threads));
        } else {
            builder.threadCount(Runtime.getRuntime().availableProcessors());
        }
        builder.sortBy("name");
        builder.useGitIgnore(true);
        return builder.build();
    }

    /**
     * 构建依赖分析结果的 JSON
     */
    private String buildDependencyResponseJson(DependencyResult result, long elapsedMs) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalClasses", result.getTotalClasses());
        response.put("averageOutgoingDependencies", result.getAverageOutgoingDependencies());
        response.put("maxOutgoingDependencies", result.getMaxOutgoingDependencies());
        response.put("maxOutgoingClasses", result.getMaxOutgoingClasses());
        response.put("durationMs", elapsedMs);

        // 序列化类信息
        Map<String, Map<String, Object>> classInfoMap = new LinkedHashMap<>();
        for (Map.Entry<String, ClassInfo> entry : result.getClassInfoMap().entrySet()) {
            ClassInfo info = entry.getValue();
            Map<String, Object> infoMap = new LinkedHashMap<>();
            infoMap.put("fullyQualifiedName", info.getFullyQualifiedName());
            infoMap.put("packageName", info.getPackageName());
            infoMap.put("simpleName", info.getSimpleName());
            infoMap.put("type", info.getType().name());
            classInfoMap.put(entry.getKey(), infoMap);
        }
        response.put("classInfoMap", classInfoMap);

        // 序列化依赖图（完整）
        Map<String, List<String>> graphMap = new LinkedHashMap<>();
        DependencyGraph graph = result.getGraph();
        for (String className : graph.getAllClasses()) {
            graphMap.put(className, new ArrayList<>(graph.getDependencies(className)));
        }
        response.put("graph", graphMap);

        // 序列化循环分组
        List<List<String>> cyclesJson = new ArrayList<>();
        for (Set<String> cycle : result.getCycleGroups()) {
            cyclesJson.add(new ArrayList<>(cycle));
        }
        response.put("cycles", cyclesJson);

        return JsonUtil.toJson(response);
    }

    /**
     * 获取依赖分析结果对象（供内部使用，优先从缓存取）
     */
    public DependencyResult getDependencyResult(Map<String, String> params) throws Exception {
        String cacheKey = generateDependencyCacheKey(params);
        DependencyResult cached = dependencyCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        DependencyResult result = doAnalyzeDependencies(params);
        dependencyCache.put(cacheKey, result, 3600000L);
        return result;
    }

    /**
     * 查询某个类的直接依赖（出向）
     */
    public Map<String, Object> getDirectDependencies(String className, Map<String, String> params) throws Exception {
        DependencyResult result = getDependencyResult(params);
        List<String> outgoing = result.getGraph().getDirectDependencies(className);
        Map<String, Object> resp = new HashMap<>();
        resp.put("className", className);
        resp.put("outgoing", outgoing);
        return resp;
    }

    /**
     * 查询哪些类依赖了某个类（入向）
     */
    public Map<String, Object> getInverseDependencies(String className, Map<String, String> params) throws Exception {
        DependencyResult result = getDependencyResult(params);
        List<String> incoming = result.getGraph().getInverseDependencies(className);
        Map<String, Object> resp = new HashMap<>();
        resp.put("className", className);
        resp.put("incoming", incoming);
        return resp;
    }

    /**
     * 获取供 3D 力导向图使用的节点和边数据
     * @param params 分析参数（与依赖分析相同）
     * @return 包含 nodes 和 links 的 Map
     */
    public Map<String, Object> getGraphDataFor3D(Map<String, String> params) throws Exception {
        // 复用已有的依赖分析结果（带缓存）
        DependencyResult result = getDependencyResult(params);

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
        // 可选附带统计信息
        graphData.put("totalClasses", result.getTotalClasses());
        graphData.put("totalEdges", links.size());
        return graphData;
    }

// 注意：getDependencyResult 是私有方法，需要将其可见性改为 private 或 protected，它已经存在。
// 如果它不可见，请确保以下方法存在（已存在于您的代码中）：
// private DependencyResult getDependencyResult(Map<String, String> params) throws Exception
}