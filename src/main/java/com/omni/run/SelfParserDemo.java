package com.omni.run;

import com.omni.framework.codestats.dependency.extractor.JavaDependencyExtractor;
import com.omni.framework.codestats.dependency.model.ExtractedResult;

import java.nio.file.Path;
import java.nio.file.Paths;

public class SelfParserDemo {
    public static void main(String[] args) throws Exception {
        // 假设存在测试文件，这里直接解析自身源码
        Path demoFile = Paths.get("src/main/java/com/omni/run/SelfParserDemo.java");
        if (!demoFile.toFile().exists()) {
            System.out.println("找不到文件，请手动指定一个 Java 文件路径");
            return;
        }
        JavaDependencyExtractor extractor = new JavaDependencyExtractor();
        ExtractedResult result = extractor.extract(demoFile);
        System.out.println("类信息: " + result.getClassInfo());
        System.out.println("依赖: " + result.getDependencies());
        System.out.println("汇总: " + result.getResult());
    }
}