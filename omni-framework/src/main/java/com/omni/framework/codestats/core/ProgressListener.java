package com.omni.framework.codestats.core;

@FunctionalInterface
public interface ProgressListener {
    void onProgress(long current, long total);
}