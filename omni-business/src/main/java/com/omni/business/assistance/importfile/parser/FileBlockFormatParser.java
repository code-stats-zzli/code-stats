package com.omni.business.assistance.importfile.parser;

import com.omni.business.assistance.importfile.model.ImportFile;
import com.omni.business.assistance.importfile.model.ParseResult;
import com.omni.framework.spring.annotation.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FileBlockFormatParser implements ImportFormatParser {

    private static final Pattern FILE_BLOCK = Pattern.compile(
            "```file:([^\\r\\n]+?)\\s*\\r?\\n([\\s\\S]*?)\\r?\\n```",
            Pattern.MULTILINE);

    @Override public String getName() { return "file-block"; }
    @Override public String getDisplayName() { return "File Block 格式 (```file:path)"; }
    @Override public int getPriority() { return 50; }

    @Override
    public boolean supports(String text) {
        return text != null && FILE_BLOCK.matcher(text).find();
    }

    @Override
    public ParseResult parse(String text) {
        ParseResult r = new ParseResult();
        r.setFormat(getName());
        r.setFormatDisplayName(getDisplayName());
        r.setFiles(extractFileBlocks(text));
        return r;
    }

    /** 供 OmniPackFormatParser 复用 */
    public List<ImportFile> extractFileBlocks(String text) {
        List<ImportFile> list = new ArrayList<>();
        if (text == null) return list;
        Matcher m = FILE_BLOCK.matcher(text);
        int idx = 0;
        while (m.find()) {
            idx++;
            String raw = m.group(1).trim();
            String content = m.group(2);
            String norm = PathNormalizer.normalize(raw);
            ImportFile f = new ImportFile();
            f.setOrigin("file 块 #" + idx);
            if (norm == null) {
                f.setPath(raw);
                f.setError("非法路径（含 .. / 盘符 / 空）");
                list.add(f);
                continue;
            }
            f.setPath(norm);
            f.setContent(content);
            f.setSize(content.getBytes(StandardCharsets.UTF_8).length);
            f.setLineCount(countLines(content));
            f.setLanguage(guessLanguage(norm));
            list.add(f);
        }
        return list;
    }

    static int countLines(String s) {
        if (s == null || s.isEmpty()) return 0;
        int n = 1;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') n++;
        return n;
    }

    static String guessLanguage(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0) return "text";
        String ext = path.substring(dot + 1).toLowerCase();
        switch (ext) {
            case "java": return "java";
            case "js":   return "javascript";
            case "ts":   return "typescript";
            case "html": case "htm": return "html";
            case "css":  return "css";
            case "xml":  return "xml";
            case "json": return "json";
            case "yml": case "yaml": return "yaml";
            case "md":   return "markdown";
            case "sql":  return "sql";
            case "py":   return "python";
            case "sh":   return "shell";
            case "properties": return "properties";
            default: return "text";
        }
    }
}