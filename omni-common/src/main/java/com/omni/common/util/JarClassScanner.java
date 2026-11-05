package com.omni.common.util;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * JAR 类扫描工具（用于驱动加载失败的诊断）。
 *
 * <p>当 JAR 中不存在期望的驱动类时，本工具扫描 JAR 内所有 class，
 * 按"可能驱动类"的启发式过滤后返回，供用户在界面上直接点选修正 driverClass。</p>
 *
 * <p>"可能驱动类"判定规则：</p>
 * <ul>
 *   <li>排除内部类（含 {@code $}）</li>
 *   <li>排除 {@code META-INF/} 下的类</li>
 *   <li>类名以 {@code Driver} / {@code driver} 结尾</li>
 *   <li>包路径包含 {@code .jdbc.} 或 {@code .driver.}</li>
 *   <li>排除 {@code .test.} 包、{@code Test} / {@code Tests} 结尾</li>
 * </ul>
 *
 * <p>纯诊断工具，任何异常都静默吞掉，绝不影响主流程。</p>
 */
public final class JarClassScanner {

    private JarClassScanner() {}

    private static final int DEFAULT_LIMIT = 50;

    /** 从 byte[] 扫描 */
    public static List<String> findDriverCandidates(byte[] jarData) {
        if (jarData == null || jarData.length == 0) return Collections.emptyList();
        try (JarInputStream jis = new JarInputStream(new ByteArrayInputStream(jarData))) {
            return scan(jis, JarClassScanner::isPossibleDriverClass, DEFAULT_LIMIT);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** 从 Path 扫描 */
    public static List<String> findDriverCandidates(Path jarPath) {
        if (jarPath == null || !Files.isRegularFile(jarPath)) return Collections.emptyList();
        try (InputStream is = Files.newInputStream(jarPath);
             JarInputStream jis = new JarInputStream(is)) {
            return scan(jis, JarClassScanner::isPossibleDriverClass, DEFAULT_LIMIT);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** 列出 JAR 内所有类（仅供高级诊断，限制上限） */
    public static List<String> listAllClasses(byte[] jarData, int limit) {
        if (jarData == null || jarData.length == 0) return Collections.emptyList();
        if (limit <= 0) limit = DEFAULT_LIMIT;
        try (JarInputStream jis = new JarInputStream(new ByteArrayInputStream(jarData))) {
            return scan(jis, c -> true, limit);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // ==================== 内部实现 ====================

    private static List<String> scan(JarInputStream jis, Predicate<String> filter, int limit) {
        List<String> result = new ArrayList<>();
        try {
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                String name = entry.getName();
                if (!name.endsWith(".class")) continue;
                if (name.startsWith("META-INF/")) continue;
                if (name.startsWith("module-info")) continue;
                String className = name.substring(0, name.length() - 6).replace('/', '.');
                if (filter.test(className)) {
                    result.add(className);
                    if (result.size() >= limit) break;
                }
            }
        } catch (Exception e) {
            // 静默：诊断工具
        }
        Collections.sort(result);
        return result;
    }

    private static boolean isPossibleDriverClass(String className) {
        if (className.contains("$")) return false;
        String lower = className.toLowerCase();
        if (lower.contains(".test.")) return false;
        if (className.endsWith("Test") || className.endsWith("Tests")) return false;
        if (className.endsWith("Driver") || className.endsWith("driver")) return true;
        if (lower.contains(".jdbc.") || lower.contains(".driver.")) return true;
        return false;
    }
}