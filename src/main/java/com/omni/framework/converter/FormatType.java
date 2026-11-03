package com.omni.framework.converter;

/**
 * 格式转换支持的类型枚举。
 * <p>新增格式只需在此枚举中添加一项，不修改其他任何代码。</p>
 */
public enum FormatType {
    JSON("json", "application/json"),
    XML("xml", "application/xml"),
    CSV("csv", "text/csv"),
    YAML("yaml", "application/x-yaml"),
    PROPERTIES("properties", "text/plain"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    PDF("pdf", "application/pdf"),
    HTML("html", "text/html"),
    MARKDOWN("md", "text/markdown"),
    TXT("txt", "text/plain"),
    PNG("png", "image/png"),
    JPG("jpg", "image/jpeg"),
    BMP("bmp", "image/bmp"),
    GIF("gif", "image/gif");

    public final String ext;
    public final String mime;

    FormatType(String ext, String mime) {
        this.ext = ext;
        this.mime = mime;
    }

    public static FormatType ofExt(String ext) {
        if (ext == null) return null;
        String e = ext.toLowerCase().replaceFirst("^\\.", "");
        for (FormatType f : values()) {
            if (f.ext.equals(e)) return f;
        }
        return null;
    }
}