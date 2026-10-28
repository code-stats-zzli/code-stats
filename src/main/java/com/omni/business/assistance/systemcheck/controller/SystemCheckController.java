package com.omni.business.assistance.systemcheck.controller;

import com.omni.business.assistance.systemcheck.command.SystemCheckCommand;
import com.omni.business.assistance.systemcheck.command.SystemCheckTaskManager;
import com.omni.business.assistance.systemcheck.config.CheckConfig;
import com.omni.business.assistance.systemcheck.model.CheckResult;
import com.omni.business.assistance.systemcheck.model.Finding;
import com.omni.business.assistance.systemcheck.service.SystemCheckService;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestBody;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.spring.mvc.ResponseContextHolder;
import com.omni.framework.tomcat.servlet.HttpServletResponse;
import com.omni.framework.util.JsonUtil;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Controller
@RequestMapping("/systemcheck/api")
public class SystemCheckController {

    @Autowired
    private SystemCheckService service;

    /** 获取默认配置（含每模块详细配置字段）— ★ 增加兜底，避免静默失败 */
    @RequestMapping("/config")
    @ResponseBody
    public String getConfig() {
        try {
            if (service == null) {
                throw new IllegalStateException("SystemCheckService 未注入（Spring 扫描或依赖问题）");
            }
            CheckConfig cfg = CheckConfig.load();
            List<SystemCheckCommand> commands = service.getCommands();
            if (commands == null || commands.isEmpty()) {
                throw new IllegalStateException("检查命令列表为空（SystemCheckService 初始化失败）");
            }
            Map<String, Object> meta = cfg.toMetaMap(commands);
            if (meta.get("checks") == null) {
                meta.put("checks", new ArrayList<>());
            }
            return JsonUtil.toJson(meta);
        } catch (Throwable e) {
            // 把真实异常打到服务端日志，前端拿到结构化错误而不是 500 HTML
            e.printStackTrace();
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("threadCount", 4);
            err.put("checks", new ArrayList<>());
            err.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            return JsonUtil.toJson(err);
        }
    }

