package com.omni.business.assistance.systemcheck.service;

import com.omni.business.assistance.systemcheck.command.SystemCheckCommand;
import com.omni.business.assistance.systemcheck.command.impl.*;
import com.omni.business.assistance.systemcheck.config.CheckConfig;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Service;

import java.util.*;

@Service
public class SystemCheckService {
    private static final Logger log = LoggerFactory.getLogger(SystemCheckService.class);

    private final List<SystemCheckCommand> commands = new ArrayList<>();

    public SystemCheckService() {
        commands.add(new SystemInfoCheck());
        commands.add(new FirewallCheck());
        commands.add(new PortCheck());
        commands.add(new RdpCheck());
        commands.add(new LockScreenCheck());
        commands.add(new CredentialsCheck());
        commands.add(new ArchiveCheck());
        commands.add(new PlaintextScanCheck());
        commands.add(new DependencyVulnCheck());
        commands.add(new EndpointSecurityCheck());
        log.info("SystemCheckService 初始化完成，共 {} 个检查命令", commands.size());
    }

    public List<SystemCheckCommand> getCommands() { return commands; }
}