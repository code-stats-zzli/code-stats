package com.omni.business.spider.controller;

import com.omni.business.spider.service.SpiderTaskService;
import com.omni.framework.spider.model.Article;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestBody;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.spring.mvc.ResponseContextHolder;
import com.omni.framework.tomcat.servlet.HttpServletResponse;
import com.omni.framework.util.JsonUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/spider/api")
public class SpiderController {

    @Autowired
    private SpiderTaskService taskService;

    @RequestMapping("/start")
    @ResponseBody
    public String startTask(@RequestBody Map<String, Object> req) {
        String username = (String) req.get("username");
        int maxConcurrent = req.containsKey("maxConcurrent") ? ((Number) req.get("maxConcurrent")).intValue() : 3;
        long intervalMs = req.containsKey("intervalMs") ? ((Number) req.get("intervalMs")).longValue() : 1500;
        boolean mergeEnabled = req.containsKey("mergeEnabled") ? (Boolean) req.get("mergeEnabled") : true;
        String mergeFileName = (String) req.getOrDefault("mergeFileName", "merged.txt");
        int maxArticles = req.containsKey("maxArticles") ? ((Number) req.get("maxArticles")).intValue() : 100;
        int maxTimeSeconds = req.containsKey("maxTimeSeconds") ? ((Number) req.get("maxTimeSeconds")).intValue() : 0;

        if (username == null || username.trim().isEmpty()) {
            return errorResponse("用户名不能为空");
        }
        String taskId = taskService.startTask(username, maxConcurrent, intervalMs, mergeEnabled, mergeFileName, maxArticles, maxTimeSeconds);
        Map<String, String> result = new HashMap<>();
        result.put("taskId", taskId);
        return JsonUtil.toJson(result);
    }

    @RequestMapping("/status")
    @ResponseBody
    public String getTaskStatus(String taskId) {
        Map<String, Object> status = taskService.getTaskStatus(taskId);
        if (status == null) {
            return errorResponse("任务不存在");
        }
        return JsonUtil.toJson(status);
    }

    @RequestMapping("/articles")
    @ResponseBody
    public String getArticles(String taskId) {
        List<Article> articles = taskService.getArticles(taskId);
        if (articles == null) {
            return errorResponse("任务不存在或无文章");
        }
        return JsonUtil.toJson(articles);
    }

    @RequestMapping("/download")
    public void downloadMergedFile(String taskId) throws Exception {
        HttpServletResponse response = ResponseContextHolder.getResponse();
        String filePath = taskService.getMergedFilePath(taskId);
        if (filePath == null || !Files.exists(Paths.get(filePath))) {
            response.setStatus(404, "Not Found");
            response.write("合并文件不存在");
            response.send();
            return;
        }
        Path path = Paths.get(filePath);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + path.getFileName().toString() + "\"");
        response.setContentType("text/plain");
        response.sendStatic(Files.newInputStream(path), "text/plain", Files.size(path));
    }

    @RequestMapping("/article")
    public void downloadSingleArticle(String taskId, String url) throws Exception {
        HttpServletResponse response = ResponseContextHolder.getResponse();
        List<Article> articles = taskService.getArticles(taskId);
        if (articles == null) {
            response.setStatus(404, "Not Found");
            response.write("文章不存在");
            response.send();
            return;
        }
        Article target = articles.stream().filter(a -> url.equals(a.getUrl())).findFirst().orElse(null);
        if (target == null || target.getContent() == null) {
            response.setStatus(404, "Not Found");
            response.write("文章内容不存在");
            response.send();
            return;
        }
        String fileName = target.getTitle().replaceAll("[\\\\/:*?\"<>|]", "_") + ".txt";
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setContentType("text/plain");
        response.write(target.getContent());
        response.send();
    }

    private String errorResponse(String msg) {
        return JsonUtil.toJson(Map.of("success", false, "error", msg));
    }
}