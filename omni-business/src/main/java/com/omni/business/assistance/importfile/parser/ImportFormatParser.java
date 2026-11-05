package com.omni.business.assistance.importfile.parser;

import com.omni.business.assistance.importfile.model.ParseResult;

public interface ImportFormatParser {
    String getName();           // 唯一标识，如 "file-block"
    String getDisplayName();    // 展示名称
    int getPriority();          // 数值越大越优先探测
    boolean supports(String text);
    ParseResult parse(String text);
}