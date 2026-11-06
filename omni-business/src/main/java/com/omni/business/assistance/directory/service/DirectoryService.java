package com.omni.business.assistance.directory.service;

import com.omni.common.util.DriverFileNameParser;
import com.omni.common.util.FileIOUtils;
import com.omni.common.util.RandomUtils;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Service;
import com.omni.framework.spring.config.ConfigLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** ★ L1-06：readResourceContent 用 FileIOUtils */
@Service
public class DirectoryService {

    private static final Logger log = LoggerFactory.getLogger(DirectoryService.class);

    public static final String ROOT_TYPE_LOCAL     = "local";
    public static final String ROOT_TYPE_RESOURCES = "resources";

    public static final String RES_TYPE_IMAGES      = "images";
    public static final String RES_TYPE_PAGES       = "pages";
    public static final String RES_TYPE_SQL         = "sql";
    public static final String RES_TYPE_ATTACHMENTS = "attachments";
    public static final String RES_TYPE_DRIVERS     = "drivers";
    public static final String RES_TYPE_DEPLOY      = "deploy";
    public static final String RES_TYPE_OTHERS      = "others";

    private static final Map<String, String> RESOURCE_TYPES = new LinkedHashMap<>();
    static {
        RESOURCE_TYPES.put(RES_TYPE_IMAGES,      "图片");
        RESOURCE_TYPES.put(RES_TYPE_PAGES,       "导出页面");
        RESOURCE_TYPES.put(RES_TYPE_SQL,         "SQL管理");
        RESOURCE_TYPES.put(RES_TYPE_ATTACHMENTS, "附件");
        RESOURCE_TYPES.put(RES_TYPE_DRIVERS,     "驱动管理");
        RESOURCE_TYPES.put(RES_TYPE_DEPLOY,      "项目部署");
        RESOURCE_TYPES.put(RES_TYPE_OTHERS,      "其他");
    }

    private static final String UUID_REGEX = "[0-9a-f]{12,32}";
    private static final Pattern DRIVER_STORAGE_PATTERN = Pattern.compile(
            "^([a-z]+)_(.+?)_(.+\\.jar)$", Pattern.CASE_INSENSITIVE);

    private final Path localRoot;
    private final Path resourcesRoot;

    public DirectoryService() {
        String localRootPath = ConfigLoader.getProperty("files.local.root", System.getProperty("user.dir"));
        this.localRoot = Paths.get(localRootPath).normalize().toAbsolutePath();
        ensureRootExists(localRoot);

        this.resourcesRoot = Paths.get(System.getProperty("user.dir"), "resources").normalize().toAbsolutePath();
        ensureRootExists(resourcesRoot);
        for (String type : RESOURCE_TYPES.keySet()) ensureRootExists(resourcesRoot.resolve(type));
        log.info("资源管理根目录已就绪: {}, 驱动目录: {}, 部署目录: {}",
                resourcesRoot, resourcesRoot.resolve(RES_TYPE_DRIVERS), resourcesRoot.resolve(RES_TYPE_DEPLOY));
    }

    private void ensureRootExists(Path root) {
        if (!Files.exists(root)) {
            try { Files.createDirectories(root); }
            catch (IOException e) { throw new RuntimeException("无法创建目录: " + root, e); }
        }
    }

