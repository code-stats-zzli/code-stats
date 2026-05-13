package com.codestats.lib.codestats.language;

public class LanguageSpec {
    private final Language language;
    private final String lineComment;     // 单行注释起始符，如 //
    private final String blockStart;      // 块注释起始符，如 /*
    private final String blockEnd;        // 块注释结束符，如 */
    private final String[] extensions;    // 关联的扩展名

    public LanguageSpec(Language language, String lineComment, String blockStart, String blockEnd, String[] extensions) {
        this.language = language;
        this.lineComment = lineComment;
        this.blockStart = blockStart;
        this.blockEnd = blockEnd;
        this.extensions = extensions;
    }

    public Language getLanguage() { return language; }
    public String getLineComment() { return lineComment; }
    public String getBlockStart() { return blockStart; }
    public String getBlockEnd() { return blockEnd; }
    public String[] getExtensions() { return extensions; }

    // 工厂方法：内置常见语言
    public static LanguageSpec forLanguage(Language lang) {
        switch (lang) {
            case JAVA: return new LanguageSpec(lang, "//", "/*", "*/", new String[]{"java"});
            case KOTLIN: return new LanguageSpec(lang, "//", "/*", "*/", new String[]{"kt", "kts"});
            case PYTHON: return new LanguageSpec(lang, "#", null, null, new String[]{"py"});
            case JAVASCRIPT: return new LanguageSpec(lang, "//", "/*", "*/", new String[]{"js", "mjs", "cjs"});
            case TYPESCRIPT: return new LanguageSpec(lang, "//", "/*", "*/", new String[]{"ts", "tsx"});
            case GO: return new LanguageSpec(lang, "//", "/*", "*/", new String[]{"go"});
            case RUST: return new LanguageSpec(lang, "//", "/*", "*/", new String[]{"rs"});
            case C: return new LanguageSpec(lang, "//", "/*", "*/", new String[]{"c", "h"});
            case CPP: return new LanguageSpec(lang, "//", "/*", "*/", new String[]{"cpp", "hpp", "cc", "cxx"});
            case CSHARP: return new LanguageSpec(lang, "//", "/*", "*/", new String[]{"cs"});
            case SWIFT: return new LanguageSpec(lang, "//", "/*", "*/", new String[]{"swift"});
            case PHP: return new LanguageSpec(lang, "//", "/*", "*/", new String[]{"php"});
            case RUBY: return new LanguageSpec(lang, "#", "=begin", "=end", new String[]{"rb"});
            case SHELL: return new LanguageSpec(lang, "#", null, null, new String[]{"sh", "bash", "zsh"});
            case SQL: return new LanguageSpec(lang, "--", "/*", "*/", new String[]{"sql"});
            case HTML: return new LanguageSpec(lang, null, "<!--", "-->", new String[]{"html", "htm"});
            case XML: return new LanguageSpec(lang, null, "<!--", "-->", new String[]{"xml"});
            case PROPERTIES: return new LanguageSpec(lang, "#", null, null, new String[]{"properties"});
            case DOCKERFILE: return new LanguageSpec(lang, "#", null, null, new String[]{"dockerfile", "Dockerfile"});
            case MAKEFILE: return new LanguageSpec(lang, "#", null, null, new String[]{"mk", "Makefile"});
            default: return new LanguageSpec(lang, null, null, null, new String[0]);
        }
    }
}