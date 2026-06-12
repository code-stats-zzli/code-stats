package com.omni.business.spider.controller;

import com.omni.business.spider.dto.StartTaskRequest;
import com.omni.business.spider.service.SpiderTaskService;
import com.omni.framework.spider.model.Article;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestBody;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.RequestParam;
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
    public String startTask(@RequestBody StartTaskRequest req) {
        String taskId = taskService.startTask(
                req.getUsername(),
                req.getMaxConcurrent(),
                req.getIntervalMs(),
                req.isMergeEnabled(),
                req.getMergeFileName(),
                req.getMaxArticles(),
                req.getMaxTimeSeconds()
        );
        Map<String, String> result = new HashMap<>();
        result.put("taskId", taskId);
        return JsonUtil.toJson(result);
    }

    @RequestMapping("/status")
    @ResponseBody
    public String getTaskStatus(@RequestParam("taskId") String taskId) {
        Map<String, Object> status = taskService.getTaskStatus(taskId);
        if (status == null) {
            return errorResponse("任务不存在");
        }
        // 确保失败文章列表也被包含在返回中
        return JsonUtil.toJson(status);
    }

    @RequestMapping("/articles")
    @ResponseBody
    public String getArticles(@RequestParam("taskId") String taskId) {
        List<Article> articles = taskService.getArticles(taskId);
        if (articles == null) {
            return errorResponse("任务不存在或无文章");
        }
        return JsonUtil.toJson(articles);
    }

    @RequestMapping("/download")
    public void downloadMergedFile(@RequestParam("taskId") String taskId) throws Exception {
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
    public void downloadSingleArticle(@RequestParam("taskId") String taskId,
                                      @RequestParam("url") String url) throws Exception {
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