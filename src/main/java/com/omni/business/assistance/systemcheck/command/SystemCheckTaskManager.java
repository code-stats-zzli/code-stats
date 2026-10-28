package com.omni.business.assistance.systemcheck.command;

import com.omni.business.assistance.systemcheck.config.CheckConfig;
import com.omni.business.assistance.systemcheck.model.CheckResult;
import com.omni.business.assistance.systemcheck.model.Finding;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.util.*;
import java.util.concurrent.*;

/** 系统检查异步任务管理器（支持实时状态、取消） */
public class SystemCheckTaskManager {
    private static final Logger log = LoggerFactory.getLogger(SystemCheckTaskManager.class);

    private static final Map<String, TaskContext> TASKS = new ConcurrentHashMap<>();
    /** 任务保留时间：1 小时 */
    private static final long RETENTION_MS = 60 * 60 * 1000L;

    /** 单个任务上下文 */
    public static class TaskContext {
        public final String taskId;
        public final long startTime;
        public volatile long endTime = 0;
        public volatile String state = "RUNNING";  // RUNNING / COMPLETED / CANCELLED / ERROR
        public volatile String errorMessage;
        public final List<CheckResult> results = Collections.synchronizedList(new ArrayList<>());
        public final Map<String, ModuleState> modules = new LinkedHashMap<>();
        public volatile int totalModules;
        public volatile int completedModules;
        public volatile int estimatedTotalSeconds;

        public final SystemCheckContext ctx;
        public final ExecutorService pool;
        public final List<Future<?>> futures = Collections.synchronizedList(new ArrayList<>());

        TaskContext(String taskId, CheckConfig config) {
            this.taskId = taskId;
            this.startTime = System.currentTimeMillis();
            this.ctx = new SystemCheckContext(config);
            this.pool = Executors.newFixedThreadPool(Math.max(1, config.getThreadCount()),
                    r -> { Thread t = new Thread(r, "sys-check-" + taskId); t.setDaemon(true); return t; });
        }

        public long getElapsedMillis() {
            return (endTime > 0 ? endTime : System.currentTimeMillis()) - startTime;
        }
    }

    /** 单模块状态 */
    public static class ModuleState {
        public final String checkId;
        public final String checkName;
        public volatile String state = "PENDING";  // PENDING / RUNNING / SUCCESS / FAILED / CANCELLED
        public volatile long startTime = 0;
        public volatile long endTime = 0;
        public volatile int findingCount = 0;
        public volatile int estimatedSeconds = 5;
        public volatile String errorMessage;

        ModuleState(String checkId, String checkName) {
            this.checkId = checkId;
            this.checkName = checkName;
        }

        public long getDurationMillis() {
            if (startTime == 0) return 0;
            return (endTime > 0 ? endTime : System.currentTimeMillis()) - startTime;
        }
    }

    /** 创建任务，返回 taskId */
    public static String createTask(CheckConfig config, List<SystemCheckCommand> commands, Set<String> selectedIds) {
        cleanupOld();
        String taskId = UUID.randomUUID().toString().replace("-", "");
        TaskContext tc = new TaskContext(taskId, config);
        TASKS.put(taskId, tc);

        // 过滤启用的命令
        List<SystemCheckCommand> toRun = new ArrayList<>();
        for (SystemCheckCommand cmd : commands) {
            boolean enabled = config.getCheckEnabled().getOrDefault(cmd.getId(), true);
            if (selectedIds != null) enabled = selectedIds.contains(cmd.getId());
            if (enabled) toRun.add(cmd);
        }
        tc.totalModules = toRun.size();
        for (SystemCheckCommand cmd : toRun) {
            ModuleState ms = new ModuleState(cmd.getId(), cmd.getName());
            ms.estimatedSeconds = cmd.getEstimatedSeconds(config);
            tc.modules.put(cmd.getId(), ms);
            tc.estimatedTotalSeconds += ms.estimatedSeconds;
        }

        // 提交每个模块到线程池
        for (SystemCheckCommand cmd : toRun) {
            ModuleState ms = tc.modules.get(cmd.getId());
            Future<?> f = tc.pool.submit(() -> {
                if (tc.ctx.isCancelled()) {
                    ms.state = "CANCELLED";
                    return;
                }
                ms.state = "RUNNING";
                ms.startTime = System.currentTimeMillis();
                long t0 = ms.startTime;
                CheckResult r = new CheckResult(cmd.getId(), cmd.getName());
                r.setEstimatedSeconds(ms.estimatedSeconds);
                try {
                    List<Finding> findings = cmd.execute(tc.ctx);
                    for (Finding fi : findings) fi.setSuggestion(suggestionFor(fi));
                    r.setFindings(findings);
                    r.setSuccess(true);
                    ms.findingCount = findings.size();
                    ms.state = tc.ctx.isCancelled() ? "CANCELLED" : "SUCCESS";
                } catch (Exception e) {
                    if (tc.ctx.isCancelled() || e instanceof InterruptedException) {
                        ms.state = "CANCELLED";
                        r.setSuccess(false);
                        r.setErrorMessage("已取消");
                    } else {
                        log.error("检查项失败: " + cmd.getId(), e);
                        ms.state = "FAILED";
                        r.setSuccess(false);
                        r.setErrorMessage(e.getMessage());
                        ms.errorMessage = e.getMessage();
                    }
                } finally {
                    ms.endTime = System.currentTimeMillis();
                    r.setDurationMs(ms.endTime - t0);
                    tc.results.add(r);
                    tc.completedModules++;
                }
            });
            tc.futures.add(f);
        }

        // 监控线程：全部完成后标记状态
        new Thread(() -> {
            try {
                while (!tc.ctx.isCancelled()) {
                    boolean allDone = tc.futures.stream().allMatch(Future::isDone);
                    if (allDone) {
                        tc.endTime = System.currentTimeMillis();
                        if (!"CANCELLED".equals(tc.state)) tc.state = "COMPLETED";
                        break;
                    }
                    Thread.sleep(200);
                }
                if (tc.ctx.isCancelled() && tc.endTime == 0) {
                    tc.endTime = System.currentTimeMillis();
                    tc.state = "CANCELLED";
                }
            } catch (InterruptedException ignored) {
                tc.endTime = System.currentTimeMillis();
                tc.state = "CANCELLED";
            } finally {
                tc.pool.shutdownNow();
            }
        }, "sys-check-monitor-" + taskId).start();

        log.info("系统检查任务已创建: taskId={}, 模块数={}, 预估总耗时={}s",
                taskId, tc.totalModules, tc.estimatedTotalSeconds);
        return taskId;
    }

