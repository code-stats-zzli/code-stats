package com.omni.framework.spider.storage;

import com.omni.framework.spider.model.Article;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class FileStorage implements Storage {
    private final Path outputDir;

    public FileStorage(String outputDir) throws IOException {
        this.outputDir = Paths.get(outputDir);
        if (!Files.exists(this.outputDir)) {
            Files.createDirectories(this.outputDir);
        }
    }

    @Override
    public void save(Article article) throws IOException {
        // 文件名：使用标题（去掉非法字符）
        String fileName = article.getTitle().replaceAll("[\\\\/:*?\"<>|]", "_") + ".txt";
        Path filePath = outputDir.resolve(fileName);
        StringBuilder sb = new StringBuilder();
        sb.append("标题：").append(article.getTitle()).append("\n");
        sb.append("链接：").append(article.getUrl()).append("\n");
        sb.append("作者：").append(article.getAuthor()).append("\n");
        sb.append("阅读数：").append(article.getViewCount()).append("\n");
        sb.append("正文：\n").append(article.getContent()).append("\n");
        sb.append("\n").append("=".repeat(80)).append("\n");
        Files.write(filePath, sb.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    @Override
    public void close() {
        // nothing
    }
}