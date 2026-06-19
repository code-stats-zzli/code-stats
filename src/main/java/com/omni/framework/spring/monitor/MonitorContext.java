package com.omni.framework.spring.monitor;

import java.util.ArrayList;
import java.util.List;

/**
 * 监控上下文，使用 ThreadLocal 存储当前请求的调用信息
 */
public class MonitorContext {

    private static final ThreadLocal<MonitorData> THREAD_LOCAL = new ThreadLocal<>();

    public static void startInvocation(String methodName) {
        MonitorData data = THREAD_LOCAL.get();
        if (data == null) {
            data = new MonitorData();
            THREAD_LOCAL.set(data);
        }
        data.addInvocation(new InvocationInfo(methodName, System.currentTimeMillis()));
    }

    public static void endInvocation(String methodName, long duration, boolean success, Throwable error) {
        MonitorData data = THREAD_LOCAL.get();
        if (data != null) {
            data.updateLastInvocation(duration, success, error);
        }
    }

    public static MonitorData getCurrentData() {
        MonitorData data = THREAD_LOCAL.get();
        if (data == null) {
            data = new MonitorData();
            THREAD_LOCAL.set(data);
        }
        return data;
    }

    public static void clear() {
        THREAD_LOCAL.remove();
    }

    /**
     * 监控数据对象（可扩展）
     */
    public static class MonitorData {
        private final List<InvocationInfo> invocations = new ArrayList<>();
        private long startTime = System.currentTimeMillis();

        public void addInvocation(InvocationInfo info) {
            invocations.add(info);
        }

        public void updateLastInvocation(long duration, boolean success, Throwable error) {
            if (!invocations.isEmpty()) {
                InvocationInfo last = invocations.get(invocations.size() - 1);
                last.duration = duration;
                last.success = success;
                last.error = error;
            }
        }

        public List<InvocationInfo> getInvocations() {
            return invocations;
        }

        public long getStartTime() {
            return startTime;
        }
    }

    public static class InvocationInfo {
        public final String methodName;
        public final long startTime;
        public long duration;
        public boolean success;
        public Throwable error;

        public InvocationInfo(String methodName, long startTime) {
            this.methodName = methodName;
            this.startTime = startTime;
        }
    }
}