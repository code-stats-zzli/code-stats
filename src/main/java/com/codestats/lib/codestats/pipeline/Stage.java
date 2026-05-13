package com.codestats.lib.codestats.pipeline;

public interface Stage<T, R> {
    R process(T input) throws Exception;
}