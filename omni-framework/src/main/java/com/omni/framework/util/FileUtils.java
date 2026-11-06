package com.omni.framework.util;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;

/** ★ L1-06：读写统一走 FileIOUtils（UTF-8）。 */
public class FileUtils {

    public static Path resolveSafePath(Path root, String userPath) throws SecurityException {
        Path target;
        if (userPath == null || userPath.trim().isEmpty()) target = root;
        else target = root.resolve(userPath).normalize();
        if (!target.startsWith(root)) throw new SecurityException("路径越界访问: " + target);
        return target;
    }

    public static List<Map<String, Object>> listDirectory(Path dir, Path root) throws IOException {
        if (!Files.isDirectory(dir)) throw new IOException("路径不是目录: " + dir);
        List<Map<String, Object>> entries = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", entry.getFileName().toString());
                item.put("path", getRelativePath(root, entry));
                item.put("type", Files.isDirectory(entry) ? "directory" : "file");
                item.put("size", Files.isRegularFile(entry) ? Files.size(entry) : 0L);
                item.put("modified", sdf.format(Files.getLastModifiedTime(entry).toMillis()));
                entries.add(item);
            }
        }
        entries.sort((a, b) -> {
            if (a.get("type").equals(b.get("type"))) return ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
            return a.get("type").equals("directory") ? -1 : 1;
        });
        return entries;
    }

    public static boolean isDirectoryEmpty(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return true;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            return !stream.iterator().hasNext();
        }
    }

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
            children.sort((a, b) -> {
                if (a.get("type").equals(b.get("type"))) return ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
                return a.get("type").equals("directory") ? -1 : 1;
            });
            node.put("children", children);
        } else {
            node.put("children", Collections.emptyList());
        }
        return node;
    }

    public static void createDirectories(Path target) throws IOException { Files.createDirectories(target); }

    public static void createNewFile(Path target) throws IOException {
        Path parent = target.getParent();
        if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);
        Files.createFile(target);
    }

    public static void deletePath(Path target) throws IOException {
        if (Files.isDirectory(target)) {
            Files.walkFileTree(target, new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException { Files.delete(file); return FileVisitResult.CONTINUE; }
                @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException { Files.delete(dir); return FileVisitResult.CONTINUE; }
            });
        } else {
            Files.delete(target);
        }
    }

    public static void rename(Path source, String newName) throws IOException {
        Path target = source.resolveSibling(newName).normalize();
        Files.move(source, target);
    }

    public static String readFile(Path file) throws IOException {
        return com.omni.common.util.FileIOUtils.readString(file);
    }

    public static void writeFile(Path file, String content) throws IOException {
        com.omni.common.util.FileIOUtils.writeString(file, content);
    }

    public static String getRelativePath(Path root, Path absolutePath) {
        Path relative = root.relativize(absolutePath);
        if (relative.toString().isEmpty()) return "";
        return relative.toString().replace('\\', '/');
    }

    public static Path getAbsolutePath(Path root, String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) return root;
        return root.resolve(relativePath).normalize();
    }
}