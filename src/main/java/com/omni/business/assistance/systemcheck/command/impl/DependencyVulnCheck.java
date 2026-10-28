package com.omni.business.assistance.systemcheck.command.impl;

import com.omni.business.assistance.systemcheck.command.AbstractSystemCheckCommand;
import com.omni.business.assistance.systemcheck.command.SystemCheckContext;
import com.omni.business.assistance.systemcheck.model.Finding;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 依赖漏洞扫描（内置离线漏洞库，简化版）
 * 只扫描用户项目目录下的 pom.xml / build.gradle / package.json / requirements.txt / go.mod
 */
public class DependencyVulnCheck extends AbstractSystemCheckCommand {

    // {名称, 生态, 最低受影响版本(空=不限), 修复版本, CVE, 说明}
    private static final String[][] VULN_DB = {
            // Maven
            {"log4j-core","maven","2.0.0-beta9","2.17.1","CVE-2021-44228","Log4Shell 远程代码执行"},
            {"fastjson","maven","","1.2.83","CVE-2022-25845","反序列化 RCE"},
            {"jackson-databind","maven","","2.12.7.1","CVE-2020-36518","反序列化 DoS"},
            {"spring-webmvc","maven","5.2.0","5.2.20","CVE-2022-22965","Spring4Shell RCE"},
            {"spring-webmvc","maven","5.3.0","5.3.18","CVE-2022-22965","Spring4Shell RCE"},
            {"struts2-core","maven","","2.5.29","CVE-2021-31805","S2-062 OGNL RCE"},
            {"shiro-core","maven","","1.11.0","CVE-2023-22602","Spring Boot 集成认证绕过"},
            {"xstream","maven","","1.4.20","CVE-2022-40151","反序列化 RCE"},
            {"commons-text","maven","1.5.0","1.10.0","CVE-2022-42889","Text4Shell RCE"},
            {"snakeyaml","maven","","1.33","CVE-2022-1471","构造器反序列化 RCE"},
            {"commons-collections","maven","","3.2.2","CVE-2015-6420","反序列化漏洞利用链"},
            // npm
            {"lodash","npm","","4.17.21","CVE-2021-23337","原型污染/命令注入"},
            {"axios","npm","","1.7.4","CVE-2024-39338","SSRF"},
            {"moment","npm","","2.29.4","CVE-2022-31129","ReDoS"},
            {"minimist","npm","","1.2.7","CVE-2022-3517","ReDoS"},
            {"jsonwebtoken","npm","","9.0.0","CVE-2022-23529","RCE"},
            {"ejs","npm","","3.1.10","CVE-2024-33883","RCE"},
            // pip
            {"django","pip","","3.2.24","CVE-2024-27351","ReDoS(3.2.x)"},
            {"django","pip","4.0","4.2.11","CVE-2024-27351","ReDoS(4.x)"},
            {"flask","pip","","2.2.5","CVE-2023-30861","信息泄露(2.2.x)"},
            {"jinja2","pip","","3.1.4","CVE-2024-34064","XSS"},
            {"requests","pip","","2.32.2","CVE-2024-35195","TLS 证书校验绕过"},
            {"pyyaml","pip","","5.4","CVE-2020-14343","反序列化 RCE"},
            {"pillow","pip","","10.3.0","CVE-2024-28219","缓冲区溢出"}
    };

    @Override public String getId() { return "dependency-vuln"; }
    @Override public String getName() { return "依赖漏洞扫描"; }

