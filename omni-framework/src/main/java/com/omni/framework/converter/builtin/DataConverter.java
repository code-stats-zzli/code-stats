package com.omni.framework.converter.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.omni.framework.converter.ConvertException;
import com.omni.framework.converter.ConvertRequest;
import com.omni.framework.converter.ConvertResult;
import com.omni.framework.converter.Converter;
import com.omni.framework.converter.FormatType;
import com.omni.framework.spring.annotation.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * 结构化数据转换器：JSON ↔ XML / CSV / YAML / Properties / TXT / HTML。
 */
@Component
public class DataConverter implements Converter {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override public String getName() { return "data"; }

    @Override public Set<FormatType> supportedSources() {
        return EnumSet.of(FormatType.JSON, FormatType.XML, FormatType.CSV,
                FormatType.YAML, FormatType.PROPERTIES);
    }

    @Override public Set<FormatType> supportedTargets() {
        return EnumSet.of(FormatType.JSON, FormatType.XML, FormatType.CSV,
                FormatType.YAML, FormatType.PROPERTIES, FormatType.TXT, FormatType.HTML);
    }

    @Override public int getPriority() { return 100; }

    @Override
    public ConvertResult convert(ConvertRequest req) throws ConvertException {
        try {
            JsonNode node = readNode(req);
            String base = req.baseName();
            switch (req.getTargetType()) {
                case JSON:
                    return ConvertResult.okText(FormatType.JSON,
                            JSON.writerWithDefaultPrettyPrinter().writeValueAsString(node), base + ".json");
                case XML:
                    return ConvertResult.okText(FormatType.XML, toXml(node), base + ".xml");
                case CSV:
                    return ConvertResult.okText(FormatType.CSV, toCsv(node), base + ".csv");
                case YAML:
                    return ConvertResult.okText(FormatType.YAML, toYaml(node), base + ".yaml");
                case PROPERTIES:
                    return ConvertResult.okText(FormatType.PROPERTIES, toProps(node), base + ".properties");
                case TXT:
                    return ConvertResult.okText(FormatType.TXT,
                            JSON.writerWithDefaultPrettyPrinter().writeValueAsString(node), base + ".txt");
                case HTML:
                    return ConvertResult.okText(FormatType.HTML, toHtml(node), base + ".html");
                default:
                    throw new ConvertException("Data 不支持输出到 " + req.getTargetType());
            }
        } catch (ConvertException e) {
            throw e;
        } catch (Exception e) {
            throw new ConvertException("Data 转换失败: " + e.getMessage(), e);
        }
    }

    private JsonNode readNode(ConvertRequest req) throws Exception {
        FormatType src = req.getSourceType();
        String text = req.readSourceText();
        if (src == FormatType.JSON) return JSON.readTree(text);
        if (src == FormatType.XML)  return xmlToJson(text);
        if (src == FormatType.CSV)  return csvToJson(text);
        if (src == FormatType.YAML) {
            Object obj = new Yaml().load(text);
            return JSON.valueToTree(obj);
        }
        if (src == FormatType.PROPERTIES) {
            Properties p = new Properties();
            p.load(new StringReader(text));
            ObjectNode obj = JSON.createObjectNode();
            for (String k : p.stringPropertyNames()) obj.put(k, p.getProperty(k));
            return obj;
        }
        throw new ConvertException("Data 不支持作为源: " + src);
    }

