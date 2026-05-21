package com.omni.framework.spring.mvc;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 表示一个 multipart 文件项，内容存储在临时文件中。
 * 实现了 AutoCloseable，以便在请求结束后自动清理临时文件。
 */
public class FileItem implements Closeable {
    private final String fieldName;
    private final String fileName;
    private final String contentType;
    private final long size;
    private final Path tempFile;

    public FileItem(String fieldName, String fileName, String contentType, long size, Path tempFile) {
        this.fieldName = fieldName;
        this.fileName = fileName;
        this.contentType = contentType;
        this.size = size;
        this.tempFile = tempFile;
    }

    public String getFieldName() { return fieldName; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSize() { return size; }

    /**
     * 获取文件内容的输入流（每次调用会返回新的 InputStream）
     */
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(tempFile);
    }

    /**
     * 将临时文件内容全部读取为字节数组（注意：大文件慎用）
     */
    public byte[] getBytes() throws IOException {
        return Files.readAllBytes(tempFile);
    }

    /**
     * 将临时文件移动到目标路径（例如持久化目录）
     */
    public void transferTo(Path dest) throws IOException {
        Files.move(tempFile, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void close() throws IOException {
        Files.deleteIfExists(tempFile);
    }
}