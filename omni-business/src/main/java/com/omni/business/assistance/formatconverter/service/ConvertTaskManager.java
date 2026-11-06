package com.omni.business.assistance.formatconverter.service;

import com.omni.business.common.converter.TempFileManager;
import com.omni.business.common.task.TaskInfo;
import com.omni.business.common.task.TaskRegistry;
import com.omni.common.util.RandomUtils;
import com.omni.common.util.ThreadPoolUtils;
import com.omni.framework.converter.ConvertRequest;
import com.omni.framework.converter.ConvertResult;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 异步转换任务管理。
 * <p>★ L2-02：改用 TaskRegistry。</p>
 */
public class ConvertTaskManager {
    private static final Logger log = LoggerFactory.getLogger(ConvertTaskManager.class);

    public static class TaskContext implements TaskInfo {
        public final String taskId;
        public final long startTime;
        public volatile long endTime;
        public volatile String state = "RUNNING";
        public volatile String errorMessage;
        public volatile ConvertResult result;
        public volatile String storedName;

        TaskContext(String taskId) {
            this.taskId = taskId;
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
            m.put("storedName", storedName);
            if (result != null && result.isSuccess()) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("targetType", result.getTargetType().ext);
                r.put("fileName", result.getSuggestedFileName());
                r.put("size", result.size());
                r.put("meta", result.getMeta());
                m.put("result", r);
            }
            return m;
        }
    }

    private static final TaskRegistry<TaskContext> REGISTRY = new TaskRegistry<>(30 * 60 * 1000L);

    public static String submit(ConvertRequest req, Function<ConvertRequest, ConvertResult> fn) {
        final String taskId = RandomUtils.uuidNoDash();
        final TaskContext ctx = new TaskContext(taskId);
        REGISTRY.register(ctx);
        Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    ConvertResult r = fn.apply(req);
                    ctx.result = r;
                    if (r.isSuccess()) {
                        try {
                            ctx.storedName = TempFileManager.store(r);
                            ctx.state = "COMPLETED";
                        } catch (Exception e) {
                            ctx.state = "ERROR";
                            ctx.errorMessage = "结果落盘失败: " + e.getMessage();
                        }
                    } else {
                        ctx.state = "ERROR";
                        ctx.errorMessage = r.getMessage();
                    }
                } catch (Exception e) {
                    log.error("异步转换异常", e);
                    ctx.state = "ERROR";
                    ctx.errorMessage = e.getMessage();
                } finally {
                    ctx.endTime = System.currentTimeMillis();
                }
            }
        };
        ThreadPoolUtils.daemonThreadFactory("convert-task-" + taskId).newThread(task).start();
        log.info("创建异步转换任务: taskId={}, {}→{}", taskId,
                req.getSourceType(), req.getTargetType());
        return taskId;
    }

    public static Map<String, Object> getStatus(String taskId) { return REGISTRY.getStatus(taskId); }
    public static void cleanupOld() { REGISTRY.cleanupOld(); }
}