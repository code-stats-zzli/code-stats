package com.codestats.lib.codestats.output;

import java.io.IOException;

public class ConsoleWriter implements OutputWriter {
    @Override
    public void write(String content) throws IOException {
        System.out.print(content);
    }
}