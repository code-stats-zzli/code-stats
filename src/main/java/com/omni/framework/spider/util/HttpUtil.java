package com.omni.framework.spider.util;

import com.microsoft.playwright.Page;
import com.omni.common.util.PlaywrightStealthUtil;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpUtil {
    private static final Logger log = LoggerFactory.getLogger(HttpUtil.class);
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 使用 Playwright 无头浏览器获取页面内容（对抗反爬）
     */
    public static String fetchWithPlaywright(String url) {
        if (!PlaywrightStealthUtil.isAvailable()) {
            log.warn("Playwright 不可用，降级使用普通 HTTP 请求");
            return fetchWithHttp(url);
        }
        Page page = PlaywrightStealthUtil.createDefaultPage();
        try {
            PlaywrightStealthUtil.navigateAndWait(page, url, 30);
            String content = page.content();
            log.debug("Playwright 抓取成功: {}", url);
            return content;
        } catch (Exception e) {
            log.error("Playwright 抓取失败: {}", url, e);
            return fetchWithHttp(url);
        } finally {
            page.close();
        }
    }

    /**
     * 普通 HTTP GET 请求（备用）
     */
    public static String fetchWithHttp(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body();
            } else {
                log.warn("HTTP {} 响应码: {}", url, response.statusCode());
                return null;
            }
        } catch (IOException | InterruptedException e) {
            log.error("HTTP 请求失败: {}", url, e);
            Thread.currentThread().interrupt();
            return null;
        }
    }
}