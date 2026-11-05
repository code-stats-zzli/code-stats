// ======== D:\work\git\CodeStats\src\main\java\com\omni\business\spider\service\SpiderTaskService.java ========
package com.omni.business.assistance.spider.service;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spider.config.SpiderConfig;
import com.omni.framework.spider.core.Crawler;
import com.omni.framework.spider.model.Article;
import com.omni.framework.spring.annotation.Service;
import com.omni.framework.codestats.util.PlaywrightStealthUtil;
import com.microsoft.playwright.Page;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class SpiderTaskService {

    private static final Logger log = LoggerFactory.getLogger(SpiderTaskService.class);
    private final Map<String, TaskContext> tasks = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public static class TaskContext implements Crawler.StatusUpdater {
        final String taskId;
        final String username;
        volatile String state;
        volatile int totalArticles;
        volatile List<Article> articles = new ArrayList<>();
        volatile List<FailedArticle> failedArticles = new ArrayList<>();
        volatile String mergedFilePath;
        volatile Map<String, Object> authorInfo = new HashMap<>();
        volatile String errorMessage;
        long startTime;

        volatile String currentStep;
        volatile String currentUrl;
        volatile int processedCount;

        TaskContext(String taskId, String username) {
            this.taskId = taskId;
            this.username = username;
            this.state = "RUNNING";
            this.startTime = System.currentTimeMillis();
            this.currentStep = "初始化";
            this.processedCount = 0;
        }

        public synchronized void updateStatus(String step, String url, Article article) {
            if (step != null) this.currentStep = step;
            if (url != null) this.currentUrl = url;
            if (article != null) {
                this.articles.add(article);
                this.processedCount = this.articles.size();
                this.totalArticles = this.processedCount;
            }
        }

        public synchronized void updateStatus(String step, String url, String error) {
            if (step != null) this.currentStep = step;
            if (url != null) this.currentUrl = url;
            if (error != null) {
                this.failedArticles.add(new FailedArticle(url, error));
                this.totalArticles = this.articles.size() + this.failedArticles.size();
            }
        }

        public Map<String, Object> toStatusMap() {
            Map<String, Object> status = new HashMap<>();
            status.put("state", state);
            status.put("totalArticles", totalArticles);
            status.put("successCount", articles.size());
            status.put("failedCount", failedArticles.size());
            status.put("articles", articles);
            status.put("failedArticles", failedArticles);
            status.put("authorInfo", authorInfo);
            status.put("mergedFilePath", mergedFilePath);
            status.put("errorMessage", errorMessage);
            status.put("currentStep", currentStep);
            status.put("currentUrl", currentUrl);
            status.put("processedCount", processedCount);
            return status;
        }
    }

    public static class FailedArticle {
        private String url;
        private String reason;
        public FailedArticle(String url, String reason) { this.url = url; this.reason = reason; }
        public String getUrl() { return url; }
        public String getReason() { return reason; }
    }

    public String startTask(String username, int maxConcurrent, long intervalMs,
                            boolean mergeEnabled, String mergeFileName,
                            int maxArticles, int maxTimeSeconds) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        TaskContext ctx = new TaskContext(taskId, username);
        tasks.put(taskId, ctx);

        fetchAuthorInfoAsync(username, ctx);

        executor.submit(() -> {
            try {
                SpiderConfig config = new SpiderConfig.Builder()
                        .username(username)
                        .maxConcurrent(maxConcurrent)
                        .intervalMs(intervalMs)
                        .outputDir("./spider_output/" + taskId)
                        .mergeEnabled(mergeEnabled)
                        .mergeFileName(mergeFileName)
                        .maxArticles(maxArticles)
                        .maxTimeSeconds(maxTimeSeconds)
                        .build();

                Crawler crawler = new Crawler(config, ctx);
                crawler.start();

                ctx.mergedFilePath = crawler.getMergeFilePath();

                if (ctx.mergedFilePath != null && !ctx.failedArticles.isEmpty()) {
                    appendFailedLinksToMergeFile(ctx.mergedFilePath, ctx.failedArticles);
                }

                if (ctx.authorInfo.isEmpty()) {
                    ctx.authorInfo.put("name", username);
                    ctx.authorInfo.put("nickname", username);
                    ctx.authorInfo.put("description", "CSDN 博主");
                }

                ctx.state = "COMPLETED";
                ctx.currentStep = "爬取完成";
            } catch (Exception e) {
                log.error("爬虫任务失败: " + taskId, e);
                ctx.state = "ERROR";
                ctx.errorMessage = e.getMessage();
                ctx.currentStep = "失败: " + e.getMessage();
            }
        });
        return taskId;
    }

    /**
     * 异步获取作者详细信息（包括统计数据、专栏列表）
     */
    private void fetchAuthorInfoAsync(String username, TaskContext ctx) {
        executor.submit(() -> {
            try {
                String profileUrl = "https://blog.csdn.net/" + username;
                Map<String, Object> info = fetchAuthorFullProfile(profileUrl);
                if (info != null && !info.isEmpty()) {
                    ctx.authorInfo.putAll(info);
                } else {
                    ctx.authorInfo.put("name", username);
                    ctx.authorInfo.put("nickname", username);
                    ctx.authorInfo.put("description", "CSDN 博主");
                }
            } catch (Exception e) {
                log.warn("获取作者详细信息失败: {}", username, e);
                ctx.authorInfo.put("name", username);
                ctx.authorInfo.put("nickname", username);
                ctx.authorInfo.put("description", "CSDN 博主");
            }
        });
    }

    /**
     * 从博主首页完整获取：昵称、简介、统计数据（访问量、原创、粉丝、关注、点赞、评论、收藏、排名）、专栏列表
     */
    private Map<String, Object> fetchAuthorFullProfile(String profileUrl) {
        Map<String, Object> info = new HashMap<>();
        if (!PlaywrightStealthUtil.isAvailable()) {
            log.warn("Playwright不可用，无法获取作者详细信息");
            return info;
        }
        Page page = null;
        try {
            page = PlaywrightStealthUtil.createDefaultPage();
            PlaywrightStealthUtil.navigateAndWait(page, profileUrl, 20);
            String content = page.content();
            Document doc = Jsoup.parse(content);

            // 1. 昵称
            String nickname = doc.select(".user-name, .profile-name, .follow-nickName, .blog-name").first() != null ?
                    doc.select(".user-name, .profile-name, .follow-nickName, .blog-name").first().text() : null;
            if (nickname == null) nickname = doc.select("title").first() != null ?
                    doc.select("title").first().text().replace("的博客", "").trim() : "";
            info.put("nickname", nickname);
            info.put("name", nickname);

            // 2. 简介
            String description = doc.select(".user-profile-intro, .person-signature, .profile-intro, .user-intro").first() != null ?
                    doc.select(".user-profile-intro, .person-signature, .profile-intro, .user-intro").first().text() : "";
            if (description.isEmpty()) {
                description = doc.select("meta[name=description]").attr("content");
            }
            info.put("description", description);

            // 3. 统计数据（通过侧边栏或顶部统计栏）
            // 常见结构：个人主页右侧有统计卡片，包含原创、粉丝、访问量等
            Elements statItems = doc.select(".aside-box .statistics-item, .user-stats .stat-item, .profile-statistics .stat-item");
            if (statItems.isEmpty()) {
                // 尝试另一种选择器
                statItems = doc.select(".data-info dl dt, .user-info dl dt");
            }
            for (Element item : statItems) {
                String text = item.text();
                if (text.contains("原创")) {
                    String num = text.replaceAll("[^0-9]", "");
                    if (!num.isEmpty()) info.put("originalCount", Integer.parseInt(num));
                } else if (text.contains("粉丝")) {
                    String num = text.replaceAll("[^0-9]", "");
                    if (!num.isEmpty()) info.put("fansCount", Integer.parseInt(num));
                } else if (text.contains("访问") && text.contains("总")) {
                    String num = text.replaceAll("[^0-9]", "");
                    if (!num.isEmpty()) info.put("totalVisits", Integer.parseInt(num));
                } else if (text.contains("点赞")) {
                    String num = text.replaceAll("[^0-9]", "");
                    if (!num.isEmpty()) info.put("likesCount", Integer.parseInt(num));
                } else if (text.contains("评论")) {
                    String num = text.replaceAll("[^0-9]", "");
                    if (!num.isEmpty()) info.put("commentsCount", Integer.parseInt(num));
                } else if (text.contains("收藏")) {
                    String num = text.replaceAll("[^0-9]", "");
                    if (!num.isEmpty()) info.put("collectionsCount", Integer.parseInt(num));
                } else if (text.contains("排名")) {
                    String num = text.replaceAll("[^0-9]", "");
                    if (!num.isEmpty()) info.put("rank", Integer.parseInt(num));
                }
            }

            // 单独处理关注数（有时单独显示）
            Element followElem = doc.select(".follow-count, .follow-num").first();
            if (followElem != null) {
                String num = followElem.text().replaceAll("[^0-9]", "");
                if (!num.isEmpty()) info.put("followsCount", Integer.parseInt(num));
            }

            // 4. 专栏列表
            List<Map<String, String>> columns = new ArrayList<>();
            Elements columnItems = doc.select(".column-item, .blog-list-box .column, .aside-box .column-item");
            for (Element col : columnItems) {
                String colName = col.select(".column-name, .title").text();
                String articleCountStr = col.select(".count, .num").text().replaceAll("[^0-9]", "");
                int articleCount = articleCountStr.isEmpty() ? 0 : Integer.parseInt(articleCountStr);
                Map<String, String> column = new HashMap<>();
                column.put("name", colName);
                column.put("articleCount", String.valueOf(articleCount));
                columns.add(column);
            }
            info.put("columns", columns);

            // 若未能获取到统计数据，从页面另外的模块再次尝试
            if (!info.containsKey("totalVisits")) {
                Element visitSpan = doc.select(".blog-stats .pv, .user-stats .pv").first();
                if (visitSpan != null) {
                    String num = visitSpan.text().replaceAll("[^0-9]", "");
                    if (!num.isEmpty()) info.put("totalVisits", Integer.parseInt(num));
                }
            }
            if (!info.containsKey("originalCount")) {
                Element originalSpan = doc.select(".blog-stats .article-num, .user-stats .article-num").first();
                if (originalSpan != null) {
                    String num = originalSpan.text().replaceAll("[^0-9]", "");
                    if (!num.isEmpty()) info.put("originalCount", Integer.parseInt(num));
                }
            }

        } catch (Exception e) {
            log.warn("解析作者详细信息失败", e);
        } finally {
            if (page != null) page.close();
        }
        return info;
    }

    private void appendFailedLinksToMergeFile(String mergedFilePath, List<FailedArticle> failedArticles) {
        Path path = Paths.get(mergedFilePath);
        if (!Files.exists(path)) return;
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n========== 下载失败的文章列表 ==========\n");
        for (FailedArticle fa : failedArticles) {
            sb.append("URL: ").append(fa.getUrl()).append("\n");
            sb.append("原因: ").append(fa.getReason()).append("\n");
            sb.append("----------------------------------------\n");
        }
        try {
            Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
            log.info("已追加 {} 条失败链接到合并文件: {}", failedArticles.size(), mergedFilePath);
        } catch (IOException e) {
            log.error("追加失败链接到合并文件失败", e);
        }
    }

    public Map<String, Object> getTaskStatus(String taskId) {
        TaskContext ctx = tasks.get(taskId);
        return ctx == null ? null : ctx.toStatusMap();
    }

    public List<Article> getArticles(String taskId) {
        TaskContext ctx = tasks.get(taskId);
        return ctx == null ? null : ctx.articles;
    }

    public String getMergedFilePath(String taskId) {
        TaskContext ctx = tasks.get(taskId);
        return ctx == null ? null : ctx.mergedFilePath;
    }

    public void cleanupExpiredTasks(long maxAgeMillis) {
        long now = System.currentTimeMillis();
        tasks.entrySet().removeIf(entry -> {
            TaskContext ctx = entry.getValue();
            return ("COMPLETED".equals(ctx.state) || "ERROR".equals(ctx.state))
                    && (now - ctx.startTime > maxAgeMillis);
        });
    }
}