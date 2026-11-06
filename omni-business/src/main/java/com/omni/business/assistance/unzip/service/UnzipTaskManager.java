package com.omni.business.assistance.unzip.service;

import com.omni.business.common.task.TaskInfo;
import com.omni.business.common.task.TaskRegistry;
import com.omni.common.util.RandomUtils;
import com.omni.framework.unzip.ArchiveType;
import com.omni.framework.unzip.ExtractResult;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 解压任务管理。
 * <p>★ L2-02：改用 TaskRegistry。</p>
 */
public class UnzipTaskManager {

    public static class TaskContext implements TaskInfo {
        public final String taskId;
        public final long startTime;
        public final String originalFileName;
        public volatile ExtractResult result;
        public volatile Path outputDir;
        public volatile Path mergedFile;
        public volatile Path zipFile;
        public volatile String state = "RUNNING";
        public volatile String errorMessage;
        public volatile long endTime;

        TaskContext(String taskId, String originalFileName) {
            this.taskId = taskId;
            this.originalFileName = originalFileName;
            this.startTime = System.currentTimeMillis();
        }

        @Override public String getTaskId() { return taskId; }
        @Override public long getStartTime() { return startTime; }
        @Override public long getEndTime() { return endTime; }
        @Override public String getState() { return state; }
        @Override public String getErrorMessage() { return errorMessage; }

        @Override
        public Map<String, Object> toStatusMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("originalFileName", originalFileName);
            if (result != null) {
                ArchiveType t = result.getType();
                m.put("archiveType", t != null ? t.name() : null);
                m.put("totalFiles", result.getTotalFiles());
                m.put("totalDirs", result.getTotalDirs());
                m.put("binaryFiles", result.getBinaryFiles());
                m.put("totalBytes", result.getTotalBytes());
                m.put("durationMs", result.getDurationMs());
                m.put("entries", result.getEntries());
            }
            return m;
        }
    }

    private static final TaskRegistry<TaskContext> REGISTRY = new TaskRegistry<>(30 * 60 * 1000L);

    public static String registerCompleted(ExtractResult result, String originalFileName) {
        String taskId = RandomUtils.uuidNoDash();
        TaskContext ctx = new TaskContext(taskId, originalFileName);
        ctx.result = result;
        ctx.outputDir = result != null ? result.getOutputDir() : null;
        ctx.state = "COMPLETED";
        ctx.endTime = System.currentTimeMillis();
        REGISTRY.register(ctx);
        return taskId;
    }

    public static TaskContext get(String taskId) { return REGISTRY.get(taskId); }
    public static Map<String, Object> getStatus(String taskId) { return REGISTRY.getStatus(taskId); }
    public static void cleanupOld() { REGISTRY.cleanupOld(); }
}