    /**
     * 启动检查任务（异步）
     * 返回 { success, taskId }
     */
    @RequestMapping("/run")
    @ResponseBody
    @SuppressWarnings("unchecked")
    public String run(@RequestBody Map<String, Object> req) {
        try {
            CheckConfig cfg = CheckConfig.load();
            if (req != null) {
                Object tc = req.get("threadCount");
                if (tc != null) {
                    try { cfg.setThreadCount(Integer.parseInt(String.valueOf(tc))); }
                    catch (Exception ignored) {}
                }
                Object mc = req.get("moduleConfig");
                if (mc instanceof Map) {
                    cfg.mergeModuleConfig((Map<String, Map<String, Object>>) mc);
                }
            }

            Set<String> selected = null;
            if (req != null && req.get("checks") instanceof List) {
                selected = new HashSet<>();
                for (Object o : (List<Object>) req.get("checks")) selected.add(String.valueOf(o));
            }

            String taskId = SystemCheckTaskManager.createTask(cfg, service.getCommands(), selected);
            return JsonUtil.toJson(Map.of("success", true, "taskId", taskId));
        } catch (Exception e) {
            return JsonUtil.toJson(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /** 查询任务状态（含运行中实时耗时、各模块状态、完成时结果） */
    @RequestMapping("/status")
    @ResponseBody
    public String status(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            return JsonUtil.toJson(Map.of("success", false, "error", "缺少 taskId"));
        }
        Map<String, Object> s = SystemCheckTaskManager.getStatus(taskId);
        if (s == null) {
            return JsonUtil.toJson(Map.of("success", false, "error", "任务不存在或已过期"));
        }
        s.put("success", true);
        return JsonUtil.toJson(s);
    }

    /** 取消任务 */
    @RequestMapping("/cancel")
    @ResponseBody
    public String cancel(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            return JsonUtil.toJson(Map.of("success", false, "error", "缺少 taskId"));
        }
        boolean ok = SystemCheckTaskManager.cancel(taskId);
        return JsonUtil.toJson(Map.of("success", ok, "message", ok ? "已取消" : "无法取消（已完成或不存在）"));
    }

    // ==================== 导出 ====================

    @RequestMapping("/export/html")
    public void exportHtml() throws Exception {
        HttpServletResponse resp = ResponseContextHolder.getResponse();
        if (resp == null) return;
        CheckConfig cfg = CheckConfig.load();
        Map<String, Object> result = runSync(cfg);
        @SuppressWarnings("unchecked")
        List<CheckResult> results = (List<CheckResult>) result.get("results");
        long totalMs = (long) result.get("totalMs");

        StringBuilder rows = new StringBuilder();
        int cOk = 0, cBad = 0, cInfo = 0;
        for (CheckResult r : results) {
            for (Finding f : r.getFindings()) {
                if ("警告".equals(f.getStatus())) continue;
                String color = "合规".equals(f.getStatus()) ? "#1a7f37"
                        : "不合规".equals(f.getStatus()) ? "#c62828" : "#6a737d";
                if ("合规".equals(f.getStatus())) cOk++;
                else if ("不合规".equals(f.getStatus())) cBad++;
                else cInfo++;
                rows.append("<tr><td>").append(esc(f.getCategory())).append("</td>")
                        .append("<td>").append(esc(f.getItem())).append("</td>")
                        .append("<td style='color:").append(color).append("'>").append(esc(f.getStatus())).append("</td>")
                        .append("<td>").append(esc(f.getRisk())).append("</td>")
                        .append("<td>").append(esc(f.getDetail())).append("</td>")
                        .append("<td>").append(esc(f.getSuggestion())).append("</td></tr>");
            }
        }

        String html = "<!DOCTYPE html><html><head><meta charset='utf-8'><title>系统检查报告</title>"
                + "<style>body{font-family:'Microsoft YaHei',sans-serif;margin:20px;background:#f6f8fa}"
                + "h1{color:#0969da}table{border-collapse:collapse;width:100%;background:#fff;font-size:13px}"
                + "th,td{border:1px solid #d0d7de;padding:6px 8px;text-align:left;vertical-align:top}"
                + "th{background:#eef2f6}</style></head><body>"
                + "<h1>系统安全检查报告</h1>"
                + "<p>生成时间：" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "</p>"
                + "<p>合规：" + cOk + " · 不合规：" + cBad + " · 信息：" + cInfo
                + " · 实际耗时：" + (totalMs / 1000.0) + " 秒</p>"
                + "<table><thead><tr><th>类别</th><th>检查项</th><th>状态</th><th>风险</th><th>详情</th><th>建议</th></tr></thead>"
                + "<tbody>" + rows + "</tbody></table></body></html>";

        byte[] data = html.getBytes(StandardCharsets.UTF_8);
        String fileName = URLEncoder.encode("系统检查报告_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".html", "UTF-8")
                .replaceAll("\\+", "%20");
        resp.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        resp.setContentType("text/html; charset=utf-8");
        resp.getRawOutputStream().write(data);
        resp.getRawOutputStream().flush();
    }

    @RequestMapping("/export/csv")
    public void exportCsv() throws Exception {
        HttpServletResponse resp = ResponseContextHolder.getResponse();
        if (resp == null) return;
        Map<String, Object> result = runSync(CheckConfig.load());
        @SuppressWarnings("unchecked")
        List<CheckResult> results = (List<CheckResult>) result.get("results");

        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("Category,Item,Status,Risk,Detail,Suggestion\n");
        for (CheckResult r : results) {
            for (Finding f : r.getFindings()) {
                if ("警告".equals(f.getStatus())) continue;
                csv.append(q(f.getCategory())).append(',')
                        .append(q(f.getItem())).append(',')
                        .append(q(f.getStatus())).append(',')
                        .append(q(f.getRisk())).append(',')
                        .append(q(f.getDetail())).append(',')
                        .append(q(f.getSuggestion())).append('\n');
            }
        }
        byte[] data = csv.toString().getBytes(StandardCharsets.UTF_8);
        String fileName = URLEncoder.encode("系统检查结果_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv", "UTF-8")
                .replaceAll("\\+", "%20");
        resp.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        resp.setContentType("text/csv; charset=utf-8");
        resp.getRawOutputStream().write(data);
        resp.getRawOutputStream().flush();
    }

    @RequestMapping("/export/json")
    public void exportJson() throws Exception {
        HttpServletResponse resp = ResponseContextHolder.getResponse();
        if (resp == null) return;
        Map<String, Object> result = runSync(CheckConfig.load());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        out.put("results", result.get("results"));
        byte[] data = JsonUtil.toJson(out).getBytes(StandardCharsets.UTF_8);
        String fileName = URLEncoder.encode("系统检查结果_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".json", "UTF-8")
                .replaceAll("\\+", "%20");
        resp.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        resp.setContentType("application/json; charset=utf-8");
        resp.getRawOutputStream().write(data);
        resp.getRawOutputStream().flush();
    }

    /** 导出用同步执行（阻塞等待完成） */
    private Map<String, Object> runSync(CheckConfig cfg) throws Exception {
        String taskId = SystemCheckTaskManager.createTask(cfg, service.getCommands(), null);
        long t0 = System.currentTimeMillis();
        while (true) {
            Map<String, Object> s = SystemCheckTaskManager.getStatus(taskId);
            if (s == null) throw new RuntimeException("任务丢失");
            if (!"RUNNING".equals(s.get("state"))) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("results", s.get("results"));
                out.put("totalMs", System.currentTimeMillis() - t0);
                return out;
            }
            Thread.sleep(300);
        }
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;")
                                .replace(">", "&gt;").replace("\"", "&quot;");
    }
    private static String q(String s) {
        return "\"" + (s == null ? "" : s.replace("\"", "\"\"")) + "\"";
    }
}