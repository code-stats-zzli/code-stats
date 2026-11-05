package com.omni.framework.codestats.language;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class LanguageDetector {
    private static final Map<String, Language> EXTENSION_MAP = new HashMap<>();
    static {
        for (Language lang : Language.values()) {
            LanguageSpec spec = LanguageSpec.forLanguage(lang);
            for (String ext : spec.getExtensions()) {
                EXTENSION_MAP.put(ext.toLowerCase(), lang);
            }
        }
        // 手动补充一些特殊映射
        EXTENSION_MAP.put("kt", Language.KOTLIN);
        EXTENSION_MAP.put("kts", Language.KOTLIN);
        EXTENSION_MAP.put("py", Language.PYTHON);
        EXTENSION_MAP.put("js", Language.JAVASCRIPT);
        EXTENSION_MAP.put("ts", Language.TYPESCRIPT);
        EXTENSION_MAP.put("go", Language.GO);
        EXTENSION_MAP.put("rs", Language.RUST);
    }

    public Language detect(Path file) {
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            String ext = fileName.substring(dot + 1).toLowerCase();
            Language lang = EXTENSION_MAP.get(ext);
            if (lang != null) return lang;
        }
        // 检测 shebang
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String firstLine = reader.readLine();
            if (firstLine != null && firstLine.startsWith("#!")) {
                if (firstLine.contains("python")) return Language.PYTHON;
                if (firstLine.contains("node")) return Language.JAVASCRIPT;
                if (firstLine.contains("ruby")) return Language.RUBY;
                if (firstLine.contains("perl")) return Language.PERL;
                if (firstLine.contains("bash") || firstLine.contains("sh")) return Language.SHELL;
            }
        } catch (Exception ignored) { }
        return Language.UNKNOWN;
    }
}