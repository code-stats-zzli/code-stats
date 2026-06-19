package com.omni.business.assistance.three3d.service;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 监控数据定时采集服务
 * - 每5秒触发一次任务
 * - 每次任务并发发起10个请求（可配置）
 * - 每个响应保存为独立的 JSON 文件
 */
@Service
public class MonitorDataScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonitorDataScheduler.class);

    // 配置项（可通过 ConfigLoader 读取）
    private static final String API_URL = "http://localhost:8080/omni/3d/api/monitor-data";
    private static final int CONCURRENT_REQUESTS = 10;
    private static final long INITIAL_DELAY_SECONDS = 1;
    private static final long PERIOD_SECONDS = 5;
    private static final String OUTPUT_DIR = "./monitor_data";

    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService requestExecutor;

    public MonitorDataScheduler() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        // 调度线程池（单线程，用于定时触发）
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MonitorScheduler");
            t.setDaemon(true);
            return t;
        });
        // 请求执行线程池（最大并发数）
        this.requestExecutor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS, r -> {
            Thread t = new Thread(r, "MonitorRequest");
            t.setDaemon(true);
            return t;
        });

        // 创建输出目录
        try {
            Path dir = Paths.get(OUTPUT_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            log.error("创建输出目录失败", e);
        }

        // 启动定时任务
        scheduler.scheduleAtFixedRate(this::executeConcurrentRequests,
                INITIAL_DELAY_SECONDS, PERIOD_SECONDS, TimeUnit.SECONDS);

        log.info("监控数据采集服务已启动，URL: {}, 并发数: {}, 间隔: {}s",
                API_URL, CONCURRENT_REQUESTS, PERIOD_SECONDS);
    }

    /**
     * 每次定时触发的任务：并发发起多个请求
     */
    private void executeConcurrentRequests() {
        log.debug("开始并发请求，数量: {}", CONCURRENT_REQUESTS);
        long startTime = System.currentTimeMillis();

        // 提交所有请求任务
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            final int seq = i;
            requestExecutor.submit(() -> doRequest(seq));
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.debug("所有请求已提交，耗时 {} ms", elapsed);
    }

    /**
     * 单个请求的执行逻辑
     */
    private void doRequest(int seq) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            String body = response.body();

            // 生成带时间戳和序号的文件名
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
            String fileName = String.format("monitor_%s_%02d.json", timestamp, seq);
            Path filePath = Paths.get(OUTPUT_DIR, fileName);

            // 写入文件
            Files.writeString(filePath, body, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            log.info("请求成功 [{}] 状态码: {}, 文件: {}", seq, statusCode, fileName);

        } catch (Exception e) {
            log.error("请求失败 [{}]", seq, e);
        }
    }

    /**
     * 优雅关闭（可在应用关闭时调用）
     */
    public void shutdown() {
        scheduler.shutdownNow();
        requestExecutor.shutdownNow();
        log.info("监控数据采集服务已关闭");
    }
}