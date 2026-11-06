package com.omni.business.assistance.connection.service;

import com.omni.common.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JDBC 连接串解析器。
 * <p>★ L1-05：使用 StringUtils.requireNonBlank。</p>
 * <p>★ P1-11：新增 sqlserver 支持。</p>
 */
public final class ConnectionStringParser {

    private ConnectionStringParser() {}

    public static Map<String, Object> parse(String url) {
        Map<String, Object> r = new LinkedHashMap<>();
        if (StringUtils.isBlank(url)) {
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
            } else if (u.startsWith("jdbc:sqlserver://")) {
                r.put("dbType", "sqlserver");
                String rest = u.substring("jdbc:sqlserver://".length());
                int semi = rest.indexOf(';');
                String hp = semi >= 0 ? rest.substring(0, semi) : rest;
                String props = semi >= 0 ? rest.substring(semi + 1) : "";
                parseHostPort(hp, 1433, r);
                for (String kv : props.split(";")) {
                    int eq = kv.indexOf('=');
                    if (eq > 0) {
                        String k = kv.substring(0, eq).trim();
                        String v = kv.substring(eq + 1).trim();
                        if ("databaseName".equalsIgnoreCase(k)) {
                            r.put("databaseName", v);
                        } else if ("database".equalsIgnoreCase(k)) {
                            r.put("databaseName", v);
                        }
                    }
                }
                if (!r.containsKey("databaseName")) r.put("databaseName", "");
            } else {
                r.put("valid", false);
                r.put("message", "无法识别的连接串，仅支持 jdbc:mysql:// / jdbc:postgresql:// / jdbc:oracle:thin:@ / jdbc:sqlserver://");
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
        parseHostPort(hp, defaultPort, r);
        r.put("databaseName", db);
    }

    private static void parseHostPort(String hp, int defaultPort, Map<String, Object> r) {
        int colon = hp.lastIndexOf(':');
        if (colon >= 0) {
            r.put("host", hp.substring(0, colon));
            r.put("port", parseIntSafe(hp.substring(colon + 1), defaultPort));
        } else {
            r.put("host", hp);
            r.put("port", defaultPort);
        }
    }

    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
}