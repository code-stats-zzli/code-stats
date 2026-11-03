package com.omni.framework.converter;

import java.io.UnsupportedEncodingException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 转换请求 DTO。
 */
public class ConvertRequest {
    private String requestId;
    private FormatType sourceType;
    private FormatType targetType;
    private byte[] sourceBytes;
    private String sourceText;
    private String sourceFileName;
    private Map<String, Object> options = new LinkedHashMap<>();

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public FormatType getSourceType() { return sourceType; }
    public void setSourceType(FormatType sourceType) { this.sourceType = sourceType; }

    public FormatType getTargetType() { return targetType; }
    public void setTargetType(FormatType targetType) { this.targetType = targetType; }

    public byte[] getSourceBytes() { return sourceBytes; }
    public void setSourceBytes(byte[] sourceBytes) { this.sourceBytes = sourceBytes; }

    public String getSourceText() { return sourceText; }
    public void setSourceText(String sourceText) { this.sourceText = sourceText; }

    public String getSourceFileName() { return sourceFileName; }
    public void setSourceFileName(String sourceFileName) { this.sourceFileName = sourceFileName; }

    public Map<String, Object> getOptions() { return options; }
    public void setOptions(Map<String, Object> options) {
        this.options = options != null ? options : new LinkedHashMap<>();
    }

    /** 统一读取源内容为 UTF-8 文本 */
    public String readSourceText() {
        if (sourceText != null) return sourceText;
        if (sourceBytes == null) return "";
        try {
            return new String(sourceBytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return new String(sourceBytes);
        }
    }

    /** 文件基础名（不含扩展名） */
    public String baseName() {
        String n = sourceFileName;
        if (n == null || n.isEmpty()) return "converted";
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }
}