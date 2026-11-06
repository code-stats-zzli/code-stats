package com.omni.business.common.util;

import com.omni.framework.util.JsonUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务响应结果封装。
 *
 * <p>统一 JSON 结构：</p>
 * <pre>
 *   ok()              → {"success": true}
 *   ok(data)          → {"success": true, "data": ...}
 *   okOne(k, v)       → {"success": true, "<k>": v}
 *   okFlat(map)       → {"success": true, ...map 平铺}
 *   okId(id)          → {"success": true, "id": id}
 *   error(msg)        → {"success": false, "error": msg}
 * </pre>
 */
public final class BizResults {

    private BizResults() {}

    public static String ok() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        return JsonUtil.toJson(m);
    }

    public static String ok(Object data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        m.put("data", data);
        return JsonUtil.toJson(m);
    }

    public static String okOne(String key, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        if (key != null) m.put(key, value);
        return JsonUtil.toJson(m);
    }

    public static String okFlat(Map<String, Object> fields) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        if (fields != null) m.putAll(fields);
        return JsonUtil.toJson(m);
    }

    public static String okId(String id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        m.put("id", id);
        return JsonUtil.toJson(m);
    }

    public static String error(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("error", msg);
        return JsonUtil.toJson(m);
    }

    public static String error(String msg, String errorType) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("error", msg);
        m.put("errorType", errorType);
        return JsonUtil.toJson(m);
    }
}