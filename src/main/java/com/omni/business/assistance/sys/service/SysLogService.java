package com.omni.business.assistance.sys.service;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Service;
import com.omni.framework.spring.config.ConfigLoader;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统日志读取服务。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>枚举日志目录下的可读文件（.log / .txt）</li>
 *   <li>tail：读取最后 N 行（限制最大字节数，防止大文件爆内存）</li>
 *   <li>readFrom：从偏移量读取增量（用于前端轮询）</li>
 *   <li>安全：拒绝路径穿越、拒绝非白名单扩展名、拒绝越出根目录</li>
 * </ul>
 *
 * <p>日志目录探测顺序：</p>
 * <ol>
 *   <li>application.properties 中 sys.log.dir（绝对或相对 user.dir）</li>
 *   <li>未配置时，解析 classpath 下 log-config.xml 中第一个
 *       &lt;param name="file" value="..."/&gt; 的父目录</li>
 *   <li>兜底：user.dir/logs</li>
 * </ol>
 *
 * <p>后续扩展建议：新增其他系统功能时，请按
 * com.omni.business.assistance.sys.service /
 * ...sys.controller 分包，避免与本类耦合。</p>
 */
@Service
public class SysLogService {

    private static final Logger log = LoggerFactory.getLogger(SysLogService.class);

    /** 单次读取最大字节（防止大文件爆内存） */
    private static final long MAX_READ_BYTES = 5L * 1024 * 1024;
    /** tail 默认最多返回行数 */
    private static final int MAX_TAIL_LINES = 5000;

    /** 允许读取的扩展名（白名单） */
    private static final List<String> ALLOWED_EXTS = Arrays.asList(".log", ".txt");

    /** 已探测的目录缓存（首次探测开销大，避免反复解析 XML） */
    private static final Map<String, Path> DIR_CACHE = new ConcurrentHashMap<>();

    private final Path logDir;

    public SysLogService() {
        this.logDir = detectLogDir();
        log.info("系统日志目录: {}", this.logDir);
    }

    public Path getLogDir() {
        return logDir;
    }

    // ==================== 目录探测 ====================

    private static Path detectLogDir() {
        Path cached = DIR_CACHE.get("default");
        if (cached != null) return cached;
        synchronized (DIR_CACHE) {
            cached = DIR_CACHE.get("default");
            if (cached != null) return cached;

            Path dir = null;

            // ① 显式配置
            try {
                String cfg = ConfigLoader.getProperty("sys.log.dir", "");
                if (cfg != null && !cfg.trim().isEmpty()) {
                    dir = toAbs(cfg.trim());
                }
            } catch (Exception ignored) {}

            // ② 从 log-config.xml 解析
            if (dir == null) {
                String fromXml = detectFromLogConfig();
                if (fromXml != null) {
                    dir = toAbs(fromXml);
                }
            }

            // ③ 兜底
            if (dir == null) {
                dir = Paths.get(System.getProperty("user.dir", "."), "logs")
                        .toAbsolutePath().normalize();
            }

            DIR_CACHE.put("default", dir);
            return dir;
        }
    }

    private static Path toAbs(String p) {
        Path path = Paths.get(p);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir", ".")).resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }

    /**
     * 从 classpath 下 log-config.xml 解析第一个
     * param name="file" 的父目录。解析失败不影响服务启动。
     */
    private static String detectFromLogConfig() {
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("log-config.xml")) {
            if (is == null) return null;
            org.w3c.dom.Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(is);
            org.w3c.dom.NodeList params = doc.getElementsByTagName("param");
            for (int i = 0; i < params.getLength(); i++) {
                org.w3c.dom.Element p = (org.w3c.dom.Element) params.item(i);
                if ("file".equals(p.getAttribute("name"))) {
                    String v = p.getAttribute("value");
                    if (v != null && !v.trim().isEmpty()) {
                        File f = new File(v.trim());
                        String parent = f.getParent();
                        return parent != null ? parent : null;
                    }
                }
            }
        } catch (Exception e) {
            // 静默失败：探测仅用于零配置体验
        }
        return null;
    }

    // ==================== 对外 API ====================

