package com.omni.framework.codestats.output;

import java.io.IOException;

public interface OutputWriter {
    void write(String content) throws IOException;
}