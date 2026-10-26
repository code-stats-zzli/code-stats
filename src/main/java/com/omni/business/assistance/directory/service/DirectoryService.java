package com.omni.business.assistance.directory.service;

import com.omni.common.util.FileUtils;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Service;
import com.omni.framework.spring.config.ConfigLoader;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 目录与资源管理服务
 * - 本地目录管理（local）：可浏览、编辑、上传、下载
 * - 资源管理（resources）：项目根目录下的 ./resources，按类型分目录，文件名 名称_uuid.ext
 */
@Service
public class DirectoryService {

    private static final Logger log = LoggerFactory.getLogger(DirectoryService.class);

    /** 本地目录类型 */
    public static final String ROOT_TYPE_LOCAL = "local";
    /** 资源管理类型 */
    public static final String ROOT_TYPE_RESOURCES = "resources";

    /** 资源类型常量 */
    public static final String RES_TYPE_IMAGES      = "images";
    public static final String RES_TYPE_PAGES       = "pages";
    public static final String RES_TYPE_ATTACHMENTS = "attachments";
    public static final String RES_TYPE_OTHERS      = "others";

    /** 资源类型 -> 显示名称 */
    private static final Map<String, String> RESOURCE_TYPES = new LinkedHashMap<>();
    static {
        RESOURCE_TYPES.put(RES_TYPE_IMAGES,      "图片");
        RESOURCE_TYPES.put(RES_TYPE_PAGES,       "导出页面");
        RESOURCE_TYPES.put(RES_TYPE_ATTACHMENTS, "附件");
        RESOURCE_TYPES.put(RES_TYPE_OTHERS,      "其他");
    }

    /** uuid 长度和格式 */
    private static final int UUID_LENGTH = 12;
    private static final String UUID_REGEX = "[0-9a-f]{" + UUID_LENGTH + "}";

    private Path localRoot;
    private Path resourcesRoot;

    public DirectoryService() {
        // 本地根：当前工作目录（可配置）
        String localRootPath = ConfigLoader.getProperty("files.local.root", System.getProperty("user.dir"));
        this.localRoot = Paths.get(localRootPath).normalize().toAbsolutePath();
        ensureRootExists(localRoot);

        // 资源根：固定在项目根目录 ./resources，无需配置
        this.resourcesRoot = Paths.get(System.getProperty("user.dir"), "resources")
                .normalize().toAbsolutePath();
        ensureRootExists(resourcesRoot);

        // 确保所有资源类型子目录存在
        for (String type : RESOURCE_TYPES.keySet()) {
            ensureRootExists(resourcesRoot.resolve(type));
        }
        log.info("资源管理根目录已就绪: {}", resourcesRoot);
    }

    private void ensureRootExists(Path root) {
        if (!Files.exists(root)) {
            try {
                Files.createDirectories(root);
            } catch (IOException e) {
                throw new RuntimeException("无法创建目录: " + root, e);
            }
        }
    }

    // ==================== 本地目录管理（保留原有功能） ====================

    public Path getRootPath(String rootType) {
        if (ROOT_TYPE_RESOURCES.equals(rootType)) {
            return resourcesRoot;
        }
        return localRoot;
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
        if (!newDir.startsWith(root)) {
            throw new SecurityException("创建路径越界");
        }
        FileUtils.createDirectories(newDir);
    }

    public void createFile(String rootType, String parentPath, String fileName) throws IOException {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        Path root = getRootPath(rootType);
        Path parent = FileUtils.resolveSafePath(root, parentPath);
        Path newFile = parent.resolve(fileName).normalize();
        if (!newFile.startsWith(root)) {
            throw new SecurityException("创建路径越界");
        }
        FileUtils.createNewFile(newFile);
    }

    public void deletePath(String rootType, String targetPath) throws IOException {
        Path root = getRootPath(rootType);
        Path target = FileUtils.resolveSafePath(root, targetPath);
        if (target.equals(root)) {
            throw new SecurityException("根目录不可删除");
        }
        FileUtils.deletePath(target);
    }

