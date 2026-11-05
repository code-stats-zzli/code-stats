package com.omni.framework.spring.mvc;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;

public interface HttpMessageConverter<T> {
    boolean canRead(Class<?> clazz, String contentType);
    T read(InputStream inputStream, Class<?> clazz) throws Exception;
    boolean canWrite(Class<?> clazz, String acceptType);
    void write(T obj, OutputStream outputStream, String contentType) throws Exception;
}