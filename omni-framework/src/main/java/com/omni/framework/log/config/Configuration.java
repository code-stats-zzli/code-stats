package com.omni.framework.log.config;

import com.omni.framework.log.Level;
import com.omni.framework.log.appender.*;
import com.omni.framework.log.filter.LevelRangeFilter;
import com.omni.framework.log.layout.PatternLayout;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.*;

public class Configuration {
    private final Map<String, Appender> appenders = new HashMap<>();
    private final Map<String, LoggerConfig> loggerConfigs = new HashMap<>();
    private LoggerConfig rootConfig;

    public void loadFromClasspath(String resourcePath) throws Exception {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("配置文件未找到: " + resourcePath);
            }
            loadFromStream(is);
        }
    }

    public void loadFromStream(InputStream is) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
        NodeList props = doc.getElementsByTagName("property");
        Map<String, String> properties = new HashMap<>();
        for (int i = 0; i < props.getLength(); i++) {
            Element prop = (Element) props.item(i);
            String name = prop.getAttribute("name");
            String value = prop.getTextContent();
            properties.put(name, value);
        }

        NodeList appenderNodes = doc.getElementsByTagName("appender");
        for (int i = 0; i < appenderNodes.getLength(); i++) {
            Element elem = (Element) appenderNodes.item(i);
            String name = elem.getAttribute("name");
            String className = elem.getAttribute("class");
            Appender appender = createAppender(className, elem, properties);
            appender.setName(name);
            NodeList layoutNodes = elem.getElementsByTagName("layout");
            if (layoutNodes.getLength() > 0) {
                Element layoutElem = (Element) layoutNodes.item(0);
                String layoutClass = layoutElem.getAttribute("class");
                if ("com.omni.framework.log.layout.PatternLayout".equals(layoutClass)) {
                    String pattern = getParamValue(layoutElem, "pattern", properties);
                    if (pattern == null) pattern = "%d [%t] %-5level %logger - %msg%n";
                    appender.setLayout(new PatternLayout(replaceProperties(pattern, properties)));
                }
            }
            NodeList filterNodes = elem.getElementsByTagName("filter");
            for (int j = 0; j < filterNodes.getLength(); j++) {
                Element filterElem = (Element) filterNodes.item(j);
                String filterClass = filterElem.getAttribute("class");
                if ("com.omni.framework.log.filter.LevelRangeFilter".equals(filterClass)) {
                    LevelRangeFilter filter = new LevelRangeFilter();
                    String min = getParamValue(filterElem, "minLevel", properties);
                    String max = getParamValue(filterElem, "maxLevel", properties);
                    if (min != null) filter.setMinLevel(min);
                    if (max != null) filter.setMaxLevel(max);
                    appender.addFilter(filter);
                }
            }
            appenders.put(name, appender);
        }

        NodeList loggerNodes = doc.getElementsByTagName("logger");
        for (int i = 0; i < loggerNodes.getLength(); i++) {
            Element elem = (Element) loggerNodes.item(i);
            String loggerName = elem.getAttribute("name");
            String levelStr = elem.getAttribute("level");
            boolean additivity = "true".equals(elem.getAttribute("additivity"));
            Level level = Level.toLevel(levelStr);
            LoggerConfig config = new LoggerConfig(loggerName, level, additivity);
            NodeList refs = elem.getElementsByTagName("appender-ref");
            for (int j = 0; j < refs.getLength(); j++) {
                String ref = ((Element) refs.item(j)).getAttribute("ref");
                Appender a = appenders.get(ref);
                if (a != null) config.addAppender(a);
            }
            loggerConfigs.put(loggerName, config);
        }

        NodeList rootNodes = doc.getElementsByTagName("root");
        if (rootNodes.getLength() > 0) {
            Element rootElem = (Element) rootNodes.item(0);
            String levelStr = rootElem.getAttribute("level");
            boolean additivity = "true".equals(rootElem.getAttribute("additivity"));
            Level level = Level.toLevel(levelStr);
            rootConfig = new LoggerConfig("ROOT", level, additivity);
            NodeList refs = rootElem.getElementsByTagName("appender-ref");
            for (int j = 0; j < refs.getLength(); j++) {
                String ref = ((Element) refs.item(j)).getAttribute("ref");
                Appender a = appenders.get(ref);
                if (a != null) rootConfig.addAppender(a);
            }
        }
    }

    private Appender createAppender(String className, Element elem, Map<String, String> props) throws Exception {
        if ("com.omni.framework.log.appender.ConsoleAppender".equals(className)) {
            return new ConsoleAppender();
        } else if ("com.omni.framework.log.appender.FileAppender".equals(className)) {
            return new FileAppender(getParamValue(elem, "file", props));
        } else if ("com.omni.framework.log.appender.RollingFileAppender".equals(className)) {
            String file = getParamValue(elem, "file", props);
            long maxSize = parseSize(getParamValue(elem, "maxFileSize", props));
            int maxBackup = Integer.parseInt(getParamValue(elem, "maxBackupIndex", props));
            return new RollingFileAppender(file, maxSize, maxBackup);
        } else if ("com.omni.framework.log.appender.AsyncAppender".equals(className)) {
            int queueSize = Integer.parseInt(getParamValue(elem, "queueSize", props));
            NodeList refs = elem.getElementsByTagName("appender-ref");
            if (refs.getLength() == 0) throw new IllegalArgumentException("AsyncAppender missing delegate appender");
            String delegateName = ((Element) refs.item(0)).getAttribute("ref");
            Appender delegate = appenders.get(delegateName);
            if (delegate == null) throw new IllegalArgumentException("Delegate appender not found: " + delegateName);
            return new AsyncAppender(delegate, queueSize);
        }
        throw new IllegalArgumentException("Unknown appender class: " + className);
    }

    private String getParamValue(Element elem, String paramName, Map<String, String> props) {
        NodeList nodes = elem.getElementsByTagName("param");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element param = (Element) nodes.item(i);
            if (paramName.equals(param.getAttribute("name"))) {
                return replaceProperties(param.getAttribute("value"), props);
            }
        }
        return null;
    }

    private String replaceProperties(String val, Map<String, String> props) {
        if (val == null) return null;
        for (Map.Entry<String, String> e : props.entrySet()) {
            val = val.replace("${" + e.getKey() + "}", e.getValue());
        }
        val = val.replace("${user.home}", System.getProperty("user.home", "."))
                .replace("${user.dir}", System.getProperty("user.dir", "."))
                .replace("${java.io.tmpdir}", System.getProperty("java.io.tmpdir", "."));
        return val;
    }

    private long parseSize(String s) {
        if (s == null) return 10 * 1024 * 1024;
        s = s.toUpperCase();
        if (s.endsWith("MB")) return Long.parseLong(s.substring(0, s.length() - 2)) * 1024 * 1024;
        if (s.endsWith("KB")) return Long.parseLong(s.substring(0, s.length() - 2)) * 1024;
        if (s.endsWith("B")) return Long.parseLong(s.substring(0, s.length() - 1));
        return Long.parseLong(s);
    }

    /**
     * ★ FIX: 支持父包逐级继承。
     *   修复前：只查 loggerConfigs.get(name)，命中不到直接 fallback root，
     *           导致 <logger name="com.omni" level="DEBUG"> 形同虚设。
     *   修复后：com.omni.framework.spring.mvc.DispatcherServlet
     *           → com.omni.framework.spring.mvc
     *           → com.omni.framework.spring
     *           → com.omni                 ← 命中
     */
    public LoggerConfig getLoggerConfig(String name) {
        if (name == null || name.isEmpty()) {
            return rootConfig;
        }
        LoggerConfig config = loggerConfigs.get(name);
        if (config != null) return config;

        int idx = name.length();
        while ((idx = name.lastIndexOf('.', idx - 1)) > 0) {
            config = loggerConfigs.get(name.substring(0, idx));
            if (config != null) return config;
        }
        return rootConfig;
    }

    public void useDefaultConsoleConfig() {
        PatternLayout layout = new PatternLayout("%d [%t] %-5level %logger - %msg%n");
        ConsoleAppender console = new ConsoleAppender();
        console.setLayout(layout);
        console.setName("CONSOLE");
        appenders.put("CONSOLE", console);

        rootConfig = new LoggerConfig("ROOT", Level.DEBUG, true);
        rootConfig.addAppender(console);
    }

    public static class LoggerConfig {
        public final String name;
        public final Level level;
        public final boolean additive;
        public final List<Appender> appenders = new ArrayList<>();

        public LoggerConfig(String name, Level level, boolean additive) {
            this.name = name;
            this.level = level;
            this.additive = additive;
        }
        public void addAppender(Appender a) { appenders.add(a); }
    }
}