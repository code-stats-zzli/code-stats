package com.codestats.lib.codestats.gui;

import com.codestats.lib.codestats.core.FileInfo;
import com.codestats.lib.codestats.core.ProgressMonitor;
import com.codestats.lib.codestats.core.StatisticsResult;
import com.codestats.lib.codestats.filter.CompositeFilter;
import com.codestats.lib.codestats.filter.ExcludePathFilter;
import com.codestats.lib.codestats.filter.ExtensionFilter;
import com.codestats.lib.codestats.pipeline.CollectStage;
import com.codestats.lib.codestats.pipeline.MergeFilesStage;
import com.codestats.lib.codestats.pipeline.ParallelAnalyzeStage;
import com.codestats.lib.codestats.sorter.SorterRegistry;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class CodeStatsController {
    private final CodeStatsGUIPanel panel;

    public CodeStatsController(CodeStatsGUIPanel panel) {
        this.panel = panel;
    }

    public void startAnalysis() {
        panel.setStartEnabled(false);
        panel.clearResults();
        panel.resetProgress();

        String rootDir = panel.getRootDir();
        List<String> extensions = Arrays.asList(panel.getExtensionsText());
        String text = panel.getExcludePathsText();
        List<String> excludePaths = (text == null || text.trim().isEmpty()) ? new ArrayList<>() : Arrays.asList(panel.getExcludePathsText());
        boolean excludeComments = panel.isExcludeComments();
        int threads = panel.getThreadCount();
        String sortBy = panel.getSortBy();

        CompositeFilter filterChain = new CompositeFilter();
        if (!extensions.isEmpty()) filterChain.add(new ExtensionFilter(extensions));
        if (!excludePaths.isEmpty()) filterChain.add(new ExcludePathFilter(excludePaths));

        new SwingWorker<StatisticsResult, Void>() {
            private long startTime;
            private int directoryCount;
            private int totalFiles;

            @Override
            protected StatisticsResult doInBackground() throws Exception {
                CollectStage collectStage = new CollectStage(filterChain);
                List<Path> files = collectStage.process(Paths.get(rootDir));
                if (files.isEmpty()) {
                    throw new Exception("No matching files found.");
                }

                // ★ 收集额外路径的文件并去重追加
                String extraPathsStr = panel.getExtraPathsText();
                if (extraPathsStr != null && !extraPathsStr.trim().isEmpty()) {
                    String[] extraPaths = extraPathsStr.split(",");
                    List<Path> extraFiles = new ArrayList<>();
                    for (String extraPath : extraPaths) {
                        Path path = Paths.get(extraPath.trim());
                        if (Files.isDirectory(path)) {
                            extraFiles.addAll(Files.walk(path)
                                    .filter(Files::isRegularFile)
                                    .collect(Collectors.toList()));
                        } else if (Files.isRegularFile(path)) {
                            extraFiles.add(path);
                        }
                    }
                    if (!extraFiles.isEmpty()) {
                        Set<Path> mergedSet = new LinkedHashSet<>(files);
                        mergedSet.addAll(extraFiles);
                        files = new ArrayList<>(mergedSet);
                    }
                }

                // 合并文件输出（在收集完成后立即执行）
                if (panel.isMergeEnabled()) {
                    String mergePathStr = panel.getMergeOutputFile();
                    if (mergePathStr != null && !mergePathStr.trim().isEmpty()) {
                        Path mergePath = Paths.get(mergePathStr.trim());
                        try {
                            new MergeFilesStage(mergePath).process(files);
                        } catch (Exception e) {
                            System.err.println("Merge failed: " + e.getMessage());
                        }
                    }
                }

                directoryCount = (int) files.stream().map(Path::getParent).distinct().count();
                totalFiles = files.size();
                SwingUtilities.invokeLater(() -> {
                    panel.updateTotalFiles(totalFiles);
                    panel.updateDirectoryCount(directoryCount);
                });

                ProgressMonitor monitor = new ProgressMonitor(totalFiles);
                monitor.addFileProgressListener((processed, currentPath) -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    SwingUtilities.invokeLater(() -> {
                        int percent = (int) (processed * 100 / totalFiles);
                        String elapsedStr = formatDuration(elapsed);
                        String etaStr = "--:--";
                        if (processed > 0 && processed < totalFiles) {
                            long etaMs = (elapsed * (totalFiles - processed)) / processed;
                            etaStr = formatDuration(etaMs);
                        }
                        panel.updateProgress(percent,
                                currentPath != null ? currentPath.getFileName().toString() : "-",
                                elapsedStr, etaStr);
                    });
                });

                startTime = System.currentTimeMillis();

                ParallelAnalyzeStage analyzeStage = new ParallelAnalyzeStage(threads, excludeComments, monitor);
                List<FileInfo> fileInfos = analyzeStage.process(files);

                fileInfos.sort(SorterRegistry.getComparator(sortBy));
                StatisticsResult result = new StatisticsResult();
                for (FileInfo info : fileInfos) {
                    result.addFileInfo(info);
                }
                result.setDurationMs(System.currentTimeMillis() - startTime);
                return result;
            }

            @Override
            protected void done() {
                try {
                    StatisticsResult result = get();
                    panel.getTableModel().setFileInfos(result.getFileInfos(), result);

                    StringBuilder sb = new StringBuilder();
                    sb.append("========== Summary ==========\n");
                    sb.append("Total files: ").append(result.getFileInfos().size()).append("\n");
                    sb.append("Total lines: ").append(result.getTotalLines()).append("\n");
                    sb.append("  Code lines: ").append(result.getTotalCodeLines()).append("\n");
                    sb.append("  Comment lines: ").append(result.getTotalCommentLines()).append("\n");
                    sb.append("  Blank lines: ").append(result.getTotalBlankLines()).append("\n");
                    sb.append("Time: ").append(result.getDurationMs()).append(" ms\n\n");
                    sb.append("---------- By Language ----------\n");
                    result.getLanguageSummary().forEach((lang, sum) -> {
                        sb.append(String.format("%-12s : files=%d, lines=%d, code=%d, comment=%d, blank=%d\n",
                                lang, sum.getFiles(), sum.getLines(), sum.getCode(),
                                sum.getComments(), sum.getBlanks()));
                    });
                    panel.setSummaryText(sb.toString());
                    panel.setProgressDone();
                } catch (InterruptedException | ExecutionException e) {
                    JOptionPane.showMessageDialog(panel,
                            "Analysis failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    panel.setStartEnabled(true);
                }
            }
        }.execute();
    }

    private String formatDuration(long millis) {
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}