    public void rename(String rootType, String sourcePath, String newName) throws IOException {
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("新名称不能为空");
        }
        Path root = getRootPath(rootType);
        Path source = FileUtils.resolveSafePath(root, sourcePath);
        if (source.equals(root)) {
            throw new SecurityException("根目录不可重命名");
        }
        FileUtils.rename(source, newName);
    }

    public String readFile(String rootType, String filePath) throws IOException {
        Path root = getRootPath(rootType);
        Path target = FileUtils.resolveSafePath(root, filePath);
        if (!Files.isRegularFile(target)) {
            throw new IOException("不是文件: " + target);
        }
        return FileUtils.readFile(target);
    }

    public void writeFile(String rootType, String filePath, String content) throws IOException {
        Path root = getRootPath(rootType);
        Path target = FileUtils.resolveSafePath(root, filePath);
        FileUtils.writeFile(target, content);
    }

    public byte[] readFileBytes(String rootType, String filePath) throws IOException {
        Path root = getRootPath(rootType);
        Path target = FileUtils.resolveSafePath(root, filePath);
        if (!Files.isRegularFile(target)) {
            throw new IOException("不是文件: " + target);
        }
        return Files.readAllBytes(target);
    }

    public void zipDirectory(String rootType, String dirPath, OutputStream out) throws IOException {
        Path root = getRootPath(rootType);
        Path targetDir = FileUtils.resolveSafePath(root, dirPath);
        if (!Files.isDirectory(targetDir)) {
            throw new IOException("路径不是目录: " + targetDir);
        }
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            Files.walk(targetDir)
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        String relativePath = targetDir.relativize(file).toString().replace('\\', '/');
                        try {
                            zos.putNextEntry(new ZipEntry(relativePath));
                            Files.copy(file, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException("打包失败: " + relativePath, e);
                        }
                    });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw e;
        }
    }

    // ==================== 资源管理 ====================

    /** 获取资源类型映射（key -> 中文名） */
    public Map<String, String> getResourceTypes() {
        return new LinkedHashMap<>(RESOURCE_TYPES);
    }

    /** 获取资源类型目录（自动创建） */
    public Path getResourceTypePath(String type) {
        if (!RESOURCE_TYPES.containsKey(type)) {
            throw new IllegalArgumentException("未知资源类型: " + type);
        }
        Path path = resourcesRoot.resolve(type);
        ensureRootExists(path);
        return path;
    }

    /**
     * 生成存储文件名：名称_uuid.ext
     * uuid 为 12 位十六进制，足够唯一
     */
    private String generateStoredName(String originalName) {
        String baseName = originalName;
        String ext = "";
        int dotIdx = originalName.lastIndexOf('.');
        if (dotIdx > 0) {
            baseName = originalName.substring(0, dotIdx);
            ext = originalName.substring(dotIdx);
        }
        String uuid = UUID.randomUUID().toString().replace("-", "")
                .substring(0, UUID_LENGTH);
        return baseName + "_" + uuid + ext;
    }

    /**
     * 从存储文件名解析显示名（去掉 uuid 后缀）
     * 例如: logo_a1b2c3d4e5f6.png -> logo.png
     * 若文件名不符合 名称_uuid.ext 格式，原样返回
     */
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
     * 根据显示名查找实际存储名
     * 优先精确匹配，再匹配 名称_uuid.ext 模式
     */
    public String findStoredName(String type, String displayName) throws IOException {
        Path typePath = getResourceTypePath(type);

        // 1. 精确匹配（兼容老数据或非规范命名）
        Path direct = typePath.resolve(displayName);
        if (Files.isRegularFile(direct)) {
            return displayName;
        }

        // 2. 匹配 名称_uuid.ext 模式
        String baseName = displayName;
        String ext = "";
        int dotIdx = displayName.lastIndexOf('.');
        if (dotIdx > 0) {
            baseName = displayName.substring(0, dotIdx);
            ext = displayName.substring(dotIdx);
        }
        final String finalBase = baseName;
        final String finalExt = ext;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(typePath)) {
            for (Path p : stream) {
                if (!Files.isRegularFile(p)) continue;
                String stored = p.getFileName().toString();
                if (stored.startsWith(finalBase + "_") && stored.endsWith(finalExt)) {
                    int start = finalBase.length() + 1;
                    int end = stored.length() - finalExt.length();
                    if (end > start) {
                        String middle = stored.substring(start, end);
                        if (middle.matches(UUID_REGEX)) {
                            return stored;
                        }
                    }
                }
            }
        }
        throw new IOException("资源不存在: " + displayName);
    }

    /** 列出某类型的所有资源 */
    public List<Map<String, Object>> listResources(String type) throws IOException {
        Path typePath = getResourceTypePath(type);
        List<Map<String, Object>> list = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(typePath)) {
            for (Path p : stream) {
                if (!Files.isRegularFile(p)) continue;
                String storedName = p.getFileName().toString();
                String displayName = parseDisplayName(storedName);

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("storedName", storedName);
                item.put("displayName", displayName);
                item.put("type", type);
                item.put("size", Files.size(p));
                item.put("modified", sdf.format(Files.getLastModifiedTime(p).toMillis()));
                list.add(item);
            }
        }
        // 按修改时间倒序（最新在前）
        list.sort((a, b) -> ((String) b.get("modified")).compareTo((String) a.get("modified")));
        return list;
    }

    /**
     * 上传资源：以 名称_uuid.ext 格式保存
     */
    public Map<String, Object> uploadResource(String type, String originalName, byte[] content) throws IOException {
        Path typePath = getResourceTypePath(type);
        String storedName = generateStoredName(originalName);
        Path target = typePath.resolve(storedName);
        Files.write(target, content);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("storedName", storedName);
        result.put("displayName", originalName);
        result.put("type", type);
        result.put("size", content.length);
        log.info("上传资源: type={}, displayName={}, storedName={}, size={}B",
                type, originalName, storedName, content.length);
        return result;
    }

    /** 删除资源（按显示名） */
    public void deleteResource(String type, String displayName) throws IOException {
        String storedName = findStoredName(type, displayName);
        Path target = getResourceTypePath(type).resolve(storedName);
        Files.delete(target);
        log.info("删除资源: type={}, displayName={}, storedName={}", type, displayName, storedName);
    }

    /** 获取资源物理路径（按显示名） */
    public Path getResourcePath(String type, String displayName) throws IOException {
        String storedName = findStoredName(type, displayName);
        return getResourceTypePath(type).resolve(storedName);
    }

    /** 统计各类型资源数量 */
    public Map<String, Integer> countResources() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String type : RESOURCE_TYPES.keySet()) {
            try {
                Path typePath = getResourceTypePath(type);
                int count = 0;
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(typePath)) {
                    for (Path p : stream) {
                        if (Files.isRegularFile(p)) count++;
                    }
                }
                counts.put(type, count);
            } catch (Exception e) {
                counts.put(type, 0);
            }
        }
        return counts;
    }
}