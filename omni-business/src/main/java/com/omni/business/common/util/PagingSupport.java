package com.omni.business.common.util;

import com.omni.framework.spring.mvc.RequestContextHolder;
import com.omni.framework.tomcat.servlet.HttpServletRequest;

import java.util.Map;

/**
 * 分页参数解析工具。
 *
 * <p>统一处理：page（默认 1）、pageSize（默认值由调用方指定，上限由调用方指定）。</p>
 */
public final class PagingSupport {

    private PagingSupport() {}

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int DEFAULT_MAX_PAGE_SIZE = 500;

    /** 从当前 HTTP 请求中解析分页参数 */
    public static Page fromRequest(int defaultPageSize, int maxPageSize) {
        HttpServletRequest req = RequestContextHolder.getRequest();
        if (req == null) return new Page(1, defaultPageSize);
        return build(req.getParameter("page"), req.getParameter("pageSize"),
                defaultPageSize, maxPageSize);
    }

    public static Page fromRequest() {
        return fromRequest(DEFAULT_PAGE_SIZE, DEFAULT_MAX_PAGE_SIZE);
    }

    /** 从 Map（如 QueryParams 解析结果）中解析分页参数 */
    public static Page fromMap(Map<String, String> params, int defaultPageSize, int maxPageSize) {
        if (params == null) return new Page(1, defaultPageSize);
        return build(params.get("page"), params.get("pageSize"),
                defaultPageSize, maxPageSize);
    }

    public static Page fromMap(Map<String, String> params) {
        return fromMap(params, DEFAULT_PAGE_SIZE, DEFAULT_MAX_PAGE_SIZE);
    }

    private static Page build(String pageStr, String sizeStr, int defaultPageSize, int maxPageSize) {
        int page = parseInt(pageStr, 1);
        int size = parseInt(sizeStr, defaultPageSize);
        if (page < 1) page = 1;
        if (size < 1) size = defaultPageSize;
        if (maxPageSize > 0 && size > maxPageSize) size = maxPageSize;
        return new Page(page, size);
    }

    private static int parseInt(String s, int def) {
        if (s == null || s.trim().isEmpty()) return def;
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    /** 分页参数值对象 */
    public static class Page {
        private final int page;
        private final int pageSize;

        public Page(int page, int pageSize) {
            this.page = page;
            this.pageSize = pageSize;
        }

        public int getPage() { return page; }
        public int getPageSize() { return pageSize; }

        /** SQL OFFSET */
        public int getOffset() { return (page - 1) * pageSize; }
    }
}