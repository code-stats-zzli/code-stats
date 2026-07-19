package com.omni.framework.jstats.infrastructure;

import com.omni.framework.jstats.JStatsException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class CommandExecutor {
    private final long defaultTimeoutSeconds;

    public CommandExecutor() {
        this(10);
    }

    public CommandExecutor(long defaultTimeoutSeconds) {
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
    }

    public String execute(String... command) throws JStatsException {
        return execute(defaultTimeoutSeconds, command);
    }

    public String execute(long timeoutSeconds, String... command) throws JStatsException {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new JStatsException("命令执行超时 (" + timeoutSeconds + "秒): " + String.join(" ", command));
            }

            int exitCode = process.exitValue();
            String output = readStream(process.getInputStream());
            if (exitCode != 0) {
                throw new JStatsException("命令执行失败，退出码: " + exitCode + ", 输出: " + output);
            }
            return output;
        } catch (Exception e) {
            throw new JStatsException("执行命令失败: " + String.join(" ", command), e);
        }
    }

    private String readStream(java.io.InputStream inputStream) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}