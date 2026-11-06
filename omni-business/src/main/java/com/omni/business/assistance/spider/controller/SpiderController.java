package com.omni.business.assistance.spider.controller;

import com.omni.business.assistance.spider.dto.StartTaskRequest;
import com.omni.business.assistance.spider.service.SpiderTaskService;
import com.omni.business.common.util.BaseController;
import com.omni.business.common.util.ResponseHelper;
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

/** ★ L1-01 + L2-01 */
@Controller
@RequestMapping("/spider/api")
public class SpiderController extends BaseController {

    @Autowired
    private SpiderTaskService taskService;

    @RequestMapping("/start")
    @ResponseBody
    public String startTask(@RequestBody StartTaskRequest req) {
        String taskId = taskService.startTask(
                req.getUsername(), req.getMaxConcurrent(), req.getIntervalMs(),
                req.isMergeEnabled(), req.getMergeFileName(),
                req.getMaxArticles(), req.getMaxTimeSeconds());
        return okOne("taskId", taskId);
    }

    @RequestMapping("/status")
    @ResponseBody
    public String getTaskStatus(@RequestParam("taskId") String taskId) {
        Map<String, Object> status = taskService.getTaskStatus(taskId);
        if (status == null) return error("任务不存在");
        return JsonUtil.toJson(status);
    }

    @RequestMapping("/articles")
    @ResponseBody
    public String getArticles(@RequestParam("taskId") String taskId) {
        List<Article> articles = taskService.getArticles(taskId);
        if (articles == null) return error("任务不存在或无文章");
        return JsonUtil.toJson(articles);
    }

    @RequestMapping("/download")
    public void downloadMergedFile(@RequestParam("taskId") String taskId) throws Exception {
        HttpServletResponse response = ResponseContextHolder.getResponse();
        if (response == null) return;
        String filePath = taskService.getMergedFilePath(taskId);
        if (filePath == null || !Files.exists(Paths.get(filePath))) {
            ResponseHelper.writeError(response, 404, "合并文件不存在");
            return;
        }
        Path path = Paths.get(filePath);
        ResponseHelper.writeTextAttachment(response, path, path.getFileName().toString());
    }

    @RequestMapping("/article")
    public void downloadSingleArticle(@RequestParam("taskId") String taskId,
                                      @RequestParam("url") String url) throws Exception {
        HttpServletResponse response = ResponseContextHolder.getResponse();
        if (response == null) return;
        List<Article> articles = taskService.getArticles(taskId);
        if (articles == null) { ResponseHelper.writeError(response, 404, "文章不存在"); return; }
        Article target = articles.stream().filter(a -> url.equals(a.getUrl())).findFirst().orElse(null);
        if (target == null || target.getContent() == null) { ResponseHelper.writeError(response, 404, "文章内容不存在"); return; }
        String fileName = target.getTitle().replaceAll("[\\\\/:*?\"<>|]", "_") + ".txt";
        ResponseHelper.writeAttachment(response, target.getContent().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                fileName, "text/plain; charset=utf-8");
    }
}