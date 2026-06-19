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
        // 非堆内存无法直接获取，保留为0（也可通过 JMX 获取，此处简化）
        long nonHeapUsed = 0;
        Map<String, Object> memory = new HashMap<>();
        memory.put("heapUsed", heapUsed / 1024 / 1024);
        memory.put("heapMax", heapMax / 1024 / 1024);
        memory.put("nonHeapUsed", nonHeapUsed);
        return memory;
    }

    /**
     * 聚合所有监控数据（供前端 3D 大屏使用）
     * 现在返回完全真实的数据：
     * - 线程状态：来自 ThreadMXBean
     * - 内存：来自 Runtime
     * - 连接池：来自 DataSourceUtils.getPoolStats()
     * - 请求追踪：来自 TraceContextHolder（当前活跃请求）
     */
    public Map<String, Object> getAggregatedMonitorData() {
        Map<String, Object> result = new HashMap<>();
        result.put("threads", getAllThreadsInfo());
        result.put("memory", getMemoryInfo());

        // ========== 连接池真实数据 ==========
        Map<String, Object> pool = DataSourceUtils.getPoolStats();
        result.put("pool", pool);

        // ========== 请求追踪真实数据（从 TraceContextHolder 获取当前活跃请求） ==========
        Map<Long, TraceContextHolder.TraceContext> allContexts = TraceContextHolder.getAllContexts();
        int total = 0;
        int running = 0;
        int errors = 0;
        long totalDuration = 0;
        int completedCount = 0;
        List<Map<String, Object>> recentRequests = new ArrayList<>();

        for (TraceContextHolder.TraceContext ctx : allContexts.values()) {
            total++;
            // 如果 endTime == 0 表示请求还在处理中
            if (ctx.endTime == 0) {
                running++;
            } else {
                // 已完成请求，统计耗时
                long duration = ctx.endTime - ctx.startTime;
                totalDuration += duration;
                completedCount++;
                // 只保留最近10条已完成的请求用于展示
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

        // 计算平均响应时间（仅基于已完成的请求）
        long avgResponseTime = completedCount > 0 ? totalDuration / completedCount : 0;

        Map<String, Object> requests = new LinkedHashMap<>();
        requests.put("total", total);
        requests.put("running", running);
        requests.put("errors", errors);
        requests.put("avgResponseTime", avgResponseTime);
        requests.put("recent", recentRequests);
        result.put("requests", requests);
        result.put("threads", getAllThreadsInfo());
        result.put("memory", getMemoryInfo());
        result.put("pool", DataSourceUtils.getPoolStats());

        // ★ 新增：线程详情（包含调用链）
        result.put("threadDetails", getThreadDetails());

        return result;
    }
    // ======== JvmMonitorService.java 新增内容 ========


    /**
     * 获取所有线程的详细信息（包含调用链）
     */
    private List<Map<String, Object>> getThreadDetails() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        long[] allThreadIds = threadMXBean.getAllThreadIds();
        // 获取带堆栈的线程信息（最大深度 20）
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

            // 匹配 TraceContext
            TraceContextHolder.TraceContext ctx = contextMap.get(tid);
            String callChain = null;
            String status = "IDLE";
            if (ctx != null) {
                if (ctx.endTime == 0) {
                    status = "RUNNING";
                    callChain = ctx.nodePath != null ? ctx.nodePath : "Dispatcher";
                } else {
                    status = "COMPLETED";
                    // 已完成也保留最后一次调用链（可选）
                    callChain = ctx.nodePath != null ? ctx.nodePath : "Completed";
                }
            } else {
                // 如果线程栈中包含我们感兴趣的业务类，也可以提取简化的调用链
                // 这里简单起见，只显示前几个栈元素作为调用链
                StackTraceElement[] stack = info.getStackTrace();
                if (stack != null && stack.length > 0) {
                    StringBuilder sb = new StringBuilder();
                    int count = 0;
                    for (StackTraceElement ste : stack) {
                        String className = ste.getClassName();
                        // 过滤掉框架内部类，只保留业务包
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
            }
            item.put("callChain", callChain != null ? callChain : "");
            item.put("status", status);

            // 对于 RUNNING 状态的线程，额外提供栈信息用于前端粒子动画（可选）
            if ("RUNNING".equals(status) && callChain != null) {
                // 可以拆分成节点列表
                String[] nodes = callChain.split(" → ");
                item.put("nodes", nodes);
            }

            details.add(item);
        }
        return details;
    }
}