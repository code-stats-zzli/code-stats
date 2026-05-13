package com.codestats.lib.codestats.core;

@FunctionalInterface
public interface ProgressListener {
    void onProgress(long current, long total);
}