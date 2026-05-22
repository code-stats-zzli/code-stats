package com.omni.business.datasource.controller;

import com.omni.business.datasource.service.DriverJarService;
import com.omni.common.datasource.driver.DriverJar;
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

    @Autowired
    private DriverJarService driverService;

    @RequestMapping("/list")
    @ResponseBody
    public String list() throws Exception {
        return JsonUtil.toJson(driverService.listAll());
    }

    @RequestMapping("/delete")
    @ResponseBody
    public String delete(String id) throws Exception {
        boolean ok = driverService.delete(id);
        return JsonUtil.toJson(Map.of("success", ok));
    }

    @RequestMapping("/upload")
    @ResponseBody
    public String upload() throws Exception {
        HttpServletRequest req = RequestContextHolder.getRequest();
        MultipartResolver multipart = req.getMultipartResolver();
        if (multipart == null) return error("不支持 multipart");

        FileItem fileItem = multipart.getFileItem("file");
        if (fileItem == null) return error("缺少文件");

        String name = multipart.getParameter("name");
        String fileName = fileItem.getFileName();

        DriverJar driver = new DriverJar();
        driver.setName(name);
        driver.setJarData(fileItem.getBytes());
        driver.setFileName(fileName);
        boolean ok = driverService.save(driver);
        return JsonUtil.toJson(Map.of("success", ok));
    }

    @RequestMapping("/update")
    @ResponseBody
    public String update() throws Exception {
        HttpServletRequest req = RequestContextHolder.getRequest();
        MultipartResolver multipart = req.getMultipartResolver();
        if (multipart == null) return error("不支持 multipart");

        String id = multipart.getParameter("id");
        String name = multipart.getParameter("name");
        FileItem fileItem = multipart.getFileItem("file");

        DriverJar driver = new DriverJar();
        driver.setId(id);
        driver.setName(name);
        if (fileItem != null) {
            driver.setJarData(fileItem.getBytes());
            driver.setFileName(fileItem.getFileName());
        }
        boolean ok = driverService.update(driver);
        return JsonUtil.toJson(Map.of("success", ok));
    }

    @RequestMapping("/download")
    @ResponseBody
    public void download(String id) throws Exception {
        DriverJar driver = driverService.getById(id);
        if (driver == null) {
            throw new RuntimeException("驱动不存在");
        }
        var resp = com.omni.framework.spring.mvc.ResponseContextHolder.getResponse();
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + driver.getFileName() + "\"");
        resp.setContentType("application/java-archive");
        resp.getRawOutputStream().write(driver.getJarData());
        resp.send();
    }

    private String error(String msg) {
        return JsonUtil.toJson(Map.of("success", false, "error", msg));
    }
}