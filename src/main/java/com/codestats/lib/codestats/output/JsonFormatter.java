package com.codestats.lib.codestats.output;

import com.codestats.lib.codestats.core.FileInfo;
import com.codestats.lib.codestats.core.StatisticsResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class JsonFormatter extends OutputFormatter {
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    public JsonFormatter(OutputWriter writer) { super(writer); }

    @Override
    public void format(StatisticsResult result) throws IOException {
        Map<String, Object> output = new LinkedHashMap<>();
        Map<String,  Number> value = new java.util.HashMap<>();
        value.put("totalFiles", result.getFileInfos().size());
        value.put("totalLines", result.getTotalLines());
        value.put("totalCodeLines", result.getTotalCodeLines());
        value.put("totalCommentLines", result.getTotalCommentLines());
        value.put("totalBlankLines", result.getTotalBlankLines());
        value.put("durationMs", result.getDurationMs());
        output.put("summary", value);
        output.put("files", result.getFileInfos().stream().map(this::fileInfoToMap).collect(Collectors.toList()));
        output.put("languages", result.getLanguageSummary().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), e -> {
                    Map<String, Long> stringLongMap = new java.util.HashMap<>();
                    stringLongMap.put("files", e.getValue().getFiles());
                    stringLongMap.put("lines", e.getValue().getLines());
                    stringLongMap.put("code", e.getValue().getCode());
                    stringLongMap.put("comments", e.getValue().getComments());
                    stringLongMap.put("blanks", e.getValue().getBlanks());
                    return stringLongMap;
                })));
        String json = MAPPER.writeValueAsString(output);
        writer.write(json);
    }

    private Map<String, Object> fileInfoToMap(FileInfo info) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("path", info.getPath().toString());
        m.put("lines", info.getLines());
        m.put("codeLines", info.getCodeLines());
        m.put("commentLines", info.getCommentLines());
        m.put("blankLines", info.getBlankLines());
        m.put("language", info.getLanguage().name());
        m.put("complexity", info.getComplexity());
        return m;
    }
}