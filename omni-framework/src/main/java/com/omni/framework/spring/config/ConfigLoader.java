package com.omni.framework.spring.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * 配置加载器。
 *
 * <p>加载优先级（高 → 低）：</p>
 * <ol>
 *   <li>{@code user.dir/resources/others/system-params.json}（JSON，最高优先级，可实时修改）</li>
 *   <li>{@code classpath:application.properties}（兜底默认值）</li>
 * </ol>
 *
 * <p>{@link #setProperty(String, String)} / {@link #setProperties(Map)} 会同时
 * 更新内存与 JSON 文件，实现"实时可修改"。</p>
 */
public class ConfigLoader {
    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 系统参数 JSON 文件（资源目录 others，最高优先级） */
    public static final Path JSON_FILE = Paths.get(
            System.getProperty("user.dir"), "resources", "others", "system-params.json"
    ).toAbsolutePath().normalize();

    private static final Properties props = new Properties();

    static {
        loadDefaults();
        loadJsonFile(true);
    }

    public static Path getJsonFilePath() {
        return JSON_FILE;
    }

    // ==================== 加载 ====================

    private static void loadDefaults() {
        try (InputStream is = ConfigLoader.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (is == null) {
                log.warn("classpath:application.properties 不存在，使用空默认配置");
                return;
            }
            try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                Properties p = new Properties();
                p.load(reader);
                synchronized (props) {
                    props.putAll(p);
                }
            }
            log.info("已加载 classpath:application.properties");
        } catch (Exception e) {
            log.warn("加载 classpath application.properties 失败", e);
        }
    }

    /**
     * 加载 JSON 文件（最高优先级）。
     * @param createIfMissing true 时，文件不存在则用当前 props 生成一份默认 JSON
     */
    private static void loadJsonFile(boolean createIfMissing) {
        try {
            if (!Files.isRegularFile(JSON_FILE)) {
                if (createIfMissing) {
                    persistToJson();
                    log.info("系统参数 JSON 文件不存在，已用默认配置生成: {}", JSON_FILE);
                }
                return;
            }
            byte[] data = Files.readAllBytes(JSON_FILE);
            if (data.length == 0) return;

            Map<String, Object> raw = MAPPER.readValue(
                    data, new TypeReference<Map<String, Object>>() {});
            Map<String, String> map = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                if (e.getValue() == null) continue;
                map.put(e.getKey(), String.valueOf(e.getValue()));
            }
            synchronized (props) {
                props.putAll(map);
            }
            log.info("已加载外部 JSON 配置: {} ({} 项)", JSON_FILE, map.size());
        } catch (Exception e) {
            log.warn("加载 JSON 配置失败: " + JSON_FILE, e);
        }
    }

    // ==================== 持久化 ====================

    /**
     * 将当前全部配置（classpath + 覆盖）原子写入 JSON 文件。
     * Key 按字母排序，保证多次写入结果稳定。
     */
    public static synchronized void persistToJson() {
        try {
            Path parent = JSON_FILE.getParent();
            if (parent != null) Files.createDirectories(parent);

            TreeMap<String, String> sorted = new TreeMap<>();
            synchronized (props) {
                for (String name : props.stringPropertyNames()) {
                    sorted.put(name, props.getProperty(name));
                }
            }
            byte[] data = MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(sorted);

            Path tmp = JSON_FILE.resolveSibling(JSON_FILE.getFileName() + ".tmp");
            Files.write(tmp, data);
            Files.move(tmp, JSON_FILE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            log.error("持久化 JSON 配置失败", e);
        }
    }

    // ==================== 读取 ====================

    public static String getProperty(String key) {
        return props.getProperty(key);
    }

    public static String getProperty(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    // ==================== 运行时更新（内存 + JSON 同步） ====================

    public static synchronized void setProperty(String key, String value) {
        if (key == null) return;
        if (value == null) value = "";
        props.setProperty(key, value);
        persistToJson();
    }

    public static synchronized void setProperties(Map<String, String> map) {
        if (map == null || map.isEmpty()) return;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (e.getKey() == null) continue;
            props.setProperty(e.getKey(), e.getValue() == null ? "" : e.getValue());
        }
        persistToJson();
    }

    public static synchronized void reload() {
        props.clear();
        loadDefaults();
        loadJsonFile(false);
    }
}