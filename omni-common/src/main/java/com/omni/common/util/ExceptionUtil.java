package com.omni.common.util;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 异常工具类（common 版本，纯 JDK）。
 * <p>注意：{@code com.omni.framework.util.ExceptionUtil} 是 framework 层另一个同名类，
 * 两者职责相似但包不同，请不要交叉引用。</p>
 */
public final class ExceptionUtil {

    private ExceptionUtil() {}

    /** 获取根因（最底层 cause） */
    public static Throwable getRootCause(Throwable e) {
        if (e == null) return null;
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    /** 获取完整堆栈 */
    public static String getFullStackTrace(Throwable e) {
        if (e == null) return "";
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    /** 简短描述：RootSimpleName: message */
    public static String getMessage(Throwable e) {
        if (e == null) return "";
        Throwable root = getRootCause(e);
        String msg = root.getMessage();
        return root.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
    }

    /** 简短描述（单行，截断） */
    public static String brief(Throwable e) {
        if (e == null) return "unknown";
        String m = getMessage(e);
        return m.length() > 300 ? m.substring(0, 300) + "..." : m;
    }
}