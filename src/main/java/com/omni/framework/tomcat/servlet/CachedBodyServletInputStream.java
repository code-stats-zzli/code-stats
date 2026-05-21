package com.omni.framework.tomcat.servlet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 缓存请求体的 InputStream，支持多次读取
 */
public class CachedBodyServletInputStream extends InputStream {
    private final ByteArrayInputStream byteArrayInputStream;

    public CachedBodyServletInputStream(byte[] cachedBody) {
        this.byteArrayInputStream = new ByteArrayInputStream(cachedBody);
    }

    @Override
    public int read() throws IOException {
        return byteArrayInputStream.read();
    }

    @Override
    public int available() throws IOException {
        return byteArrayInputStream.available();
    }
}