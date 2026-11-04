package com.omni.business.assistance.cluster.channel;

import com.omni.business.assistance.cluster.model.ClusterNode;
import com.omni.business.assistance.cluster.model.DeployRequest;
import com.omni.business.assistance.cluster.model.JdkInfo;

import java.nio.file.Path;
import java.util.ArrayList;

/**
 * 手动通道：主服务只生成下载地址 + 启动命令，用户自己去目标机执行。
 * 部署成功后，Worker 会通过注册接口回到这里。
 */
public class ManualDeployChannel implements DeployChannel {

    @Override public String getName() { return "manual"; }

    @Override
    public JdkInfo checkJdk(DeployRequest req) {
        JdkInfo info = new JdkInfo();
        info.setError("手动模式无法远程探测 JDK，请在目标机自行验证 java -version");
        return info;
    }

    @Override
    public int probePort(DeployRequest req, int fromPort, int count) {
        return fromPort;   // 手动模式不探测，返回建议值让用户自行确认
    }

    @Override
    public DeployResult deploy(DeployRequest req, Path localJar, ClusterNode node,
                               String masterUrl, String masterToken) {
        DeployResult r = new DeployResult();
        r.success = true;
        r.manualOnly = true;
        r.downloadUrl = masterUrl + "/cluster/api/packages/download?jar="
                + encode(localJar.getFileName().toString())
                + "&token=" + encode(masterToken);
        String jarFile = localJar.getFileName().toString();
        String javaPath = req.getJavaPath() != null && !req.getJavaPath().isEmpty()
                ? req.getJavaPath() : "java";
        r.startCommand = String.format(
                "%s -jar %s " +
                        "--server.port=%d " +
                        "--cluster.role=worker " +
                        "--cluster.node.id=%s " +
                        "--cluster.master.url=%s " +
                        "--cluster.master.token=%s " +
                        "--screenshot.assistant.enabled=false " +
                        "--spring.profiles.active=worker",
                javaPath, jarFile, node.getPort(), node.getNodeId(), masterUrl, masterToken);
        r.logs = new ArrayList<>();
        r.logs.add("请在目标机执行：");
        r.logs.add("1) 下载 " + r.downloadUrl + " 到本地");
        r.logs.add("2) 执行上面生成的命令");
        return r;
    }

    @Override
    public StopResult stop(ClusterNode node) {
        StopResult r = new StopResult();
        r.success = true;
        r.manualOnly = true;
        r.stopCommand = node.getPid() != null
                ? "kill " + node.getPid() + "  (Linux)  /  taskkill /F /PID " + node.getPid() + "  (Windows)"
                : "请到目标机执行 pkill -f 'cluster.node.id=" + node.getNodeId() + "'";
        return r;
    }

    private static String encode(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }
}