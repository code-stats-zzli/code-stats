package com.codestats.framework.util;

import com.codestats.framework.spring.mvc.RequestContextHolder;
import com.codestats.framework.tomcat.connector.Request;

import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 请求参数解析工具
 */
public class ParamUtils {

    /**
     * 从当前线程绑定的 Request 中解析查询参数
     * @return 参数键值对（键已做 URL 解码）
     */
    public static Map<String, String> parseQueryParams() {
        Request request = RequestContextHolder.getRequest();
        Map<String, String> map = new HashMap<>();
        if (request == null) {
            return map;
        }
        String query = request.getQueryString();
        if (query == null || query.isEmpty()) {
            return map;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                    String value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                    map.put(key, value);
                } catch (Exception ignored) {
                    // 忽略解码异常
                }
            }
        }
        return map;
    }
}