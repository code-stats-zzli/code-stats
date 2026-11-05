package com.omni.framework.spider.storage;

import com.omni.framework.spider.model.Article;

public interface Storage {
    void save(Article article) throws Exception;
    void close();
}