    /** 获取任务状态快照 */
    public static Map<String, Object> getStatus(String taskId) {
        TaskContext tc = TASKS.get(taskId);
        if (tc == null) return null;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", taskId);
        m.put("state", tc.state);
        m.put("startTime", tc.startTime);
        m.put("endTime", tc.endTime);
        m.put("elapsedMs", tc.getElapsedMillis());
        m.put("elapsedSeconds", tc.getElapsedMillis() / 1000.0);
        m.put("estimatedTotalSeconds", tc.estimatedTotalSeconds);
        m.put("totalModules", tc.totalModules);
        m.put("completedModules", tc.completedModules);
        m.put("errorMessage", tc.errorMessage);

        // 模块状态列表
        List<Map<String, Object>> modules = new ArrayList<>();
        for (ModuleState ms : tc.modules.values()) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("checkId", ms.checkId);
            mm.put("checkName", ms.checkName);
            mm.put("state", ms.state);
            mm.put("startTime", ms.startTime);
            mm.put("endTime", ms.endTime);
            mm.put("durationMs", ms.getDurationMillis());
            mm.put("findingCount", ms.findingCount);
            mm.put("estimatedSeconds", ms.estimatedSeconds);
            mm.put("errorMessage", ms.errorMessage);
            modules.add(mm);
        }
        m.put("modules", modules);

        // 完成时汇总结果
        if (!"RUNNING".equals(tc.state)) {
            List<CheckResult> results;
            synchronized (tc.results) {
                results = new ArrayList<>(tc.results);
            }
            // 排序：按原顺序（命令列表顺序）
            results.sort(Comparator.comparingInt(r -> {
                int i = 0;
                for (String id : tc.modules.keySet()) {
                    if (id.equals(r.getCheckId())) return i;
                    i++;
                }
                return 999;
            }));
            m.put("results", results);

            int cOk = 0, cBad = 0, cWarn = 0, cInfo = 0;
            for (CheckResult r : results) {
                for (Finding f : r.getFindings()) {
                    switch (f.getStatus()) {
                        case "合规": cOk++; break;
                        case "不合规": cBad++; break;
                        case "警告": cWarn++; break;
                        default: cInfo++;
                    }
                }
            }
            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("ok", cOk); stat.put("bad", cBad);
            stat.put("warn", cWarn); stat.put("info", cInfo);
            m.put("summary", stat);
        }
        return m;
    }

    /** 取消任务 */
    public static boolean cancel(String taskId) {
        TaskContext tc = TASKS.get(taskId);
        if (tc == null) return false;
        if (!"RUNNING".equals(tc.state)) return false;
        tc.ctx.cancel();
        for (Future<?> f : tc.futures) f.cancel(true);
        tc.pool.shutdownNow();
        tc.endTime = System.currentTimeMillis();
        tc.state = "CANCELLED";
        log.info("系统检查任务已取消: taskId={}", taskId);
        return true;
    }

    /** 清理过期任务 */
    public static void cleanupOld() {
        long now = System.currentTimeMillis();
        TASKS.entrySet().removeIf(e -> {
            TaskContext tc = e.getValue();
            return tc.endTime > 0 && (now - tc.endTime > RETENTION_MS);
        });
    }

    private static String suggestionFor(Finding f) {
        if ("合规".equals(f.getStatus()) || "信息".equals(f.getStatus())) return "";
        String k = f.getCategory() + "|" + f.getItem();
        if (k.contains("端口") && k.contains("监听"))
            return "以管理员配置入站阻断规则(Block)或停用对应服务";
        if (k.contains("防火墙")) return "以管理员启用防火墙并设入站默认阻断";
        if (k.contains("远程桌面")) return "关闭 xrdp/VNC 或 RDP 服务及 3389/5900 端口";
        if (k.contains("屏保") || k.contains("锁屏")) return "设置无操作 300 秒锁屏且恢复需密码";
        if (k.contains("凭证") || k.contains("明文")) return "将密码迁移至环境变量/密钥管理,删除明文";
        if (k.contains("非开源")) return "核实正版授权,避免使用破解版";
        if (k.contains("压缩包")) return "对比桌管软件-应用商城发布版本,及时更新";
        if (k.contains("依赖")) return "升级依赖包至修复版本";
        if (k.contains("跨网")) return "卸载跨网/远控/穿透工具;业务必需时走审批";
        if (k.contains("无线")) return "非必要请禁用 WiFi 无线网卡";
        if (k.contains("U盘")) return "部署外设管控(禁用 USB 存储),确需使用走审批";
        return "";
    }
}