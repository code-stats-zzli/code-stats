package com.codestats.framework.codestats.core;

import com.codestats.framework.log.LoggerFactory;
import com.codestats.framework.log.core.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

public class ProgressMonitor {
    private static final Logger log = LoggerFactory.getLogger(ProgressMonitor.class);

    private final AtomicLong processed = new AtomicLong(0);
    private final long total;
    private volatile long lastPrint = System.currentTimeMillis();
    private final List<ProgressListener> listeners = new ArrayList<>();
    private final List<BiConsumer<Long, Path>> fileProgressListeners = new CopyOnWriteArrayList<>();

    public ProgressMonitor(long total) {
        this.total = total;
    }

    public void addProgressListener(ProgressListener listener) {
        listeners.add(listener);
    }

    public void addFileProgressListener(BiConsumer<Long, Path> listener) {
        fileProgressListeners.add(listener);
    }

    public void increment() {
        long now = processed.incrementAndGet();
        notifyListeners(now, null);
    }

    public void incrementWithFile(Path currentFile) {
        long now = processed.incrementAndGet();
        notifyListeners(now, currentFile);
    }

    private void notifyListeners(long now, Path currentFile) {
        for (ProgressListener listener : listeners) {
            listener.onProgress(now, total);
        }
        for (BiConsumer<Long, Path> listener : fileProgressListeners) {
            listener.accept(now, currentFile);
        }
        if (System.currentTimeMillis() - lastPrint > 2000 || now == total) {
            String extra = currentFile != null ? " (" + currentFile.getFileName() + ")" : "";
            log.debug("Progress: {} / {} ({:.1f}%){}", now, total, 100.0 * now / total, extra);
            lastPrint = System.currentTimeMillis();
            if (now == total) log.debug("Progress complete");
        }
    }
}