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
import com.framework.log.LoggerFactory;
import com.framework.log.core.Logger;

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
}