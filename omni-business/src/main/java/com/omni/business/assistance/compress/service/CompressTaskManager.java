package com.omni.business.assistance.compress.service;

import com.omni.framework.unzip.ArchiveType;
import com.omni.framework.unzip.CompressResult;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CompressTaskManager {
    private static final Map<String, TaskContext> TASKS = new ConcurrentHashMap<>();
    private static final long RETENTION_MS = 30 * 60 * 1000L;

    public static class TaskContext {
        public final String taskId;
        public final long startTime;
        public final String archiveFileName;
        public volatile CompressResult result;
        public volatile Path archiveFile;
        public volatile Path mergedFile;
        public volatile String state = "COMPLETED";
        public volatile String errorMessage;
        public volatile long endTime;

        TaskContext(String taskId, String archiveFileName) {
            this.taskId = taskId;
            this.archiveFileName = archiveFileName;
            this.startTime = System.currentTimeMillis();
        }
    }

    public static String registerCompleted(CompressResult result, Path archiveFile,
                                           Path mergedFile, String archiveFileName) {
        cleanupOld();
        String taskId = UUID.randomUUID().toString().replace("-", "");
        TaskContext ctx = new TaskContext(taskId, archiveFileName);
        ctx.result = result;
        ctx.archiveFile = archiveFile;
        ctx.mergedFile = mergedFile;
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
        m.put("archiveFileName", ctx.archiveFileName);
        m.put("startTime", ctx.startTime);
        m.put("endTime", ctx.endTime);
        m.put("errorMessage", ctx.errorMessage);
        if (ctx.result != null) {
            ArchiveType t = ctx.result.getType();
            m.put("archiveType", t != null ? t.name() : null);
            m.put("fileCount", ctx.result.getFileCount());
            m.put("rawBytes", ctx.result.getRawBytes());
            m.put("archiveBytes", ctx.result.getArchiveBytes());
            m.put("durationMs", ctx.result.getDurationMs());
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