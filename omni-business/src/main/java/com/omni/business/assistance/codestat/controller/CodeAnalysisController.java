package com.omni.business.assistance.codestat.controller;

import com.omni.business.assistance.codestat.service.CodeAnalysisService;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.spring.mvc.ResponseContextHolder;
import com.omni.framework.tomcat.servlet.HttpServletResponse;
import com.omni.framework.util.JsonUtil;
import com.omni.framework.util.ParamUtils;

import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Base64;

@Controller
@RequestMapping("/code/api")
public class CodeAnalysisController {
    private static final Logger log = LoggerFactory.getLogger(CodeAnalysisController.class);

    @Autowired
    private CodeAnalysisService codeAnalysisService;

    /** 合并文件常见扩展名：宽容放行只认这些类型 */
    private static final Set<String> MERGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".txt", ".log", ".csv", ".json", ".md"
    ));
    /** 宽容模式的时间窗口：24 小时 */
    private static final long GRACE_WINDOW_MS = 24L * 3600 * 1000;

    private static Path[] allowedRoots() {
        String extra = com.omni.framework.spring.config.ConfigLoader
                .getProperty("code.analysis.download.allowed-roots", "");
        List<Path> roots = new ArrayList<>();
        roots.add(Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize());
        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp != null && !tmp.isEmpty()) {
            roots.add(Paths.get(tmp).toAbsolutePath().normalize());
        }
        if (!extra.isEmpty()) {
            for (String s : extra.split(",")) {
                if (!s.trim().isEmpty()) {
                    roots.add(Paths.get(s.trim()).toAbsolutePath().normalize());
                }
            }
        }
        return roots.toArray(new Path[0]);
    }

    @RequestMapping("/config")
    @ResponseBody
    public String getDefaultConfig() {
        return codeAnalysisService.getDefaultConfigJson();
    }

    // ==================== ★ 新增：模块列表 ====================

    /**
     * 获取 rootDir 下的模块列表（一级子目录统计），供前端"模块过滤"选择。
     * GET /code/api/modules?rootDir=...&extensions=java&excludePaths=...&useGitIgnore=...
     */
    @RequestMapping("/modules")
    @ResponseBody
    public String getModules() {
        try {
            List<Map<String, Object>> modules = codeAnalysisService.getModules(ParamUtils.parseQueryParams());
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("success", true);
            res.put("modules", modules);
            return JsonUtil.toJson(res);
        } catch (Exception e) {
            log.error("获取模块列表失败", e);
            return errorResponse("获取模块列表失败: " + e.getMessage());
        }
    }

    @RequestMapping("/analyze")
    @ResponseBody
    public String analyze() {
        return codeAnalysisService.analyze(ParamUtils.parseQueryParams());
    }

    @RequestMapping("/dependencies")
    @ResponseBody
    public String getDependencies() {
        return codeAnalysisService.analyzeDependencies(ParamUtils.parseQueryParams());
    }

    @RequestMapping("/dependencies/outgoing")
    @ResponseBody
    public String getOutgoingDependencies() {
        Map<String, String> params = ParamUtils.parseQueryParams();
        String className = params.get("className");
        if (className == null || className.trim().isEmpty()) {
            return errorResponse("缺少 className 参数");
        }
        try {
            return JsonUtil.toJson(codeAnalysisService.getDirectDependencies(className, params));
        } catch (Exception e) {
            return errorResponse(e.getMessage());
        }
    }

    @RequestMapping("/dependencies/incoming")
    @ResponseBody
    public String getIncomingDependencies() {
        Map<String, String> params = ParamUtils.parseQueryParams();
        String className = params.get("className");
        if (className == null || className.trim().isEmpty()) {
            return errorResponse("缺少 className 参数");
        }
        try {
            return JsonUtil.toJson(codeAnalysisService.getInverseDependencies(className, params));
        } catch (Exception e) {
            return errorResponse(e.getMessage());
        }
    }

    /**
     * 下载合并文件。校验顺序：
     *   1) 归一化路径
     *   2) 在 CodeAnalysisService 注册表 → 放行
     *   3) 在白名单根目录内 → 放行
     *   4) ★ 宽容模式：文件存在 + 扩展名在 MERGE_EXTENSIONS + 24h 内修改过 → 放行并注册
     *   5) 否则 403（带可操作提示）
     */
    @RequestMapping("/download")
    public void downloadFile() throws Exception {
        HttpServletResponse response = ResponseContextHolder.getResponse();
        if (response == null) {
            throw new RuntimeException("No response available for download");
        }

        Map<String, String> params = ParamUtils.parseQueryParams();
        String encodedPath = params.get("path");
        if (encodedPath == null || encodedPath.trim().isEmpty()) {
            encodedPath = params.get("filePath");
        }
        if (encodedPath == null || encodedPath.trim().isEmpty()) {
            writeError(response, 400, "Bad Request", "Missing parameter: path or filePath");
            return;
        }

        String decodedPath;
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(encodedPath);
            decodedPath = new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            writeError(response, 400, "Bad Request", "Invalid Base64 encoding for path parameter");
            return;
        }

        String rawPath;
        try {
            rawPath = URLDecoder.decode(decodedPath, "UTF-8");
        } catch (Exception e) {
            writeError(response, 400, "Bad Request", "Invalid URL encoding");
            return;
        }

        Path requestedPath;
        try {
            requestedPath = Paths.get(rawPath).toAbsolutePath().normalize();
        } catch (Exception e) {
            writeError(response, 400, "Bad Request", "Invalid path: " + e.getMessage());
            return;
        }

        // 1) 注册表
        boolean allowed = CodeAnalysisService.isDownloadable(requestedPath);

        // 2) 白名单
        if (!allowed) {
            for (Path root : allowedRoots()) {
                if (requestedPath.startsWith(root)) {
                    allowed = true;
                    break;
                }
            }
        }

        // 3) ★ 宽容模式
        if (!allowed && isRecentMergeFile(requestedPath)) {
            allowed = true;
            try {
                CodeAnalysisService.registerDownloadable(requestedPath);
            } catch (Exception ignored) {}
            log.warn("[download] 宽容放行最近生成的合并文件（建议配置 code.analysis.download.allowed-roots）: {}",
                    requestedPath);
        }

        if (!allowed) {
            log.warn("[download] 403 拒绝: path={}, user.dir={}, tmpdir={}",
                    requestedPath,
                    System.getProperty("user.dir"),
                    System.getProperty("java.io.tmpdir"));
            writeError(response, 403, "Forbidden",
                    "Access denied: " + requestedPath
                            + " 既不在下载注册表，也不在白名单 (user.dir / tmpdir / configured roots)，"
                            + "也不是最近 24h 生成的合并文件 (.txt/.log/.csv/.json/.md)。\n"
                            + "修复方式：\n"
                            + "  A) 重新点一次「开始分析」，让该路径被注册；\n"
                            + "  B) 在 application.properties 配置 code.analysis.download.allowed-roots="
                            + requestedPath.getParent() + " 后重启；\n"
                            + "  C) 把合并文件输出到 user.dir（" + System.getProperty("user.dir") + "）下的子目录。");
            return;
        }

        if (!Files.exists(requestedPath) || Files.isDirectory(requestedPath)) {
            writeError(response, 404, "Not Found", "File not found: " + requestedPath);
            return;
        }

        long fileSize;
        try {
            fileSize = Files.size(requestedPath);
        } catch (Exception e) {
            writeError(response, 500, "Internal Server Error", "Cannot stat file: " + e.getMessage());
            return;
        }

        String fileName = requestedPath.getFileName().toString();
        String addTimestampParam = params.get("addTimestamp");
        if ("true".equalsIgnoreCase(addTimestampParam)) {
            String baseName = fileName;
            String ext = "";
            int dotIdx = fileName.lastIndexOf('.');
            if (dotIdx > 0) {
                baseName = fileName.substring(0, dotIdx);
                ext = fileName.substring(dotIdx);
            }
            String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            fileName = baseName + "_" + timestamp + ext;
        }

        String encodedFileName;
        try {
            encodedFileName = java.net.URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");
        } catch (Exception e) {
            encodedFileName = fileName;
        }
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encodedFileName);
        response.setContentType("application/octet-stream");

        try (InputStream in = Files.newInputStream(requestedPath)) {
            response.sendStatic(in, "application/octet-stream", fileSize);
        }
    }

    /**
     * 判断是否「最近生成的合并文件」：
     *   - 是普通文件
     *   - 扩展名在 MERGE_EXTENSIONS
     *   - 修改时间在最近 GRACE_WINDOW_MS 内（允许未来时间 1 分钟内的小误差）
     */
    private boolean isRecentMergeFile(Path p) {
        if (p == null || !Files.exists(p) || Files.isDirectory(p)) return false;
        String name = p.getFileName().toString().toLowerCase();
        boolean extOk = false;
        for (String ext : MERGE_EXTENSIONS) {
            if (name.endsWith(ext)) { extOk = true; break; }
        }
        if (!extOk) return false;
        try {
            long ageMs = System.currentTimeMillis() - Files.getLastModifiedTime(p).toMillis();
            return ageMs >= -60_000L && ageMs <= GRACE_WINDOW_MS;
        } catch (Exception e) {
            return false;
        }
    }

    private void writeError(HttpServletResponse response, int status, String reason, String msg) throws Exception {
        response.setStatus(status, reason);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", msg);
        response.setContentType("application/json; charset=utf-8");
        response.write(JsonUtil.toJson(body));
        response.send();
    }

    private String errorResponse(String msg) {
        Map<String, Object> res = new HashMap<>();
        res.put("success", false);
        res.put("error", msg == null ? "未知错误" : msg);
        return JsonUtil.toJson(res);
    }
}