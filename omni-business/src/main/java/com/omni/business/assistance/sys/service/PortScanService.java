package com.omni.business.assistance.sys.service;

import com.omni.business.assistance.sys.model.PortInfo;
import com.omni.business.assistance.sys.model.PortScanRequest;
import com.omni.business.assistance.sys.model.PortScanResult;
import com.omni.business.assistance.sys.model.SshConnection;
import com.omni.business.assistance.sys.service.portscan.DefaultServiceIdentifier;
import com.omni.business.assistance.sys.service.portscan.LinuxPortCollector;
import com.omni.business.assistance.sys.service.portscan.PortCollector;
import com.omni.business.assistance.sys.service.portscan.RemoteExec;
import com.omni.business.assistance.sys.service.portscan.ServiceIdentifier;
import com.omni.business.assistance.sys.service.portscan.WindowsPortCollector;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 端口探测编排服务。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li><b>策略注册</b>：collectors 按 osType 索引，新增平台 = 新增一个 {@link PortCollector} + register()</li>
 *   <li><b>RemoteExec 桥接</b>：把 SshConnectionService.exec 封装成函数式回调，策略层不依赖 SSH 实现</li>
 *   <li><b>容错</b>：采集异常写入 result.error，不中断请求；调用方据此提示用户</li>
 *   <li><b>汇总</b>：按分类 / 按服务聚合，方便前端展示</li>
 * </ul>
 */
@Service
public class PortScanService {
    private static final Logger log = LoggerFactory.getLogger(PortScanService.class);

    @Autowired
    private SshConnectionService sshConnectionService;

    private final Map<String, PortCollector> collectors = new LinkedHashMap<>();
    private final ServiceIdentifier serviceIdentifier = new DefaultServiceIdentifier();

    public PortScanService() {
        register(new LinuxPortCollector());
        register(new WindowsPortCollector());
        log.info("PortScanService 初始化完成，采集器: {}", collectors.keySet());
    }

    /** 注册采集器（osType 不区分大小写） */
    public void register(PortCollector collector) {
        if (collector == null || collector.getOsType() == null) return;
        collectors.put(collector.getOsType().toLowerCase(), collector);
    }

    public PortScanResult scan(PortScanRequest req) {
        long start = System.currentTimeMillis();
        PortScanResult result = new PortScanResult();

        if (req == null || req.getConnectionId() == null || req.getConnectionId().trim().isEmpty()) {
            result.setError("缺少 connectionId");
            result.setPorts(Collections.emptyList());
            result.setSummary(Collections.singletonMap("total", 0));
            result.setDurationMs(System.currentTimeMillis() - start);
            return result;
        }

        SshConnection conn = sshConnectionService.get(req.getConnectionId());
        if (conn == null) {
            result.setError("连接不存在: " + req.getConnectionId());
            result.setPorts(Collections.emptyList());
            result.setSummary(Collections.singletonMap("total", 0));
            result.setDurationMs(System.currentTimeMillis() - start);
            return result;
        }

        String osType = conn.getOsType() == null ? "linux" : conn.getOsType().toLowerCase();
        if ("auto".equals(osType)) osType = "linux";

        PortCollector collector = collectors.get(osType);
        if (collector == null) collector = collectors.get("linux");
        if (collector == null) {
            result.setError("没有可用的端口采集器");
            result.setPorts(Collections.emptyList());
            result.setSummary(Collections.singletonMap("total", 0));
            result.setDurationMs(System.currentTimeMillis() - start);
            return result;
        }

        final String finalOsType = osType;
        final PortCollector finalCollector = collector;
        RemoteExec exec = cmd -> {
            Map<String, Object> r = sshConnectionService.exec(req.getConnectionId(), cmd);
            Object stdout = r == null ? null : r.get("stdout");
            return stdout == null ? "" : stdout.toString();
        };

        List<PortInfo> ports = new ArrayList<>();
        try {
            List<PortInfo> raw = finalCollector.collect(exec);
            if (raw != null) ports.addAll(raw);
        } catch (Exception e) {
            log.error("端口采集失败: osType={}, connId={}", finalOsType, req.getConnectionId(), e);
            result.setError("端口采集失败: " + e.getMessage());
        }

        for (PortInfo p : ports) {
            p.setServiceName(serviceIdentifier.identify(p.getPort(), p.getProtocol(), p.getProcessName()));
            p.setServiceCategory(serviceIdentifier.category(p.getPort()));
        }

        if (req.getOnlyPorts() != null && !req.getOnlyPorts().isEmpty()) {
            Set<Integer> only = new HashSet<>(req.getOnlyPorts());
            ports = ports.stream().filter(p -> only.contains(p.getPort())).collect(Collectors.toList());
        }

        ports.sort(Comparator.comparingInt(PortInfo::getPort)
                .thenComparing(p -> "TCP".equalsIgnoreCase(p.getProtocol()) ? 0 : 1));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", ports.size());
        summary.put("tcpCount", ports.stream().filter(p -> "TCP".equalsIgnoreCase(p.getProtocol())).count());
        summary.put("udpCount", ports.stream().filter(p -> "UDP".equalsIgnoreCase(p.getProtocol())).count());
        summary.put("byCategory", ports.stream().collect(Collectors.groupingBy(
                p -> p.getServiceCategory() == null ? "其他" : p.getServiceCategory(),
                LinkedHashMap::new, Collectors.counting())));
        summary.put("byService", ports.stream().collect(Collectors.groupingBy(
                p -> p.getServiceName() == null ? "未知" : p.getServiceName(),
                LinkedHashMap::new, Collectors.counting())));

        result.setConnectionId(req.getConnectionId());
        result.setHost(conn.getHost());
        result.setOsType(finalOsType);
        result.setStartTime(start);
        result.setEndTime(System.currentTimeMillis());
        result.setDurationMs(result.getEndTime() - start);
        result.setPorts(ports);
        result.setSummary(summary);
        return result;
    }
}