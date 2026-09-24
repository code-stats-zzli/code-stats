package com.omni.framework.codestats.dependency.extractor;


import com.omni.framework.codestats.dependency.model.MethodCallEdge;
import com.omni.framework.codestats.dependency.model.MethodCallGraph;
import com.omni.framework.codestats.dependency.model.MethodInfo;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * 批量扫描目录，聚合所有文件的方法调用图。
 *
 * <h3>跨文件配对策略</h3>
 * <ol>
 *   <li>优先按 receiverTypeHint + 参数个数匹配</li>
 *   <li>否则按方法名 + 参数个数匹配</li>
 *   <li>多个候选时全部建边并标注 unresolved</li>
 * </ol>
 */
public class MethodExtractorBatch {
    private static final Logger log = LoggerFactory.getLogger(MethodExtractorBatch.class);

    /** 兼容旧调用：不排除任何路径 */
    public static MethodCallGraph extractAll(Path srcDir) throws IOException {
        return extractAll(srcDir, Collections.emptyList(), false);
    }

    /**
     * 带过滤的批量抽取。
     *
     * @param srcDir       源码根目录
     * @param excludePaths 排除的路径片段（命中则跳过整个子树）
     * @param excludeTest  是否排除 test / tests 目录
     */
    public static MethodCallGraph extractAll(Path srcDir,
                                             List<String> excludePaths,
                                             boolean excludeTest) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        if (srcDir == null || !Files.exists(srcDir)) {
            log.warn("目录不存在: {}", srcDir);
            return new MethodCallGraph();
        }

        final Path root = srcDir.toAbsolutePath().normalize();
        final List<String> excludes = excludePaths == null ? Collections.emptyList() : excludePaths;

        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                // 跳过根目录本身
                if (dir.equals(root)) return FileVisitResult.CONTINUE;

                String relative = root.relativize(dir).toString().replace('\\', '/');
                String dirName = dir.getFileName() == null ? "" : dir.getFileName().toString();

                // 排除路径片段
                for (String ex : excludes) {
                    if (ex == null || ex.isEmpty()) continue;
                    // 命中目录名或相对路径片段即跳过
                    if (dirName.equalsIgnoreCase(ex) || relative.contains(ex)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                // 排除 test 目录
                if (excludeTest) {
                    if ("test".equalsIgnoreCase(dirName) || "tests".equalsIgnoreCase(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) javaFiles.add(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        log.info("扫描目录 {}，命中 {} 个 Java 文件", root, javaFiles.size());

        MethodCallGraph graph = new MethodCallGraph();

        // ============ 第一遍：抽取所有文件的方法和原始调用 ============
        List<MethodExtractor> extractors = new ArrayList<>();
        for (Path f : javaFiles) {
            try {
                MethodExtractor ex = new MethodExtractor(f);
                ex.extract();
                extractors.add(ex);
                for (MethodInfo m : ex.getMethods()) graph.addMethod(m);
            } catch (Exception e) {
                log.warn("抽取失败: {}", f, e);
            }
        }

        // ============ 第二遍：配对调用 ============
        Map<String, List<MethodInfo>> nameIndex = new HashMap<>();
        for (MethodInfo m : graph.getAllMethods()) {
            nameIndex.computeIfAbsent(m.getMethodName(), k -> new ArrayList<>()).add(m);
        }

        for (MethodExtractor ex : extractors) {
            for (MethodExtractor.RawCall rc : ex.getRawCalls()) {
                List<MethodInfo> candidates = matchCandidates(rc, nameIndex);
                if (candidates.isEmpty()) continue;  // 可能是 JDK/第三方方法，忽略
                boolean resolved = candidates.size() == 1;
                for (MethodInfo callee : candidates) {
                    MethodCallEdge.CallType type = rc.isConstructor
                            ? MethodCallEdge.CallType.SPECIAL
                            : (callee.isStatic() ? MethodCallEdge.CallType.STATIC
                               : MethodCallEdge.CallType.VIRTUAL);
                    graph.addCall(new MethodCallEdge(rc.callerId, callee.getId(),
                            type, rc.line, resolved));
                }
            }
        }

        log.info("方法级抽取完成: {} 个文件, {} 个方法, {} 条调用",
                javaFiles.size(), graph.size(), graph.edgeCount());
        return graph;
    }

    /** 匹配候选方法 */
    private static List<MethodInfo> matchCandidates(MethodExtractor.RawCall rc,
                                                    Map<String, List<MethodInfo>> nameIndex) {
        List<MethodInfo> pool = nameIndex.getOrDefault(rc.calleeName, Collections.emptyList());
        if (pool.isEmpty()) return Collections.emptyList();

        // 按参数个数过滤
        List<MethodInfo> byArg = new ArrayList<>();
        for (MethodInfo m : pool) {
            if (m.getParameterTypes().size() == rc.argCount) byArg.add(m);
        }
        List<MethodInfo> candidates = byArg.isEmpty() ? pool : byArg;

        // 如果有 receiver 类型提示，进一步过滤
        if (rc.receiverTypeHint != null && !rc.receiverTypeHint.isEmpty()) {
            List<MethodInfo> byType = new ArrayList<>();
            for (MethodInfo m : candidates) {
                String cn = m.getClassName();
                String simple = cn.contains(".") ? cn.substring(cn.lastIndexOf('.') + 1) : cn;
                if (simple.equals(rc.receiverTypeHint)
                        || cn.endsWith("." + rc.receiverTypeHint)) {
                    byType.add(m);
                }
            }
            if (!byType.isEmpty()) return byType;
        }
        return candidates;
    }
}