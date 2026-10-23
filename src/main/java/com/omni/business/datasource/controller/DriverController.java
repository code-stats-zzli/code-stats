package com.omni.business.datasource.controller;

import com.omni.business.datasource.service.DriverJarService;
import com.omni.common.datasource.driver.DriverJar;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.spring.mvc.FileItem;
import com.omni.framework.spring.mvc.MultipartResolver;
import com.omni.framework.spring.mvc.RequestContextHolder;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.util.JsonUtil;

import java.util.Map;

@Controller
@RequestMapping("/driver/api")
public class DriverController {

    private static final Logger log = LoggerFactory.getLogger(DriverController.class);

    @Autowired
    private DriverJarService driverService;

    @RequestMapping("/list")
    @ResponseBody
    public String list() {
        try {
            return JsonUtil.toJson(driverService.listAll());
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

    /**
     * ★ 修复：包 try-catch，异常时返回 JSON 而不是让 DispatcherServlet 抛 500 HTML
     */
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

            String name = multipart.getParameter("name");
            if (name == null || name.trim().isEmpty()) {
                return error("驱动名称 name 不能为空");
            }
            String fileName = fileItem.getFileName();
            if (fileName == null || fileName.trim().isEmpty()) {
                return error("文件名不能为空");
            }

            byte[] data = fileItem.getBytes();
            if (data == null || data.length == 0) {
                return error("文件内容为空");
            }

            DriverJar driver = new DriverJar();
            driver.setName(name.trim());
            driver.setFileName(fileName.trim());
            driver.setJarData(data);

            boolean ok = driverService.save(driver);
            if (!ok) return error("保存失败（数据库影响行数为 0）");

            log.info("驱动上传成功: name={}, file={}, size={} bytes",
                    name, fileName, data.length);
            return JsonUtil.toJson(Map.of("success", true));
        } catch (Exception e) {
            log.error("驱动上传失败", e);
            return error("驱动上传失败: " + rootCauseMessage(e));
        }
    }

    /**
     * ★ 修复：同上
     */
    @RequestMapping("/update")
    @ResponseBody
    public String update() {
        try {
            HttpServletRequest req = RequestContextHolder.getRequest();
            if (req == null) return error("无法获取请求对象");

            MultipartResolver multipart = req.getMultipartResolver();
            if (multipart == null) return error("不支持 multipart 或请求体解析失败");

            String id = multipart.getParameter("id");
            String name = multipart.getParameter("name");
            if (id == null || id.trim().isEmpty()) return error("id 不能为空");
            if (name == null || name.trim().isEmpty()) return error("驱动名称 name 不能为空");

            FileItem fileItem = multipart.getFileItem("file");

            DriverJar driver = new DriverJar();
            driver.setId(id.trim());
            driver.setName(name.trim());
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

    @RequestMapping("/download")
    @ResponseBody
    public void download(String id) {
        try {
            DriverJar driver = driverService.getById(id);
            if (driver == null) {
                throw new RuntimeException("驱动不存在: " + id);
            }
            var resp = com.omni.framework.spring.mvc.ResponseContextHolder.getResponse();
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + driver.getFileName() + "\"");
            resp.setContentType("application/java-archive");
            resp.getRawOutputStream().write(driver.getJarData());
            resp.send();
        } catch (Exception e) {
            log.error("下载驱动失败, id={}", id, e);
        }
    }

    // ==================== 工具 ====================

    private String error(String msg) {
        return JsonUtil.toJson(Map.of("success", false, "error", msg));
    }

    /**
     * 从异常链中取出最底层的信息，避免前端只看到 "Update failed: INSERT ..." 这种外壳
     */
    private String rootCauseMessage(Throwable e) {
        if (e == null) return "未知错误";
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return cur.getClass().getSimpleName() + ": " + (msg == null ? "(no message)" : msg);
    }
}