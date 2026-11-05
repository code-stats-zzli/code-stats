package com.omni.business.assistance.sys.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.business.assistance.sys.model.RemoteFile;
import com.omni.business.assistance.sys.model.SshConnection;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Service;
import com.omni.framework.spring.config.ConfigLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSH 远程连接服务（密码不入库版本）。
 *
 * <p><b>密码策略</b>：</p>
 * <ul>
 *   <li>保存连接时，password 写入 {@link SshCredentialCache}，入库前置 null，不落盘；</li>
 *   <li>查询连接时，password 始终为 null，只回显 hasPassword；</li>
 *   <li>测试/执行/上传/下载时，从缓存取密码 set 进副本对象，不改动 connections Map 中的原对象；</li>
 *   <li>页面关闭时调用 {@link #closeSession(String)} 清理缓存。</li>
 * </ul>
 */
@Service
public class SshConnectionService {
    private static final Logger log = LoggerFactory.getLogger(SshConnectionService.class);

    @Autowired
    private SshCredentialCache credentialCache;

    private final Map<String, SshConnection> connections = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path storeFile;

    private final int execTimeout;
    private final int scpTimeout;
    private final long maxReadBytes;
    private final long maxUploadBytes;

    public SshConnectionService() {
        String custom = safeGet("ssh.store.path", "");
        if (custom != null && !custom.trim().isEmpty()) {
            this.storeFile = Paths.get(custom.trim()).toAbsolutePath().normalize();
        } else {
            this.storeFile = Paths.get(System.getProperty("user.dir"), "resources", "ssh-connections.json")
                    .toAbsolutePath().normalize();
        }
        this.execTimeout    = parseInt(safeGet("ssh.exec.timeout", "60"), 60);
        this.scpTimeout     = parseInt(safeGet("ssh.scp.timeout", "120"), 120);
        this.maxReadBytes   = parseLong(safeGet("ssh.read.max-bytes", String.valueOf(1024L * 1024)), 1024L * 1024);
        this.maxUploadBytes = parseLong(safeGet("ssh.upload.max-bytes", String.valueOf(20L * 1024 * 1024)), 20L * 1024 * 1024);
        loadFromDisk();
    }

    // ==================== 持久化（密码不落盘） ====================

    private void loadFromDisk() {
        try {
            if (!Files.exists(storeFile)) return;
            byte[] data = Files.readAllBytes(storeFile);
            List<SshConnection> list = objectMapper.readValue(data, new TypeReference<List<SshConnection>>() {});
            if (list != null) {
                for (SshConnection c : list) {
                    if (c != null && c.getId() != null) {
                        c.setPassword(null); // 双保险：加载后密码为 null
                        connections.put(c.getId(), c);
                    }
                }
            }
            log.info("已加载 {} 个 SSH 连接配置（密码不落盘）: {}", connections.size(), storeFile);
        } catch (Exception e) {
            log.warn("加载 SSH 连接配置失败: {}", e.getMessage());
        }
    }

    private synchronized void saveToDisk() {
        try {
            Path parent = storeFile.getParent();
            if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);
            List<SshConnection> list = new ArrayList<>(connections.values());
            // 双保险：入库前显式清空密码，配合 @JsonProperty(WRITE_ONLY)
            for (SshConnection c : list) c.setPassword(null);
            byte[] data = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(list);
            Files.write(storeFile, data);
        } catch (Exception e) {
            log.error("保存 SSH 连接配置失败", e);
        }
    }

    // ==================== 连接 CRUD ====================

    public List<SshConnection> list() {
        List<SshConnection> out = new ArrayList<>();
        for (SshConnection c : connections.values()) {
            out.add(toView(c));
        }
        return out;
    }

    public SshConnection get(String id) {
        SshConnection c = id == null ? null : connections.get(id);
        return c == null ? null : toView(c);
    }

    public SshConnection save(SshConnection c) {
        if (c == null) throw new IllegalArgumentException("连接配置为空");

        String id = c.getId();
        boolean isNew = (id == null || id.trim().isEmpty());
        if (isNew) {
            id = UUID.randomUUID().toString().replace("-", "");
            c.setId(id);
        }

        // 取出本次请求里的密码（可能为 null / 空，表示不修改密码）
        String incomingPassword = c.getPassword();

        // 入库前置 null，保证 connections Map 中不存密码
        c.setPassword(null);
        connections.put(id, c);

        // 密码放缓存：只有显式传了非空密码才覆盖
        if (incomingPassword != null && !incomingPassword.isEmpty()) {
            credentialCache.put(id, incomingPassword);
        }

        saveToDisk();
        return toView(c);
    }

    public void delete(String id) {
        if (id == null) return;
        connections.remove(id);
        credentialCache.clear(id);
        saveToDisk();
    }

    /** 页面关闭时清理密码缓存 */
    public void closeSession(String id) {
        credentialCache.clear(id);
    }

    // ==================== 连接测试 ====================

    public Map<String, Object> test(String id) {
        SshConnection c = requireRaw(id);
        Map<String, Object> r = new LinkedHashMap<>();

        // 组装带密码的副本
        SshConnection op = copyForOperation(c);
        String pwd = credentialCache.get(id);
        if (pwd != null) op.setPassword(pwd);

        boolean hasKey = op.getKeyPath() != null && !op.getKeyPath().trim().isEmpty();
        if (pwd == null && !hasKey) {
            c.setLastTestTime(System.currentTimeMillis());
            c.setLastTestOk(false);
            c.setLastTestError("密码已失效（页面关闭或超时），请重新保存密码");
            r.put("success", false);
            r.put("error", "密码已失效（页面关闭或超时），请重新保存密码");
            return r;
        }

        try {
            String osType = JschSshClient.testConnection(op, 15);
            c.setOsType(osType);
            c.setLastTestTime(System.currentTimeMillis());
            c.setLastTestOk(true);
            c.setLastTestError(null);
            r.put("success", true);
            r.put("osType", osType);
            r.put("stdout", osType);
            r.put("error", null);
        } catch (Exception e) {
            c.setLastTestTime(System.currentTimeMillis());
            c.setLastTestOk(false);
            c.setLastTestError(e.getMessage());
            r.put("success", false);
            r.put("error", e.getMessage());
        }
        saveToDisk();
        return r;
    }

    // ==================== 执行命令 ====================

    public Map<String, Object> exec(String id, String cmd) throws Exception {
        if (cmd == null || cmd.trim().isEmpty()) throw new IllegalArgumentException("命令不能为空");
        if (isDangerous(cmd)) throw new SecurityException("命令被安全策略拦截");

        SshConnection op = loadWithCredential(id);
        ensureOs(op);

        String finalCmd = wrapCommand(op, cmd);
        JschSshClient.Result res = JschSshClient.exec(op, finalCmd, execTimeout);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("exitCode", res.exitCode);
        r.put("stdout", res.stdout);
        r.put("stderr", res.stderr);
        r.put("durationMs", res.durationMs);
        r.put("timeout", res.timeout);
        return r;
    }

    // ==================== 目录列表 ====================

    public List<RemoteFile> listDir(String id, String path) throws Exception {
        SshConnection op = loadWithCredential(id);
        ensureOs(op);
        String p = safePath(path, op);
        if ("windows".equalsIgnoreCase(op.getOsType())) return listDirWindows(op, p);
        return listDirLinux(op, p);
    }

    private List<RemoteFile> listDirLinux(SshConnection c, String path) throws Exception {
        String cmd = "ls -lA --time-style=long-iso -- " + shellQuote(path) + " 2>/dev/null";
        JschSshClient.Result res = JschSshClient.exec(c, cmd, 20);
        List<RemoteFile> list = new ArrayList<>();
        if (res.exitCode != 0 && (res.stdout == null || res.stdout.isEmpty())) return list;

        String baseDir = path;
        if (baseDir.endsWith("/") && baseDir.length() > 1) baseDir = baseDir.substring(0, baseDir.length() - 1);
        if (".".equals(baseDir)) baseDir = "";

        String[] lines = res.stdout == null ? new String[0] : res.stdout.split("\n");
        for (String line : lines) {
            String s = line == null ? "" : line.trim();
            if (s.isEmpty() || s.startsWith("total ")) continue;
            String[] parts = s.split("\\s+", 8);
            if (parts.length < 8) continue;
            String perm = parts[0];
            if (perm.length() < 10) continue;
            boolean isDir = perm.charAt(0) == 'd';
            long size = 0;
            try { size = Long.parseLong(parts[4]); } catch (Exception ignored) {}
            String modified = parts[5] + " " + parts[6];
            String name = parts[7];
            if (".".equals(name) || "..".equals(name)) continue;

            RemoteFile rf = new RemoteFile();
            rf.setName(name);
            rf.setDirectory(isDir);
            rf.setSize(size);
            rf.setModified(modified);
            rf.setPermissions(perm);
            rf.setPath(joinPath(baseDir, name, "/"));
            list.add(rf);
        }
        sortList(list);
        return list;
    }

    private List<RemoteFile> listDirWindows(SshConnection c, String path) throws Exception {
        String q = path.replace("'", "''");
        String cmd = "powershell -NoProfile -Command \"Get-ChildItem -Force -LiteralPath '" + q
                + "' | Select-Object Name,Length,LastWriteTime,Attributes | ConvertTo-Json -Compress\"";
        JschSshClient.Result res = JschSshClient.exec(c, cmd, 25);
        List<RemoteFile> list = new ArrayList<>();
        if (res.exitCode != 0 && (res.stdout == null || res.stdout.isEmpty())) return list;
        String json = res.stdout == null ? "" : res.stdout.trim();
        if (json.isEmpty()) return list;

        try {
            if (json.startsWith("{")) json = "[" + json + "]";
            com.fasterxml.jackson.databind.JsonNode arr = objectMapper.readTree(json);
            if (arr.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode n : arr) {
                    String name = n.path("Name").asText();
                    if (".".equals(name) || "..".equals(name)) continue;
                    RemoteFile rf = new RemoteFile();
                    rf.setName(name);
                    rf.setSize(n.path("Length").asLong(0));
                    String attr = n.path("Attributes").asText("");
                    rf.setPermissions(attr);
                    rf.setDirectory(attr.contains("Directory"));
                    String modified = n.path("LastWriteTime").asText("");
                    if (modified.startsWith("\\/Date(")) {
                        try {
                            int end = modified.indexOf(")/");
                            long ms = Long.parseLong(modified.substring(7, end));
                            modified = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(ms));
                        } catch (Exception ignored) {}
                    }
                    rf.setModified(modified);
                    rf.setPath(joinPath(path, name, "\\"));
                    list.add(rf);
                }
            }
        } catch (Exception e) {
            log.warn("解析 Windows 目录列表失败: {}", e.getMessage());
        }
        sortList(list);
        return list;
    }

    // ==================== 读 / 写文件 ====================

    public String readFile(String id, String path) throws Exception {
        SshConnection op = loadWithCredential(id);
        ensureOs(op);

        String sizeCmd = "windows".equalsIgnoreCase(op.getOsType())
                ? "powershell -NoProfile -Command \"(Get-Item -LiteralPath '" + path.replace("'", "''") + "').Length\""
                : "stat -c %s -- " + shellQuote(path);
        try {
            JschSshClient.Result sz = JschSshClient.exec(op, sizeCmd, 10);
            if (sz.stdout != null && !sz.stdout.trim().isEmpty()) {
                long size = Long.parseLong(sz.stdout.trim());
                if (size > maxReadBytes) {
                    throw new IllegalArgumentException("文件过大 (" + size + " bytes)，请直接下载");
                }
            }
        } catch (NumberFormatException ignored) {}

        String cmd = "windows".equalsIgnoreCase(op.getOsType())
                ? "powershell -NoProfile -Command \"[System.IO.File]::ReadAllText('" + path.replace("'", "''") + "')\""
                : "cat -- " + shellQuote(path);
        JschSshClient.Result res = JschSshClient.exec(op, cmd, 20);
        if (res.exitCode != 0 && (res.stdout == null || res.stdout.isEmpty())) {
            throw new Exception("读取文件失败: " + safeTrim(res.stderr));
        }
        return res.stdout == null ? "" : res.stdout;
    }

    public void writeFile(String id, String path, byte[] data) throws Exception {
        if (data == null) throw new IllegalArgumentException("内容为空");
        if (data.length > maxUploadBytes) throw new IllegalArgumentException("文件过大");
        SshConnection op = loadWithCredential(id);
        JschSshClient.upload(op, data, path, scpTimeout);
    }

    public byte[] download(String id, String path) throws Exception {
        SshConnection op = loadWithCredential(id);
        return JschSshClient.download(op, path, scpTimeout);
    }

    // ==================== 增 / 删 / 改 ====================

    public void delete(String id, String path) throws Exception {
        SshConnection op = loadWithCredential(id);
        ensureOs(op);
        if (path == null || path.trim().isEmpty()
                || "/".equals(path.trim()) || "\\".equals(path.trim())
                || path.trim().matches("^[A-Za-z]:[\\\\/]?$")) {
            throw new SecurityException("不允许删除根目录");
        }
        String cmd;
        if ("windows".equalsIgnoreCase(op.getOsType())) {
            String q = "\"" + path.replace("\"", "\"\"") + "\"";
            cmd = "powershell -NoProfile -Command \"if (Test-Path -LiteralPath " + q
                    + ") { if ((Get-Item -LiteralPath " + q + ").PSIsContainer) { Remove-Item -LiteralPath "
                    + q + " -Recurse -Force } else { Remove-Item -LiteralPath " + q + " -Force } }\"";
        } else {
            cmd = "rm -rf -- " + shellQuote(path);
        }
        JschSshClient.Result res = JschSshClient.exec(op, cmd, 30);
        if (res.exitCode != 0) throw new Exception("删除失败: " + safeTrim(res.stderr));
    }

    public void rename(String id, String from, String to) throws Exception {
        if (from == null || to == null) throw new IllegalArgumentException("参数为空");
        SshConnection op = loadWithCredential(id);
        ensureOs(op);
        String cmd;
        if ("windows".equalsIgnoreCase(op.getOsType())) {
            cmd = "powershell -NoProfile -Command \"Move-Item -LiteralPath '"
                    + from.replace("'", "''") + "' -Destination '" + to.replace("'", "''") + "' -Force\"";
        } else {
            cmd = "mv -- " + shellQuote(from) + " " + shellQuote(to);
        }
        JschSshClient.Result res = JschSshClient.exec(op, cmd, 15);
        if (res.exitCode != 0) throw new Exception("重命名失败: " + safeTrim(res.stderr));
    }

    public void mkdir(String id, String parent, String name) throws Exception {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("目录名不能为空");
        if (name.contains("/") || name.contains("\\")) throw new IllegalArgumentException("目录名不能包含斜杠");
        SshConnection op = loadWithCredential(id);
        ensureOs(op);
        String sep = "windows".equalsIgnoreCase(op.getOsType()) ? "\\" : "/";
        String full = joinPath(parent == null || parent.isEmpty() ? "." : parent, name.trim(), sep);
        String cmd;
        if ("windows".equalsIgnoreCase(op.getOsType())) {
            cmd = "cmd /c mkdir \"" + full.replace("\"", "\"\"") + "\"";
        } else {
            cmd = "mkdir -p -- " + shellQuote(full);
        }
        JschSshClient.Result res = JschSshClient.exec(op, cmd, 15);
        if (res.exitCode != 0) throw new Exception("创建目录失败: " + safeTrim(res.stderr));
    }

    // ==================== 内部工具 ====================

    private SshConnection requireRaw(String id) {
        SshConnection c = connections.get(id);
        if (c == null) throw new IllegalArgumentException("连接不存在: " + id);
        return c;
    }

    /**
     * 从持久层 + 缓存组装带密码的副本对象。
     * 副本修改不会影响 connections Map 中的原对象。
     */
    private SshConnection loadWithCredential(String id) {
        SshConnection src = requireRaw(id);
        SshConnection op = copyForOperation(src);
        String pwd = credentialCache.get(id);
        if (pwd != null) op.setPassword(pwd);
        boolean hasKey = op.getKeyPath() != null && !op.getKeyPath().trim().isEmpty();
        if (pwd == null && !hasKey) {
            throw new IllegalStateException(
                    "该连接的密码已失效（页面关闭或超时），请重新保存密码后再操作。connectionId=" + id);
        }
        return op;
    }

    private SshConnection copyForOperation(SshConnection src) {
        SshConnection c = new SshConnection();
        c.setId(src.getId());
        c.setName(src.getName());
        c.setHost(src.getHost());
        c.setPort(src.getPort());
        c.setUsername(src.getUsername());
        c.setKeyPath(src.getKeyPath());
        c.setOsType(src.getOsType());
        c.setRemark(src.getRemark());
        // 不复制 password
        return c;
    }

    private SshConnection toView(SshConnection src) {
        SshConnection v = copyForOperation(src);
        v.setHasPassword(credentialCache.contains(src.getId()));
        v.setHasKeyPath(src.getKeyPath() != null && !src.getKeyPath().trim().isEmpty());
        v.setLastTestTime(src.getLastTestTime());
        v.setLastTestOk(src.isLastTestOk());
        v.setLastTestError(src.getLastTestError());
        return v;
    }

    private void ensureOs(SshConnection c) {
        if (c.getOsType() == null || c.getOsType().isEmpty() || "auto".equalsIgnoreCase(c.getOsType())) {
            try {
                c.setOsType(JschSshClient.detectOsType(c, 15));
            } catch (Exception e) {
                c.setOsType("linux");
            }
        }
    }

    private String wrapCommand(SshConnection c, String cmd) {
        if ("windows".equalsIgnoreCase(c.getOsType())) return "cmd /c " + cmd;
        return cmd;
    }

    private boolean isDangerous(String cmd) {
        String lc = cmd.toLowerCase();
        String[] black = {"rm -rf /", "rm -rf /*", "shutdown", "reboot",
                ":(){:|:&};:", "mkfs", "dd if=/dev/zero of=/dev/sd", "format c:"};
        for (String b : black) if (lc.contains(b)) return true;
        return false;
    }

    private String safePath(String p, SshConnection c) {
        if (p == null || p.trim().isEmpty()) {
            return "windows".equalsIgnoreCase(c.getOsType()) ? "C:\\" : ".";
        }
        String s = p.trim().replace('\0', ' ');
        for (String seg : s.split("[/\\\\]")) {
            if ("..".equals(seg)) throw new SecurityException("路径包含非法片段: ..");
        }
        return s;
    }

    private static String shellQuote(String path) {
        return "'" + path.replace("'", "'\\''") + "'";
    }

    private static String joinPath(String base, String name, String sep) {
        if (base == null || base.isEmpty() || ".".equals(base)) return name;
        if (base.endsWith("/") || base.endsWith("\\")) return base + name;
        return base + sep + name;
    }

    private static void sortList(List<RemoteFile> list) {
        list.sort((a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
    }

    private static String safeTrim(String s) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() > 400 ? t.substring(0, 400) + "..." : t;
    }

    private static String safeGet(String key, String def) {
        try { return ConfigLoader.getProperty(key, def); }
        catch (Throwable t) { return def; }
    }
    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
    private static long parseLong(String s, long def) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return def; }
    }
}