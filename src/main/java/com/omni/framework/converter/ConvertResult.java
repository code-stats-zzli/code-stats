package com.omni.framework.converter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 转换结果 DTO。
 * <p>文本类输出走 text 字段；二进制类输出走 data 字段。</p>
 */
public class ConvertResult {
    private boolean success;
    private FormatType targetType;
    private byte[] data;
    private String text;
    private String suggestedFileName;
    private String message;
    private Map<String, Object> meta = new LinkedHashMap<>();

    public static ConvertResult okText(FormatType type, String text, String fileName) {
        ConvertResult r = new ConvertResult();
        r.success = true;
        r.targetType = type;
        r.text = text;
        r.suggestedFileName = fileName;
        return r;
    }

    public static ConvertResult okBytes(FormatType type, byte[] data, String fileName) {
        ConvertResult r = new ConvertResult();
        r.success = true;
        r.targetType = type;
        r.data = data;
        r.suggestedFileName = fileName;
        return r;
    }

    public static ConvertResult error(String msg) {
        ConvertResult r = new ConvertResult();
        r.success = false;
        r.message = msg;
        return r;
    }

    public boolean isSuccess() { return success; }
    public FormatType getTargetType() { return targetType; }
    public byte[] getData() { return data; }
    public String getText() { return text; }
    public String getSuggestedFileName() { return suggestedFileName; }
    public String getMessage() { return message; }
    public Map<String, Object> getMeta() { return meta; }
    public void setMeta(Map<String, Object> meta) {
        this.meta = meta != null ? meta : new LinkedHashMap<>();
    }

    /** 结果字节大小（仅用于响应展示） */
    public long size() {
        if (data != null) return data.length;
        if (text != null) return text.getBytes().length;
        return 0;
    }
}