    @Override
    public List<Finding> execute(SystemCheckContext ctx) {
        List<Finding> list = new ArrayList<>();
        List<String[]> hits = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        Set<String> manifests = new HashSet<>(Arrays.asList(
                "pom.xml","build.gradle","build.gradle.kts","package.json","requirements.txt","go.mod"));

        // 扫描位置：用户主目录（简化，不扫全盘）
        Path rootPath = Paths.get(System.getProperty("user.home", ""));
        if (!Files.exists(rootPath)) {
            list.add(new Finding("依赖漏洞", "第三方依赖扫描", "合规", "信息", "主目录不可访问"));
            return list;
        }
        int maxDepth = ctx.getConfig().getDependencyVulnMaxDepth();

        try {
            Files.walkFileTree(rootPath, EnumSet.noneOf(FileVisitOption.class),
                    maxDepth, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir,
                                                                 java.nio.file.attribute.BasicFileAttributes attrs) {
                            String name = dir.getFileName() == null ? "" : dir.getFileName().toString().toLowerCase();
                            if (name.equals("node_modules") || name.equals(".git") || name.equals(".idea")
                                    || name.equals(".gradle") || name.equals("target") || name.equals("build")
                                    || name.equals(".m2") || name.equals(".cache")) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            return FileVisitResult.CONTINUE;
                        }
                        @Override
                        public FileVisitResult visitFile(Path file,
                                                         java.nio.file.attribute.BasicFileAttributes attrs) {
                            String name = file.getFileName().toString().toLowerCase();
                            if (!manifests.contains(name)) return FileVisitResult.CONTINUE;
                            String eco = name.equals("pom.xml") || name.equals("build.gradle") || name.equals("build.gradle.kts") ? "maven"
                                    : name.equals("package.json") ? "npm" :
                                      name.equals("requirements.txt") ? "pip" :
                                      name.equals("go.mod") ? "go" : "";
                            for (String[] dep : parseManifest(file.toString(), eco)) {
                                String dn = dep[0].toLowerCase();
                                String dv = dep[1];
                                if (!usable(dv)) continue;
                                for (String[] r : VULN_DB) {
                                    if (!r[1].equals(eco) || !r[0].equals(dn)) continue;
                                    if (!r[2].isEmpty() && cmp(dv, r[2]) < 0) continue;
                                    if (cmp(dv, r[3]) >= 0) continue;
                                    String key = eco + "|" + r[0] + "|" + dv + "|" + r[4];
                                    if (seen.add(key)) hits.add(new String[]{file.toString(), r[0], dv, r[4], r[5], r[3]});
                                }
                            }
                            return FileVisitResult.CONTINUE;
                        }
                        @Override
                        public FileVisitResult visitFileFailed(Path file, java.io.IOException exc) {
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (Exception ignored) {}

        if (hits.isEmpty()) {
            list.add(new Finding("依赖漏洞", "第三方依赖漏洞版本", "合规", "信息",
                    "未发现已知漏洞版本(本地漏洞库)"));
        } else {
            for (String[] h : hits) {
                list.add(new Finding("依赖漏洞", "第三方依赖漏洞版本", "不合规", "高",
                        h[0] + " : " + h[1] + "@" + h[2] + " (" + h[3] + " " + h[4] + ") → 请升级至 " + h[5] + " 及以上"));
            }
        }
        return list;
    }

    private List<String[]> parseManifest(String path, String eco) {
        List<String[]> out = new ArrayList<>();
        try {
            String c = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
            if (eco.equals("maven")) {
                Matcher dm = Pattern.compile("<dependency>(.*?)</dependency>", Pattern.DOTALL).matcher(c);
                while (dm.find()) {
                    String block = dm.group(1);
                    String a = tag(block, "artifactId");
                    String v = tag(block, "version");
                    if (a != null && v != null && !v.contains("${")) out.add(new String[]{a, v});
                }
                if (path.endsWith(".gradle") || path.endsWith(".gradle.kts")) {
                    Matcher gm = Pattern.compile(
                                    "(?:implementation|api|compile)\\s*\\(?\\s*['\"]([^'\"$:]+):([^'\"$:]+):([^'\"$]+)['\"]")
                            .matcher(c);
                    while (gm.find()) out.add(new String[]{gm.group(2), gm.group(3)});
                }
            } else if (eco.equals("npm")) {
                Matcher m = Pattern.compile("\"(dependencies|devDependencies)\"\\s*:\\s*\\{([^{}]*)\\}").matcher(c);
                while (m.find()) {
                    Matcher e = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"").matcher(m.group(2));
                    while (e.find()) out.add(new String[]{e.group(1), e.group(2)});
                }
            } else if (eco.equals("pip")) {
                for (String line : c.split("\n")) {
                    Matcher m = Pattern.compile("^\\s*([A-Za-z0-9._-]+)\\s*[=<>~!]+\\s*([0-9][A-Za-z0-9._-]*)").matcher(line);
                    if (m.find()) out.add(new String[]{m.group(1), m.group(2)});
                }
            } else if (eco.equals("go")) {
                Matcher m = Pattern.compile("require\\s*\\(([^)]*)\\)").matcher(c);
                while (m.find()) {
                    Matcher e = Pattern.compile("^\\s*([^\\s]+)\\s+v?([0-9][^\\s]*)", Pattern.MULTILINE).matcher(m.group(1));
                    while (e.find()) out.add(new String[]{e.group(1), e.group(2)});
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static String tag(String block, String t) {
        Matcher m = Pattern.compile("<" + t + ">\\s*([^<]+?)\\s*</" + t + ">").matcher(block);
        return m.find() ? m.group(1).trim() : null;
    }

    private static boolean usable(String v) {
        return v != null && v.matches(".*\\d.*") && !v.contains("${")
                && !v.contains("~") && !v.contains("^") && !v.contains("*");
    }

    private static long[] parseVer(String v) {
        if (v == null) return new long[0];
        String s = v.trim().toLowerCase();
        int i = 0;
        while (i < s.length() && !Character.isDigit(s.charAt(i))) i++;
        s = s.substring(i);
        String[] parts = s.split("[^0-9]");
        long[] nums = new long[parts.length];
        for (int j = 0; j < parts.length; j++) {
            try { nums[j] = Long.parseLong(parts[j]); } catch (Exception e) { nums[j] = 0; }
        }
        return nums;
    }

    private static int cmp(String a, String b) {
        long[] x = parseVer(a), y = parseVer(b);
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            long xi = i < x.length ? x[i] : 0;
            long yi = i < y.length ? y[i] : 0;
            if (xi != yi) return xi < yi ? -1 : 1;
        }
        return 0;
    }
}