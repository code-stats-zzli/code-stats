package com.omni.framework.tomcat.loader;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

public class WebappClassLoader extends URLClassLoader {
    public WebappClassLoader(ClassLoader parent, URL[] urls) {
        super(urls, parent);
    }
    @Override
    public void close() throws IOException {
        super.close();
    }
}