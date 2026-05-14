package com.codestats.business.assistance.directory.service;


import com.codestats.common.util.FileUtils;
import com.framework.spring.annotation.Service;
import com.framework.spring.config.ConfigLoader;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 目录管理服务层 - 支持多根目录（本地/服务器）
 * 底层操作委托给 FileUtils 工具类
 */
@Service
public class DirectoryService {

    // 根类型常量
    public static final String ROOT_TYPE_LOCAL = "local";
    public static final String ROOT_TYPE_SERVER = "server";

    private Path localRoot;
    private Path serverRoot;

    public DirectoryService() {
        // 本地根：当前用户目录下的 codestats
        String localRootPath = ConfigLoader.getProperty("files.local.root", System.getProperty("user.dir"));
        localRoot = Paths.get(localRootPath).normalize().toAbsolutePath();
        ensureRootExists(localRoot);

        // 服务器根：默认 D:/temp，可配置
        String serverRootPath = ConfigLoader.getProperty("files.server.root", "D:/temp");
        serverRoot = Paths.get(serverRootPath).normalize().toAbsolutePath();
        ensureRootExists(serverRoot);
    }

    private void ensureRootExists(Path root) {
        if (!root.toFile().exists()) {
            try {
                java.nio.file.Files.createDirectories(root);
            } catch (IOException e) {
                throw new RuntimeException("无法创建根目录: " + root, e);
            }
        }
    }

    /**
     * 根据根类型获取根目录 Path
     */
    public Path getRootPath(String rootType) {
        if (ROOT_TYPE_SERVER.equals(rootType)) {
            return serverRoot;
        }
        return localRoot;
    }

    /**
     * 获取根信息（供前端展示）
     */
    public Map<String, Object> getRootInfo(String rootType) {
        Path root = getRootPath(rootType);
        Map<String, Object> info = new HashMap<>();
        info.put("path", "");
        info.put("absolutePath", root.toString());
        info.put("name", root.toString());
        info.put("rootType", rootType);
        return info;
    }

    /**
     * 获取目录树节点（懒加载）
     */
    public Map<String, Object> getTreeNode(String rootType, String path, boolean includeChildren) throws IOException {
        Path root = getRootPath(rootType);
        Path target = FileUtils.resolveSafePath(root, path);
        return FileUtils.getTreeNode(target, root, includeChildren);
    }

    /**
     * 获取目录下的文件/子目录列表
     */
    public List<Map<String, Object>> listDirectory(String rootType, String path) throws IOException {
        Path root = getRootPath(rootType);
        Path target = FileUtils.resolveSafePath(root, path);
        return FileUtils.listDirectory(target, root);
    }

    /**
     * 创建目录
     */
    public void createDirectory(String rootType, String parentPath, String dirName) throws IOException {
        Path root = getRootPath(rootType);
        Path parent = FileUtils.resolveSafePath(root, parentPath);
        Path newDir = parent.resolve(dirName).normalize();
        if (!newDir.startsWith(root)) {
            throw new SecurityException("创建路径越界");
        }
        FileUtils.createDirectories(newDir);
    }

    /**
     * 创建空文件
     */
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

    /**
     * 删除文件或目录（根目录不可删除）
     */
    public void deletePath(String rootType, String targetPath) throws IOException {
        Path root = getRootPath(rootType);
        Path target = FileUtils.resolveSafePath(root, targetPath);
        if (target.equals(root)) {
            throw new SecurityException("根目录不可删除");
        }
        FileUtils.deletePath(target);
    }

    /**
     * 重命名文件或目录（根目录不可重命名）
     */
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

    /**
     * 读取文件内容（文本）
     */
    public String readFile(String rootType, String filePath) throws IOException {
        Path root = getRootPath(rootType);
        Path target = FileUtils.resolveSafePath(root, filePath);
        if (!java.nio.file.Files.isRegularFile(target)) {
            throw new IOException("不是文件: " + target);
        }
        return FileUtils.readFile(target);
    }

    /**
     * 写入文件内容
     */
    public void writeFile(String rootType, String filePath, String content) throws IOException {
        Path root = getRootPath(rootType);
        Path target = FileUtils.resolveSafePath(root, filePath);
        FileUtils.writeFile(target, content);
    }

    // ========== 新增：下载功能 ==========

    /**
     * 读取文件二进制内容
     */
    public byte[] readFileBytes(String rootType, String filePath) throws IOException {
        Path root = getRootPath(rootType);
        Path target = FileUtils.resolveSafePath(root, filePath);
        if (!java.nio.file.Files.isRegularFile(target)) {
            throw new IOException("不是文件: " + target);
        }
        return java.nio.file.Files.readAllBytes(target);
    }

    /**
     * 将目录打包成 ZIP 并写入输出流
     * @param rootType 根类型
     * @param dirPath 相对路径
     * @param out 输出流（通常为 Http 响应流）
     */
    public void zipDirectory(String rootType, String dirPath, OutputStream out) throws IOException {
        Path root = getRootPath(rootType);
        Path targetDir = FileUtils.resolveSafePath(root, dirPath);
        if (!java.nio.file.Files.isDirectory(targetDir)) {
            throw new IOException("路径不是目录: " + targetDir);
        }

        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            java.nio.file.Files.walk(targetDir)
                    .filter(java.nio.file.Files::isRegularFile)
                    .forEach(file -> {
                        String relativePath = targetDir.relativize(file).toString().replace('\\', '/');
                        try {
                            zos.putNextEntry(new ZipEntry(relativePath));
                            java.nio.file.Files.copy(file, zos);
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
}