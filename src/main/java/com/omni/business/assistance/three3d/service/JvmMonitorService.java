package com.omni.business.assistance.three3d.service;

import com.omni.framework.spring.annotation.Service;
import com.omni.framework.spring.monitor.MonitorContext;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JvmMonitorService {

    // 获取综合监控数据
        Map<String, Object> result = new LinkedHashMap<>();
        /**
         * 获取当前线程的监控数据（来自 ThreadLocal）
         */
        public Map<String, Object> getCurrentThreadMonitorData() {
            MonitorContext.MonitorData data = MonitorContext.getCurrentData();
            Map<String, Object> result = new HashMap<>();
            result.put("invocations", data.getInvocations());
            result.put("startTime", data.getStartTime());
            result.put("threadName", Thread.currentThread().getName());
            result.put("threadId", Thread.currentThread().getId());
            return result;
        }

        /**
         * 获取所有线程状态（通过 JMX）
         */
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

        /**
         * 获取 JVM 内存信息
         */
        public Map<String, Object> getMemoryInfo() {
            Runtime runtime = Runtime.getRuntime();
            long heapUsed = runtime.totalMemory() - runtime.freeMemory();
            long heapMax = runtime.maxMemory();
            long nonHeapUsed = 0; // 非堆内存无法直接获取，可留空或估算
            Map<String, Object> memory = new HashMap<>();
            memory.put("heapUsed", heapUsed / 1024 / 1024); // MB
            memory.put("heapMax", heapMax / 1024 / 1024);
            memory.put("nonHeapUsed", nonHeapUsed);
            return memory;
        }

        /**
         * 聚合所有监控数据（供前端 3D 大屏使用）
         */
        public Map<String, Object> getAggregatedMonitorData() {
            Map<String, Object> result = new HashMap<>();
            result.put("threads", getAllThreadsInfo());
            result.put("memory", getMemoryInfo());
            // 连接池数据可以从 DataSource 中获取，这里模拟
            Map<String, Object> pool = new HashMap<>();
            pool.put("active", 8);
            pool.put("idle", 5);
            pool.put("max", 20);
            result.put("pool", pool);
            return result;
        }
}