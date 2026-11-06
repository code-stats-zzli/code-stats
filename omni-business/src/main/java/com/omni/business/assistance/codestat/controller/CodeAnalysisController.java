package com.omni.business.assistance.codestat.controller;

import com.omni.business.assistance.codestat.service.CodeAnalysisService;
import com.omni.business.common.util.BaseController;
import com.omni.business.common.util.ResponseHelper;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.spring.config.ConfigLoader;
import com.omni.framework.spring.mvc.ResponseContextHolder;
import com.omni.framework.tomcat.servlet.HttpServletResponse;
import com.omni.framework.util.JsonUtil;
import com.omni.framework.util.ParamUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/** ★ L1-01 + L2-01 */
@Controller
@RequestMapping("/code/api")
public class CodeAnalysisController extends BaseController {
    private static final Logger log = LoggerFactory.getLogger(CodeAnalysisController.class);

    @Autowired
    private CodeAnalysisService codeAnalysisService;

    private static final Set<String> MERGE_EXTENSIONS = new HashSet<>(Arrays.asList(".txt", ".log", ".csv", ".json", ".md"));
    private static final long GRACE_WINDOW_MS = 24L * 3600 * 1000;

    private static Path[] allowedRoots() {
        String extra = ConfigLoader.getProperty("code.analysis.download.allowed-roots", "");
        List<Path> roots = new ArrayList<>();
        roots.add(Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize());
        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp != null && !tmp.isEmpty()) roots.add(Paths.get(tmp).toAbsolutePath().normalize());
        if (!extra.isEmpty()) {
            for (String s : extra.split(",")) if (!s.trim().isEmpty()) roots.add(Paths.get(s.trim()).toAbsolutePath().normalize());
        }
        return roots.toArray(new Path[0]);
    }

    @RequestMapping("/config")
    @ResponseBody
    public String getDefaultConfig() {
        return codeAnalysisService.getDefaultConfigJson();
    }

    @RequestMapping("/modules")
    @ResponseBody
    public String getModules() {
        try {
            List<Map<String, Object>> modules = codeAnalysisService.getModules(ParamUtils.parseQueryParams());
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("modules", modules);
            return okFlat(res);
        } catch (Exception e) {
            log.error("获取模块列表失败", e);
            return error("获取模块列表失败: " + e.getMessage());
        }
    }

    @RequestMapping("/analyze")
    @ResponseBody
    public String analyze() { return codeAnalysisService.analyze(ParamUtils.parseQueryParams()); }

    @RequestMapping("/dependencies")
    @ResponseBody
    public String getDependencies() { return codeAnalysisService.analyzeDependencies(ParamUtils.parseQueryParams()); }

    @RequestMapping("/dependencies/outgoing")
    @ResponseBody
    public String getOutgoingDependencies() {
        Map<String, String> params = ParamUtils.parseQueryParams();
        String className = params.get("className");
        if (className == null || className.trim().isEmpty()) return error("缺少 className 参数");
        try { return JsonUtil.toJson(codeAnalysisService.getDirectDependencies(className, params)); }
        catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/dependencies/incoming")
    @ResponseBody
    public String getIncomingDependencies() {
        Map<String, String> params = ParamUtils.parseQueryParams();
        String className = params.get("className");
        if (className == null || className.trim().isEmpty()) return error("缺少 className 参数");
        try { return JsonUtil.toJson(codeAnalysisService.getInverseDependencies(className, params)); }
        catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/download")
    public void downloadFile() throws Exception {
        HttpServletResponse response = ResponseContextHolder.getResponse();
        if (response == null) throw new RuntimeException("No response available");

        Map<String, String> params = ParamUtils.parseQueryParams();
        String encodedPath = params.get("path");
        if (encodedPath == null || encodedPath.trim().isEmpty()) encodedPath = params.get("filePath");
        if (encodedPath == null || encodedPath.trim().isEmpty()) {
            ResponseHelper.writeError(response, 400, "Missing parameter: path or filePath");
            return;
        }
        String decodedPath;
        try { decodedPath = new String(Base64.getDecoder().decode(encodedPath), StandardCharsets.UTF_8); }
        catch (IllegalArgumentException e) { ResponseHelper.writeError(response, 400, "Invalid Base64"); return; }
        String rawPath;
        try { rawPath = java.net.URLDecoder.decode(decodedPath, "UTF-8"); }
        catch (Exception e) { ResponseHelper.writeError(response, 400, "Invalid URL encoding"); return; }

        Path requestedPath;
        try { requestedPath = Paths.get(rawPath).toAbsolutePath().normalize(); }
        catch (Exception e) { ResponseHelper.writeError(response, 400, "Invalid path"); return; }

        boolean allowed = CodeAnalysisService.isDownloadable(requestedPath);
        if (!allowed) for (Path root : allowedRoots()) if (requestedPath.startsWith(root)) { allowed = true; break; }
        if (!allowed && isRecentMergeFile(requestedPath)) {
            allowed = true;
            try { CodeAnalysisService.registerDownloadable(requestedPath); } catch (Exception ignored) {}
        }
        if (!allowed) {
            ResponseHelper.writeError(response, 403, "Access denied: " + requestedPath);
            return;
        }
        if (!Files.exists(requestedPath) || Files.isDirectory(requestedPath)) {
            ResponseHelper.writeError(response, 404, "File not found: " + requestedPath);
            return;
        }
        String fileName = requestedPath.getFileName().toString();
        if ("true".equalsIgnoreCase(params.get("addTimestamp"))) {
            String baseName = fileName, ext = "";
            int dotIdx = fileName.lastIndexOf('.');
            if (dotIdx > 0) { baseName = fileName.substring(0, dotIdx); ext = fileName.substring(dotIdx); }
            String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            fileName = baseName + "_" + timestamp + ext;
        }
        ResponseHelper.writeAttachment(response, requestedPath, fileName);
    }

    private boolean isRecentMergeFile(Path p) {
        if (p == null || !Files.exists(p) || Files.isDirectory(p)) return false;
        String name = p.getFileName().toString().toLowerCase();
        boolean extOk = false;
        for (String ext : MERGE_EXTENSIONS) if (name.endsWith(ext)) { extOk = true; break; }
        if (!extOk) return false;
        try {
            long ageMs = System.currentTimeMillis() - Files.getLastModifiedTime(p).toMillis();
            return ageMs >= -60_000L && ageMs <= GRACE_WINDOW_MS;
        } catch (Exception e) { return false; }
    }
}