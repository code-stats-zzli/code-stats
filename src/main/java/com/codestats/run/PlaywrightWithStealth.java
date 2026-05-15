package com.codestats.run;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;

import java.util.Arrays;

public class PlaywrightWithStealth {
    public static void main(String[] args) {
        try (Playwright playwright = Playwright.create()) {
            // 1. 配置代理 (可选)
            // 若需使用代理，请取消注释并配置
            // BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
            //         .setProxy(new Proxy("http://your-proxy-ip:port"));

            // 2. 浏览器启动参数
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(false)  // 无头模式更易被检测，调试阶段建议设为 false
                    .setArgs(Arrays.asList(
                            "--disable-blink-features=AutomationControlled",
                            "--disable-features=IsolateOrigins,site-per-process"
                    ));
            // 若使用代理，请将 launchOptions 替换为带代理配置的变量
            Browser browser = playwright.chromium().launch(launchOptions);

            // 3. 创建上下文，模拟真实用户环境
            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .setViewportSize(1280, 720)
                    .setLocale("en-US")
                    .setTimezoneId("America/New_York");
            BrowserContext context = browser.newContext(contextOptions);

            Page page = context.newPage();

            // 4. 执行页面访问
            try {
                // 使用更长的超时时间，并等待网络空闲
                page.navigate("https://chat.deepseek.com/", new Page.NavigateOptions().setTimeout(60000).setWaitUntil(WaitUntilState.NETWORKIDLE));
                System.out.println("页面加载成功！");
                // 你的后续操作...
            } catch (TimeoutError e) {
                System.err.println("页面加载超时，请检查网络或反爬虫策略：" + e.getMessage());
                // 可以在这里保存截图用于调试
                // page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get("debug.png")));
            }
        }
    }
}