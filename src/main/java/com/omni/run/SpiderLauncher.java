package com.omni.run;

import com.omni.framework.spider.config.SpiderConfig;
import com.omni.framework.spider.core.Crawler;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

public class SpiderLauncher {
    private static final Logger log = LoggerFactory.getLogger(SpiderLauncher.class);

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("用法: java -jar spider.jar <csdn_username> [maxConcurrent] [intervalMs]");
            //return;
        }
        String username = args.length > 0 ? args[0] : "qq_41652036?type=blog";
        int maxConcurrent = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        long intervalMs = args.length > 2 ? Long.parseLong(args[2]) : 1500;
        int idx = username.indexOf('?');
        String safeName = idx == -1 ? username : username.substring(0, idx);
        SpiderConfig config = new SpiderConfig.Builder()
                .username(username)
                .maxConcurrent(maxConcurrent)
                .intervalMs(intervalMs)
                .outputDir("D:\\space\\csdn\\download\\csdn_" + safeName)
                .mergeEnabled(true)
                .mergeFileName("merged.txt")
                .build();

        try {
            Crawler crawler = new Crawler(config);
            crawler.start();
        } catch (Exception e) {
            log.error("爬虫启动失败", e);
        }
    }



}