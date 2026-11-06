package com.omni.framework.codestats.config;

import org.yaml.snakeyaml.Yaml;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CodeStats 专用配置加载器。
 *
 * <p>原有职责：加载 {@code config.yaml}（线程、扩展名、排序、GitIgnore 等代码分析配置），
 * 通过 {@link #load(String[])} 返回 {@link Config} 对象，通过 {@link #getModuleConfig(String)}
 * 按模块读取嵌套配置。</p>
 *
 * <p><b>本次新增：</b></p>
 * <ul>
 *   <li>{@link #getProperty(String)} / {@link #getProperty(String, String)}：
 *       直接转发到 {@code com.omni.framework.spring.config.ConfigLoader}（全局 spring 配置，
 *       即 {@code application.properties} + {@code system-params.json}）。</li>
 *   <li>目的：让 codestats 子模块（如 {@code CodeAnalysisService}）不用为了读全局配置
 *       而再 import 一个同名类，避免 {@code com.omni.framework.spring.config.ConfigLoader}
 *       与 {@code com.omni.framework.codestats.config.ConfigLoader} 混淆。</li>
 *   <li>两者职责不冲突：本类依然是 yaml 加载器；转发方法仅用于读取全局 properties 项，
 *       如 {@code code.analysis.wrapper-dirs} 这类代码分析相关配置。</li>
 * </ul>
 */
public class ConfigLoader {
    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);

    private static Map<String, Object> yamlRootMap;

    static {
        try {
            yamlRootMap = new HashMap<>();
            loadYamlFromClasspathOrFile("config.yaml", null);
            log.info("成功加载配置文件 config.yaml");
        } catch (Exception e) {
            log.error("加载 config.yaml 失败，使用空配置", e);
            yamlRootMap = new HashMap<>();
        }
    }

    // ==================== 新增：转发到 spring 全局配置 ====================

    /**
     * 读取全局配置项（application.properties + system-params.json）。
     * 直接转发到 {@code com.omni.framework.spring.config.ConfigLoader}。
     *
     * @return 配置值，不存在返回 {@code null}
     */
    public static String getProperty(String key) {
        return com.omni.framework.spring.config.ConfigLoader.getProperty(key);
    }

    /**
     * 读取全局配置项，不存在时返回默认值。
     *
     * @param key          配置键
     * @param defaultValue 默认值（可为 null）
     * @return 配置值
     */
    public static String getProperty(String key, String defaultValue) {
        return com.omni.framework.spring.config.ConfigLoader.getProperty(key, defaultValue);
    }

    // ==================== 原有：yaml 加载 / 解析 ====================

    public static Config load(String[] args) {
        Config.Builder builder = new Config.Builder();
        if (yamlRootMap == null || yamlRootMap.isEmpty()) {
            loadYamlFromClasspathOrFile("config.yaml", builder);
        } else {
            applyYamlToBuilder(builder);
        }
        loadEnv(builder);
        parseArgs(args, builder);
        if (builder.getRootDir() == null) {
            if (builder.getDefaultInputPath() != null) {
                builder.rootDir(builder.getDefaultInputPath());
            } else {
                builder.rootDir(Paths.get("."));
            }
        }
        return builder.build();
    }

    private static void loadYamlFromClasspathOrFile(String resourceName, Config.Builder builder) {
        try (InputStream is = ConfigLoader.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (is != null) {
                parseYaml(is, builder);
                return;
            }
        } catch (IOException ignored) {}
        Path filePath = Paths.get(resourceName);
        if (Files.exists(filePath)) {
            try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
                parseYaml(fis, builder);
            } catch (IOException ignored) {}
        }
    }

    @SuppressWarnings("unchecked")
    private static void parseYaml(InputStream inputStream, Config.Builder builder) {
        Yaml yaml = new Yaml();
        Map<String, Object> map = yaml.load(inputStream);
        if (map == null) return;

        if (yamlRootMap == null) {
            yamlRootMap = new HashMap<>(map);
        } else {
            yamlRootMap.putAll(map);
        }

        if (builder != null) {
            applyYamlToBuilder(builder, map);
        }
    }

    private static void applyYamlToBuilder(Config.Builder builder) {
        applyYamlToBuilder(builder, yamlRootMap);
    }

    @SuppressWarnings("unchecked")
    private static void applyYamlToBuilder(Config.Builder builder, Map<String, Object> map) {
        if (map.containsKey("rootDir")) {
            String dir = normalizePath(map.get("rootDir").toString());
            builder.rootDir(Paths.get(dir));
        }
        if (map.containsKey("extensions")) builder.extensions((List<String>) map.get("extensions"));
        if (map.containsKey("excludePaths")) builder.excludePaths((List<String>) map.get("excludePaths"));
        if (map.containsKey("excludeComments")) builder.excludeComments((Boolean) map.get("excludeComments"));
        if (map.containsKey("threadCount")) builder.threadCount((Integer) map.get("threadCount"));
        if (map.containsKey("sortBy")) builder.sortBy((String) map.get("sortBy"));
        if (map.containsKey("outputFormat")) builder.outputFormat((String) map.get("outputFormat"));
        if (map.containsKey("outputFile")) {
            String f = normalizePath(map.get("outputFile").toString());
            builder.outputFile(Paths.get(f));
        }
        if (map.containsKey("useGitIgnore")) builder.useGitIgnore((Boolean) map.get("useGitIgnore"));
        if (map.containsKey("defaultInputPath")) {
            String d = normalizePath(map.get("defaultInputPath").toString());
            builder.defaultInputPath(Paths.get(d));
        }
        if (map.containsKey("defaultOutputPath")) {
            String d = normalizePath(map.get("defaultOutputPath").toString());
            builder.defaultOutputPath(Paths.get(d));
        }
        if (map.containsKey("extraPaths")) {
            builder.extraPaths((List<String>) map.get("extraPaths"));
        }
    }

    // 路径规范化：将反斜杠统一转为正斜杠，兼容 Windows 写法
    private static String normalizePath(String path) {
        if (path == null) return null;
        return path.replace('\\', '/');
    }

    public static Map<String, Object> getModuleConfig(String module) {
        if (yamlRootMap == null) {
            return new HashMap<>();
        }
        Object moduleObj = yamlRootMap.get(module);
        if (moduleObj instanceof Map) {
            return (Map<String, Object>) moduleObj;
        }
        return new HashMap<>();
    }

    private static void loadEnv(Config.Builder builder) {
        String root = System.getenv("CODE_STATS_ROOT_DIR");
        if (root != null) builder.rootDir(Paths.get(normalizePath(root)));
        String exts = System.getenv("CODE_STATS_EXTENSIONS");
        if (exts != null) builder.extensions(Arrays.asList(exts.split(",")));
        String threads = System.getenv("CODE_STATS_THREADS");
        if (threads != null) builder.threadCount(Integer.parseInt(threads));
        String sort = System.getenv("CODE_STATS_SORT_BY");
        if (sort != null) builder.sortBy(sort);
        String defaultInput = System.getenv("CODE_STATS_DEFAULT_INPUT");
        if (defaultInput != null) builder.defaultInputPath(Paths.get(normalizePath(defaultInput)));
        String defaultOutput = System.getenv("CODE_STATS_DEFAULT_OUTPUT");
        if (defaultOutput != null) builder.defaultOutputPath(Paths.get(normalizePath(defaultOutput)));
        String extra = System.getenv("CODE_STATS_EXTRA_PATHS");
        if (extra != null) builder.extraPaths(Arrays.asList(extra.split(",")));
    }

    private static void parseArgs(String[] args, Config.Builder builder) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-d": builder.rootDir(Paths.get(normalizePath(args[++i]))); break;
                case "-e": builder.extensions(Arrays.asList(args[++i].split(","))); break;
                case "-x": builder.excludePaths(Arrays.asList(args[++i].split(","))); break;
                case "-c": builder.excludeComments(Boolean.parseBoolean(args[++i])); break;
                case "-t": builder.threadCount(Integer.parseInt(args[++i])); break;
                case "-s": builder.sortBy(args[++i]); break;
                case "-o": builder.outputFormat(args[++i]); break;
                case "-f": builder.outputFile(Paths.get(normalizePath(args[++i]))); break;
                case "--gui": break;
                case "--no-gitignore": builder.useGitIgnore(false); break;
                case "--default-input": builder.defaultInputPath(Paths.get(normalizePath(args[++i]))); break;
                case "--default-output": builder.defaultOutputPath(Paths.get(normalizePath(args[++i]))); break;
                case "--extra-paths": builder.extraPaths(Arrays.asList(args[++i].split(","))); break;
                default: log.warn("Unknown option: {}", args[i]); break;
            }
        }
    }
}