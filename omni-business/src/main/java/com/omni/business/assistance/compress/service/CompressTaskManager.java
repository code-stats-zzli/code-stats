package com.omni.business.assistance.compress.service;

import com.omni.business.common.task.TaskInfo;
import com.omni.business.common.task.TaskRegistry;
import com.omni.common.util.RandomUtils;
import com.omni.framework.unzip.ArchiveType;
import com.omni.framework.unzip.CompressResult;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 压缩任务管理。
 * <p>★ L2-02：改用 TaskRegistry 统一并发管理。</p>
 */
public class CompressTaskManager {

    public static class TaskContext implements TaskInfo {
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

        @Override public String getTaskId() { return taskId; }
        @Override public long getStartTime() { return startTime; }
        @Override public long getEndTime() { return endTime; }
        @Override public String getState() { return state; }
        @Override public String getErrorMessage() { return errorMessage; }

        @Override
        public Map<String, Object> toStatusMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("archiveFileName", archiveFileName);
            if (result != null) {
                ArchiveType t = result.getType();
                m.put("archiveType", t != null ? t.name() : null);
                m.put("fileCount", result.getFileCount());
                m.put("rawBytes", result.getRawBytes());
                m.put("archiveBytes", result.getArchiveBytes());
                m.put("durationMs", result.getDurationMs());
            }
            return m;
        }
    }

    private static final TaskRegistry<TaskContext> REGISTRY = new TaskRegistry<>(30 * 60 * 1000L);

    public static String registerCompleted(CompressResult result, Path archiveFile,
                                           Path mergedFile, String archiveFileName) {
        String taskId = RandomUtils.uuidNoDash();
        TaskContext ctx = new TaskContext(taskId, archiveFileName);
        ctx.result = result;
        ctx.archiveFile = archiveFile;
        ctx.mergedFile = mergedFile;
        ctx.endTime = System.currentTimeMillis();
        REGISTRY.register(ctx);
        return taskId;
    }

    public static TaskContext get(String taskId) { return REGISTRY.get(taskId); }

    public static Map<String, Object> getStatus(String taskId) { return REGISTRY.getStatus(taskId); }

    public static void cleanupOld() { REGISTRY.cleanupOld(); }
}