    /** 列出可读的日志文件（按修改时间倒序） */
    public List<Map<String, Object>> listFiles() throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        if (!Files.isDirectory(logDir)) return list;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(logDir)) {
            for (Path p : ds) {
                if (!Files.isRegularFile(p)) continue;
                String name = p.getFileName().toString().toLowerCase();
                boolean ok = ALLOWED_EXTS.stream().anyMatch(name::endsWith);
                if (!ok) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", p.getFileName().toString());
                m.put("size", Files.size(p));
                m.put("modified", Files.getLastModifiedTime(p).toMillis());
                list.add(m);
            }
        }
        list.sort((a, b) -> Long.compare((Long) b.get("modified"), (Long) a.get("modified")));
        return list;
    }

    /**
     * 读取最后 N 行。返回 { fileName, size, offset, lines: [...] }。
     * offset 等于当前文件大小，前端以此作为增量读取起点。
     */
    public Map<String, Object> tail(String fileName, int lines) throws IOException {
        if (lines <= 0) lines = 500;
        if (lines > MAX_TAIL_LINES) lines = MAX_TAIL_LINES;

        Path file = resolve(fileName);
        long size = Files.size(file);

        long startPos = Math.max(0, size - MAX_READ_BYTES);
        int bufLen = (int) (size - startPos);
        byte[] buf = new byte[Math.max(bufLen, 0)];
        if (bufLen > 0) {
            try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
                raf.seek(startPos);
                raf.readFully(buf);
            }
        }

        String content = new String(buf, StandardCharsets.UTF_8);
        // 从中间截断时，丢弃第一行不完整内容
        if (startPos > 0) {
            int nl = content.indexOf('\n');
            content = (nl >= 0) ? content.substring(nl + 1) : "";
        }

        String[] all = content.split("\n", -1);
        int from = Math.max(0, all.length - lines);
        List<String> result = new ArrayList<>();
        for (int i = from; i < all.length; i++) {
            if (i == all.length - 1 && all[i].isEmpty()) continue;
            result.add(all[i]);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fileName", fileName);
        m.put("size", size);
        m.put("offset", size);
        m.put("lines", result);
        return m;
    }

    /**
     * 从 offset 读取增量。返回 { fileName, size, offset, content, truncated? }。
     * 若 offset &gt; 当前 size（文件被截断/滚动），返回 truncated=true，前端需重新 tail。
     */
    public Map<String, Object> readFrom(String fileName, long offset) throws IOException {
        Path file = resolve(fileName);
        long size = Files.size(file);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fileName", fileName);
        m.put("size", size);

        if (offset < 0) offset = 0;
        if (offset > size) {
            m.put("truncated", true);
            m.put("content", "");
            m.put("offset", size);
            return m;
        }
        if (offset == size) {
            m.put("content", "");
            m.put("offset", size);
            return m;
        }

        long toRead = size - offset;
        if (toRead > MAX_READ_BYTES) {
            m.put("truncated", true);
            m.put("content", "");
            m.put("offset", size);
            return m;
        }

        byte[] buf = new byte[(int) toRead];
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(offset);
            raf.readFully(buf);
        }
        m.put("content", new String(buf, StandardCharsets.UTF_8));
        m.put("offset", size);
        return m;
    }

    /**
     * 安全解析文件名。校验顺序：
     *   1) 非空
     *   2) 不含 .. / \ 等路径分隔符（防御路径穿越）
     *   3) 扩展名在白名单
     *   4) resolve + normalize 后必须落在 logDir 内
     *   5) 必须存在且为普通文件
     */
    public Path resolve(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String name = fileName.trim();
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new SecurityException("非法文件名: " + name);
        }
        String lower = name.toLowerCase();
        boolean extOk = ALLOWED_EXTS.stream().anyMatch(lower::endsWith);
        if (!extOk) {
            throw new SecurityException("不允许的文件类型: " + name);
        }
        Path p = logDir.resolve(name).normalize();
        if (!p.startsWith(logDir)) {
            throw new SecurityException("路径越界: " + name);
        }
        if (!Files.isRegularFile(p)) {
            throw new IllegalArgumentException("文件不存在: " + name);
        }
        return p;
    }
}