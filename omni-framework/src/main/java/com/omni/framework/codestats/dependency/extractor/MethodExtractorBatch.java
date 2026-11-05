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

public class MethodExtractorBatch {
    private static final Logger log = LoggerFactory.getLogger(MethodExtractorBatch.class);

    public static MethodCallGraph extractAll(Path srcDir) throws IOException {
        return extractAll(srcDir, Collections.emptyList(), false);
    }

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
                if (dir.equals(root)) return FileVisitResult.CONTINUE;
                String relative = root.relativize(dir).toString().replace('\\', '/');
                String dirName = dir.getFileName() == null ? "" : dir.getFileName().toString();
                for (String ex : excludes) {
                    if (ex == null || ex.isEmpty()) continue;
                    if (dirName.equalsIgnoreCase(ex) || relative.contains(ex)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
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

        Map<String, List<MethodInfo>> nameIndex = new HashMap<>();
        for (MethodInfo m : graph.getAllMethods()) {
            nameIndex.computeIfAbsent(m.getMethodName(), k -> new ArrayList<>()).add(m);
        }

        for (MethodExtractor ex : extractors) {
            for (MethodExtractor.RawCall rc : ex.getRawCalls()) {
                List<MethodInfo> candidates = matchCandidates(rc, nameIndex);
                if (candidates.isEmpty()) continue;
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

    private static List<MethodInfo> matchCandidates(MethodExtractor.RawCall rc,
                                                    Map<String, List<MethodInfo>> nameIndex) {
        List<MethodInfo> pool = nameIndex.getOrDefault(rc.calleeName, Collections.emptyList());
        if (pool.isEmpty()) return Collections.emptyList();

        List<MethodInfo> byArg = new ArrayList<>();
        for (MethodInfo m : pool) {
            if (m.getParameterTypes().size() == rc.argCount) byArg.add(m);
        }
        List<MethodInfo> candidates = byArg.isEmpty() ? pool : byArg;

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