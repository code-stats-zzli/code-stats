package com.omni.business.assistance.importfile.service;

import com.omni.business.assistance.importfile.model.ImportFile;
import com.omni.business.assistance.importfile.model.ImportResult;
import com.omni.business.assistance.importfile.model.ParseResult;
import com.omni.business.assistance.importfile.parser.ImportFormatParser;
import com.omni.business.assistance.importfile.parser.ImportFormatRegistry;
import com.omni.common.util.FileUtils;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Service;
import com.omni.framework.spring.config.ConfigLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class ImportFileService {
    private static final Logger log = LoggerFactory.getLogger(ImportFileService.class);

    @Autowired
    private ImportFormatRegistry registry;

    /** 解析：forcedFormat 为空/auto 则自动探测 */
    public ParseResult parse(String text, String forcedFormat) throws Exception {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("内容不能为空");
        }
        ImportFormatParser parser;
        if (forcedFormat != null && !forcedFormat.isEmpty() && !"auto".equalsIgnoreCase(forcedFormat)) {
            parser = registry.find(forcedFormat);
            if (parser == null) throw new IllegalArgumentException("不支持的格式: " + forcedFormat);
        } else {
            parser = registry.detect(text);
            if (parser == null) {
                throw new IllegalArgumentException("无法识别格式，请检查内容是否符合规范（支持 file-block / omni-pack / merge-file）");
            }
        }
        return parser.parse(text);
    }

    /** 执行导入 */
    public ImportResult importFiles(String text, String forcedFormat, String targetRoot,
                                    String conflict, List<String> selectedPaths) throws Exception {
        long maxFileSize = parseLong(ConfigLoader.getProperty("import.file.max-file-size", "1048576"), 1048576L);
        int maxFiles     = parseInt (ConfigLoader.getProperty("import.file.max-files",    "1000"),     1000);

        ParseResult pr = parse(text, forcedFormat);

        // 元数据里的 conflict / targetRoot 是默认值（页面可覆盖）
        if ((conflict == null || conflict.isEmpty()) && pr.getPack() != null) {
            conflict = pr.getPack().getConflict();
        }
        if ((targetRoot == null || targetRoot.trim().isEmpty()) && pr.getPack() != null
                && pr.getPack().getTargetRoot() != null && !pr.getPack().getTargetRoot().isEmpty()) {
            targetRoot = pr.getPack().getTargetRoot();
        }
        if (conflict == null || conflict.isEmpty()) conflict = "OVERWRITE";

        Path root = resolveTargetRoot(targetRoot);
        Set<String> wanted = (selectedPaths == null || selectedPaths.isEmpty())
                ? null : new HashSet<>(selectedPaths);

        List<ImportFile> toWrite = new ArrayList<>();
        for (ImportFile f : pr.getFiles()) {
            if (f.getError() != null) continue;
            if (wanted != null && !wanted.contains(f.getPath())) continue;
            toWrite.add(f);
        }
        if (toWrite.isEmpty()) throw new IllegalArgumentException("没有可导入的文件");

        ImportResult result = new ImportResult();
        result.setTargetRoot(root.toString());
        result.setTotal(toWrite.size());
        if (toWrite.size() > maxFiles) {
            throw new IllegalArgumentException("文件数量超过限制 " + maxFiles);
        }

        for (ImportFile f : toWrite) {
            ImportResult.Item item = new ImportResult.Item();
            item.setPath(f.getPath());
            item.setSize(f.getSize());
            try {
                if (f.getSize() > maxFileSize) {
                    throw new IllegalArgumentException("文件大小超过限制 " + maxFileSize);
                }
                Path target = FileUtils.resolveSafePath(root, f.getPath());
                Path parent = target.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }
                if (Files.exists(target)) {
                    switch (conflict.toUpperCase()) {
                        case "OVERWRITE":
                            Files.write(target, f.getContent().getBytes(StandardCharsets.UTF_8));
                            item.setStatus("SUCCESS");
                            item.setMessage("已覆盖");
                            break;
                        case "RENAME": {
                            Path renamed = uniquePath(target);
                            Files.write(renamed, f.getContent().getBytes(StandardCharsets.UTF_8));
                            item.setStatus("SUCCESS");
                            item.setMessage("重命名为 " + renamed.getFileName());
                            break;
                        }
                        default:
                            item.setStatus("SKIPPED");
                            item.setMessage("已存在，跳过");
                            break;
                    }
                } else {
                    Files.write(target, f.getContent().getBytes(StandardCharsets.UTF_8));
                    item.setStatus("SUCCESS");
                    item.setMessage("写入成功");
                }
                if ("SUCCESS".equals(item.getStatus())) result.setSuccess(result.getSuccess() + 1);
                else if ("SKIPPED".equals(item.getStatus())) result.setSkipped(result.getSkipped() + 1);
                else result.setFailed(result.getFailed() + 1);
            } catch (Exception e) {
                item.setStatus("FAILED");
                item.setMessage(e.getClass().getSimpleName() + ": " + e.getMessage());
                result.setFailed(result.getFailed() + 1);
                log.error("写入失败: {}", f.getPath(), e);
            }
            result.getItems().add(item);
        }
        return result;
    }

    private Path resolveTargetRoot(String targetRoot) throws Exception {
        if (targetRoot == null || targetRoot.trim().isEmpty()) {
            throw new IllegalArgumentException("目标根目录不能为空");
        }
        Path root = Paths.get(targetRoot.trim()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("目标根目录不存在: " + root);
        }
        String cfg = ConfigLoader.getProperty("import.file.allowed-roots", "");
        List<String> allowed = new ArrayList<>();
        for (String s : cfg.split(",")) if (!s.trim().isEmpty()) allowed.add(s.trim());
        if (!allowed.isEmpty()) {
            boolean ok = false;
            for (String s : allowed) {
                Path p = Paths.get(s).toAbsolutePath().normalize();
                if (root.startsWith(p) || root.equals(p)) { ok = true; break; }
            }
            if (!ok) throw new SecurityException("目标根目录不在允许列表内: " + root);
        }
        return root;
    }

    private Path uniquePath(Path p) {
        String name = p.getFileName().toString();
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) { base = name.substring(0, dot); ext = name.substring(dot); }
        int i = 1;
        Path parent = p.getParent();
        while (true) {
            Path c = parent.resolve(base + "_" + i + ext);
            if (!Files.exists(c)) return c;
            i++;
        }
    }

    private static long parseLong(String s, long d) { try { return Long.parseLong(s.trim()); } catch (Exception e) { return d; } }
    private static int  parseInt (String s, int  d) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return d; } }
}