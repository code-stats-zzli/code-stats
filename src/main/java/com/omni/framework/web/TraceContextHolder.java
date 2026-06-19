package com.omni.framework.web;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局请求跟踪上下文，以线程ID为键存储每个请求的链路信息
 * 与原有 RequestContextHolder（Spring MVC 请求持有）区分开
 */
public class TraceContextHolder {

    private static final Map<Long, TraceContext> CONTEXT_MAP = new ConcurrentHashMap<>();

    public static class TraceContext {
        public final long threadId;
        public final String uri;
        public final long startTime;
        public volatile long endTime;
        public volatile String error;
        public volatile String nodePath; // 调用链，如 "Dispatcher->UserController->UserService->UserMapper"

        public TraceContext(long threadId, String uri) {
            this.threadId = threadId;
            this.uri = uri;
            this.startTime = System.currentTimeMillis();
            this.nodePath = "Dispatcher";
        }

        public void appendNode(String node) {
            if (nodePath == null || nodePath.isEmpty()) {
                nodePath = node;
            } else {
                nodePath += "->" + node;
            }
        }
    }

    public static void putContext(long threadId, String uri) {
        CONTEXT_MAP.put(threadId, new TraceContext(threadId, uri));
    }

    public static TraceContext getContext(long threadId) {
        return CONTEXT_MAP.get(threadId);
    }

    public static TraceContext getCurrentContext() {
        return getContext(Thread.currentThread().getId());
    }

    public static void updateContext(long threadId, String error, String extraNode) {
        TraceContext ctx = CONTEXT_MAP.get(threadId);
        if (ctx != null) {
            ctx.endTime = System.currentTimeMillis();
            if (error != null) ctx.error = error;
            if (extraNode != null && !extraNode.isEmpty()) {
                ctx.appendNode(extraNode);
            }
        }
    }

    public static void removeContext(long threadId) {
        CONTEXT_MAP.remove(threadId);
    }

    public static Map<Long, TraceContext> getAllContexts() {
        return CONTEXT_MAP;
    }
}