package com.omni.common.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池工厂工具。
 * <p>所有线程均为守护线程，避免 JVM 退出时被阻塞。</p>
 */
public final class ThreadPoolUtils {

    private ThreadPoolUtils() {}

    /** 守护线程工厂：线程名前缀 + 自增序号 */
    public static ThreadFactory daemonThreadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger(0);
        return r -> {
            Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    public static ExecutorService newFixedThreadPool(int nThreads, String prefix) {
        return Executors.newFixedThreadPool(Math.max(1, nThreads), daemonThreadFactory(prefix));
    }

    public static ExecutorService newCachedThreadPool(String prefix) {
        return Executors.newCachedThreadPool(daemonThreadFactory(prefix));
    }

    public static ExecutorService newSingleThreadPool(String prefix) {
        return Executors.newSingleThreadExecutor(daemonThreadFactory(prefix));
    }

    public static ScheduledExecutorService newSingleScheduledPool(String prefix) {
        return Executors.newSingleThreadScheduledExecutor(daemonThreadFactory(prefix));
    }

    public static ScheduledExecutorService newScheduledPool(int corePoolSize, String prefix) {
        return Executors.newScheduledThreadPool(Math.max(1, corePoolSize), daemonThreadFactory(prefix));
    }

    public static void shutdown(ExecutorService pool) {
        if (pool == null) return;
        try {
            if (!pool.isShutdown()) pool.shutdownNow();
        } catch (Throwable ignored) {}
    }
}