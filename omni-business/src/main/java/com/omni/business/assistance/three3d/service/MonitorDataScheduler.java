package com.omni.business.assistance.three3d.service;

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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 监控数据定时采集服务（可选组件）。
 *
 * <p>修复点（针对线上一直刷 Connection refused）：</p>
 * <ol>
 *   <li><b>不再硬编码 URL</b>：从 server.port / context.path 拼接，
 *       也支持 monitor.scheduler.url 直接覆盖；</li>
 *   <li><b>默认关闭</b>：monitor.scheduler.enabled 默认 false，
 *       该功能对大多数部署没有意义，开启后才会启动定时任务；</li>
 *   <li><b>失败熔断</b>：连续 N 轮全部失败后自动暂停，避免每 5 秒刷 10 条堆栈；</li>
 *   <li><b>降噪日志</b>：首轮失败打一次完整堆栈（含 URL），之后只打摘要；</li>
 *   <li><b>优雅关闭</b>：shutdown 时停调度、停线程池。</li>
 * </ol>
 *
 * <p>配置项（application.properties，全部可选）：</p>
 * <pre>
 *   monitor.scheduler.enabled         = false       # 默认关闭；设为 true 才启动
 *   monitor.scheduler.url             = (自动拼接)  # 完整 URL，设置后覆盖端口/context 拼接
 *   monitor.scheduler.concurrent      = 10          # 每轮并发请求数
 *   monitor.scheduler.initial-delay   = 1           # 首次延迟（秒）
 *   monitor.scheduler.period          = 5           # 周期（秒）
 *   monitor.scheduler.output-dir      = ./monitor_data
 *   monitor.scheduler.max-fail-rounds = 3           # 连续失败多少轮后自动暂停
 * </pre>
 *
 * <p>如果只是想让报错消失：</p>
 * <pre>
 *   monitor.scheduler.enabled=false   # 保持默认即可，重启后不再采集、不再报错
 * </pre>
 * <p>如果确实需要采集：</p>
 * <pre>
 *   monitor.scheduler.enabled=true
 *   # 确认 server.port / context.path 与实际运行一致，
 *   # 或直接设置 monitor.scheduler.url=http://localhost:真实端口/真实context/3d/api/monitor-data
 * </pre>
 */
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

    /** 连续「整轮全部失败」计数；成功一轮即清零 */
    private final AtomicInteger consecutiveFailRounds = new AtomicInteger(0);
    /** 最近一次异常，仅首轮失败时打印完整堆栈 */
    private final AtomicReference<Exception> lastError = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> scheduledFuture;

    public MonitorDataScheduler() {
        this.enabled = "true".equalsIgnoreCase(
                ConfigLoader.getProperty("monitor.scheduler.enabled", "false"));

        this.apiUrl = resolveApiUrl();
        this.concurrentRequests = parseInt(
                ConfigLoader.getProperty("monitor.scheduler.concurrent", "10"), 10);
        this.initialDelaySeconds = parseLong(
                ConfigLoader.getProperty("monitor.scheduler.initial-delay", "1"), 1L);
        this.periodSeconds = parseLong(
                ConfigLoader.getProperty("monitor.scheduler.period", "5"), 5L);
        this.outputDir = ConfigLoader.getProperty(
                "monitor.scheduler.output-dir", "./monitor_data");
        this.maxFailRounds = parseInt(
                ConfigLoader.getProperty("monitor.scheduler.max-fail-rounds", "3"), 3);

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MonitorScheduler");
            t.setDaemon(true);
            return t;
        });
        this.requestExecutor = Executors.newFixedThreadPool(
                Math.max(1, concurrentRequests), r -> {
                    Thread t = new Thread(r, "MonitorRequest");
                    t.setDaemon(true);
                    return t;
                });

        if (!enabled) {
            log.info("MonitorDataScheduler 已禁用（monitor.scheduler.enabled=false）。" +
                    "如需定时采集监控数据，请设 monitor.scheduler.enabled=true。" +
                    "当前拼接 URL 为: {}", apiUrl);
            return;
        }

        try {
            Path dir = Paths.get(outputDir);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            log.error("创建监控输出目录失败: {}", outputDir, e);
        }

        running.set(true);
        this.scheduledFuture = scheduler.scheduleAtFixedRate(
                this::executeConcurrentRequests,
                initialDelaySeconds, periodSeconds, TimeUnit.SECONDS);

        log.info("MonitorDataScheduler 已启动: url={}, 并发={}, 间隔={}s, 输出目录={}, 失败熔断阈值={}轮",
                apiUrl, concurrentRequests, periodSeconds, outputDir, maxFailRounds);
    }

    /**
     * URL 解析优先级：
     *   1) monitor.scheduler.url 显式配置
     *   2) http://localhost:{server.port}{context.path}/3d/api/monitor-data
     *      其中 server.port 默认 28080，context.path 默认 /omni
     */
    private static String resolveApiUrl() {
        String explicit = ConfigLoader.getProperty("monitor.scheduler.url", "");
        if (explicit != null && !explicit.trim().isEmpty()) {
            return explicit.trim();
        }
        String port = ConfigLoader.getProperty("server.port", "28080");
        String ctx = ConfigLoader.getProperty("context.path", "/omni");
        if (ctx == null) ctx = "";
        ctx = ctx.trim();
        if (!ctx.isEmpty() && !ctx.startsWith("/")) ctx = "/" + ctx;
        if (ctx.endsWith("/")) ctx = ctx.substring(0, ctx.length() - 1);
        return "http://localhost:" + port + ctx + "/3d/api/monitor-data";
    }

    // ==================== 调度 ====================

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
            } catch (Exception e) {
                failed++;
                lastError.set(e);
            }
        }

        if (failed >= concurrentRequests) {
            int rounds = consecutiveFailRounds.incrementAndGet();
            if (rounds == 1) {
                // 只打一次完整堆栈，指明 URL 与排查方向
                log.error("MonitorDataScheduler 本轮 {} 个请求全部失败，目标 URL={}。"
                                + "请检查 server.port / context.path 是否与实际一致，"
                                + "或直接设置 monitor.scheduler.url。"
                                + "为避免刷屏，后续相同错误降级为 warn；连续 {} 轮后自动暂停。",
                        concurrentRequests, apiUrl, maxFailRounds, lastError.get());
            } else if (rounds <= maxFailRounds) {
                log.warn("MonitorDataScheduler 连续 {} 轮全部失败，URL={}，错误={}",
                        rounds, apiUrl, brief(lastError.get()));
            } else if (rounds == maxFailRounds + 1) {
                log.error("MonitorDataScheduler 连续 {} 轮全部失败，已自动暂停定时采集。"
                                + "修复 server.port / context.path / monitor.scheduler.url 后重启应用，"
                                + "或设置 monitor.scheduler.enabled=false 彻底关闭。",
                        rounds);
                pause();
            }
        } else {
            if (consecutiveFailRounds.get() > 0) {
                log.info("MonitorDataScheduler 采集恢复正常（成功 {} / {}）",
                        concurrentRequests - failed, concurrentRequests);
            }
            consecutiveFailRounds.set(0);
        }
    }

    private boolean doRequest(int seq) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.debug("监控请求 [{}] 返回非 200: {}", seq, response.statusCode());
                return false;
            }

            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
            String fileName = String.format("monitor_%s_%02d.json", timestamp, seq);
            Path filePath = Paths.get(outputDir, fileName);
            Files.writeString(filePath, response.body(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.debug("监控请求 [{}] 成功，文件: {}", seq, fileName);
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
        if (f != null) {
            f.cancel(false);
            scheduledFuture = null;
        }
    }

    public void shutdown() {
        running.set(false);
        try {
            if (scheduledFuture != null) scheduledFuture.cancel(false);
            scheduler.shutdownNow();
            requestExecutor.shutdownNow();
        } catch (Exception ignored) {
        }
        log.info("MonitorDataScheduler 已关闭");
    }

    // ==================== 工具 ====================

    private static String brief(Throwable e) {
        if (e == null) return "unknown";
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur.getClass().getSimpleName() + ": " + cur.getMessage();
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static long parseLong(String s, long def) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return def; }
    }
}