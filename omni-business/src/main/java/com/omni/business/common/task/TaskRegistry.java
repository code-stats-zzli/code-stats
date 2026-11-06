package com.omni.business.common.task;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用异步任务注册中心。
 *
 * <p>收敛 CompressTaskManager / UnzipTaskManager / ConvertTaskManager / SystemCheckTaskManager
 * 里 4 份同构的 TASKS + register + get + getStatus + cleanupOld 骨架。</p>
 *
 * <p>并发正确性说明：</p>
 * <ul>
 *   <li>登记：{@code ConcurrentHashMap.put}，无竞态</li>
 *   <li>清理：{@code ConcurrentHashMap.entrySet().removeIf} 是原子操作</li>
 *   <li>取消：调用方传入 {@code canceller}，由子类控制取消语义</li>
 * </ul>
 */
public final class TaskRegistry<T extends TaskInfo> {

    private final Map<String, T> tasks = new ConcurrentHashMap<>();
    private final long retentionMs;

    public TaskRegistry(long retentionMs) {
        this.retentionMs = retentionMs;
    }

    // ==================== 注册 / 查询 ====================

    public T register(T task) {
        if (task == null || task.getTaskId() == null) {
            throw new IllegalArgumentException("task / taskId 不能为空");
        }
        cleanupOld();
        tasks.put(task.getTaskId(), task);
        return task;
    }

    public T get(String taskId) {
        return taskId == null ? null : tasks.get(taskId);
    }

    /** 返回通用状态 Map；任务特有字段由 task.toStatusMap() 合并 */
    public Map<String, Object> getStatus(String taskId) {
        cleanupOld();
        T task = get(taskId);
        if (task == null) return null;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", task.getTaskId());
        m.put("state", task.getState());
        m.put("startTime", task.getStartTime());
        m.put("endTime", task.getEndTime());
        long end = task.getEndTime() > 0 ? task.getEndTime() : System.currentTimeMillis();
        m.put("elapsedMs", end - task.getStartTime());
        m.put("errorMessage", task.getErrorMessage());

        Map<String, Object> extra = task.toStatusMap();
        if (extra != null) m.putAll(extra);
        return m;
    }

    // ==================== 取消 ====================

    public boolean cancel(String taskId, Runnable canceller) {
        T task = get(taskId);
        if (task == null || task.isTerminated()) return false;
        try {
            if (canceller != null) canceller.run();
        } catch (Exception ignored) {}
        return true;
    }

    // ==================== 清理 ====================

    public void cleanupOld() {
        if (retentionMs <= 0) return;
        long now = System.currentTimeMillis();
        tasks.entrySet().removeIf(e -> {
            T t = e.getValue();
            return t.getEndTime() > 0 && (now - t.getEndTime() > retentionMs);
        });
    }

    public int size() {
        return tasks.size();
    }

    public Map<String, T> asMap() {
        return Collections.unmodifiableMap(tasks);
    }
}