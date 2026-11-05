package com.omni.framework.util;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 异常信息处理工具类
 * 提供将异常堆栈（包括完整 cause 链）转换为字符串的方法
 */
public final class ExceptionUtil {

    private ExceptionUtil() {
        // 私有构造，防止实例化
    }

    /**
     * 获取异常的完整堆栈信息（包含所有 cause 链）
     *
     * @param throwable 异常对象
     * @return 包含完整堆栈的字符串
     */
    public static String getFullStackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    /**
     * 获取异常的根源因（最底层的 cause）
     */
    public static Throwable getRootCause(Throwable throwable) {
        if (throwable == null) return null;
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }
    public static String getFullStackTraceByLoop(Throwable throwable) {
        if (throwable == null) return "";
        StringBuilder sb = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            sb.append(current.toString()).append("\n");
            for (StackTraceElement ste : current.getStackTrace()) {
                sb.append("\tat ").append(ste).append("\n");
            }
            current = current.getCause();
            if (current != null) {
                sb.append("Caused by: ");
            }
        }
        return sb.toString();
    }

    /**
     * 获取异常的简短描述 + 完整堆栈（与 getFullStackTrace 相同，保留此方法便于语义）
     */
    public static String getStackTrace(Throwable throwable) {
        return getFullStackTrace(throwable);
    }
}