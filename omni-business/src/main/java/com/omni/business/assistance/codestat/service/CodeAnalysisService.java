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

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class CodeAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(CodeAnalysisService.class);

    private final Cache<String, DependencyResult> dependencyCache = CacheFactory.createDefaultCache();

    private static final Set<String> DOWNLOADABLE_PATHS = ConcurrentHashMap.newKeySet();
    private static final int DOWNLOADABLE_CAP = 1000;

    private static final Set<String> EXTRA_SKIP_DIRS = new HashSet<>(Arrays.asList(
            ".git", ".svn", ".hg", "node_modules", "target", "build", "dist",
            ".idea", ".vscode", ".gradle", ".m2", ".cache",
            "__pycache__", ".venv", "venv", ".pytest_cache", ".next", ".nuxt"
    ));

    private static final String ROOT_FILES_MODULE = "(根目录文件)";

    public static void registerDownloadable(Path file) {
        if (file == null) return;
        if (DOWNLOADABLE_PATHS.size() >= DOWNLOADABLE_CAP) DOWNLOADABLE_PATHS.clear();
        DOWNLOADABLE_PATHS.add(file.toAbsolutePath().normalize().toString());
    }

    public static boolean isDownloadable(Path file) {
        if (file == null) return false;
        return DOWNLOADABLE_PATHS.contains(file.toAbsolutePath().normalize().toString());
    }

    // ==================== 代码统计 ====================

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
        map.put("useGitIgnore", false);
        String defaultMergePath = config.getDefaultOutputPath() != null
                ? config.getDefaultOutputPath().toString()
                : Paths.get(System.getProperty("user.dir"), "merged_outputs", "merged_"
                                                                              + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt").toString();
        map.put("mergeOutputFile", defaultMergePath);
        map.put("extraPaths", config.getExtraPaths() != null ? String.join(",", config.getExtraPaths()) : "");
        return map;
    }

    public String getDefaultConfigJson() {
        return JsonUtil.toJson(getDefaultConfig());
    }

    // ==================== 模块列表（保持原有逻辑） ====================

    public List<Map<String, Object>> getModules(Map<String, String> params) {
        try {
            Config config = buildConfig(params);
            Path rootDir = config.getRootDir().toAbsolutePath().normalize();
            if (!Files.isDirectory(rootDir)) return new ArrayList<>();

            List<String> extensions = config.getExtensions();
            if (extensions == null || extensions.isEmpty()) {
                extensions = Collections.singletonList("java");
            }
            final Set<String> extSet = new HashSet<>();
            for (String e : extensions) {
                if (e != null && !e.trim().isEmpty()) extSet.add(e.trim().toLowerCase());
            }

            List<Map<String, Object>> modules = new ArrayList<>();

            try (DirectoryStream<Path> ds = Files.newDirectoryStream(rootDir)) {
                for (Path p : ds) {
                    if (!Files.isDirectory(p)) continue;
                    String name = p.getFileName() == null ? "" : p.getFileName().toString();
                    if (name.isEmpty() || name.startsWith(".") || EXTRA_SKIP_DIRS.contains(name)) continue;

                    int[] stats = countFiles(p, extSet);
                    if (stats[0] == 0) continue;

                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", name);
                    m.put("path", p.toString());
                    m.put("fileCount", stats[0]);
                    m.put("totalBytes", stats[1]);
                    m.put("isRootFiles", false);
                    modules.add(m);
                }
            }

            int[] rootStats = countFilesFlat(rootDir, extSet);
            if (rootStats[0] > 0) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", ROOT_FILES_MODULE);
                m.put("path", rootDir.toString());
                m.put("fileCount", rootStats[0]);
                m.put("totalBytes", rootStats[1]);
                m.put("isRootFiles", true);
                modules.add(m);
            }

            modules.sort((a, b) -> Integer.compare(
                    (int) b.get("fileCount"), (int) a.get("fileCount")));
            return modules;
        } catch (Exception e) {
            log.error("获取模块列表失败", e);
            return new ArrayList<>();
        }
    }

    private int[] countFiles(Path dir, Set<String> extSet) {
        final int[] count = {0};
        final long[] bytes = {0};
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                    String n = d.getFileName() == null ? "" : d.getFileName().toString();
                    if (!d.equals(dir) && (EXTRA_SKIP_DIRS.contains(n) || n.startsWith("."))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) {
                    if (Files.isRegularFile(f)) {
                        String fn = f.getFileName().toString().toLowerCase();
                        int dot = fn.lastIndexOf('.');
                        if (dot > 0 && extSet.contains(fn.substring(dot + 1))) {
                            count[0]++;
                            bytes[0] += attrs.size();
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path f, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception ignored) {
        }
        return new int[]{count[0], (int) Math.min(bytes[0], Integer.MAX_VALUE)};
    }

    private int[] countFilesFlat(Path rootDir, Set<String> extSet) {
        final int[] count = {0};
        final long[] bytes = {0};
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(rootDir)) {
            for (Path p : ds) {
                if (!Files.isRegularFile(p)) continue;
                String fn = p.getFileName().toString().toLowerCase();
                int dot = fn.lastIndexOf('.');
                if (dot > 0 && extSet.contains(fn.substring(dot + 1))) {
                    count[0]++;
                    bytes[0] += Files.size(p);
                }
            }
        } catch (Exception ignored) {
        }
        return new int[]{count[0], (int) Math.min(bytes[0], Integer.MAX_VALUE)};
    }

    // ==================== ★ analyze() 已修复：传 rootDir 给 MergeFilesStage ====================

    public String analyze(Map<String, String> params) {
        try {
            Config config = buildConfig(params);
            long startTime = System.currentTimeMillis();

            CompositeFilter filterChain = buildFilterChain(config);
            List<Path> files = new CollectStage(filterChain).process(config.getRootDir());
            files = appendExtraFiles(files, params.get("extraPaths"));

            String modulesParam = params.get("modules");
            if (modulesParam != null && !modulesParam.trim().isEmpty()) {
                files = filterByModules(files, modulesParam, config.getRootDir());
            }

            ParallelAnalyzeStage analyzeStage = new ParallelAnalyzeStage(
                    config.getThreadCount(), config.isExcludeComments(), null);
            List<FileInfo> fileInfos = analyzeStage.process(files);
            fileInfos.sort(SorterRegistry.getComparator(config.getSortBy()));

            StatisticsResult result = new StatisticsResult();
            for (FileInfo info : fileInfos) result.addFileInfo(info);
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
                try {
                    Path mergePath = Paths.get(mergeOutputFile).toAbsolutePath().normalize();
                    // ★ 关键修复：把 rootDir / extraPathDirs 传给 MergeFilesStage，
                    //   使其写入相对路径，从而可被「代码导入」还原
                    List<Path> extraDirs = collectExtraDirs(params.get("extraPaths"));
                    new MergeFilesStage(config.getRootDir(), extraDirs, mergePath).process(files);
                    registerDownloadable(mergePath);
                    mergeOutputFile = mergePath.toString();
                    log.info("合并文件已生成（相对路径模式）: {}", mergeOutputFile);
                } catch (Exception e) {
                    log.error("合并文件失败: {}", mergeOutputFile, e);
                    mergeOutputFile = null;
                }
            }

            return buildResponseJson(fileInfos, result, mergeEnabled, mergeOutputFile);
        } catch (Exception e) {
            log.error("代码分析失败", e);
            return errorJson(e);
        }
    }

    /**
     * ★ 新增：把 extraPaths 中的「目录」收集起来，用于合并文件相对路径计算。
     * 单文件 extraPath 不纳入 base（会用文件名兜底，保证可导入）。
     */
    private List<Path> collectExtraDirs(String extraPathsStr) {
        List<Path> dirs = new ArrayList<>();
        if (extraPathsStr == null || extraPathsStr.trim().isEmpty()) return dirs;
        for (String raw : extraPathsStr.split(",")) {
            String s = raw.trim();
            if (s.isEmpty()) continue;
            try {
                Path p = Paths.get(s).toAbsolutePath().normalize();
                if (Files.isDirectory(p)) dirs.add(p);
            } catch (Exception ignored) {
            }
        }
        return dirs;
    }

    private List<Path> filterByModules(List<Path> files, String modulesParam, Path rootDir) {
        Path rootAbs = rootDir.toAbsolutePath().normalize();
        Set<Path> modulePaths = new LinkedHashSet<>();
        boolean includeRootFiles = false;

        for (String raw : modulesParam.split(",")) {
            String name = raw.trim();
            if (name.isEmpty()) continue;
            if (ROOT_FILES_MODULE.equals(name)) {
                includeRootFiles = true;
            } else {
                modulePaths.add(rootAbs.resolve(name).normalize());
            }
        }

        if (modulePaths.isEmpty() && !includeRootFiles) {
            return files;
        }

        final Set<Path> finalPaths = modulePaths;
        final boolean finalIncludeRoot = includeRootFiles;
        return files.stream()
                .filter(f -> {
                    Path abs = f.toAbsolutePath().normalize();
                    if (finalIncludeRoot && abs.getParent() != null
                            && abs.getParent().equals(rootAbs)) {
                        return true;
                    }
                    for (Path p : finalPaths) {
                        if (abs.startsWith(p)) return true;
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    // ==================== 依赖分析（保持原有逻辑） ====================

    private String generateDependencyCacheKey(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        sb.append(normalize(params.get("rootDir"))).append("|");
        sb.append(normalize(params.get("extensions"))).append("|");
        sb.append(normalize(params.get("excludePaths"))).append("|");
        sb.append(normalize(params.get("extraPaths"))).append("|");
        sb.append(normalize(params.get("threadCount"))).append("|");
        sb.append(normalize(params.get("excludeComments"))).append("|");
        sb.append(normalize(params.get("useGitIgnore")));
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) hex.append(String.format("%02x", digest[i]));
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(sb.toString().hashCode());
        }
    }

    private static String normalize(String s) { return s == null ? "" : s.trim(); }

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
            dependencyCache.put(cacheKey, result, 3600000L);
            return buildDependencyResponseJson(result, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("依赖分析失败", e);
            return errorJson(e);
        }
    }

    private DependencyResult doAnalyzeDependencies(Map<String, String> params) throws Exception {
        Config config = buildConfigForDependency(params);
        CompositeFilter filterChain = buildFilterChain(config);

        List<Path> files = new CollectStage(filterChain).process(config.getRootDir());
        files = appendExtraFiles(files, params.get("extraPaths"));

        if (files.isEmpty()) {
            return new DependencyResult(Collections.emptyMap(), new DependencyGraph(), 0L);
        }

        DependencyAnalysisStage stage = new DependencyAnalysisStage(config.getThreadCount());
        return stage.process(files);
    }

    private CompositeFilter buildFilterChain(Config config) {
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
        return filterChain;
    }

    private List<Path> appendExtraFiles(List<Path> existing, String extraPathsStr) throws Exception {
        if (extraPathsStr == null || extraPathsStr.trim().isEmpty()) return existing;
        Set<Path> merged = new LinkedHashSet<>(existing);
        for (String extraPath : extraPathsStr.split(",")) {
            String trimmed = extraPath.trim();
            if (trimmed.isEmpty()) continue;
            Path root = Paths.get(trimmed).toAbsolutePath().normalize();
            if (Files.isDirectory(root)) {
                Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (!dir.equals(root) && EXTRA_SKIP_DIRS.contains(dir.getFileName().toString())) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (Files.isRegularFile(file)) merged.add(file);
                        return FileVisitResult.CONTINUE;
                    }
                    @Override
                    public FileVisitResult visitFileFailed(Path file, java.io.IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else if (Files.isRegularFile(root)) {
                merged.add(root);
            }
        }
        return new ArrayList<>(merged);
    }

    // ==================== 响应序列化（保持原有逻辑） ====================

    private String buildDependencyResponseJson(DependencyResult result, long elapsedMs) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalClasses", result.getTotalClasses());
        response.put("averageOutgoingDependencies", result.getAverageOutgoingDependencies());
        response.put("maxOutgoingDependencies", result.getMaxOutgoingDependencies());
        response.put("maxOutgoingClasses", result.getMaxOutgoingClasses());
        response.put("durationMs", elapsedMs);
        response.put("failedFiles", result.getFailedFiles());

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

        Map<String, List<String>> graphMap = new LinkedHashMap<>();
        DependencyGraph graph = result.getGraph();
        for (String className : graph.getAllClasses()) {
            graphMap.put(className, new ArrayList<>(graph.getDependencies(className)));
        }
        response.put("graph", graphMap);

        List<List<String>> cyclesJson = new ArrayList<>();
        for (Set<String> cycle : result.getCycleGroups()) cyclesJson.add(new ArrayList<>(cycle));
        response.put("cycles", cyclesJson);
        return JsonUtil.toJson(response);
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

    private String errorJson(Throwable e) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("success", false);
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        err.put("error", root.getClass().getSimpleName() + ": " + root.getMessage());
        return JsonUtil.toJson(err);
    }

    // ==================== 外部查询接口（保持原有逻辑） ====================

    public DependencyResult getDependencyResult(Map<String, String> params) throws Exception {
        String cacheKey = generateDependencyCacheKey(params);
        DependencyResult cached = dependencyCache.get(cacheKey);
        if (cached != null) return cached;
        DependencyResult result = doAnalyzeDependencies(params);
        dependencyCache.put(cacheKey, result, 3600000L);
        return result;
    }

    public Map<String, Object> getDirectDependencies(String className, Map<String, String> params) throws Exception {
        DependencyResult result = getDependencyResult(params);
        Map<String, Object> resp = new HashMap<>();
        resp.put("className", className);
        resp.put("outgoing", result.getGraph().getDirectDependencies(className));
        return resp;
    }

    public Map<String, Object> getInverseDependencies(String className, Map<String, String> params) throws Exception {
        DependencyResult result = getDependencyResult(params);
        Map<String, Object> resp = new HashMap<>();
        resp.put("className", className);
        resp.put("incoming", result.getGraph().getInverseDependencies(className));
        return resp;
    }

    public Map<String, Object> getGraphDataFor3D(Map<String, String> params) throws Exception {
        DependencyResult result = getDependencyResult(params);
        List<Map<String, String>> nodes = new ArrayList<>();
        for (Map.Entry<String, ClassInfo> entry : result.getClassInfoMap().entrySet()) {
            Map<String, String> node = new HashMap<>();
            node.put("id", entry.getKey());
            node.put("type", entry.getValue().getType().name().toLowerCase());
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
        return graphData;
    }

    // ==================== 配置构建（保持原有逻辑） ====================

    private Config buildConfig(Map<String, String> params) {
        Config.Builder builder = new Config.Builder();
        String rootDir = params.get("rootDir");
        builder.rootDir(rootDir != null && !rootDir.isEmpty() ? Paths.get(rootDir) : Paths.get("."));
        String exts = params.get("extensions");
        if (exts != null && !exts.isEmpty()) builder.extensions(Arrays.asList(exts.split(",")));
        String exclude = params.get("excludePaths");
        if (exclude != null && !exclude.isEmpty()) builder.excludePaths(Arrays.asList(exclude.split(",")));
        builder.excludeComments("true".equalsIgnoreCase(params.get("excludeComments")));
        String threads = params.get("threadCount");
        if (threads != null && !threads.isEmpty()) builder.threadCount(Integer.parseInt(threads));
        String sort = params.get("sortBy");
        if (sort != null && !sort.isEmpty()) builder.sortBy(sort);
        String extraPathsStr = params.get("extraPaths");
        if (extraPathsStr != null && !extraPathsStr.isEmpty()) builder.extraPaths(Arrays.asList(extraPathsStr.split(",")));
        builder.useGitIgnore("true".equalsIgnoreCase(params.get("useGitIgnore")));
        return builder.build();
    }

    private Config buildConfigForDependency(Map<String, String> params) {
        Config.Builder builder = new Config.Builder();
        String rootDir = params.get("rootDir");
        builder.rootDir(rootDir != null && !rootDir.isEmpty() ? Paths.get(rootDir) : Paths.get("."));
        String exts = params.get("extensions");
        builder.extensions(exts != null && !exts.isEmpty() ? Arrays.asList(exts.split(",")) : Collections.singletonList("java"));
        String exclude = params.get("excludePaths");
        if (exclude != null && !exclude.isEmpty()) builder.excludePaths(Arrays.asList(exclude.split(",")));
        builder.excludeComments("true".equalsIgnoreCase(params.get("excludeComments")));
        String threads = params.get("threadCount");
        builder.threadCount(threads != null && !threads.isEmpty() ? Integer.parseInt(threads) : Runtime.getRuntime().availableProcessors());
        builder.sortBy("name");
        builder.useGitIgnore("true".equalsIgnoreCase(params.get("useGitIgnore")));
        return builder.build();
    }
}