package com.codestats.framework.log.appender;

import com.codestats.framework.log.core.LogEvent;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class AsyncAppender extends Appender {
    private final BlockingQueue<LogEvent> queue;
    private final Appender delegate;
    private final Worker worker;
    private volatile boolean running = true;

    public AsyncAppender(Appender delegate, int queueSize) {
        this.delegate = delegate;
        this.queue = new ArrayBlockingQueue<>(queueSize);
        this.worker = new Worker();
        this.worker.start();
    }

    @Override
    protected void append(LogEvent event) {
        if (!queue.offer(event)) {
            System.err.println("AsyncAppender queue full, discarding log");
        }
    }

    @Override
    public void close() {
        running = false;
        worker.interrupt();
        delegate.close();
    }

    private class Worker extends Thread {
        @Override
        public void run() {
            while (running || !queue.isEmpty()) {
                try {
                    LogEvent event = queue.take();
                    delegate.doAppend(event);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}