package com.omni.business.assistance.three3d.controller;

import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 3D 实时监控数据接口（模拟 + 未来可扩展真实埋点）
 */
@Controller
@RequestMapping("/3d/api")
public class MonitorController {

    private static final Random RANDOM = new Random();
    private static final AtomicLong REQUEST_COUNT = new AtomicLong(0);

    /**
     * 获取当前系统运行态数据（模拟）
     * GET /3d/api/monitor-data
     * 返回 JSON: { threads: {...}, memory: {...}, pool: {...} }
     */
    @RequestMapping("/monitor-data")
    @ResponseBody
    public String getMonitorData() {
        // 模拟动态变化，每次请求产生略微不同的值
        long count = REQUEST_COUNT.incrementAndGet();

        int active = 8 + (int)(Math.sin(count * 0.1) * 6) + RANDOM.nextInt(4);
        if (active < 1) active = 1;
        int idle = 5 + RANDOM.nextInt(10);
        int max = 30;

        int heapUsed = 400 + (int)(Math.sin(count * 0.05) * 300) + RANDOM.nextInt(100);
        if (heapUsed < 100) heapUsed = 100;
        int heapMax = 1024;
        int nonHeapUsed = 80 + (int)(Math.sin(count * 0.07) * 50) + RANDOM.nextInt(40);

        int running = 20 + (int)(Math.sin(count * 0.03) * 10) + RANDOM.nextInt(6);
        int waiting = 5 + RANDOM.nextInt(10);
        int blocked = RANDOM.nextInt(3);

        Map<String, Object> result = new HashMap<>();
        Map<String, Object> threads = new HashMap<>();
        threads.put("running", running);
        threads.put("waiting", waiting);
        threads.put("blocked", blocked);
        threads.put("total", running + waiting + blocked);

        Map<String, Object> memory = new HashMap<>();
        memory.put("heapUsed", heapUsed);
        memory.put("heapMax", heapMax);
        memory.put("nonHeapUsed", nonHeapUsed);

        Map<String, Object> pool = new HashMap<>();
        pool.put("active", active);
        pool.put("idle", idle);
        pool.put("max", max);

        result.put("threads", threads);
        result.put("memory", memory);
        result.put("pool", pool);

        return JsonUtil.toJson(result);
    }
}