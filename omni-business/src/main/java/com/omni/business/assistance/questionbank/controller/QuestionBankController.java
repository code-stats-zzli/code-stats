package com.omni.business.assistance.questionbank.controller;

import com.omni.business.assistance.questionbank.service.QuestionBankService;
import com.omni.business.common.util.BaseController;
import com.omni.business.common.util.PagingSupport;
import com.omni.business.common.util.ResponseHelper;
import com.omni.framework.spring.annotation.*;
import com.omni.framework.tomcat.servlet.HttpServletResponse;
import com.omni.framework.util.JsonUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** ★ L1-01 + L1-02 + L2-01 */
@Controller
@RequestMapping("/questionbank/api")
public class QuestionBankController extends BaseController {

    @Autowired
    private QuestionBankService service;

    @RequestMapping("/config")
    @ResponseBody
    public String getDefaultConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("rootDir", service.getDefaultRootDir());
        config.put("outputFile", service.getDefaultOutputFile());
        config.put("keyword", service.getDefaultKeyword());
        config.put("namePrefixes", service.getDefaultNamePrefixes());
        return JsonUtil.toJson(config);
    }

    @RequestMapping("/scan")
    @ResponseBody
    public String scanFiles(@RequestParam("rootDir") String rootDir,
                            @RequestParam(value = "keyword", required = false) String keyword,
                            @RequestParam(value = "namePrefixes", required = false) String namePrefixes) {
        try {
            List<String> files = service.scanExcelFiles(rootDir, keyword, namePrefixes);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("files", files);
            return okFlat(r);
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @RequestMapping("/merge")
    @ResponseBody
    public String mergeFiles(@RequestBody Map<String, String> req) {
        try {
            Map<String, Object> mergeResult = service.mergeFiles(
                    req.get("rootDir"), req.get("keyword"), req.get("namePrefixes"), req.get("outputFile"));
            return okFlat(mergeResult);
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @RequestMapping("/search")
    @ResponseBody
    public String search(@RequestParam("keyword") String keyword) {
        try {
            Map<String, Object> r = service.searchByKeyword(keyword);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("columns", r.get("columns"));
            out.put("row", r.get("row"));
            return okFlat(out);
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    /** ★ L1-02：使用 PagingSupport */
    @RequestMapping("/preview")
    @ResponseBody
    public String previewTable(String keyword) {
        try {
            PagingSupport.Page pg = PagingSupport.fromRequest(20, 500);
            Map<String, Object> data = service.getPreviewData(keyword, pg.getPage(), pg.getPageSize());
            return okFlat(data);
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @RequestMapping("/download")
    public void downloadMergedFile(@RequestParam("file") String file) throws Exception {
        HttpServletResponse resp = com.omni.framework.spring.mvc.ResponseContextHolder.getResponse();
        if (resp == null) return;
        String decodedPath = java.net.URLDecoder.decode(file, java.nio.charset.StandardCharsets.UTF_8.name());
        Path path = Paths.get(decodedPath).normalize();
        if (path.toString().contains("..")) { ResponseHelper.writeError(resp, 403, "Access denied"); return; }
        if (!Files.exists(path) || Files.isDirectory(path)) {
            ResponseHelper.writeError(resp, 404, "File not found: " + path);
            return;
        }
        ResponseHelper.writeTextAttachment(resp, path, path.getFileName().toString());
    }

    @RequestMapping("/exportExcel")
    public void exportExcel(String keyword) throws Exception {
        HttpServletResponse response = com.omni.framework.spring.mvc.ResponseContextHolder.getResponse();
        if (response == null) return;
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            String fileName = "题库数据_" + System.currentTimeMillis() + ".xlsx";
            String encodedFileName = java.net.URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);
            service.exportToExcel(keyword, response.getRawOutputStream());
        } catch (Exception e) {
            ResponseHelper.writeError(response, 500, "导出失败：" + e.getMessage());
        }
    }
}