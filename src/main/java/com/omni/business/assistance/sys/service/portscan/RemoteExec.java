package com.omni.business.assistance.sys.service.portscan;

/**
 * 远程命令执行回调。
 * PortScanService 用它桥接 SshConnectionService.exec，
 * PortCollector 只关心「执行命令并拿到 stdout」。
 */
@FunctionalInterface
public interface RemoteExec {
    String exec(String cmd) throws Exception;
}