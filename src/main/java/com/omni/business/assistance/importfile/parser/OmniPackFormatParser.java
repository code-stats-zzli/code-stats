package com.omni.business.assistance.importfile.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.business.assistance.importfile.model.ImportFile;
import com.omni.business.assistance.importfile.model.ImportPack;
import com.omni.business.assistance.importfile.model.ParseResult;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OmniPackFormatParser implements ImportFormatParser {

    private static final Pattern OMNI_PACK = Pattern.compile(
            "```omni-pack\\s*\\r?\\n([\\s\\S]*?)\\r?\\n```",
            Pattern.MULTILINE);

    @Autowired
    private FileBlockFormatParser fileBlockParser;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override public String getName() { return "omni-pack"; }
    @Override public String getDisplayName() { return "Omni Pack 扩展格式 (```omni-pack + ```file:)"; }
    @Override public int getPriority() { return 100; }   // 最具体，优先探测

    @Override
    public boolean supports(String text) {
        return text != null && OMNI_PACK.matcher(text).find();
    }

    @Override
    public ParseResult parse(String text) {
        ParseResult r = new ParseResult();
        r.setFormat(getName());
        r.setFormatDisplayName(getDisplayName());

        Matcher m = OMNI_PACK.matcher(text);
        if (m.find()) {
            try {
                ImportPack pack = objectMapper.readValue(m.group(1), ImportPack.class);
                r.setPack(pack);
            } catch (Exception e) {
                r.getWarnings().add("omni-pack 元数据解析失败（忽略元数据继续）: " + e.getMessage());
            }
        }

        List<ImportFile> files = fileBlockParser.extractFileBlocks(text);
        r.setFiles(files);
        return r;
    }
}