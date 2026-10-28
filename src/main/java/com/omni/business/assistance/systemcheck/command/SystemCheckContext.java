package com.omni.business.assistance.systemcheck.command;

import com.omni.business.assistance.systemcheck.config.CheckConfig;

/**
 * 检查命令的执行上下文（持有配置、系统信息、取消标志、进度回调）
 */
public class SystemCheckContext {
    private final CheckConfig config;
    private final boolean isWin;
    private final boolean isRoot;
    private volatile boolean cancelled = false;

    /**
     * ★ 新增：进度回调接口。
     * Command 内部通过 {@link #reportProgress(String)} 上报当前进度文本，
     * TaskManager 会在每个模块的 pool 线程里设置回调，把文本写入对应 ModuleState。
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(String text);
    }

    /**
     * ★ 新增：进度回调（线程隔离）。
     * 并发执行多个模块时，每个模块在自己的线程里设置回调，
     * ThreadLocal 保证每个模块看到的都是自己的进度，互不覆盖。
     */
    private final ThreadLocal<ProgressCallback> progressCallback = new ThreadLocal<>();

    public SystemCheckContext(CheckConfig config) {
        this.config = config;
        this.isWin = System.getProperty("os.name", "").toLowerCase().contains("win");
        this.isRoot = detectRoot();
    }

    private boolean detectRoot() {
        if (isWin) {
            return AbstractSystemCheckCommand.execStatic("net session").length() > 0;
        }
        String user = System.getProperty("user.name", "");
        return "root".equals(user) || "0".equals(user);
    }

    public CheckConfig getConfig() { return config; }
    public boolean isWin() { return isWin; }
    public boolean isRoot() { return isRoot; }

    /** 请求取消（命令内部应定期检查 isCancelled()） */
    public void cancel() { this.cancelled = true; }
    public boolean isCancelled() { return cancelled; }

    // ==================== ★ 新增：进度上报 API ====================

    /** 由 TaskManager 在每个模块的 pool 线程里调用，绑定当前模块的进度回调 */
    public void setProgressCallback(ProgressCallback cb) {
        if (cb == null) progressCallback.remove();
        else progressCallback.set(cb);
    }

    /** 由 TaskManager 在任务结束时调用，清理 ThreadLocal 避免泄漏 */
    public void clearProgressCallback() {
        progressCallback.remove();
    }

    /**
     * 由 Command 内部调用，上报当前进度（例如正在检查的文件名）。
     * 未绑定回调时静默忽略，不影响正常执行。
     */
    public void reportProgress(String text) {
        ProgressCallback cb = progressCallback.get();
        if (cb != null) {
            try {
                cb.onProgress(text);
            } catch (Exception ignored) {
                // 进度上报失败绝不影响检查本身
            }
        }
    }
}