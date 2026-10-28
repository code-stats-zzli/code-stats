package com.omni.business.assistance.systemcheck.service;

import com.omni.business.assistance.systemcheck.command.SystemCheckCommand;
import com.omni.business.assistance.systemcheck.command.SystemCheckContext;
import com.omni.business.assistance.systemcheck.command.impl.*;
import com.omni.business.assistance.systemcheck.config.CheckConfig;
import com.omni.business.assistance.systemcheck.model.CheckResult;
import com.omni.business.assistance.systemcheck.model.Finding;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Service;

import java.util.*;
import java.util.concurrent.*;

/** 系统检查调度引擎（命令模式） */
@Service
public class SystemCheckService {
    private static final Logger log = LoggerFactory.getLogger(SystemCheckService.class);

    private final Map<String, SystemCheckCommand> commandMap = new LinkedHashMap<>();
    private final List<SystemCheckCommand> commands = new ArrayList<>();

    public SystemCheckService() {
        register(new SystemInfoCheck());
        register(new FirewallCheck());
        register(new PortCheck());
        register(new RdpCheck());
        register(new LockScreenCheck());
        register(new CredentialsCheck());
        register(new ArchiveCheck());
        register(new PlaintextScanCheck());
        register(new DependencyVulnCheck());
        register(new EndpointSecurityCheck());
        log.info("SystemCheckService 初始化完成，共 {} 个检查命令", commands.size());
    }

    private void register(SystemCheckCommand cmd) {
        commandMap.put(cmd.getId(), cmd);
        commands.add(cmd);
    }

    public List<SystemCheckCommand> getCommands() { return commands; }

    /** 执行系统检查（一次性返回结果 + 预估时间） */
    public List<CheckResult> runCheck(CheckConfig config, Set<String> selectedIds) {
        SystemCheckContext ctx = new SystemCheckContext(config);
        int threadCount = Math.max(1, config.getThreadCount());

        List<SystemCheckCommand> toRun = new ArrayList<>();
        for (SystemCheckCommand cmd : commands) {
            boolean enabled = config.getCheckEnabled().getOrDefault(cmd.getId(), true);
            if (selectedIds != null) enabled = selectedIds.contains(cmd.getId());
            if (enabled) toRun.add(cmd);
        }

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(threadCount, Math.max(1, toRun.size())));
        List<Future<CheckResult>> futures = new ArrayList<>();
        for (SystemCheckCommand cmd : toRun) {
            futures.add(pool.submit(() -> executeOne(cmd, ctx)));
        }

        List<CheckResult> results = new ArrayList<>();
        for (Future<CheckResult> f : futures) {
            try {
                results.add(f.get(180, TimeUnit.SECONDS));
            } catch (TimeoutException e) {
                f.cancel(true);
                CheckResult err = new CheckResult("timeout", "超时");
                err.setSuccess(false);
                err.setErrorMessage("检查超时");
                results.add(err);
            } catch (Exception e) {
                CheckResult err = new CheckResult("error", "异常");
                err.setSuccess(false);
                err.setErrorMessage(e.getMessage());
                results.add(err);
            }
        }
        pool.shutdownNow();
        return results;
    }

    private CheckResult executeOne(SystemCheckCommand cmd, SystemCheckContext ctx) {
        CheckResult r = new CheckResult(cmd.getId(), cmd.getName());
        r.setEstimatedSeconds(cmd.getEstimatedSeconds(ctx.getConfig()));
        long start = System.currentTimeMillis();
        try {
            List<Finding> findings = cmd.execute(ctx);
            for (Finding f : findings) f.setSuggestion(suggestionFor(f));
            r.setFindings(findings);
            r.setSuccess(true);
        } catch (Exception e) {
            log.error("检查项执行失败: " + cmd.getId(), e);
            r.setSuccess(false);
            r.setErrorMessage(e.getMessage());
        }
        r.setDurationMs(System.currentTimeMillis() - start);
        return r;
    }

    private String suggestionFor(Finding f) {
        if ("合规".equals(f.getStatus()) || "信息".equals(f.getStatus())) return "";
        String k = f.getCategory() + "|" + f.getItem();
        if (k.contains("端口") && k.contains("监听"))
            return "以管理员配置入站阻断规则(Block)或停用对应服务;阻断规则支持端口范围(如0-20000)";
        if (k.contains("防火墙")) return "以管理员启用防火墙并设入站默认阻断";
        if (k.contains("远程桌面")) return "关闭 xrdp/VNC 或 RDP 服务及 3389/5900 端口";
        if (k.contains("屏保") || k.contains("锁屏")) return "设置无操作 300 秒锁屏且恢复需密码";
        if (k.contains("凭证") || k.contains("明文")) return "将密码迁移至环境变量/密钥管理,删除明文,修改弱口令";
        if (k.contains("非开源")) return "核实正版授权,避免使用破解版";
        if (k.contains("压缩包")) return "请对比桌管软件-应用商城发布版本,及时更新至最新版本";
        if (k.contains("依赖")) return "升级依赖包至修复版本(见详情);优先处理 Log4Shell/Fastjson/Spring4Shell 等 RCE 类漏洞";
        if (k.contains("跨网")) return "卸载跨网/远控/穿透工具;业务必需时走审批并纳入统一管控";
        if (k.contains("无线")) return "非必要请禁用 WiFi 无线网卡(设备管理器禁用或 BIOS 关闭)";
        if (k.contains("U盘")) return "核实使用记录;部署外设管控(禁用 USB 存储),确需使用走审批并加密";
        return "";
    }
}