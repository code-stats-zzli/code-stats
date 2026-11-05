package com.omni.business.assistance.cluster.channel;

import com.omni.business.assistance.cluster.model.ClusterNode;
import com.omni.business.assistance.cluster.model.DeployRequest;
import com.omni.business.assistance.cluster.model.JdkInfo;

import java.nio.file.Path;
import java.util.List;

/**
 * 部署通道抽象。三种实现：local / ssh / manual。
 */
public interface DeployChannel {

    String getName();

    /** 探测本机/远端 JDK 候选列表 */
    JdkInfo checkJdk(DeployRequest req) throws Exception;

    /**
     * 从 fromPort 开始向后探测 count 个端口，返回第一个空闲端口。
     * 返回 -1 表示全占。
     */
    int probePort(DeployRequest req, int fromPort, int count) throws Exception;

    /**
     * 执行部署：上传 jar + 启动 Worker。
     * 返回已创建但尚未注册的 ClusterNode（state = STARTING）。
     * manual 通道在这里只返回"待用户手动执行的命令"，不真正执行。
     */
    DeployResult deploy(DeployRequest req,
                        Path localJar,
                        ClusterNode node,
                        String masterUrl,
                        String masterToken) throws Exception;

    /** 停止节点（manual 通道返回命令让用户自己执行） */
    StopResult stop(ClusterNode node) throws Exception;

    class DeployResult {
        public boolean success;
        public boolean manualOnly;          // true 表示只生成了命令
        public String  message;
        public Long    pid;                 // local/ssh 通道返回 pid
        public String  startCommand;        // 给用户展示的命令
        public String  downloadUrl;         // manual 用
        public String  actualPort;          // worker 实际占用的端口（自增 fallback 后）
        public List<String> logs;           // 部署过程中的关键日志
    }

    class StopResult {
        public boolean success;
        public boolean manualOnly;
        public String  message;
        public String  stopCommand;
    }
}