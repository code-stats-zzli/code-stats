package com.omni.framework.codestats.pipeline;

public interface Stage<T, R> {
    R process(T input) throws Exception;
}