package com.codestats.framework.codestats.output;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.nio.charset.StandardCharsets;

public class FileWriter implements OutputWriter {
    private final Path path;
    public FileWriter(Path path) { this.path = path; }
    @Override
    public void write(String content) throws IOException {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }
}