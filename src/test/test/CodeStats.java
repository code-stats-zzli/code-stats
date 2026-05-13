package org.test;

import com.codestats.collector.FileCollector;
import com.codestats.core.StatisticsResult;
import com.codestats.filter.ExcludePathFilter;
import com.codestats.filter.ExtensionFilter;
import com.codestats.filter.FilterChain;
import com.codestats.output.OutputManager;
import com.codestats.statistics.LineCountStrategy;
import com.codestats.statistics.StatisticStrategy;

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