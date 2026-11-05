package com.omni.business.assistance.sys.controller;

import com.omni.business.assistance.sys.model.RemoteFile;
import com.omni.business.assistance.sys.model.SshConnection;
import com.omni.business.assistance.sys.service.SshConnectionService;
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

import java.net.URLEncoder;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SSH 远程管理 REST 接口。
 *
 * <pre>
 * GET  /sys/api/ssh/connections             → 列表
 * POST /sys/api/ssh/connection/save         → 保存（body=SshConnection，密码进缓存不入库）
 * POST /sys/api/ssh/connection/delete       → 删除 {id}
 * POST /sys/api/ssh/connection/test         → 测试 {id}
 * POST /sys/api/ssh/connection/close        → 页面关闭清理密码缓存 {id}
 * POST /sys/api/ssh/exec                    → 执行 {id, cmd}
 * POST /sys/api/ssh/list                    → 目录 {id, path}  → List&lt;RemoteFile&gt;
 * POST /sys/api/ssh/read                    → 读文件 {id, path} → {path, content}
 * POST /sys/api/ssh/write                   → 写文件 {id, path, content(base64)}
 * POST /sys/api/ssh/upload                  → 上传 multipart(file, id, path)
 * GET  /sys/api/ssh/download?id=&path=      → 下载流
 * POST /sys/api/ssh/delete                  → 删除 {id, path}
 * POST /sys/api/ssh/rename                  → 重命名 {id, from, to}
 * POST /sys/api/ssh/mkdir                   → 新建 {id, parent, name}
 * </pre>
 */
@Controller
@RequestMapping("/sys/api/ssh")
public class SshConnectionController {

    @Autowired
    private SshConnectionService service;

    // ==================== 连接管理 ====================

    @RequestMapping("/connections")
    @ResponseBody
    public String connections() {
        return JsonUtil.toJson(ok(service.list()));
    }

