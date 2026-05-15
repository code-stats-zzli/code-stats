package com.codestats.framework.codestats.pipeline;

import com.codestats.framework.codestats.config.Config;
import com.codestats.framework.codestats.core.StatisticsResult;
import com.codestats.framework.codestats.output.*;

import java.io.IOException;

public class OutputStage implements Stage<StatisticsResult, Void> {
    private final OutputFormatter formatter;

    public OutputStage(Config config) throws IOException {
        OutputWriter writer = "file".equalsIgnoreCase(config.getOutputFormat()) && config.getOutputFile() != null ?
                new FileWriter(config.getOutputFile()) : new ConsoleWriter();
        String format = config.getOutputFormat();
        if ("json".equalsIgnoreCase(format)) {
            this.formatter = new JsonFormatter(writer);
        } else if ("csv".equalsIgnoreCase(format)) {
            this.formatter = new CsvFormatter(writer);
        } else {
            this.formatter = new ConsoleFormatter(writer);
        }
    }

    @Override
    public Void process(StatisticsResult result) throws IOException {
        // 从 Pipeline 获取开始时间，计算耗时并设置到结果对象中
        Long startTime = Pipeline.START_TIME_HOLDER.get();
        if (startTime != null) {
            result.setDurationMs(System.currentTimeMillis() - startTime);
        }
        formatter.format(result);
        return null;
    }
}