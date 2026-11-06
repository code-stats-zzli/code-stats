package com.omni.business.common.util;

import com.omni.framework.tomcat.servlet.HttpServletResponse;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 下载 / 错误响应统一封装。
 * <p>消掉 13 处 Controller 里手拼 Content-Disposition、URLEncoder、sendStatic 的重复代码。</p>
 */
public final class ResponseHelper {

    private ResponseHelper() {}

    // ==================== 下载 ====================

    public static void writeAttachment(HttpServletResponse resp, Path file, String displayName) {
        writeAttachment(resp, file, displayName, "application/octet-stream");
    }

    public static void writeAttachment(HttpServletResponse resp, Path file, String displayName, String mime) {
        if (resp == null) return;
        try {
            if (file == null || !Files.isRegularFile(file)) {
                writeError(resp, 404, "文件不存在");
                return;
            }
            String encoded = encode(displayName);
            resp.setHeader("Content-Disposition",
                    "attachment; filename=\"" + safeAsciiName(displayName) + "\"; filename*=UTF-8''" + encoded);
            resp.setContentType(mime != null ? mime : "application/octet-stream");
            try (InputStream in = Files.newInputStream(file)) {
                resp.sendStatic(in, resp.getContentType(), Files.size(file));
            }
        } catch (Exception e) {
            try { writeError(resp, 500, "下载失败: " + e.getMessage()); } catch (Exception ignored) {}
        }
    }

    public static void writeAttachment(HttpServletResponse resp, byte[] data, String displayName, String mime) {
        if (resp == null) return;
        try {
            String encoded = encode(displayName);
            resp.setHeader("Content-Disposition",
                    "attachment; filename=\"" + safeAsciiName(displayName) + "\"; filename*=UTF-8''" + encoded);
            resp.setContentType(mime != null ? mime : "application/octet-stream");
            resp.sendStatic(new java.io.ByteArrayInputStream(data == null ? new byte[0] : data),
                    resp.getContentType(), data == null ? 0 : data.length);
        } catch (Exception e) {
            try { writeError(resp, 500, "下载失败: " + e.getMessage()); } catch (Exception ignored) {}
        }
    }

    public static void writeTextAttachment(HttpServletResponse resp, Path file, String displayName) {
        writeAttachment(resp, file, displayName, "text/plain; charset=utf-8");
    }

    public static void writeZipAttachment(HttpServletResponse resp, Path zip, String displayName) {
        writeAttachment(resp, zip, displayName, "application/zip");
    }

    // ==================== 错误 ====================

    public static void writeError(HttpServletResponse resp, int status, String msg) {
        if (resp == null) return;
        try {
            if (resp.isCommitted()) return;
            resp.setStatus(status, statusText(status));
            resp.setContentType("application/json; charset=utf-8");
            resp.write(BizResults.error(msg));
            resp.send();
        } catch (Exception ignored) {}
    }

    // ==================== 内部 ====================

    private static String encode(String name) {
        if (name == null || name.isEmpty()) return "";
        try {
            return URLEncoder.encode(name, StandardCharsets.UTF_8.name()).replaceAll("\\+", "%20");
        } catch (Exception e) {
            return "";
        }
    }

    /** 兼容老客户端的 ASCII fallback（非 ASCII 字符替换为 _） */
    private static String safeAsciiName(String name) {
        if (name == null || name.isEmpty()) return "download";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            sb.append(c < 128 && c != '"' && c != '\\' ? c : '_');
        }
        return sb.toString();
    }

    private static String statusText(int code) {
        switch (code) {
            case 400: return "Bad Request";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 500: return "Internal Server Error";
            default:  return "Status " + code;
        }
    }
}