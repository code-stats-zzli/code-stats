package com.omni.business.assistance.sys.service.portscan;

import com.omni.business.assistance.sys.model.PortInfo;

import java.util.List;

/**
 * 端口采集策略。
 * 每个操作系统一个实现，返回原始解析结果（不含服务识别）。
 * 新增平台 = 新增一个实现类 + register()。
 */
public interface PortCollector {
    /** 与 SshConnection.osType 匹配，如 "linux" / "windows" */
    String getOsType();

    /** 通过 exec 在目标机执行命令并解析端口列表 */
    List<PortInfo> collect(RemoteExec exec) throws Exception;
}