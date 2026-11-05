// ======== D:\space\git\CodeStats\src\main\java\com\omni\business\assistance\three3d\service\JvmMonitorService.java ========
package com.omni.business.assistance.three3d.service;

import com.omni.framework.jdbc.DataSourceUtils;
import com.omni.framework.spring.annotation.Service;
import com.omni.framework.spring.monitor.MonitorContext;
import com.omni.framework.web.TraceContextHolder;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.*;

@Service
public class JvmMonitorService {

    public Map<String, Object> getCurrentThreadMonitorData() {
        MonitorContext.MonitorData data = MonitorContext.getCurrentData();
        Map<String, Object> result = new HashMap<>();
        result.put("invocations", data.getInvocations());
        result.put("startTime", data.getStartTime());
        result.put("threadName", Thread.currentThread().getName());
        result.put("threadId", Thread.currentThread().getId());
        return result;
    }

    public Map<String, Object> getAllThreadsInfo() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        long[] threadIds = threadMXBean.getAllThreadIds();
        ThreadInfo[] threadInfos = threadMXBean.getThreadInfo(threadIds, 0);
        int running = 0, waiting = 0, blocked = 0;
        for (ThreadInfo info : threadInfos) {
            if (info == null) continue;
            switch (info.getThreadState()) {
                case RUNNABLE:
                    running++;
                    break;
                case WAITING:
                case TIMED_WAITING:
                    waiting++;
                    break;
                case BLOCKED:
                    blocked++;
                    break;
                default:
                    break;
            }
        }
        Map<String, Object> threads = new HashMap<>();
        threads.put("running", running);
        threads.put("waiting", waiting);
        threads.put("blocked", blocked);
        threads.put("total", running + waiting + blocked);
        return threads;
    }

    public Map<String, Object> getMemoryInfo() {
        Runtime runtime = Runtime.getRuntime();
        long heapUsed = runtime.totalMemory() - runtime.freeMemory();
        long heapMax = runtime.maxMemory();
        long nonHeapUsed = 0;
        Map<String, Object> memory = new HashMap<>();
        memory.put("heapUsed", heapUsed / 1024 / 1024);
        memory.put("heapMax", heapMax / 1024 / 1024);
        memory.put("nonHeapUsed", nonHeapUsed);
        return memory;
    }

    public Map<String, Object> getAggregatedMonitorData() {
        Map<String, Object> result = new HashMap<>();
        result.put("threads", getAllThreadsInfo());
        result.put("memory", getMemoryInfo());
        result.put("pool", DataSourceUtils.getPoolStats());

        Map<Long, TraceContextHolder.TraceContext> allContexts = TraceContextHolder.getAllContexts();
        int total = 0;
        int running = 0;
        int errors = 0;
        long totalDuration = 0;
        int completedCount = 0;
        List<Map<String, Object>> recentRequests = new ArrayList<>();

        for (TraceContextHolder.TraceContext ctx : allContexts.values()) {
            total++;
            if (ctx.endTime == 0) {
                running++;
            } else {
                long duration = ctx.endTime - ctx.startTime;
                totalDuration += duration;
                completedCount++;
                if (recentRequests.size() < 10) {
                    Map<String, Object> req = new LinkedHashMap<>();
                    req.put("uri", ctx.uri);
                    req.put("duration", duration);
                    req.put("error", ctx.error != null);
                    recentRequests.add(req);
                }
            }
            if (ctx.error != null) {
                errors++;
            }
        }

        long avgResponseTime = completedCount > 0 ? totalDuration / completedCount : 0;

        Map<String, Object> requests = new LinkedHashMap<>();
        requests.put("total", total);
        requests.put("running", running);
        requests.put("errors", errors);
        requests.put("avgResponseTime", avgResponseTime);
        requests.put("recent", recentRequests);
        result.put("requests", requests);

        result.put("threadDetails", getThreadDetails());

        return result;
    }

    private List<Map<String, Object>> getThreadDetails() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        long[] allThreadIds = threadMXBean.getAllThreadIds();
        ThreadInfo[] infos = threadMXBean.getThreadInfo(allThreadIds, 20);
        Map<Long, TraceContextHolder.TraceContext> contextMap = TraceContextHolder.getAllContexts();
        List<Map<String, Object>> details = new ArrayList<>();

        for (ThreadInfo info : infos) {
            if (info == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            long tid = info.getThreadId();
            item.put("threadId", tid);
            item.put("threadName", info.getThreadName());
            item.put("state", info.getThreadState().name());

            TraceContextHolder.TraceContext ctx = contextMap.get(tid);
            String callChain = null;
            String status = "IDLE";
            long lastActiveTime = 0;

            if (ctx != null) {
                if (ctx.endTime == 0) {
                    status = "RUNNING";
                    lastActiveTime = System.currentTimeMillis();
                    callChain = ctx.nodePath != null ? ctx.nodePath : "Dispatcher";
                } else {
                    status = "COMPLETED";
                    lastActiveTime = ctx.endTime;
                    callChain = ctx.nodePath != null ? ctx.nodePath : "Completed";
                }
            } else {
                StackTraceElement[] stack = info.getStackTrace();
                if (stack != null && stack.length > 0) {
                    StringBuilder sb = new StringBuilder();
                    int count = 0;
                    for (StackTraceElement ste : stack) {
                        String className = ste.getClassName();
                        if (className.startsWith("com.omni.business") || className.startsWith("com.omni.framework")) {
                            if (count > 0) sb.append(" → ");
                            sb.append(ste.getMethodName());
                            count++;
                            if (count >= 5) break;
                        }
                    }
                    if (sb.length() > 0) {
                        callChain = sb.toString();
                    }
                }
                lastActiveTime = 0;
            }

            item.put("callChain", callChain != null ? callChain : "");
            item.put("status", status);
            item.put("lastActiveTime", lastActiveTime);

            if ("RUNNING".equals(status) && callChain != null) {
                String[] nodes = callChain.split(" → ");
                item.put("nodes", nodes);
            } else {
                item.put("nodes", new String[0]);
            }

            details.add(item);
        }

        details.sort((a, b) -> {
            String statusA = (String) a.getOrDefault("status", "IDLE");
            String statusB = (String) b.getOrDefault("status", "IDLE");
            int priA = getStatusPriority(statusA);
            int priB = getStatusPriority(statusB);
            if (priA != priB) return Integer.compare(priA, priB);
            long timeA = (long) a.getOrDefault("lastActiveTime", 0L);
            long timeB = (long) b.getOrDefault("lastActiveTime", 0L);
            return Long.compare(timeB, timeA);
        });

        return details;
    }

    private int getStatusPriority(String status) {
        switch (status) {
            case "RUNNING": return 1;
            case "BLOCKED": return 2;
            case "WAITING": return 3;
            case "TIMED_WAITING": return 4;
            case "NEW": return 5;
            case "TERMINATED": return 6;
            case "IDLE": return 7;
            case "COMPLETED": return 8;
            default: return 9;
        }
    }
}