// 完整文件路径: com.demo.business.assistance.codestat.service.CodeAnalysisService

package com.codestats.business.assistance.codestat.service;

import com.codestats.lib.codestats.config.Config;
import com.codestats.lib.codestats.config.ConfigLoader;
import com.codestats.lib.codestats.core.FileInfo;
import com.codestats.lib.codestats.core.StatisticsResult;
import com.codestats.lib.codestats.filter.CompositeFilter;
import com.codestats.lib.codestats.filter.ExcludePathFilter;
import com.codestats.lib.codestats.filter.ExtensionFilter;
import com.codestats.lib.codestats.filter.GitIgnoreFilter;
import com.codestats.lib.codestats.pipeline.*;
import com.codestats.lib.codestats.sorter.SorterRegistry;
import com.framework.spring.annotation.Service;
import com.framework.util.JsonUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CodeAnalysisService {

    /**
     * 获取默认配置（读取 CodeStats 的 yaml / 环境变量等）
     */

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
            // 默认合并输出路径
            String defaultMergePath = config.getDefaultOutputPath() != null ?
                    config.getDefaultOutputPath().toString() :
                    Paths.get(System.getProperty("user.dir"), "merged_outputs", "merged_" +
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt").toString();
            map.put("mergeOutputFile", defaultMergePath);
            // ★ 新增：返回 extraPaths 默认值
            map.put("extraPaths", config.getExtraPaths() != null ? String.join(",", config.getExtraPaths()) : "");
            return map;
        }


    /**
     * 返回默认配置的 JSON 字符串
     */
    public String getDefaultConfigJson() {
        return JsonUtil.toJson(getDefaultConfig());
    }

    public String analyze(Map<String, String> params) {
        try {
            Config config = buildConfig(params);

            long startTime = System.currentTimeMillis();

            // 1. 构建过滤器链
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

            // 2. 收集主目录文件
            CollectStage collectStage = new CollectStage(filterChain);
            List<Path> files = collectStage.process(config.getRootDir());

            // ★ 新增：处理额外路径 (extraPaths)
            String extraPathsStr = params.get("extraPaths");
            if (extraPathsStr != null && !extraPathsStr.trim().isEmpty()) {
                String[] extraPaths = extraPathsStr.split(",");
                Set<Path> extraFileSet = new LinkedHashSet<>();
                for (String extraPath : extraPaths) {
                    Path path = Paths.get(extraPath.trim());
                    if (Files.isDirectory(path)) {
                        // 递归收集目录下所有文件（注意，这里不应用过滤器？为了灵活性，可以复用过滤器）
                        // 简单起见，先收集所有文件，后续可再应用过滤器
                        Files.walk(path)
                                .filter(Files::isRegularFile)
                                .forEach(extraFileSet::add);
                    } else if (Files.isRegularFile(path)) {
                        extraFileSet.add(path);
                    }
                }
                if (!extraFileSet.isEmpty()) {
                    // 合并并去重（保留原有文件顺序，额外文件追加）
                    Set<Path> mergedSet = new LinkedHashSet<>(files);
                    mergedSet.addAll(extraFileSet);
                    files = new ArrayList<>(mergedSet);
                }
            }

            // 3. 并行分析
            ParallelAnalyzeStage analyzeStage = new ParallelAnalyzeStage(
                    config.getThreadCount(), config.isExcludeComments(), null);
            List<FileInfo> fileInfos = analyzeStage.process(files);

            // 4. 排序
            fileInfos.sort(SorterRegistry.getComparator(config.getSortBy()));

            // 5. 汇总
            StatisticsResult result = new StatisticsResult();
            for (FileInfo info : fileInfos) {
                result.addFileInfo(info);
            }
            result.setDurationMs(System.currentTimeMillis() - startTime);

            // 6. 合并文件（默认启用）
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
                new MergeFilesStage(mergePath).process(files);
            }

            // 7. 构建响应 JSON
            return buildResponseJson(fileInfos, result, mergeEnabled, mergeOutputFile);
        } catch (Exception e) {
            Map<String, String> err = new LinkedHashMap<>();
            err.put("error", e.getMessage());
            return JsonUtil.toJson(err);
        }
    }

    // 在 buildConfig 中新增 extraPaths 解析（可选，但为了 Config 完整性可以加上）
    private Config buildConfig(Map<String, String> params) {
        Config.Builder builder = new Config.Builder();

        // rootDir
        String rootDir = params.get("rootDir");
        if (rootDir != null && !rootDir.isEmpty()) {
            builder.rootDir(Paths.get(rootDir));
        } else {
            builder.rootDir(Paths.get("."));
        }

        // extensions
        String exts = params.get("extensions");
        if (exts != null && !exts.isEmpty()) {
            builder.extensions(Arrays.asList(exts.split(",")));
        }

        // excludePaths
        String exclude = params.get("excludePaths");
        if (exclude != null && !exclude.isEmpty()) {
            builder.excludePaths(Arrays.asList(exclude.split(",")));
        }

        // excludeComments
        builder.excludeComments("true".equalsIgnoreCase(params.get("excludeComments")));

        // threadCount
        String threads = params.get("threadCount");
        if (threads != null && !threads.isEmpty()) {
            builder.threadCount(Integer.parseInt(threads));
        }

        // sortBy
        String sort = params.get("sortBy");
        if (sort != null && !sort.isEmpty()) {
            builder.sortBy(sort);
        }

        // extraPaths (添加到 Config 中，以便后续可能使用)
        String extraPathsStr = params.get("extraPaths");
        if (extraPathsStr != null && !extraPathsStr.isEmpty()) {
            builder.extraPaths(Arrays.asList(extraPathsStr.split(",")));
        }

        // 默认启用 gitignore
        builder.useGitIgnore(true);

        return builder.build();
    }

    /**
     * 构造最终返回 JSON
     */
    private String buildResponseJson(List<FileInfo> fileInfos, StatisticsResult result,
                                     boolean mergeEnabled, String mergeOutputFile) {
        Map<String, Object> response = new LinkedHashMap<>();

        // 文件列表
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

        // 汇总
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalFiles", result.getFileInfos().size());
        summary.put("totalLines", result.getTotalLines());
        summary.put("totalCodeLines", result.getTotalCodeLines());
        summary.put("totalCommentLines", result.getTotalCommentLines());
        summary.put("totalBlankLines", result.getTotalBlankLines());
        summary.put("durationMs", result.getDurationMs());
        response.put("summary", summary);

        // 语言统计
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
}