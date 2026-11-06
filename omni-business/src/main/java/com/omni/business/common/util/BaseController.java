package com.omni.business.common.util;

/**
 * Controller 基类，统一封装 JSON 响应。
 * <p>所有方法均返回 JSON 字符串，可直接用作 {@code @ResponseBody} 返回值。</p>
 */
public class BaseController {

    /** 成功，无数据体 */
    protected String ok() {
        return BizResults.ok();
    }

    /** 成功，包装 data 字段 */
    protected String ok(Object data) {
        return BizResults.ok(data);
    }

    /** 成功，单键值对平铺 */
    protected String okOne(String key, Object value) {
        return BizResults.okOne(key, value);
    }

    /** 成功，Map 全字段平铺 */
    protected String okFlat(java.util.Map<String, Object> fields) {
        return BizResults.okFlat(fields);
    }

    /** 成功，返回 id */
    protected String okId(String id) {
        return BizResults.okId(id);
    }

    /** 失败 */
    protected String error(String msg) {
        return BizResults.error(msg);
    }

    /** 失败（带错误类型） */
    protected String error(String msg, String errorType) {
        return BizResults.error(msg, errorType);
    }

    /** 异常转 JSON 错误响应 */
    protected String errorJson(Throwable e) {
        return BizResults.error(BizExceptions.shortMessage(e));
    }
}