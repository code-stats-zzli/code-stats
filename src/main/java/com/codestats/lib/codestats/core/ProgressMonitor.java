package com.codestats.lib.codestats.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

public class ProgressMonitor {
    private final AtomicLong processed = new AtomicLong(0);
    private final long total;
    private volatile long lastPrint = System.currentTimeMillis();
    private final List<ProgressListener> listeners = new ArrayList<>();

    // 新增：携带文件名的进度监听器（线程安全列表）
    private final List<BiConsumer<Long, Path>> fileProgressListeners = new CopyOnWriteArrayList<>();

    public ProgressMonitor(long total) {
        this.total = total;
    }

    public void addProgressListener(ProgressListener listener) {
        listeners.add(listener);
    }

    /**
     * 新增：注册携带当前文件路径的进度监听器
     */
    public void addFileProgressListener(BiConsumer<Long, Path> listener) {
        fileProgressListeners.add(listener);
    }

    public void increment() {
        long now = processed.incrementAndGet();
        notifyListeners(now, null);
    }

    /**
     * 新增：进度+1，并携带当前处理的文件路径
     */
    public void incrementWithFile(Path currentFile) {
        long now = processed.incrementAndGet();
        notifyListeners(now, currentFile);
    }

    private void notifyListeners(long now, Path currentFile) {
        // 通知原有监听器（仅计数）
        for (ProgressListener listener : listeners) {
            listener.onProgress(now, total);
        }
        // 通知携带文件路径的监听器
        for (BiConsumer<Long, Path> listener : fileProgressListeners) {
            listener.accept(now, currentFile);
        }
        // 控制台输出（保留原有逻辑）
        if (System.currentTimeMillis() - lastPrint > 2000 || now == total) {
            String extra = currentFile != null ? " (" + currentFile.getFileName() + ")" : "";
            System.out.printf("\rProgress: %d / %d (%.1f%%)%s", now, total, 100.0 * now / total, extra);
            lastPrint = System.currentTimeMillis();
            if (now == total) System.out.println();
        }
    }
}