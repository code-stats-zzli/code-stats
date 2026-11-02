package com.omni.business.datasource.controller;

import com.omni.business.datasource.service.DriverJarService;
import com.omni.common.datasource.driver.DriverJar;
import com.omni.common.util.DriverFileNameParser;
import com.omni.common.util.DriverResourceScanner;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.RequestParam;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.spring.mvc.FileItem;
import com.omni.framework.spring.mvc.MultipartResolver;
import com.omni.framework.spring.mvc.RequestContextHolder;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.util.JsonUtil;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/driver/api")
public class DriverController {

    private static final Logger log = LoggerFactory.getLogger(DriverController.class);

    @Autowired
    private DriverJarService driverService;

    // ==================== 支持的数据库类型（供前端渲染下拉，可选） ====================

    @RequestMapping("/types")
    @ResponseBody
    public String types() {
        List<Map<String, String>> list = new ArrayList<>();
        String[] codes = DriverFileNameParser.ALLOWED_DB_TYPES;
        String[] labels = {"MySQL", "Oracle", "SQL Server", "PostgreSQL"};
        for (int i = 0; i < codes.length; i++) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("code", codes[i]);
            m.put("label", labels[i]);
            list.add(m);
        }
        return JsonUtil.toJson(Map.of("success", true, "types", list));
    }

    // ==================== 查询 ====================

    @RequestMapping("/list")
    @ResponseBody
    public String list(@RequestParam(value = "dbType", required = false) String dbType) {
        try {
            return JsonUtil.toJson(driverService.listByDbType(dbType));
        } catch (Exception e) {
            log.error("查询驱动列表失败", e);
            return error("查询驱动列表失败: " + rootCauseMessage(e));
        }
    }

    @RequestMapping("/delete")
    @ResponseBody
    public String delete(String id) {
        try {
            boolean ok = driverService.delete(id);
            return JsonUtil.toJson(Map.of("success", ok));
        } catch (Exception e) {
            log.error("删除驱动失败, id={}", id, e);
            return error("删除驱动失败: " + rootCauseMessage(e));
        }
    }

    // ==================== 上传（只接 dbType + version + file） ====================

    @RequestMapping("/upload")
    @ResponseBody
    public String upload() {
        try {
            HttpServletRequest req = RequestContextHolder.getRequest();
            if (req == null) return error("无法获取请求对象");
            MultipartResolver multipart = req.getMultipartResolver();
            if (multipart == null) return error("不支持 multipart 或请求体解析失败");

            FileItem fileItem = multipart.getFileItem("file");
            if (fileItem == null) return error("缺少文件字段 file");

            String fileName = fileItem.getFileName();
            if (fileName == null || fileName.trim().isEmpty()) return error("文件名不能为空");
            if (!fileName.toLowerCase().endsWith(".jar")) {
                return error("只允许上传 .jar 文件");
            }

            String dbType = multipart.getParameter("dbType");
            String version = multipart.getParameter("version");

            // 严格校验：dbType 必须是 4 类之一
            if (dbType == null || dbType.trim().isEmpty()) {
                return error("数据库类型 dbType 不能为空");
            }
            if (!DriverFileNameParser.isAllowedDbType(dbType)) {
                return error("不支持的数据库类型: " + dbType
                        + "（仅支持 " + String.join("/", DriverFileNameParser.ALLOWED_DB_TYPES) + "）");
            }
            // 版本必填
            if (version == null || version.trim().isEmpty()) {
                return error("驱动版本 version 不能为空");
            }
            if (version.contains("_")) {
                return error("驱动版本不能包含下划线 _（会破坏资源目录命名规则）");
            }

            byte[] data = fileItem.getBytes();
            if (data == null || data.length == 0) return error("文件内容为空");

            DriverJar driver = new DriverJar();
            driver.setDbType(DriverFileNameParser.normalizeDbType(dbType));
            driver.setName(driver.getDbType());
            driver.setVersion(version.trim());
            driver.setFileName(fileName.trim());
            driver.setJarData(data);

            boolean ok = driverService.save(driver);
            if (!ok) return error("保存失败（数据库影响行数为 0）");

            log.info("驱动上传成功: dbType={}, version={}, file={}, size={}B",
                    driver.getDbType(), driver.getVersion(), fileName, data.length);
            return JsonUtil.toJson(Map.of("success", true));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            log.error("驱动上传失败", e);
            return error("驱动上传失败: " + rootCauseMessage(e));
        }
    }

    // ==================== 更新 ====================

    @RequestMapping("/update")
    @ResponseBody
    public String update() {
        try {
            HttpServletRequest req = RequestContextHolder.getRequest();
            if (req == null) return error("无法获取请求对象");
            MultipartResolver multipart = req.getMultipartResolver();
            if (multipart == null) return error("不支持 multipart 或请求体解析失败");

            String id = multipart.getParameter("id");
            String dbType = multipart.getParameter("dbType");
            String version = multipart.getParameter("version");
            if (id == null || id.trim().isEmpty()) return error("id 不能为空");
            if (dbType == null || dbType.trim().isEmpty()) return error("数据库类型 dbType 不能为空");
            if (!DriverFileNameParser.isAllowedDbType(dbType)) {
                return error("不支持的数据库类型: " + dbType);
            }
            if (version == null || version.trim().isEmpty()) return error("驱动版本 version 不能为空");

            FileItem fileItem = multipart.getFileItem("file");
            if (fileItem != null) {
                String newFileName = fileItem.getFileName();
                if (newFileName == null || !newFileName.toLowerCase().endsWith(".jar")) {
                    return error("只允许上传 .jar 文件");
                }
            }

            DriverJar driver = new DriverJar();
            driver.setId(id.trim());
            driver.setDbType(DriverFileNameParser.normalizeDbType(dbType));
            driver.setName(driver.getDbType());
            driver.setVersion(version.trim());
            if (fileItem != null) {
                driver.setJarData(fileItem.getBytes());
                driver.setFileName(fileItem.getFileName());
            }
            boolean ok = driverService.update(driver);
            if (!ok) return error("更新失败（数据库影响行数为 0）");
            return JsonUtil.toJson(Map.of("success", true));
        } catch (Exception e) {
            log.error("更新驱动失败", e);
            return error("更新驱动失败: " + rootCauseMessage(e));
        }
    }

    // ==================== 下载 ====================

    @RequestMapping("/download")
    @ResponseBody
    public void download(String id) {
        try {
            DriverJar driver = driverService.getById(id);
            if (driver == null) throw new RuntimeException("驱动不存在: " + id);
            var resp = com.omni.framework.spring.mvc.ResponseContextHolder.getResponse();
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + driver.getFileName() + "\"");
            resp.setContentType("application/java-archive");
            resp.getRawOutputStream().write(driver.getJarData());
            resp.send();
        } catch (Exception e) {
            log.error("下载驱动失败, id={}", id, e);
        }
    }

    // ==================== 资源目录接口 ====================

    @RequestMapping("/resource/list")
    @ResponseBody
    public String resourceList() {
        try {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", true);
            out.put("dir", DriverResourceScanner.getDriversDir().toString());
            List<DriverResourceScanner.DriverFile> files = DriverResourceScanner.scan();
            List<Map<String, Object>> items = new ArrayList<>(files.size());
            for (DriverResourceScanner.DriverFile df : files) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("dbType", df.dbType);
                m.put("version", df.version);
                m.put("fileName", df.originalFileName);
                m.put("path", df.jarPath.toString());
                try { m.put("size", Files.size(df.jarPath)); } catch (Exception ignored) {}
                items.add(m);
            }
            out.put("items", items);
            return JsonUtil.toJson(out);
        } catch (Exception e) {
            return error("扫描资源目录失败: " + rootCauseMessage(e));
        }
    }

    @RequestMapping("/resource/sync")
    @ResponseBody
    public String resourceSync(@RequestParam(value = "dbType", required = false) String dbType) {
        try {
            List<DriverJar> imported = driverService.importFromResourceDir(dbType);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", true);
            out.put("imported", imported.size());
            out.put("items", imported);
            return JsonUtil.toJson(out);
        } catch (Exception e) {
            log.error("资源目录导入失败", e);
            return error("资源目录导入失败: " + rootCauseMessage(e));
        }
    }

    // ==================== 工具 ====================

    private String error(String msg) {
        return JsonUtil.toJson(Map.of("success", false, "error", msg));
    }

    private String rootCauseMessage(Throwable e) {
        if (e == null) return "未知错误";
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        String msg = cur.getMessage();
        return cur.getClass().getSimpleName() + ": " + (msg == null ? "(no message)" : msg);
    }
}