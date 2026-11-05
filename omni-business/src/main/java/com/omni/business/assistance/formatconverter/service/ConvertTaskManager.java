package com.omni.business.assistance.formatconverter.service;

import com.omni.business.common.converter.TempFileManager;
import com.omni.framework.converter.ConvertRequest;
import com.omni.framework.converter.ConvertResult;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 大文件异步转换任务管理。任务保留 30 分钟。
 */
public class ConvertTaskManager {
    private static final Logger log = LoggerFactory.getLogger(ConvertTaskManager.class);
    private static final Map<String, TaskContext> TASKS = new ConcurrentHashMap<>();
    private static final long RETENTION_MS = 30 * 60 * 1000L;

    public static class TaskContext {
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
        public long getElapsedMillis() {
            return (endTime > 0 ? endTime : System.currentTimeMillis()) - startTime;
        }
    }

    public static String submit(ConvertRequest req, Function<ConvertRequest, ConvertResult> fn) {
        cleanupOld();
        final String taskId = UUID.randomUUID().toString().replace("-", "");
        final TaskContext ctx = new TaskContext(taskId);
        TASKS.put(taskId, ctx);
        Thread t = new Thread(new Runnable() {
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
        }, "convert-task-" + taskId);
        t.setDaemon(true);
        t.start();
        log.info("创建异步转换任务: taskId={}, {}→{}", taskId,
                req.getSourceType(), req.getTargetType());
        return taskId;
    }

    public static Map<String, Object> getStatus(String taskId) {
        cleanupOld();
        TaskContext ctx = TASKS.get(taskId);
        if (ctx == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", taskId);
        m.put("state", ctx.state);
        m.put("startTime", ctx.startTime);
        m.put("endTime", ctx.endTime);
        m.put("elapsedMs", ctx.getElapsedMillis());
        m.put("errorMessage", ctx.errorMessage);
        m.put("storedName", ctx.storedName);
        if (ctx.result != null && ctx.result.isSuccess()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("targetType", ctx.result.getTargetType().ext);
            r.put("fileName", ctx.result.getSuggestedFileName());
            r.put("size", ctx.result.size());
            r.put("meta", ctx.result.getMeta());
            m.put("result", r);
        }
        return m;
    }

    private static void cleanupOld() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, TaskContext>> it = TASKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, TaskContext> e = it.next();
            TaskContext c = e.getValue();
            if (c.endTime > 0 && (now - c.endTime > RETENTION_MS)) {
                it.remove();
            }
        }
    }
}