package com.omni;

import com.omni.common.util.AppResourceExtractor;
import com.omni.framework.spring.boot.SpringApplication;

public class Bootstrap {
    public static void main(String[] args) {
        // ★ 关键：解压 jar 内资源到 user.dir，必须在 Spring 启动之前
        AppResourceExtractor.extractIfNeeded();
        SpringApplication.run(Bootstrap.class, args);
    }
}