package com.codestats.lib.codestats.config;

import org.yaml.snakeyaml.Yaml;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ConfigLoader {
    public static Config load(String[] args) {
        Config.Builder builder = new Config.Builder();
        loadYamlFromClasspathOrFile("config.yaml", builder);
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

    private static void parseYaml(InputStream inputStream, Config.Builder builder) {
        Yaml yaml = new Yaml();
        Map<String, Object> map = yaml.load(inputStream);
        if (map.containsKey("rootDir")) {
            String dir = map.get("rootDir").toString().replace("\\", "/");
            builder.rootDir(Paths.get(dir));
        }
        if (map.containsKey("extensions")) builder.extensions((List<String>) map.get("extensions"));
        if (map.containsKey("excludePaths")) builder.excludePaths((List<String>) map.get("excludePaths"));
        if (map.containsKey("excludeComments")) builder.excludeComments((Boolean) map.get("excludeComments"));
        if (map.containsKey("threadCount")) builder.threadCount((Integer) map.get("threadCount"));
        if (map.containsKey("sortBy")) builder.sortBy((String) map.get("sortBy"));
        if (map.containsKey("outputFormat")) builder.outputFormat((String) map.get("outputFormat"));
        if (map.containsKey("outputFile")) {
            String f = map.get("outputFile").toString().replace("\\", "/");
            builder.outputFile(Paths.get(f));
        }
        if (map.containsKey("useGitIgnore")) builder.useGitIgnore((Boolean) map.get("useGitIgnore"));
        if (map.containsKey("defaultInputPath")) {
            String d = map.get("defaultInputPath").toString().replace("\\", "/");
            builder.defaultInputPath(Paths.get(d));
        }
        if (map.containsKey("defaultOutputPath")) {
            String d = map.get("defaultOutputPath").toString().replace("\\", "/");
            builder.defaultOutputPath(Paths.get(d));
        }
        // ★ 新增：加载 extraPaths
        if (map.containsKey("extraPaths")) {
            builder.extraPaths((List<String>) map.get("extraPaths"));
        }
    }

    private static void loadEnv(Config.Builder builder) {
        String root = System.getenv("CODE_STATS_ROOT_DIR");
        if (root != null) builder.rootDir(Paths.get(root));
        String exts = System.getenv("CODE_STATS_EXTENSIONS");
        if (exts != null) builder.extensions(Arrays.asList(exts.split(",")));
        String threads = System.getenv("CODE_STATS_THREADS");
        if (threads != null) builder.threadCount(Integer.parseInt(threads));
        String sort = System.getenv("CODE_STATS_SORT_BY");
        if (sort != null) builder.sortBy(sort);
        String defaultInput = System.getenv("CODE_STATS_DEFAULT_INPUT");
        if (defaultInput != null) builder.defaultInputPath(Paths.get(defaultInput));
        String defaultOutput = System.getenv("CODE_STATS_DEFAULT_OUTPUT");
        if (defaultOutput != null) builder.defaultOutputPath(Paths.get(defaultOutput));
        // ★ 新增：加载额外路径环境变量
        String extra = System.getenv("CODE_STATS_EXTRA_PATHS");
        if (extra != null) builder.extraPaths(Arrays.asList(extra.split(",")));
    }

    private static void parseArgs(String[] args, Config.Builder builder) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-d": builder.rootDir(Paths.get(args[++i].replace("\\", "/"))); break;
                case "-e": builder.extensions(Arrays.asList(args[++i].split(","))); break;
                case "-x": builder.excludePaths(Arrays.asList(args[++i].split(","))); break;
                case "-c": builder.excludeComments(Boolean.parseBoolean(args[++i])); break;
                case "-t": builder.threadCount(Integer.parseInt(args[++i])); break;
                case "-s": builder.sortBy(args[++i]); break;
                case "-o": builder.outputFormat(args[++i]); break;
                case "-f": builder.outputFile(Paths.get(args[++i].replace("\\", "/"))); break;
                case "--gui": break;
                case "--no-gitignore": builder.useGitIgnore(false); break;
                case "--default-input": builder.defaultInputPath(Paths.get(args[++i].replace("\\", "/"))); break;
                case "--default-output": builder.defaultOutputPath(Paths.get(args[++i].replace("\\", "/"))); break;
                // ★ 新增命令行参数
                case "--extra-paths": builder.extraPaths(Arrays.asList(args[++i].split(","))); break;
                default: System.err.println("Unknown option: " + args[i]); break;
            }
        }
    }
}