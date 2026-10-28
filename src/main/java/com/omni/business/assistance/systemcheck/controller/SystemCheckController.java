package com.omni.business.assistance.systemcheck.controller;

import com.omni.business.assistance.systemcheck.config.CheckConfig;
import com.omni.business.assistance.systemcheck.model.CheckResult;
import com.omni.business.assistance.systemcheck.model.Finding;
import com.omni.business.assistance.systemcheck.service.SystemCheckService;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestBody;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;
import com.omni.framework.spring.mvc.ResponseContextHolder;
import com.omni.framework.tomcat.servlet.HttpServletResponse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Controller
@RequestMapping("/systemcheck/api")
public class SystemCheckController {

    @Autowired
    private SystemCheckService service;

    /** 获取默认配置 */
    @RequestMapping("/config")
    @ResponseBody
    public String getConfig() {
        CheckConfig cfg = CheckConfig.load();
        return JsonUtil.toJson(cfg.toMetaMap());
    }

    /** 执行系统检查 */
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
            }

            Set<String> selected = null;
            if (req != null && req.get("checks") instanceof List) {
                selected = new HashSet<>();
                for (Object o : (List<Object>) req.get("checks")) selected.add(String.valueOf(o));
            }

            List<CheckResult> results = service.runCheck(cfg, selected);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            resp.put("results", results);
            // 统计
            int cOk = 0, cBad = 0, cWarn = 0, cInfo = 0;
            for (CheckResult r : results) {
                for (Finding f : r.getFindings()) {
                    switch (f.getStatus()) {
                        case "合规": cOk++; break;
                        case "不合规": cBad++; break;
                        case "警告": cWarn++; break;
                        default: cInfo++;
                    }
                }
            }
            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("ok", cOk);
            stat.put("bad", cBad);
            stat.put("warn", cWarn);
            stat.put("info", cInfo);
            resp.put("summary", stat);
            return JsonUtil.toJson(resp);
        } catch (Exception e) {
            return JsonUtil.toJson(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /** 导出 HTML */
    @RequestMapping("/export/html")
    public void exportHtml() throws Exception {
        HttpServletResponse resp = ResponseContextHolder.getResponse();
        if (resp == null) return;
        CheckConfig cfg = CheckConfig.load();
        List<CheckResult> results = service.runCheck(cfg, null);

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
                        .append("<td style='color:").append(color).append("'>")
                        .append(esc(f.getStatus())).append("</td>")
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
                + "<p>合规：" + cOk + " · 不合规：" + cBad + " · 信息：" + cInfo + "</p>"
                + "<table><thead><tr><th>类别</th><th>检查项</th><th>状态</th><th>风险</th><th>详情</th><th>整改建议</th></tr></thead>"
                + "<tbody>" + rows + "</tbody></table></body></html>";

        byte[] data = html.getBytes(StandardCharsets.UTF_8);
        String fileName = URLEncoder.encode("系统检查报告_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".html", "UTF-8")
                .replaceAll("\\+", "%20");
        resp.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        resp.setContentType("text/html; charset=utf-8");
        resp.getRawOutputStream().write(data);
        resp.getRawOutputStream().flush();
    }

    /** 导出 CSV */
    @RequestMapping("/export/csv")
    public void exportCsv() throws Exception {
        HttpServletResponse resp = ResponseContextHolder.getResponse();
        if (resp == null) return;
        CheckConfig cfg = CheckConfig.load();
        List<CheckResult> results = service.runCheck(cfg, null);

        StringBuilder csv = new StringBuilder("\uFEFF"); // UTF-8 BOM
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

    /** 导出 JSON */
    @RequestMapping("/export/json")
    public void exportJson() throws Exception {
        HttpServletResponse resp = ResponseContextHolder.getResponse();
        if (resp == null) return;
        CheckConfig cfg = CheckConfig.load();
        List<CheckResult> results = service.runCheck(cfg, null);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        out.put("results", results);
        byte[] data = JsonUtil.toJson(out).getBytes(StandardCharsets.UTF_8);
        String fileName = URLEncoder.encode("系统检查结果_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".json", "UTF-8")
                .replaceAll("\\+", "%20");
        resp.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        resp.setContentType("application/json; charset=utf-8");
        resp.getRawOutputStream().write(data);
        resp.getRawOutputStream().flush();
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;")
                                .replace(">", "&gt;").replace("\"", "&quot;");
    }
    private static String q(String s) {
        return "\"" + (s == null ? "" : s.replace("\"", "\"\"")) + "\"";
    }
}