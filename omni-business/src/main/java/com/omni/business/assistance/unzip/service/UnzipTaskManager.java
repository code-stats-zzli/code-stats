package com.omni.business.assistance.unzip.service;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.unzip.ArchiveType;
import com.omni.framework.unzip.ExtractResult;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UnzipTaskManager {
    private static final Logger log = LoggerFactory.getLogger(UnzipTaskManager.class);
    private static final Map<String, TaskContext> TASKS = new ConcurrentHashMap<>();
    private static final long RETENTION_MS = 30 * 60 * 1000L;

    public static class TaskContext {
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
        public long getElapsedMillis() {
            return (endTime > 0 ? endTime : System.currentTimeMillis()) - startTime;
        }
    }

    public static String registerCompleted(ExtractResult result, String originalFileName) {
        cleanupOld();
        String taskId = UUID.randomUUID().toString().replace("-", "");
        TaskContext ctx = new TaskContext(taskId, originalFileName);
        ctx.result = result;
        ctx.outputDir = result != null ? result.getOutputDir() : null;
        ctx.state = "COMPLETED";
        ctx.endTime = System.currentTimeMillis();
        TASKS.put(taskId, ctx);
        return taskId;
    }

    public static TaskContext get(String taskId) { return TASKS.get(taskId); }

    public static Map<String, Object> getStatus(String taskId) {
        cleanupOld();
        TaskContext ctx = TASKS.get(taskId);
        if (ctx == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", taskId);
        m.put("state", ctx.state);
        m.put("originalFileName", ctx.originalFileName);
        m.put("startTime", ctx.startTime);
        m.put("endTime", ctx.endTime);
        m.put("elapsedMs", ctx.getElapsedMillis());
        m.put("errorMessage", ctx.errorMessage);
        if (ctx.result != null) {
            ArchiveType t = ctx.result.getType();
            m.put("archiveType", t != null ? t.name() : null);
            m.put("totalFiles", ctx.result.getTotalFiles());
            m.put("totalDirs", ctx.result.getTotalDirs());
            m.put("binaryFiles", ctx.result.getBinaryFiles());
            m.put("totalBytes", ctx.result.getTotalBytes());
            m.put("durationMs", ctx.result.getDurationMs());
            m.put("entries", ctx.result.getEntries());
        }
        return m;
    }

    public static void cleanupOld() {
        long now = System.currentTimeMillis();
        TASKS.entrySet().removeIf(e -> {
            TaskContext c = e.getValue();
            return c.endTime > 0 && (now - c.endTime > RETENTION_MS);
        });
    }
}