    // ==================== 本地目录 ====================

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
        Path target = com.omni.common.util.FileUtils.resolveSafePath(root, path);
        return com.omni.common.util.FileUtils.getTreeNode(target, root, includeChildren);
    }

    public List<Map<String, Object>> listDirectory(String rootType, String path) throws IOException {
        Path root = getRootPath(rootType);
        Path target = com.omni.common.util.FileUtils.resolveSafePath(root, path);
        return com.omni.common.util.FileUtils.listDirectory(target, root);
    }

    public void createDirectory(String rootType, String parentPath, String dirName) throws IOException {
        Path root = getRootPath(rootType);
        Path parent = com.omni.common.util.FileUtils.resolveSafePath(root, parentPath);
        Path newDir = parent.resolve(dirName).normalize();
        if (!newDir.startsWith(root)) throw new SecurityException("创建路径越界");
        com.omni.common.util.FileUtils.createDirectories(newDir);
    }

    public void createFile(String rootType, String parentPath, String fileName) throws IOException {
        if (fileName == null || fileName.trim().isEmpty()) throw new IllegalArgumentException("文件名不能为空");
        Path root = getRootPath(rootType);
        Path parent = com.omni.common.util.FileUtils.resolveSafePath(root, parentPath);
        Path newFile = parent.resolve(fileName).normalize();
        if (!newFile.startsWith(root)) throw new SecurityException("创建路径越界");
        com.omni.common.util.FileUtils.createNewFile(newFile);
    }

    public void deletePath(String rootType, String targetPath) throws IOException {
        Path root = getRootPath(rootType);
        Path target = com.omni.common.util.FileUtils.resolveSafePath(root, targetPath);
        if (target.equals(root)) throw new SecurityException("根目录不可删除");
        com.omni.common.util.FileUtils.deletePath(target);
    }

    public void rename(String rootType, String sourcePath, String newName) throws IOException {
        if (newName == null || newName.trim().isEmpty()) throw new IllegalArgumentException("新名称不能为空");
        Path root = getRootPath(rootType);
        Path source = com.omni.common.util.FileUtils.resolveSafePath(root, sourcePath);
        if (source.equals(root)) throw new SecurityException("根目录不可重命名");
        com.omni.common.util.FileUtils.rename(source, newName);
    }

    public String readFile(String rootType, String filePath) throws IOException {
        Path root = getRootPath(rootType);
        Path target = com.omni.common.util.FileUtils.resolveSafePath(root, filePath);
        if (!Files.isRegularFile(target)) throw new IOException("不是文件: " + target);
        return FileIOUtils.readString(target);
    }

    public void writeFile(String rootType, String filePath, String content) throws IOException {
        Path root = getRootPath(rootType);
        Path target = com.omni.common.util.FileUtils.resolveSafePath(root, filePath);
        FileIOUtils.writeString(target, content);
    }

    public void writeBytes(String rootType, String filePath, byte[] content) throws IOException {
        Path root = getRootPath(rootType);
        Path target = com.omni.common.util.FileUtils.resolveSafePath(root, filePath);
        FileIOUtils.writeBytes(target, content);
    }

    public byte[] readFileBytes(String rootType, String filePath) throws IOException {
        Path root = getRootPath(rootType);
        Path target = com.omni.common.util.FileUtils.resolveSafePath(root, filePath);
        if (!Files.isRegularFile(target)) throw new IOException("不是文件: " + target);
        return FileIOUtils.readBytes(target);
    }

    public void zipDirectory(String rootType, String dirPath, OutputStream out) throws IOException {
        Path root = getRootPath(rootType);
        Path targetDir = com.omni.common.util.FileUtils.resolveSafePath(root, dirPath);
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

    private Path resolveResourcePath(String type, String relPath) {
        Path typeRoot = getResourceTypePath(type);
        Path target = typeRoot.resolve(relPath == null ? "" : relPath).normalize();
        if (!target.startsWith(typeRoot)) throw new SecurityException("路径越界: " + relPath);
        return target;
    }

    private String generateStoredName(String originalName) {
        String baseName = originalName;
        String ext = "";
        int dotIdx = originalName.lastIndexOf('.');
        if (dotIdx > 0) { baseName = originalName.substring(0, dotIdx); ext = originalName.substring(dotIdx); }
        return baseName + "_" + RandomUtils.uuidNoDash() + ext;
    }

    private String[] parseDriverStorageName(String storedName) {
        Matcher m = DRIVER_STORAGE_PATTERN.matcher(storedName);
        if (!m.matches()) return null;
        String dbType = DriverFileNameParser.normalizeDbType(m.group(1));
        if (!DriverFileNameParser.isAllowedDbType(dbType)) return null;
        return new String[] { dbType, m.group(2), m.group(3) };
    }

    public String parseDisplayName(String type, String storedName) {
        if (storedName == null || storedName.isEmpty()) return storedName;
        if (RES_TYPE_DRIVERS.equals(type)) {
            String[] parts = parseDriverStorageName(storedName);
            return parts != null ? parts[2] : storedName;
        }
        if (RES_TYPE_DEPLOY.equals(type)) return storedName;
        int dotIdx = storedName.lastIndexOf('.');
        String baseName = dotIdx > 0 ? storedName.substring(0, dotIdx) : storedName;
        String ext = dotIdx > 0 ? storedName.substring(dotIdx) : "";
        int underscoreIdx = baseName.lastIndexOf('_');
        if (underscoreIdx > 0) {
            String uuidPart = baseName.substring(underscoreIdx + 1);
            if (uuidPart.matches(UUID_REGEX)) return baseName.substring(0, underscoreIdx) + ext;
        }
        return storedName;
    }

    public List<Map<String, Object>> listResources(String type, String relPath) throws IOException {
        Path dir = resolveResourcePath(type, relPath);
        if (!Files.isDirectory(dir)) throw new IOException("不是目录: " + relPath);

        List<Map<String, Object>> list = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                boolean isDir = Files.isDirectory(p);
                String name = p.getFileName().toString();
                String displayName = isDir ? name : parseDisplayName(type, name);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", name);
                item.put("displayName", displayName);
                item.put("isDirectory", isDir);
                item.put("type", type);
                item.put("parentPath", relPath == null ? "" : relPath);
                item.put("fullRelPath", (relPath == null || relPath.isEmpty()) ? name : relPath + "/" + name);
                item.put("size", isDir ? 0 : Files.size(p));
                item.put("modified", sdf.format(Files.getLastModifiedTime(p).toMillis()));
                if (!isDir && RES_TYPE_DRIVERS.equals(type)) {
                    String[] parts = parseDriverStorageName(name);
                    if (parts != null) { item.put("dbType", parts[0]); item.put("version", parts[1]); }
                }
                list.add(item);
            }
        }
        list.sort((a, b) -> {
            boolean da = (Boolean) a.get("isDirectory"), db = (Boolean) b.get("isDirectory");
            if (da != db) return da ? -1 : 1;
            return ((String) a.get("displayName")).compareToIgnoreCase((String) b.get("displayName"));
        });
        return list;
    }

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
        result.put("relPath", (parentRelPath == null || parentRelPath.isEmpty()) ? dirName : parentRelPath + "/" + dirName);
        log.info("创建资源子目录: type={}, parent={}, name={}", type, parentRelPath, dirName);
        return result;
    }

    public Map<String, Object> uploadResource(String type, String subPath, String originalName, byte[] content) throws IOException {
        return uploadResource(type, subPath, originalName, content, null, null);
    }

    public Map<String, Object> uploadResource(String type, String subPath, String originalName,
                                              byte[] content, String dbType, String version) throws IOException {
        if (RES_TYPE_DRIVERS.equals(type)) return uploadDriverResource(subPath, originalName, content, dbType, version);
        if (RES_TYPE_DEPLOY.equals(type)) return uploadDeployResource(subPath, originalName, content);
        return uploadGenericResource(type, subPath, originalName, content);
    }

    private Map<String, Object> uploadDriverResource(String subPath, String originalName,
                                                     byte[] content, String dbType, String version) throws IOException {
        if (originalName == null || originalName.trim().isEmpty()) throw new IllegalArgumentException("原始文件名不能为空");
        if (!originalName.trim().toLowerCase().endsWith(".jar")) throw new IllegalArgumentException("驱动只允许上传 .jar 文件");
        String type = DriverFileNameParser.normalizeDbType(dbType);
        if (!DriverFileNameParser.isAllowedDbType(type)) throw new IllegalArgumentException("不支持的数据库类型: " + dbType);
        if (version == null || version.trim().isEmpty()) throw new IllegalArgumentException("驱动版本不能为空");
        if (version.contains("_")) throw new IllegalArgumentException("驱动版本不能包含下划线 _");

        String storedName = DriverFileNameParser.buildStorageName(type, version.trim(), originalName);
        Path dir = resolveResourcePath(RES_TYPE_DRIVERS, subPath);
        if (!Files.isDirectory(dir)) throw new IOException("上传路径不是目录: " + subPath);
        Path target = dir.resolve(storedName);
        if (Files.exists(target)) throw new IOException("驱动已存在：" + type + " / " + version.trim());
        Files.write(target, content);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("storedName", storedName);
        result.put("displayName", originalName);
        result.put("type", RES_TYPE_DRIVERS);
        result.put("subPath", subPath);
        result.put("fullRelPath", (subPath == null || subPath.isEmpty()) ? storedName : subPath + "/" + storedName);
        result.put("size", content.length);
        result.put("dbType", type);
        result.put("version", version.trim());
        return result;
    }

    private Map<String, Object> uploadDeployResource(String subPath, String originalName, byte[] content) throws IOException {
        if (originalName == null || originalName.trim().isEmpty()) throw new IllegalArgumentException("原始文件名不能为空");
        String cleanName = originalName.trim();
        if (!cleanName.toLowerCase().endsWith(".jar")) throw new IllegalArgumentException("项目部署目录只允许上传 .jar 文件");
        if (cleanName.contains("/") || cleanName.contains("\\") || cleanName.contains("..") || cleanName.contains("\u0000"))
            throw new IllegalArgumentException("非法文件名: " + originalName);
        Path dir = resolveResourcePath(RES_TYPE_DEPLOY, subPath);
        if (!Files.isDirectory(dir)) throw new IOException("上传路径不是目录: " + subPath);
        Path target = dir.resolve(cleanName);
        if (!target.startsWith(getResourceTypePath(RES_TYPE_DEPLOY))) throw new SecurityException("路径越界");
        if (Files.exists(target)) throw new IOException("文件已存在：" + cleanName);
        Files.write(target, content);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("storedName", cleanName);
        result.put("displayName", cleanName);
        result.put("type", RES_TYPE_DEPLOY);
        result.put("subPath", subPath);
        result.put("fullRelPath", (subPath == null || subPath.isEmpty()) ? cleanName : subPath + "/" + cleanName);
        result.put("size", content.length);
        return result;
    }

    private Map<String, Object> uploadGenericResource(String type, String subPath,
                                                      String originalName, byte[] content) throws IOException {
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
        result.put("fullRelPath", (subPath == null || subPath.isEmpty()) ? storedName : subPath + "/" + storedName);
        result.put("size", content.length);
        return result;
    }

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
    }

    public Path getResourceFilePath(String type, String fullRelPath) throws IOException {
        Path target = resolveResourcePath(type, fullRelPath);
        if (!Files.isRegularFile(target)) throw new IOException("不是文件: " + fullRelPath);
        return target;
    }

    public String readResourceContent(String type, String fullRelPath) throws IOException {
        Path file = getResourceFilePath(type, fullRelPath);
        return FileIOUtils.readString(file);
    }

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