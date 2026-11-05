package com.omni.framework.converter.builtin;

import com.omni.framework.converter.ConvertException;
import com.omni.framework.converter.ConvertRequest;
import com.omni.framework.converter.ConvertResult;
import com.omni.framework.converter.Converter;
import com.omni.framework.converter.FormatType;
import com.omni.framework.spring.annotation.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * 纯文本 / HTML / Markdown 互转，作为兜底转换器。
 */
@Component
public class TextConverter implements Converter {

    @Override public String getName() { return "text"; }

    @Override public Set<FormatType> supportedSources() {
        return EnumSet.of(FormatType.TXT, FormatType.HTML, FormatType.MARKDOWN);
    }

    @Override public Set<FormatType> supportedTargets() {
        return EnumSet.of(FormatType.TXT, FormatType.HTML);
    }

    @Override public int getPriority() { return 50; }

    @Override
    public ConvertResult convert(ConvertRequest req) throws ConvertException {
        try {
            String text = req.readSourceText();
            String base = req.baseName();
            FormatType src = req.getSourceType();
            FormatType tgt = req.getTargetType();

            if (tgt == FormatType.TXT) {
                if (src == FormatType.HTML) text = stripHtml(text);
                else if (src == FormatType.MARKDOWN) text = mdToPlain(text);
                return ConvertResult.okText(FormatType.TXT, text, base + ".txt");
            }
            if (tgt == FormatType.HTML) {
                if (src == FormatType.MARKDOWN) text = mdToHtml(text);
                else text = "<pre>" + esc(text) + "</pre>";
                return ConvertResult.okText(FormatType.HTML,
                        "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body>"
                                + text + "</body></html>", base + ".html");
            }
            throw new ConvertException("Text 不支持输出到 " + tgt);
        } catch (ConvertException e) {
            throw e;
        } catch (Exception e) {
            throw new ConvertException("Text 转换失败: " + e.getMessage(), e);
        }
    }

    private String stripHtml(String html) {
        return html.replaceAll("(?is)<script.*?</script>", "")
                .replaceAll("(?is)<style.*?</style>", "")
                .replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ").replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<").replaceAll("&gt;", ">")
                .replaceAll("\n{3,}", "\n\n").trim();
    }

    private String mdToPlain(String md) {
        return md.replaceAll("(?m)^#+\\s*", "")
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("\\*(.+?)\\*", "$1")
                .replaceAll("`([^`]+)`", "$1")
                .replaceAll("```[\\s\\S]*?```", "").trim();
    }

    private String mdToHtml(String md) {
        StringBuilder sb = new StringBuilder();
        String[] lines = md.split("\r?\n");
        for (String line : lines) {
            if (line.startsWith("### ")) sb.append("<h3>").append(esc(line.substring(4))).append("</h3>\n");
            else if (line.startsWith("## ")) sb.append("<h2>").append(esc(line.substring(3))).append("</h2>\n");
            else if (line.startsWith("# "))  sb.append("<h1>").append(esc(line.substring(2))).append("</h1>\n");
            else if (line.startsWith("- "))  sb.append("<li>").append(esc(line.substring(2))).append("</li>\n");
            else                             sb.append("<p>").append(esc(line)).append("</p>\n");
        }
        return sb.toString();
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}