package com.omni;

import com.omni.business.assistance.cluster.WorkerBootstrap;
import com.omni.common.util.AppResourceExtractor;
import com.omni.framework.spring.boot.SpringApplication;

public class Bootstrap {
    public static void main(String[] args) {
        AppResourceExtractor.extractIfNeeded();
        if (WorkerBootstrap.isWorker(args)) {
            WorkerBootstrap.start(args);
        }
        SpringApplication.run(Bootstrap.class, args);
    }
}