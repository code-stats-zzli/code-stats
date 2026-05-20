package com.omni.common.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * 从字节数组加载 Jar 的类加载器
 */
public class ByteArrayClassLoader extends ClassLoader {
    private final byte[] jarData;

    public ByteArrayClassLoader(byte[] jarData, ClassLoader parent) {
        super(parent);
        this.jarData = jarData;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // 从 Jar 中读取指定类的字节码
        byte[] classBytes = extractClassFromJar(name);
        if (classBytes == null) {
            throw new ClassNotFoundException(name);
        }
        return defineClass(name, classBytes, 0, classBytes.length);
    }

    private byte[] extractClassFromJar(String className) {
        String classPath = className.replace('.', '/') + ".class";
        try (JarInputStream jis = new JarInputStream(new java.io.ByteArrayInputStream(jarData))) {
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                if (entry.getName().equals(classPath)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = jis.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                    }
                    return baos.toByteArray();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}