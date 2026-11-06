package com.omni.business.common.util;

/**
 * 异常处理便捷工具。
 */
public final class BizExceptions {

    private BizExceptions() {}

    /** 简短错误描述：RootSimpleName: message */
    public static String shortMessage(Throwable e) {
        if (e == null) return "未知错误";
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        if (msg == null || msg.isEmpty()) return root.getClass().getSimpleName();
        return root.getClass().getSimpleName() + ": " + msg;
    }
}