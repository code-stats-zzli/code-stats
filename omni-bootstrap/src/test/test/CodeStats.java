package org.test;

import com.omni.collector.FileCollector;
import com.omni.core.StatisticsResult;
import com.omni.filter.ExcludePathFilter;
import com.omni.filter.ExtensionFilter;
import com.omni.filter.FilterChain;
import com.omni.output.OutputManager;
import com.omni.statistics.LineCountStrategy;
import com.omni.statistics.StatisticStrategy;

public class CodeStats {
    public static void main(String[] args) {
        Config config = ConfigLoader.load(args);   // 见下方
        try {
            // 1. 构建过滤器链
            FilterChain filterChain = new FilterChain()
                    .add(new ExtensionFilter(config.getExtensions()))
                    .add(new ExcludePathFilter(config.getExcludePaths()));
            // 2. 收集文件
            FileCollector collector = new FileCollector(filterChain);
            var files = collector.collect(config.getRootDir());
            // 3. 统计
            StatisticStrategy strategy = new LineCountStrategy(config.isExcludeComments());
            StatisticEngine engine = new StatisticEngine(config.getThreadCount(), strategy);
            StatisticsResult result = engine.execute(files);
            // 4. 排序 & 输出
            OutputManager.getFormatter(config.getOutputFormat()).output(result, config);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}