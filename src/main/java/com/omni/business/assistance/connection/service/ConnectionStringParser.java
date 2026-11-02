package com.omni.business.assistance.connection.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JDBC 连接串解析器。支持：
 *   jdbc:mysql://host:port/db?params
 *   jdbc:postgresql://host:port/db?params
 *   jdbc:oracle:thin:@//host:port/service
 *   jdbc:oracle:thin:@host:port:SID
 */
public final class ConnectionStringParser {

    private ConnectionStringParser() {}

    public static Map<String, Object> parse(String url) {
        Map<String, Object> r = new LinkedHashMap<>();
        if (url == null || url.trim().isEmpty()) {
            r.put("valid", false);
            r.put("message", "连接串不能为空");
            return r;
        }
        String u = url.trim();
        try {
            if (u.startsWith("jdbc:mysql://")) {
                r.put("dbType", "mysql");
                parseHostPortDb(u.substring("jdbc:mysql://".length()), 3306, r);
            } else if (u.startsWith("jdbc:postgresql://")) {
                r.put("dbType", "postgresql");
                parseHostPortDb(u.substring("jdbc:postgresql://".length()), 5432, r);
            } else if (u.startsWith("jdbc:oracle:thin:@")) {
                r.put("dbType", "oracle");
                String rest = u.substring("jdbc:oracle:thin:@".length());
                if (rest.startsWith("//")) {
                    parseHostPortDb(rest.substring(2), 1521, r);
                } else {
                    // SID 格式：host:port:SID
                    int q = rest.indexOf('?');
                    if (q >= 0) rest = rest.substring(0, q);
                    String[] parts = rest.split(":");
                    if (parts.length >= 3) {
                        r.put("host", parts[0]);
                        r.put("port", parseIntSafe(parts[1], 1521));
                        r.put("databaseName", parts[2]);
                    } else {
                        throw new IllegalArgumentException("Oracle 连接串格式错误，期望 host:port:SID");
                    }
                }
            } else {
                r.put("valid", false);
                r.put("message", "无法识别的连接串，仅支持 jdbc:mysql:// / jdbc:oracle:thin:@ / jdbc:postgresql://");
                return r;
            }
            r.put("valid", true);
            r.put("url", u);
        } catch (Exception e) {
            r.put("valid", false);
            r.put("message", "解析失败: " + e.getMessage());
        }
        return r;
    }

    private static void parseHostPortDb(String rest, int defaultPort, Map<String, Object> r) {
        int q = rest.indexOf('?');
        if (q >= 0) rest = rest.substring(0, q);
        int slash = rest.indexOf('/');
        String hp = slash >= 0 ? rest.substring(0, slash) : rest;
        String db = slash >= 0 ? rest.substring(slash + 1) : "";
        int colon = hp.lastIndexOf(':');
        if (colon >= 0) {
            r.put("host", hp.substring(0, colon));
            r.put("port", parseIntSafe(hp.substring(colon + 1), defaultPort));
        } else {
            r.put("host", hp);
            r.put("port", defaultPort);
        }
        r.put("databaseName", db);
    }

    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
}