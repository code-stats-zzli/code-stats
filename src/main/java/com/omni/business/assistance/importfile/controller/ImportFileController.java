package com.omni.business.assistance.importfile.controller;

import com.omni.business.assistance.importfile.model.ImportResult;
import com.omni.business.assistance.importfile.model.ParseResult;
import com.omni.business.assistance.importfile.parser.ImportFormatParser;
import com.omni.business.assistance.importfile.parser.ImportFormatRegistry;
import com.omni.business.assistance.importfile.service.ImportFileService;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestBody;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;

import java.util.*;

@Controller
@RequestMapping("/import/api")
public class ImportFileController {

    @Autowired private ImportFileService service;
    @Autowired private ImportFormatRegistry registry;

    /** 可用格式列表 */
    @RequestMapping("/formats")
    @ResponseBody
    public String formats() throws Exception {
        Map<String, Object> resp = new LinkedHashMap<>();
        List<Map<String, Object>> list = new ArrayList<>();
        for (ImportFormatParser p : registry.getAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", p.getName());
            m.put("displayName", p.getDisplayName());
            m.put("priority", p.getPriority());
            list.add(m);
        }
        resp.put("success", true);
        resp.put("formats", list);
        return JsonUtil.toJson(resp);
    }

    /** 解析：{ content, format? } */
    @RequestMapping("/parse")
    @ResponseBody
    public String parse(@RequestBody Map<String, Object> req) {
        Map<String, Object> resp = new LinkedHashMap<>();
        try {
            if (req == null) throw new IllegalArgumentException("请求体为空");
            String content = (String) req.get("content");
            String format = (String) req.getOrDefault("format", "auto");
            ParseResult pr = service.parse(content, format);

            int valid = 0; long totalBytes = 0;
            for (var f : pr.getFiles()) {
                if (f.getError() == null) { valid++; totalBytes += f.getSize(); }
            }
            resp.put("success", true);
            resp.put("format", pr.getFormat());
            resp.put("formatDisplayName", pr.getFormatDisplayName());
            resp.put("pack", pr.getPack());
            resp.put("warnings", pr.getWarnings());
            resp.put("files", pr.getFiles());
            resp.put("total", pr.getFiles().size());
            resp.put("valid", valid);
            resp.put("totalBytes", totalBytes);
            return JsonUtil.toJson(resp);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            return JsonUtil.toJson(resp);
        }
    }

    /** 导入：{ content, format?, targetRoot, conflict?, selectedPaths? } */
    @RequestMapping("/import")
    @ResponseBody
    @SuppressWarnings("unchecked")
    public String importFiles(@RequestBody Map<String, Object> req) {
        Map<String, Object> resp = new LinkedHashMap<>();
        try {
            if (req == null) throw new IllegalArgumentException("请求体为空");
            String content = (String) req.get("content");
            String format = (String) req.getOrDefault("format", "auto");
            String targetRoot = (String) req.get("targetRoot");
            String conflict = (String) req.getOrDefault("conflict", "OVERWRITE");
            List<String> selected = null;
            Object sp = req.get("selectedPaths");
            if (sp instanceof List) {
                selected = new ArrayList<>();
                for (Object o : (List<Object>) sp) selected.add(String.valueOf(o));
            }
            ImportResult result = service.importFiles(content, format, targetRoot, conflict, selected);
            resp.put("success", true);
            resp.put("result", result);
            return JsonUtil.toJson(resp);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            return JsonUtil.toJson(resp);
        }
    }
}