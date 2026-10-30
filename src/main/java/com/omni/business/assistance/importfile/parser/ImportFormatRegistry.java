package com.omni.business.assistance.importfile.parser;

import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Component;
import com.omni.framework.spring.context.SpringContextHolder;

import java.util.*;

@Component
public class ImportFormatRegistry {
    private static final Logger log = LoggerFactory.getLogger(ImportFormatRegistry.class);

    private volatile List<ImportFormatParser> parsers;

    public List<ImportFormatParser> getAll() throws Exception { return loadParsers(); }

    public ImportFormatParser find(String name) throws Exception {
        if (name == null) return null;
        for (ImportFormatParser p : loadParsers()) {
            if (p.getName().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    /** 按 priority 降序探测 */
    public ImportFormatParser detect(String text) throws Exception {
        if (text == null || text.trim().isEmpty()) return null;
        for (ImportFormatParser p : loadParsers()) {
            try {
                if (p.supports(text)) return p;
            } catch (Exception e) {
                log.warn("格式探测异常: {}", p.getName(), e);
            }
        }
        return null;
    }

    private List<ImportFormatParser> loadParsers() throws Exception {
        if (parsers != null) return parsers;
        synchronized (this) {
            if (parsers != null) return parsers;
            Map<String, ImportFormatParser> beans = SpringContextHolder
                    .getApplicationContext().getBeanFactory()
                    .getBeansOfType(ImportFormatParser.class);
            List<ImportFormatParser> list = new ArrayList<>(beans.values());
            list.sort((a, b) -> b.getPriority() - a.getPriority());
            for (ImportFormatParser p : list) {
                log.info("注册 ImportFormatParser: {} (priority={})", p.getName(), p.getPriority());
            }
            parsers = Collections.unmodifiableList(list);
            return parsers;
        }
    }
}