    @RequestMapping("/connection/save")
    @ResponseBody
    public String save(@RequestBody SshConnection req) {
        try {
            if (req == null) return error("请求体为空");
            if (req.getName() == null || req.getName().trim().isEmpty()) return error("名称不能为空");
            if (req.getHost() == null || req.getHost().trim().isEmpty()) return error("host 不能为空");
            if (req.getUsername() == null || req.getUsername().trim().isEmpty()) return error("username 不能为空");
            if (req.getPort() <= 0) req.setPort(22);
            req.setHost(req.getHost().trim());
            req.setUsername(req.getUsername().trim());
            req.setName(req.getName().trim());
            return JsonUtil.toJson(ok(service.save(req)));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @RequestMapping("/connection/delete")
    @ResponseBody
    public String deleteConnection(@RequestBody Map<String, Object> body) {
        try {
            String id = str(body, "id");
            if (id == null) return error("缺少 id");
            service.delete(id);
            return JsonUtil.toJson(ok(null));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @RequestMapping("/connection/test")
    @ResponseBody
    public String test(@RequestBody Map<String, Object> body) {
        try {
            String id = str(body, "id");
            if (id == null) return error("缺少 id");
            return JsonUtil.toJson(ok(service.test(id)));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    /** 页面关闭时清理密码缓存 */
    @RequestMapping("/connection/close")
    @ResponseBody
    public String close(@RequestBody Map<String, Object> body) {
        try {
            String id = str(body, "id");
            if (id == null) return error("缺少 id");
            service.closeSession(id);
            return JsonUtil.toJson(ok(null));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    // ==================== 命令执行 ====================

    @RequestMapping("/exec")
    @ResponseBody
    public String exec(@RequestBody Map<String, Object> body) {
        try {
            String id = str(body, "id");
            String cmd = str(body, "cmd");
            if (id == null) return error("缺少 id");
            return JsonUtil.toJson(ok(service.exec(id, cmd)));
        } catch (SecurityException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    // ==================== 目录浏览 ====================

    @RequestMapping("/list")
    @ResponseBody
    public String list(@RequestBody Map<String, Object> body) {
        try {
            String id = str(body, "id");
            String path = str(body, "path");
            if (id == null) return error("缺少 id");
            List<RemoteFile> list = service.listDir(id, path);
            return JsonUtil.toJson(ok(list));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    // ==================== 读文件 ====================

    @RequestMapping("/read")
    @ResponseBody
    public String read(@RequestBody Map<String, Object> body) {
        try {
            String id = str(body, "id");
            String path = str(body, "path");
            if (id == null || path == null) return error("缺少参数");
            String content = service.readFile(id, path);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("path", path);
            r.put("content", content);
            return JsonUtil.toJson(ok(r));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    // ==================== 写文件（base64） ====================

    @RequestMapping("/write")
    @ResponseBody
    public String write(@RequestBody Map<String, Object> body) {
        try {
            String id = str(body, "id");
            String path = str(body, "path");
            String b64 = str(body, "content");
            if (id == null || path == null) return error("缺少参数");
            byte[] data;
            try {
                data = b64 == null ? new byte[0] : Base64.getDecoder().decode(b64);
            } catch (IllegalArgumentException e) {
                return error("Base64 解码失败: " + e.getMessage());
            }
            service.writeFile(id, path, data);
            return JsonUtil.toJson(ok(null));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    // ==================== 上传 multipart ====================

    @RequestMapping("/upload")
    @ResponseBody
    public String upload() {
        try {
            HttpServletRequest req = RequestContextHolder.getRequest();
            if (req == null) return error("无法获取请求对象");
            MultipartResolver mp = req.getMultipartResolver();
            if (mp == null) return error("请求不是 multipart");

            String id = mp.getParameter("id");
            String dirPath = mp.getParameter("path");
            FileItem fileItem = mp.getFileItem("file");
            if (id == null) return error("缺少 id");
            if (fileItem == null) return error("缺少 file 字段");
            String fileName = fileItem.getFileName();
            if (fileName == null || fileName.isEmpty()) return error("文件名为空");

            SshConnection c = service.get(id);
            if (c == null) return error("连接不存在");
            String sep = "windows".equalsIgnoreCase(c.getOsType()) ? "\\" : "/";
            String target;
            if (dirPath == null || dirPath.isEmpty() || ".".equals(dirPath)) {
                target = fileName;
            } else if (dirPath.endsWith("/") || dirPath.endsWith("\\")) {
                target = dirPath + fileName;
            } else {
                target = dirPath + sep + fileName;
            }

            byte[] data = fileItem.getBytes();
            service.writeFile(id, target, data);

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("path", target);
            r.put("size", data.length);
            return JsonUtil.toJson(ok(r));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    // ==================== 下载 ====================

    @RequestMapping("/download")
    public void download() throws Exception {
        HttpServletResponse resp = ResponseContextHolder.getResponse();
        if (resp == null) return;
        Map<String, String> params = ParamUtils.parseQueryParams();
        String id = params.get("id");
        String path = params.get("path");
        if (id == null || path == null) {
            resp.setStatus(400, "Bad Request");
            resp.write("缺少 id 或 path");
            resp.send();
            return;
        }
        try {
            byte[] data = service.download(id, path);
            String name = basename(path);
            if (name == null || name.isEmpty()) name = "download";
            String encoded = URLEncoder.encode(name, "UTF-8").replaceAll("\\+", "%20");
            resp.setHeader("Content-Disposition",
                    "attachment; filename=\"" + name + "\"; filename*=UTF-8''" + encoded);
            resp.setContentType("application/octet-stream");
            resp.getRawOutputStream().write(data);
            resp.getRawOutputStream().flush();
        } catch (Exception e) {
            resp.setStatus(500, "Internal Server Error");
            resp.write("下载失败: " + e.getMessage());
            resp.send();
        }
    }

    // ==================== 删除 / 重命名 / 新建目录 ====================

    @RequestMapping("/delete")
    @ResponseBody
    public String deleteFile(@RequestBody Map<String, Object> body) {
        try {
            String id = str(body, "id");
            String path = str(body, "path");
            if (id == null || path == null) return error("缺少参数");
            service.delete(id, path);
            return JsonUtil.toJson(ok(null));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @RequestMapping("/rename")
    @ResponseBody
    public String rename(@RequestBody Map<String, Object> body) {
        try {
            String id = str(body, "id");
            String from = str(body, "from");
            String to = str(body, "to");
            if (id == null || from == null || to == null) return error("缺少参数");
            service.rename(id, from, to);
            return JsonUtil.toJson(ok(null));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @RequestMapping("/mkdir")
    @ResponseBody
    public String mkdir(@RequestBody Map<String, Object> body) {
        try {
            String id = str(body, "id");
            String parent = str(body, "parent");
            String name = str(body, "name");
            if (id == null || name == null) return error("缺少参数");
            service.mkdir(id, parent == null ? "." : parent, name);
            return JsonUtil.toJson(ok(null));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    // ==================== 工具 ====================

    private static String str(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object v = body.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static String basename(String p) {
        if (p == null) return null;
        int i = p.lastIndexOf('/');
        int j = p.lastIndexOf('\\');
        int k = Math.max(i, j);
        return k >= 0 ? p.substring(k + 1) : p;
    }

    private Map<String, Object> ok(Object data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        if (data != null) m.put("data", data);
        return m;
    }

    private String error(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("error", msg == null ? "未知错误" : msg);
        return JsonUtil.toJson(m);
    }
}