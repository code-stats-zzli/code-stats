package com.omni.business.assistance.three3d.service;

import com.omni.common.util.ExceptionUtil;
import com.omni.common.util.ParseUtils;
import com.omni.common.util.ThreadPoolUtils;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Service;
import com.omni.framework.spring.config.ConfigLoader;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** ★ L1-07：线程池用 ThreadPoolUtils；★ L1-08：brief → ExceptionUtil.getRootCause */
@Service
public class MonitorDataScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonitorDataScheduler.class);

    private final boolean enabled;
    private final String apiUrl;
    private final int concurrentRequests;
    private final long initialDelaySeconds;
    private final long periodSeconds;
    private final String outputDir;
    private final int maxFailRounds;

    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService requestExecutor;

    private final AtomicInteger consecutiveFailRounds = new AtomicInteger(0);
    private final AtomicReference<Exception> lastError = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> scheduledFuture;

    public MonitorDataScheduler() {
        this.enabled = "true".equalsIgnoreCase(ConfigLoader.getProperty("monitor.scheduler.enabled", "false"));
        this.apiUrl = resolveApiUrl();
        this.concurrentRequests = ParseUtils.parseInt(ConfigLoader.getProperty("monitor.scheduler.concurrent", "10"), 10);
        this.initialDelaySeconds = ParseUtils.parseLong(ConfigLoader.getProperty("monitor.scheduler.initial-delay", "1"), 1L);
        this.periodSeconds = ParseUtils.parseLong(ConfigLoader.getProperty("monitor.scheduler.period", "5"), 5L);
        this.outputDir = ConfigLoader.getProperty("monitor.scheduler.output-dir", "./monitor_data");
        this.maxFailRounds = ParseUtils.parseInt(ConfigLoader.getProperty("monitor.scheduler.max-fail-rounds", "3"), 3);

        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.scheduler = ThreadPoolUtils.newSingleScheduledPool("MonitorScheduler");
        this.requestExecutor = ThreadPoolUtils.newFixedThreadPool(Math.max(1, concurrentRequests), "MonitorRequest");

        if (!enabled) {
            log.info("MonitorDataScheduler 已禁用（monitor.scheduler.enabled=false）。当前拼接 URL 为: {}", apiUrl);
            return;
        }
        try {
            Path dir = Paths.get(outputDir);
            if (!Files.exists(dir)) Files.createDirectories(dir);
        } catch (IOException e) { log.error("创建监控输出目录失败: {}", outputDir, e); }

        running.set(true);
        this.scheduledFuture = scheduler.scheduleAtFixedRate(this::executeConcurrentRequests,
                initialDelaySeconds, periodSeconds, TimeUnit.SECONDS);
        log.info("MonitorDataScheduler 已启动: url={}, 并发={}, 间隔={}s, 输出目录={}, 失败熔断阈值={}轮",
                apiUrl, concurrentRequests, periodSeconds, outputDir, maxFailRounds);
    }

    private static String resolveApiUrl() {
        String explicit = ConfigLoader.getProperty("monitor.scheduler.url", "");
        if (explicit != null && !explicit.trim().isEmpty()) return explicit.trim();
        String port = ConfigLoader.getProperty("server.port", "28080");
        String ctx = ConfigLoader.getProperty("context.path", "/omni");
        if (ctx == null) ctx = "";
        ctx = ctx.trim();
        if (!ctx.isEmpty() && !ctx.startsWith("/")) ctx = "/" + ctx;
        if (ctx.endsWith("/")) ctx = ctx.substring(0, ctx.length() - 1);
        return "http://localhost:" + port + ctx + "/3d/api/monitor-data";
    }

    private void executeConcurrentRequests() {
        if (!running.get()) return;
        List<Future<Boolean>> futures = new ArrayList<>(concurrentRequests);
        for (int i = 0; i < concurrentRequests; i++) {
            final int seq = i;
            futures.add(requestExecutor.submit(() -> doRequest(seq)));
        }
        int failed = 0;
        for (Future<Boolean> f : futures) {
            try {
                Boolean ok = f.get(Math.max(10, periodSeconds + 5), TimeUnit.SECONDS);
                if (ok == null || !ok) failed++;
            } catch (Exception e) { failed++; lastError.set(e); }
        }
        if (failed >= concurrentRequests) {
            int rounds = consecutiveFailRounds.incrementAndGet();
            if (rounds == 1) {
                log.error("MonitorDataScheduler 本轮 {} 个请求全部失败，目标 URL={}", concurrentRequests, apiUrl, lastError.get());
            } else if (rounds <= maxFailRounds) {
                log.warn("MonitorDataScheduler 连续 {} 轮全部失败，URL={}，错误={}", rounds, apiUrl, brief(lastError.get()));
            } else if (rounds == maxFailRounds + 1) {
                log.error("MonitorDataScheduler 连续 {} 轮全部失败，已自动暂停。", rounds);
                pause();
            }
        } else {
            if (consecutiveFailRounds.get() > 0)
                log.info("MonitorDataScheduler 采集恢复正常（成功 {} / {}）", concurrentRequests - failed, concurrentRequests);
            consecutiveFailRounds.set(0);
        }
    }

    private boolean doRequest(int seq) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) { log.debug("监控请求 [{}] 返回非 200: {}", seq, response.statusCode()); return false; }
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
            String fileName = String.format("monitor_%s_%02d.json", timestamp, seq);
            Path filePath = Paths.get(outputDir, fileName);
            Files.writeString(filePath, response.body(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (Exception e) {
            lastError.set(e);
            log.debug("监控请求 [{}] 失败: {}", seq, brief(e));
            return false;
        }
    }

    private void pause() {
        running.set(false);
        ScheduledFuture<?> f = scheduledFuture;
        if (f != null) { f.cancel(false); scheduledFuture = null; }
    }

    public void shutdown() {
        running.set(false);
        try {
            if (scheduledFuture != null) scheduledFuture.cancel(false);
            scheduler.shutdownNow();
            requestExecutor.shutdownNow();
        } catch (Exception ignored) {}
        log.info("MonitorDataScheduler 已关闭");
    }

    private static String brief(Throwable e) {
        if (e == null) return "unknown";
        Throwable root = ExceptionUtil.getRootCause(e);
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}