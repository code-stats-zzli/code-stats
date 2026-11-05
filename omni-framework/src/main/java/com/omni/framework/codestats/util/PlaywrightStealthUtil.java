package com.omni.framework.codestats.util;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.ColorScheme;
import com.microsoft.playwright.options.WaitUntilState;
import com.omni.framework.spring.config.ConfigLoader;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class PlaywrightStealthUtil {
    private static final Logger log = LoggerFactory.getLogger(PlaywrightStealthUtil.class);

    private static final AtomicReference<Playwright> playwrightRef = new AtomicReference<>();
    private static final AtomicReference<Browser> browserRef = new AtomicReference<>();
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicBoolean enabled = new AtomicBoolean(false);
    private static final AtomicBoolean initAttempted = new AtomicBoolean(false);

    private static final String[] LAUNCH_ARGS = {
            "--disable-blink-features=AutomationControlled",
            "--disable-features=IsolateOrigins,site-per-process",
            "--disable-site-isolation-trials",
            "--no-sandbox",
            "--disable-web-security",
            "--disable-dev-shm-usage",
            "--disable-gpu",
            "--disable-software-rasterizer",
            "--disable-extensions",
            "--disable-background-networking",
            "--disable-default-apps",
            "--disable-sync",
            "--metrics-recording-only",
            "--mute-audio",
            "--no-first-run",
            "--safebrowsing-disable-auto-update",
            "--ignore-certificate-errors",
            "--ignore-ssl-errors"
    };

    static {
        String enablePlaywright = ConfigLoader.getProperty("playwright.enabled", "false");
        enabled.set("true".equalsIgnoreCase(enablePlaywright));
        if (enabled.get()) {
            log.info("Playwright 配置为启用，将在首次使用时初始化");
        } else {
            log.info("Playwright 配置为禁用，不会启动浏览器");
        }
    }

    public static boolean isAvailable() {
        if (!enabled.get()) return false;
        if (!initAttempted.get()) initialize();
        return initialized.get();
    }

    private static synchronized void initialize() {
        if (initAttempted.get()) return;
        initAttempted.set(true);
        if (!enabled.get()) {
            log.debug("Playwright 未启用，跳过初始化");
            return;
        }
        try {
            Playwright playwright = Playwright.create();
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(Arrays.asList(LAUNCH_ARGS))
                    .setTimeout(60000);
            Browser browser = playwright.chromium().launch(launchOptions);
            playwrightRef.set(playwright);
            browserRef.set(browser);
            initialized.set(true);
            log.info("Playwright 浏览器初始化成功");
        } catch (Exception e) {
            log.error("Playwright 浏览器初始化失败", e);
            enabled.set(false);
            initialized.set(false);
        }
    }

    public static Browser getBrowser() {
        if (!isAvailable()) return null;
        return browserRef.get();
    }

    public static Page createDefaultPage() {
        if (!isAvailable()) {
            throw new UnsupportedOperationException("Playwright 未启用或初始化失败，请设置 playwright.enabled=true 并检查环境");
        }
        return createNewPage(null, 1280, 720, "zh-CN", "Asia/Shanghai");
    }

    public static Page createNewPage(String userAgent, int viewportWidth, int viewportHeight,
                                     String locale, String timezoneId) {
        if (!isAvailable()) {
            throw new UnsupportedOperationException("Playwright 不可用");
        }
        BrowserContext context = createNewContext(userAgent, viewportWidth, viewportHeight, locale, timezoneId);
        Page page = context.newPage();
        page.setDefaultNavigationTimeout(60000);
        page.setDefaultTimeout(60000);
        return page;
    }

    public static BrowserContext createNewContext(String userAgent, int viewportWidth, int viewportHeight,
                                                  String locale, String timezoneId) {
        if (!isAvailable()) {
            throw new UnsupportedOperationException("Playwright 不可用");
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
        headers.put("Upgrade-Insecure-Requests", "1");
        headers.put("Sec-Fetch-Dest", "document");
        headers.put("Sec-Fetch-Mode", "navigate");
        headers.put("Sec-Fetch-Site", "none");
        headers.put("Sec-Fetch-User", "?1");

        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                .setUserAgent(userAgent != null ? userAgent : getDefaultUserAgent())
                .setViewportSize(viewportWidth, viewportHeight)
                .setLocale(locale != null ? locale : "zh-CN")
                .setTimezoneId(timezoneId != null ? timezoneId : "Asia/Shanghai")
                .setExtraHTTPHeaders(headers)
                .setIgnoreHTTPSErrors(true)
                .setColorScheme(ColorScheme.LIGHT)
                .setScreenSize(viewportWidth, viewportHeight)
                .setBypassCSP(true);
        return browserRef.get().newContext(contextOptions);
    }

    private static String getDefaultUserAgent() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36";
    }

    public static void navigateAndWait(Page page, String url, int timeoutSeconds) {
        if (!isAvailable()) return;
        page.navigate(url, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.NETWORKIDLE)
                .setTimeout(timeoutSeconds * 1000L));
    }

    public static void close() {
        try {
            if (browserRef.get() != null) browserRef.get().close();
        } catch (Exception e) { log.warn("关闭浏览器异常", e); }
        try {
            if (playwrightRef.get() != null) playwrightRef.get().close();
        } catch (Exception e) { log.warn("关闭 Playwright 异常", e); }
        initialized.set(false);
        enabled.set(false);
        log.info("Playwright 资源已释放");
    }
}