    // ---------- JSON → XML ----------
    private String toXml(JsonNode node) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n");
        writeXml(sb, node, 1);
        sb.append("</root>");
        return sb.toString();
    }

    private void writeXml(StringBuilder sb, JsonNode node, int depth) {
        StringBuilder indentBuilder = new StringBuilder();
        for (int i = 0; i < depth; i++) indentBuilder.append("  ");
        String indent = indentBuilder.toString();
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String key = esc(e.getKey());
                sb.append(indent).append('<').append(key).append('>');
                if (e.getValue().isValueNode()) {
                    sb.append(esc(e.getValue().asText()));
                } else {
                    sb.append('\n');
                    writeXml(sb, e.getValue(), depth + 1);
                    sb.append(indent);
                }
                sb.append("</").append(key).append(">\n");
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                sb.append(indent).append("<item>");
                if (item.isValueNode()) {
                    sb.append(esc(item.asText()));
                } else {
                    sb.append('\n');
                    writeXml(sb, item, depth + 1);
                    sb.append(indent);
                }
                sb.append("</item>\n");
            }
        } else {
            sb.append(indent).append(esc(node.asText())).append('\n');
        }
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    // ---------- JSON → CSV ----------
    private String toCsv(JsonNode node) throws ConvertException {
        if (!node.isArray()) {
            if (node.isObject()) {
                ArrayNode arr = JSON.createArrayNode();
                arr.add(node);
                node = arr;
            } else {
                throw new ConvertException("CSV 输出要求源是对象或对象数组");
            }
        }
        if (node.size() == 0) return "";
        List<String> headers = new ArrayList<>();
        node.get(0).fieldNames().forEachRemaining(headers::add);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < headers.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(csvEsc(headers.get(i)));
        }
        sb.append('\n');
        for (JsonNode row : node) {
            for (int i = 0; i < headers.size(); i++) {
                if (i > 0) sb.append(',');
                JsonNode v = row.get(headers.get(i));
                sb.append(csvEsc(v == null || v.isNull() ? "" : v.asText()));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private String csvEsc(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    // ---------- JSON → YAML ----------
    private String toYaml(JsonNode node) {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setIndent(2);
        Yaml yaml = new Yaml(opts);
        return yaml.dump(JSON.convertValue(node, Object.class));
    }

    // ---------- JSON → Properties ----------
    private String toProps(JsonNode node) {
        StringBuilder sb = new StringBuilder();
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (e.getValue().isValueNode())
                    sb.append(e.getKey()).append('=').append(e.getValue().asText()).append('\n');
            }
        }
        return sb.toString();
    }

    // ---------- JSON → HTML ----------
    private String toHtml(JsonNode node) throws Exception {
        String json = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>JSON View</title></head>"
                + "<body style=\"background:#0a0f1a;color:#e2e8f0;font-family:monospace;padding:20px;\">"
                + "<pre>" + esc(json) + "</pre></body></html>";
    }

    // ---------- XML → JsonNode（JDK DOM） ----------
    private JsonNode xmlToJson(String xml) throws Exception {
        javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setExpandEntityReferences(false);
        org.w3c.dom.Document doc = f.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
        return JSON.valueToTree(domToObj(doc.getDocumentElement()));
    }

    private Object domToObj(org.w3c.dom.Element el) {
        Map<String, Object> map = new LinkedHashMap<>();
        org.w3c.dom.NamedNodeMap attrs = el.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            org.w3c.dom.Node a = attrs.item(i);
            map.put(a.getNodeName(), a.getNodeValue());
        }
        org.w3c.dom.NodeList children = el.getChildNodes();
        boolean hasChild = false;
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node c = children.item(i);
            if (c.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                hasChild = true;
                String name = c.getNodeName();
                Object val = domToObj((org.w3c.dom.Element) c);
                if (map.containsKey(name)) {
                    Object existing = map.get(name);
                    if (!(existing instanceof List)) {
                        List<Object> list = new ArrayList<>();
                        list.add(existing);
                        map.put(name, list);
                    }
                    @SuppressWarnings("unchecked")
                    List<Object> list = (List<Object>) map.get(name);
                    list.add(val);
                } else {
                    map.put(name, val);
                }
            } else if (c.getNodeType() == org.w3c.dom.Node.TEXT_NODE
                    || c.getNodeType() == org.w3c.dom.Node.CDATA_SECTION_NODE) {
                text.append(c.getNodeValue());
            }
        }
        String t = text.toString().trim();
        if (!hasChild) {
            if (map.isEmpty()) return t;
            if (!t.isEmpty()) map.put("#text", t);
            return map;
        }
        return map;
    }

    // ---------- CSV → JsonNode ----------
    private JsonNode csvToJson(String csv) {
        List<List<String>> rows = parseCsv(csv);
        ArrayNode arr = JSON.createArrayNode();
        if (rows.isEmpty()) return arr;
        List<String> headers = rows.get(0);
        for (int i = 1; i < rows.size(); i++) {
            ObjectNode obj = JSON.createObjectNode();
            List<String> row = rows.get(i);
            for (int j = 0; j < headers.size(); j++)
                obj.put(headers.get(j), j < row.size() ? row.get(j) : "");
            arr.add(obj);
        }
        return arr;
    }

    private List<List<String>> parseCsv(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (inQuote) {
                if (c == '"') {
                    if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        inQuote = false;
                    }
                } else {
                    cell.append(c);
                }
            } else {
                if (c == '"') {
                    inQuote = true;
                } else if (c == ',') {
                    row.add(cell.toString());
                    cell.setLength(0);
                } else if (c == '\n') {
                    row.add(cell.toString());
                    cell.setLength(0);
                    rows.add(row);
                    row = new ArrayList<>();
                } else if (c != '\r') {
                    cell.append(c);
                }
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }
}