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
public class MergeFileFormatParser implements ImportFormatParser {

    /** 匹配 "// ======== path ========" 行（行首） */
    private static final Pattern MERGE_HEADER = Pattern.compile(
            "^// ======== (.+?) ========\\s*$",
            Pattern.MULTILINE);

    @Override public String getName() { return "merge-file"; }
    @Override public String getDisplayName() { return "合并文件格式 (// ======== path ========)"; }
    @Override public int getPriority() { return 30; }

    @Override
    public boolean supports(String text) {
        return text != null && MERGE_HEADER.matcher(text).find();
    }

    @Override
    public ParseResult parse(String text) {
        ParseResult r = new ParseResult();
        r.setFormat(getName());
        r.setFormatDisplayName(getDisplayName());

        List<Header> headers = new ArrayList<>();
        Matcher m = MERGE_HEADER.matcher(text);
        while (m.find()) {
            headers.add(new Header(m.start(), m.end(), m.group(1).trim()));
        }
        if (headers.isEmpty()) return r;

        for (int i = 0; i < headers.size(); i++) {
            Header cur = headers.get(i);
            int contentStart = skipOneNewline(text, cur.end);
            int contentEnd = (i + 1 < headers.size())
                    ? headers.get(i + 1).start
                    : text.length();

            // MergeFilesStage 生成时，每段末尾补一个 \n，下一段 header 前还有一个 \n
            // 稳妥做法：去首尾所有换行（用户主要关心代码内容）
            String content = trimNewlines(text.substring(contentStart, contentEnd));

            String norm = PathNormalizer.normalize(cur.path);
            ImportFile f = new ImportFile();
            f.setOrigin("merge 块 #" + (i + 1));
            if (norm == null) {
                f.setPath(cur.path);
                f.setError("非法路径");
                r.getFiles().add(f);
                continue;
            }
            f.setPath(norm);
            f.setContent(content);
            f.setSize(content.getBytes(StandardCharsets.UTF_8).length);
            f.setLineCount(FileBlockFormatParser.countLines(content));
            f.setLanguage(FileBlockFormatParser.guessLanguage(norm));
            r.getFiles().add(f);
        }
        return r;
    }

    private static int skipOneNewline(String text, int pos) {
        if (pos < text.length() && text.charAt(pos) == '\r') pos++;
        if (pos < text.length() && text.charAt(pos) == '\n') pos++;
        return pos;
    }

    private static String trimNewlines(String s) {
        int start = 0, end = s.length();
        while (start < end && (s.charAt(start) == '\n' || s.charAt(start) == '\r')) start++;
        while (end > start && (s.charAt(end - 1) == '\n' || s.charAt(end - 1) == '\r')) end--;
        return s.substring(start, end);
    }

    private static class Header {
        final int start, end;
        final String path;
        Header(int s, int e, String p) { start = s; end = e; path = p; }
    }
}