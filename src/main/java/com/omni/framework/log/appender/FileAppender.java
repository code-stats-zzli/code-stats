package com.omni.framework.log.appender;

import com.omni.framework.log.core.LogEvent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public class FileAppender extends Appender {
    private PrintWriter writer;
    private final File resolvedFile;

    public FileAppender(String fileName) {
        // ★ 路径兜底：配置路径不可写则回退
        this.resolvedFile = resolveWritableFile(fileName);
        try {
            this.writer = new PrintWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(this.resolvedFile, true),
                            StandardCharsets.UTF_8));
            System.out.println("[FileAppender] 日志文件已打开: "
                    + this.resolvedFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("[FileAppender] 打开日志文件失败: "
                    + this.resolvedFile.getAbsolutePath()
                    + " - " + e.getClass().getSimpleName() + ": " + e.getMessage());
            this.writer = null;
        }
    }

    /**
     * 优先用配置路径；父目录不可创建或文件不可写时，
     * 依次回退到 user.dir/logs/ 与 user.home/logs/。
     */
    private static File resolveWritableFile(String configured) {
        // 1. 配置路径
        File f = toAbsolute(configured);
        if (ensureParentAndWritable(f)) return f;

        // 2. 回退 1：user.dir/logs/<原文件名>
        String baseName = f.getName();
        File fb1 = new File(new File(System.getProperty("user.dir", "."), "logs"), baseName);
        if (ensureParentAndWritable(fb1)) {
            System.err.println("[FileAppender] 配置路径不可用: " + configured
                    + "，已回退到: " + fb1.getAbsolutePath());
            return fb1;
        }

        // 3. 回退 2：user.home/logs/<原文件名>
        File fb2 = new File(new File(System.getProperty("user.home", "."), "logs"), baseName);
        if (ensureParentAndWritable(fb2)) {
            System.err.println("[FileAppender] 配置路径不可用: " + configured
                    + "，已回退到: " + fb2.getAbsolutePath());
            return fb2;
        }

        // 4. 全失败：返回原路径，后续 open 会给出明确错误
        return f;
    }

    private static File toAbsolute(String path) {
        if (path == null || path.trim().isEmpty()) {
            return new File("logs/app.log").getAbsoluteFile();
        }
        File f = new File(path.trim());
        return f.isAbsolute() ? f : f.getAbsoluteFile();
    }

    private static boolean ensureParentAndWritable(File file) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                if (!parent.mkdirs() && !parent.exists()) return false;
            }
            if (file.exists()) {
                return file.canWrite();
            }
            return file.createNewFile() || file.exists();
        } catch (Exception e) {
            return false;
        }
    }

    /** 供 RollingFileAppender 使用 */
    protected String getResolvedPath() {
        return resolvedFile.getAbsolutePath();
    }

    @Override
    protected void append(LogEvent event) {
        if (writer != null) {
            writer.print(layout.format(event));
            writer.flush();
        }
    }

    @Override
    public void close() {
        if (writer != null) writer.close();
    }
}