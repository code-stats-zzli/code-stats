package com.omni.business.assistance.directory.service;

import com.omni.common.util.FileUtils;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Service;
import com.omni.framework.spring.config.ConfigLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DirectoryService {

    private static final Logger log = LoggerFactory.getLogger(DirectoryService.class);

    public static final String ROOT_TYPE_LOCAL = "local";
    public static final String ROOT_TYPE_RESOURCES = "resources";

    public static final String RES_TYPE_IMAGES      = "images";
    public static final String RES_TYPE_PAGES       = "pages";
    public static final String RES_TYPE_SQL         = "sql";
    public static final String RES_TYPE_ATTACHMENTS = "attachments";
    public static final String RES_TYPE_OTHERS      = "others";

    private static final Map<String, String> RESOURCE_TYPES = new LinkedHashMap<>();
    static {
        RESOURCE_TYPES.put(RES_TYPE_IMAGES,      "图片");
        RESOURCE_TYPES.put(RES_TYPE_PAGES,       "导出页面");
        RESOURCE_TYPES.put(RES_TYPE_SQL,         "SQL管理");
        RESOURCE_TYPES.put(RES_TYPE_ATTACHMENTS, "附件");
        RESOURCE_TYPES.put(RES_TYPE_OTHERS,      "其他");
    }

    /** ★ 完整 32 位 UUID，兼容历史 12 位 */
    private static final String UUID_REGEX = "[0-9a-f]{12,32}";

    private final Path localRoot;
    private final Path resourcesRoot;

    public DirectoryService() {
        String localRootPath = ConfigLoader.getProperty("files.local.root", System.getProperty("user.dir"));
        this.localRoot = Paths.get(localRootPath).normalize().toAbsolutePath();
        ensureRootExists(localRoot);

        this.resourcesRoot = Paths.get(System.getProperty("user.dir"), "resources")
                .normalize().toAbsolutePath();
        ensureRootExists(resourcesRoot);
        // ★ 自查：所有资源类型目录在启动时自动创建
        for (String type : RESOURCE_TYPES.keySet()) {
            ensureRootExists(resourcesRoot.resolve(type));
        }
        log.info("资源管理根目录已就绪: {}", resourcesRoot);
    }

    private void ensureRootExists(Path root) {
        if (!Files.exists(root)) {
            try { Files.createDirectories(root); }
            catch (IOException e) { throw new RuntimeException("无法创建目录: " + root, e); }
        }
    }

    // ==================== 本地目录（保持不变） ====================

    public Path getRootPath(String rootType) {
        return ROOT_TYPE_RESOURCES.equals(rootType) ? resourcesRoot : localRoot;
    }

    public Map<String, Object> getRootInfo(String rootType) {
        Path root = getRootPath(rootType);
        Map<String, Object> info = new HashMap<>();
        info.put("path", "");
        info.put("absolutePath", root.toString());
        info.put("name", root.toString());
        info.put("rootType", rootType);
        return info;
    }

    public Map<String, Object> getTreeNode(String rootType, String path, boolean includeChildren) throws IOException {
        Path root = getRootPath(rootType);
        Path target = FileUtils.resolveSafePath(root, path);
        return FileUtils.getTreeNode(target, root, includeChildren);
    }

    public List<Map<String, Object>> listDirectory(String rootType, String path) throws IOException {
        Path root = getRootPath(rootType);
        Path target = FileUtils.resolveSafePath(root, path);
        return FileUtils.listDirectory(target, root);
    }

    public void createDirectory(String rootType, String parentPath, String dirName) throws IOException {
        Path root = getRootPath(rootType);
        Path parent = FileUtils.resolveSafePath(root, parentPath);
        Path newDir = parent.resolve(dirName).normalize();
        if (!newDir.startsWith(root)) throw new SecurityException("创建路径越界");
        FileUtils.createDirectories(newDir);
    }

    public void createFile(String rootType, String parentPath, String fileName) throws IOException {
        if (fileName == null || fileName.trim().isEmpty()) throw new IllegalArgumentException("文件名不能为空");
        Path root = getRootPath(rootType);
        Path parent = FileUtils.resolveSafePath(root, parentPath);
        Path newFile = parent.resolve(fileName).normalize();
        if (!newFile.startsWith(root)) throw new SecurityException("创建路径越界");
        FileUtils.createNewFile(newFile);
    }

    public void deletePath(String rootType, String targetPath) throws IOException {
        Path root = getRootPath(rootType);
        Path target = FileUtils.resolveSafePath(root, targetPath);
        if (target.equals(root)) throw new SecurityException("根目录不可删除");
        FileUtils.deletePath(target);
    }

    public void rename(String rootType, String sourcePath, String newName) throws IOException {
        if (newName == null || newName.trim().isEmpty()) throw new IllegalArgumentException("新名称不能为空");
        Path root = getRootPath(rootType);
        Path source = FileUtils.resolveSafePath(root, sourcePath);
        if (source.equals(root)) throw new SecurityException("根目录不可重命名");
        FileUtils.rename(source, newName);
    }

    public String readFile(String rootType, String filePath) throws IOException {
        Path root = getRootPath(rootType);
        Path target = FileUtils.resolveSafePath(root, filePath);
        if (!Files.isRegularFile(target)) throw new IOException("不是文件: " + target);
        return FileUtils.readFile(target);
    }

    public void writeFile(String rootType, String filePath, String content) throws IOException {
        Path root = getRootPath(rootType);
        Path target = FileUtils.resolveSafePath(root, filePath);
        FileUtils.writeFile(target, content);
    }

    public void writeBytes(String rootType, String filePath, byte[] content) throws IOException {
        Path root = getRootPath(rootType);
        Path target = FileUtils.resolveSafePath(root, filePath);
        Path parent = target.getParent();
        if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);
        Files.write(target, content);
    }

    public byte[] readFileBytes(String rootType, String filePath) throws IOException {
        Path root = getRootPath(rootType);
        Path target = FileUtils.resolveSafePath(root, filePath);
        if (!Files.isRegularFile(target)) throw new IOException("不是文件: " + target);
        return Files.readAllBytes(target);
    }

    public void zipDirectory(String rootType, String dirPath, OutputStream out) throws IOException {
        Path root = getRootPath(rootType);
        Path targetDir = FileUtils.resolveSafePath(root, dirPath);
        if (!Files.isDirectory(targetDir)) throw new IOException("路径不是目录: " + targetDir);
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            Files.walk(targetDir).filter(Files::isRegularFile).forEach(file -> {
                String relativePath = targetDir.relativize(file).toString().replace('\\', '/');
                try {
                    zos.putNextEntry(new ZipEntry(relativePath));
                    Files.copy(file, zos);
                    zos.closeEntry();
                } catch (IOException e) { throw new RuntimeException("打包失败: " + relativePath, e); }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) throw (IOException) e.getCause();
            throw e;
        }
    }

    // ==================== 资源管理 ====================

    public Map<String, String> getResourceTypes() { return new LinkedHashMap<>(RESOURCE_TYPES); }

    public Path getResourceTypePath(String type) {
        if (!RESOURCE_TYPES.containsKey(type)) throw new IllegalArgumentException("未知资源类型: " + type);
        Path path = resourcesRoot.resolve(type);
        ensureRootExists(path);
        return path;
    }

    /** 安全解析资源类型内的相对路径 */
    private Path resolveResourcePath(String type, String relPath) {
        Path typeRoot = getResourceTypePath(type);
        Path target = typeRoot.resolve(relPath == null ? "" : relPath).normalize();
        if (!target.startsWith(typeRoot)) throw new SecurityException("路径越界: " + relPath);
        return target;
    }

    /** 生成 名称_<32位uuid>.ext */
    private String generateStoredName(String originalName) {
        String baseName = originalName;
        String ext = "";
        int dotIdx = originalName.lastIndexOf('.');
        if (dotIdx > 0) {
            baseName = originalName.substring(0, dotIdx);
            ext = originalName.substring(dotIdx);
        }
        String uuid = UUID.randomUUID().toString().replace("-", ""); // 32 位
        return baseName + "_" + uuid + ext;
    }

    /** 从存储名还原显示名（去除 uuid 后缀） */
    public String parseDisplayName(String storedName) {
        if (storedName == null || storedName.isEmpty()) return storedName;
        int dotIdx = storedName.lastIndexOf('.');
        String baseName = dotIdx > 0 ? storedName.substring(0, dotIdx) : storedName;
        String ext = dotIdx > 0 ? storedName.substring(dotIdx) : "";
        int underscoreIdx = baseName.lastIndexOf('_');
        if (underscoreIdx > 0) {
            String uuidPart = baseName.substring(underscoreIdx + 1);
            if (uuidPart.matches(UUID_REGEX)) {
                return baseName.substring(0, underscoreIdx) + ext;
            }
        }
        return storedName;
    }

    /**
     * 列出资源（目录 + 文件）
     * @param relPath 相对于类型根目录的路径（空 / 子目录 / 子目录/子子目录）
     */
    public List<Map<String, Object>> listResources(String type, String relPath) throws IOException {
        Path dir = resolveResourcePath(type, relPath);
        if (!Files.isDirectory(dir)) throw new IOException("不是目录: " + relPath);

        List<Map<String, Object>> list = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                boolean isDir = Files.isDirectory(p);
                String name = p.getFileName().toString();
                String displayName = isDir ? name : parseDisplayName(name);

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", name);                    // 存储名（目录名 / 文件存储名）
                item.put("displayName", displayName);      // 显示名（文件去 uuid）
                item.put("isDirectory", isDir);
                item.put("type", type);
                item.put("parentPath", relPath == null ? "" : relPath);
                item.put("fullRelPath", (relPath == null || relPath.isEmpty())
                        ? name : relPath + "/" + name);
                item.put("size", isDir ? 0 : Files.size(p));
                item.put("modified", sdf.format(Files.getLastModifiedTime(p).toMillis()));
                list.add(item);
            }
        }
        // 目录在前，文件在后，名称升序
        list.sort((a, b) -> {
            boolean da = (Boolean) a.get("isDirectory");
            boolean db = (Boolean) b.get("isDirectory");
            if (da != db) return da ? -1 : 1;
            return ((String) a.get("displayName")).compareToIgnoreCase((String) b.get("displayName"));
        });
        return list;
    }

    /** 新增子目录 */
    public Map<String, Object> createSubDirectory(String type, String parentRelPath, String dirName) throws IOException {
        if (dirName == null || dirName.trim().isEmpty()) throw new IllegalArgumentException("目录名不能为空");
        if (dirName.contains("/") || dirName.contains("\\")) throw new IllegalArgumentException("目录名不能包含斜杠");

        Path parent = resolveResourcePath(type, parentRelPath);
        if (!Files.isDirectory(parent)) throw new IOException("父路径不是目录");

        Path newDir = parent.resolve(dirName.trim()).normalize();
        if (!newDir.startsWith(getResourceTypePath(type))) throw new SecurityException("路径越界");
        if (Files.exists(newDir)) throw new IOException("目录已存在: " + dirName);

        Files.createDirectories(newDir);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", dirName);
        result.put("relPath", (parentRelPath == null || parentRelPath.isEmpty())
                ? dirName : parentRelPath + "/" + dirName);
        log.info("创建资源子目录: type={}, parent={}, name={}", type, parentRelPath, dirName);
        return result;
    }

    /** 上传资源到指定子目录，命名 名称_<32位uuid>.ext */
    public Map<String, Object> uploadResource(String type, String subPath, String originalName, byte[] content) throws IOException {
        Path dir = resolveResourcePath(type, subPath);
        if (!Files.isDirectory(dir)) throw new IOException("上传路径不是目录: " + subPath);

        String storedName = generateStoredName(originalName);
        Path target = dir.resolve(storedName);
        Files.write(target, content);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("storedName", storedName);
        result.put("displayName", originalName);
        result.put("type", type);
        result.put("subPath", subPath);
        result.put("fullRelPath", (subPath == null || subPath.isEmpty())
                ? storedName : subPath + "/" + storedName);
        result.put("size", content.length);
        log.info("上传资源: type={}, subPath={}, displayName={}, storedName={}, size={}B",
                type, subPath, originalName, storedName, content.length);
        return result;
    }

    /** 删除资源（文件或目录，递归删除） */
    public void deleteResource(String type, String fullRelPath) throws IOException {
        Path target = resolveResourcePath(type, fullRelPath);
        Path typeRoot = getResourceTypePath(type);
        if (target.equals(typeRoot)) throw new SecurityException("不能删除类型根目录");
        if (!Files.exists(target)) throw new IOException("资源不存在: " + fullRelPath);

        if (Files.isDirectory(target)) {
            try (var walk = Files.walk(target)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.delete(p); } catch (IOException ignored) {}
                });
            }
        } else {
            Files.delete(target);
        }
        log.info("删除资源: type={}, path={}", type, fullRelPath);
    }

    /** 获取资源文件的物理路径 */
    public Path getResourceFilePath(String type, String fullRelPath) throws IOException {
        Path target = resolveResourcePath(type, fullRelPath);
        if (!Files.isRegularFile(target)) throw new IOException("不是文件: " + fullRelPath);
        return target;
    }

    /** 读取文本内容（SQL 等） */
    public String readResourceContent(String type, String fullRelPath) throws IOException {
        Path file = getResourceFilePath(type, fullRelPath);
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    /** 各类型资源数量（递归） */
    public Map<String, Integer> countResources() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String type : RESOURCE_TYPES.keySet()) {
            try {
                Path typePath = getResourceTypePath(type);
                int count = 0;
                try (var walk = Files.walk(typePath)) {
                    count = (int) walk.filter(Files::isRegularFile).count();
                }
                counts.put(type, count);
            } catch (Exception e) { counts.put(type, 0); }
        }
        return counts;
    }
}