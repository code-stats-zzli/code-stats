package com.omni.business.assistance.sys.controller;

import com.omni.business.assistance.sys.model.RemoteFile;
import com.omni.business.assistance.sys.model.SshConnection;
import com.omni.business.assistance.sys.service.SshConnectionService;
import com.omni.business.common.util.BaseController;
import com.omni.business.common.util.ResponseHelper;
import com.omni.common.util.StringUtils;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestBody;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.spring.mvc.FileItem;
import com.omni.framework.spring.mvc.MultipartResolver;
import com.omni.framework.spring.mvc.RequestContextHolder;
import com.omni.framework.spring.mvc.ResponseContextHolder;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.tomcat.servlet.HttpServletResponse;
import com.omni.framework.util.JsonUtil;
import com.omni.framework.util.ParamUtils;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** ★ L1-01 + L1-05 + L2-01 */
@Controller
@RequestMapping("/sys/api/ssh")
public class SshConnectionController extends BaseController {

    @Autowired
    private SshConnectionService service;

    @RequestMapping("/connections")
    @ResponseBody
    public String connections() { return ok(service.list()); }

    @RequestMapping("/connection/save")
    @ResponseBody
    public String save(@RequestBody SshConnection req) {
        try {
            if (req == null) return error("请求体为空");
            if (StringUtils.isBlank(req.getName())) return error("名称不能为空");
            if (StringUtils.isBlank(req.getHost())) return error("host 不能为空");
            if (StringUtils.isBlank(req.getUsername())) return error("username 不能为空");
            if (req.getPort() <= 0) req.setPort(22);
            req.setHost(req.getHost().trim());
            req.setUsername(req.getUsername().trim());
            req.setName(req.getName().trim());
            return ok(service.save(req));
        } catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/connection/delete")
    @ResponseBody
    public String deleteConnection(@RequestBody Map<String, Object> body) {
        try {
            String id = str(body, "id");
            if (id == null) return error("缺少 id");
            service.delete(id);
            return ok();
        } catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/connection/test")
    @ResponseBody
    public String test(@RequestBody Map<String, Object> body) {
        try {
            String id = str(body, "id");
            if (id == null) return error("缺少 id");
            return ok(service.test(id));
        } catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/connection/close")
    @ResponseBody
    public String close(@RequestBody Map<String, Object> body) {
        try {
            String id = str(body, "id");
            if (id == null) return error("缺少 id");
            service.closeSession(id);
            return ok();
        } catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/exec")
    @ResponseBody
    public String exec(@RequestBody Map<String, Object> body) {
        try {
            String id = str(body, "id"), cmd = str(body, "cmd");
            if (id == null) return error("缺少 id");
            return ok(service.exec(id, cmd));
        } catch (SecurityException e) { return error(e.getMessage()); }
        catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/list")
    @ResponseBody
    public String list(@RequestBody Map<String, Object> body) {
        try {
            String id = str(body, "id"), path = str(body, "path");
            if (id == null) return error("缺少 id");
            List<RemoteFile> list = service.listDir(id, path);
            return ok(list);
        } catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/read")
    @ResponseBody
    public String read(@RequestBody Map<String, Object> body) {
        try {
            String id = str(body, "id"), path = str(body, "path");
            if (id == null || path == null) return error("缺少参数");
            String content = service.readFile(id, path);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("path", path);
            r.put("content", content);
            return ok(r);
        } catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/write")
    @ResponseBody
    public String write(@RequestBody Map<String, Object> body) {
        try {
            String id = str(body, "id"), path = str(body, "path"), b64 = str(body, "content");
            if (id == null || path == null) return error("缺少参数");
            byte[] data;
            try { data = b64 == null ? new byte[0] : Base64.getDecoder().decode(b64); }
            catch (IllegalArgumentException e) { return error("Base64 解码失败: " + e.getMessage()); }
            service.writeFile(id, path, data);
            return ok();
        } catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/upload")
    @ResponseBody
    public String upload() {
        try {
            HttpServletRequest req = RequestContextHolder.getRequest();
            if (req == null) return error("无法获取请求对象");
            MultipartResolver mp = req.getMultipartResolver();
            if (mp == null) return error("请求不是 multipart");
            String id = mp.getParameter("id"), dirPath = mp.getParameter("path");
            FileItem fileItem = mp.getFileItem("file");
            if (id == null) return error("缺少 id");
            if (fileItem == null) return error("缺少 file 字段");
            String fileName = fileItem.getFileName();
            if (fileName == null || fileName.isEmpty()) return error("文件名为空");
            SshConnection c = service.get(id);
            if (c == null) return error("连接不存在");
            String sep = "windows".equalsIgnoreCase(c.getOsType()) ? "\\" : "/";
            String target;
            if (dirPath == null || dirPath.isEmpty() || ".".equals(dirPath)) target = fileName;
            else if (dirPath.endsWith("/") || dirPath.endsWith("\\")) target = dirPath + fileName;
            else target = dirPath + sep + fileName;
            byte[] data = fileItem.getBytes();
            service.writeFile(id, target, data);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("path", target);
            r.put("size", data.length);
            return ok(r);
        } catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/download")
    public void download() throws Exception {
        HttpServletResponse resp = ResponseContextHolder.getResponse();
        if (resp == null) return;
        Map<String, String> params = ParamUtils.parseQueryParams();
        String id = params.get("id"), path = params.get("path");
        if (id == null || path == null) {
            ResponseHelper.writeError(resp, 400, "缺少 id 或 path");
            return;
        }
        try {
            byte[] data = service.download(id, path);
            String name = basename(path);
            if (name == null || name.isEmpty()) name = "download";
            ResponseHelper.writeAttachment(resp, data, name, "application/octet-stream");
        } catch (Exception e) {
            ResponseHelper.writeError(resp, 500, "下载失败: " + e.getMessage());
        }
    }

    @RequestMapping("/delete")
    @ResponseBody
    public String deleteFile(@RequestBody Map<String, Object> body) {
        try {
            String id = str(body, "id"), path = str(body, "path");
            if (id == null || path == null) return error("缺少参数");
            service.delete(id, path);
            return ok();
        } catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/rename")
    @ResponseBody
    public String rename(@RequestBody Map<String, Object> body) {
        try {
            String id = str(body, "id"), from = str(body, "from"), to = str(body, "to");
            if (id == null || from == null || to == null) return error("缺少参数");
            service.rename(id, from, to);
            return ok();
        } catch (Exception e) { return error(e.getMessage()); }
    }

    @RequestMapping("/mkdir")
    @ResponseBody
    public String mkdir(@RequestBody Map<String, Object> body) {
        try {
            String id = str(body, "id"), parent = str(body, "parent"), name = str(body, "name");
            if (id == null || name == null) return error("缺少参数");
            service.mkdir(id, parent == null ? "." : parent, name);
            return ok();
        } catch (Exception e) { return error(e.getMessage()); }
    }

    private static String str(Map<String, Object> body, String key) {
        if (body == null) return null;
        return StringUtils.safe(body.get(key));
    }

    private static String basename(String p) {
        if (p == null) return null;
        int i = p.lastIndexOf('/'), j = p.lastIndexOf('\\');
        int k = Math.max(i, j);
        return k >= 0 ? p.substring(k + 1) : p;
    }
}