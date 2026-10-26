package com.omni.common.util;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 文件/目录操作公共工具类
 * 提供基于 NIO 的安全原子操作，所有方法均进行路径合法性校验
 */
public class FileUtils {

    /**
     * 安全解析路径，确保目标路径在根目录之下
     * @param root 根目录
     * @param userPath 用户输入的路径（相对或绝对）
     * @return 规范化的绝对路径
     * @throws SecurityException 若路径越界
     */
    public static Path resolveSafePath(Path root, String userPath) throws SecurityException {
        Path target;
        if (userPath == null || userPath.trim().isEmpty()) {
            target = root;
        } else {
            target = root.resolve(userPath).normalize();
        }
        if (!target.startsWith(root)) {
            throw new SecurityException("路径越界访问: " + target);
        }
        return target;
    }

    /**
     * 获取目录下的文件和子目录列表
     * @param dir 目录路径
     * @return 文件信息列表（包含名称、相对路径、类型、大小、修改时间）
     */
    public static List<Map<String, Object>> listDirectory(Path dir, Path root) throws IOException {
        if (!Files.isDirectory(dir)) {
            throw new IOException("路径不是目录: " + dir);
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", entry.getFileName().toString());
                item.put("path", getRelativePath(root, entry));
                item.put("type", Files.isDirectory(entry) ? "directory" : "file");
                if (Files.isRegularFile(entry)) {
                    item.put("size", Files.size(entry));
                } else {
                    item.put("size", 0L);
                }
                item.put("modified", sdf.format(Files.getLastModifiedTime(entry).toMillis()));
                entries.add(item);
            }
        }
        // 排序：目录在前，文件在后，按名称
        entries.sort((a, b) -> {
            if (a.get("type").equals(b.get("type"))) {
                return ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
            }
            return a.get("type").equals("directory") ? -1 : 1;
        });
        return entries;
    }

    /**
     * 判断目录是否为空（没有子项）
     */
    public static boolean isDirectoryEmpty(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return true;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            return !stream.iterator().hasNext();
        }
    }

    /**
     * 获取目录树节点（只返回直接子节点，不递归）
     * @param nodePath 当前节点的绝对路径
     * @param root 根目录
     * @param includeChildren 是否包含子节点信息（true时返回子节点列表，但子节点不包含孙节点）
     * @return 节点信息，包含 children 列表（若includeChildren=true且有子目录）
     */
    public static Map<String, Object> getTreeNode(Path nodePath, Path root, boolean includeChildren) throws IOException {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name", nodePath.equals(root) ? root.toString() : nodePath.getFileName().toString());
        node.put("path", getRelativePath(root, nodePath));
        node.put("type", Files.isDirectory(nodePath) ? "directory" : "file");
        node.put("hasChildren", Files.isDirectory(nodePath) && !isDirectoryEmpty(nodePath));

        if (includeChildren && Files.isDirectory(nodePath)) {
            List<Map<String, Object>> children = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(nodePath)) {
                for (Path child : stream) {
                    Map<String, Object> childNode = new LinkedHashMap<>();
                    childNode.put("name", child.getFileName().toString());
                    childNode.put("path", getRelativePath(root, child));
                    childNode.put("type", Files.isDirectory(child) ? "directory" : "file");
                    childNode.put("hasChildren", Files.isDirectory(child) && !isDirectoryEmpty(child));
                    childNode.put("expanded", false);
                    children.add(childNode);
                }
            }
            // 排序：目录在前
            children.sort((a, b) -> {
                if (a.get("type").equals(b.get("type"))) {
                    return ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
                }
                return a.get("type").equals("directory") ? -1 : 1;
            });
            node.put("children", children);
        } else {
            node.put("children", Collections.emptyList());
        }
        return node;
    }

    /**
     * 创建目录（可创建多级）
     */
    public static void createDirectories(Path target) throws IOException {
        Files.createDirectories(target);
    }

    /**
     * 创建空文件（如果父目录不存在则自动创建）
     */
    public static void createNewFile(Path target) throws IOException {
        Path parent = target.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.createFile(target);
    }

    /**
     * 删除文件或目录（递归删除非空目录）
     */
    public static void deletePath(Path target) throws IOException {
        if (Files.isDirectory(target)) {
            Files.walkFileTree(target, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            Files.delete(target);
        }
    }

    /**
     * 重命名文件或目录
     */
    public static void rename(Path source, String newName) throws IOException {
        Path target = source.resolveSibling(newName).normalize();
        Files.move(source, target);
    }

    /**
     * 读取文件内容
     */
    public static String readFile(Path file) throws IOException {
        return new String(Files.readAllBytes(file));
    }

    /**
     * 写入文件内容（覆盖）
     */
    public static void writeFile(Path file, String content) throws IOException {
        Path parent = file.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.write(file, content.getBytes());
    }

    /**
     * 获取相对路径（相对于根目录）
     */
    public static String getRelativePath(Path root, Path absolutePath) {
        Path relative = root.relativize(absolutePath);
        if (relative.toString().isEmpty()) {
            return "";
        }
        return relative.toString().replace('\\', '/');
    }

    /**
     * 从相对路径获取绝对路径（已校验）
     */
    public static Path getAbsolutePath(Path root, String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return root;
        }
        return root.resolve(relativePath).normalize();
    }
}