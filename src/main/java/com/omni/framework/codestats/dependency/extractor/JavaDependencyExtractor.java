package com.omni.framework.codestats.dependency.extractor;

import com.omni.framework.codestats.dependency.model.ClassInfo;
import com.omni.framework.codestats.dependency.model.DependencyGraph;
import com.omni.framework.codestats.dependency.model.DependencyResult;
import com.omni.framework.codestats.dependency.model.ExtractedResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 自研 Java 源码依赖提取器
 * 通过词法扫描 + 状态机提取包名、导入、类声明、字段类型、方法返回类型/参数类型
 */
public class JavaDependencyExtractor {

    public ExtractedResult extract(Path file) throws IOException {
        String source = Files.readString(file);
        SimpleJavaLexer lexer = new SimpleJavaLexer(source);
        List<SimpleJavaLexer.Token> tokens = lexer.tokenize();

        // 解析包名
        String packageName = parsePackage(tokens);
        // 解析导入列表
        List<String> imports = parseImports(tokens);
        // 解析类型声明（类/接口/枚举/注解）
        TypeDeclaration mainType = parseTypeDeclaration(tokens);
        if (mainType == null) {
            throw new IOException("无法找到类型声明: " + file);
        }

        String fqName = packageName.isEmpty() ? mainType.name : packageName + "." + mainType.name;
        ClassInfo.ClassType classType = mapClassType(mainType.kind);
        ClassInfo classInfo = new ClassInfo(fqName, packageName, mainType.name, classType);

        // 收集所有类型引用（字段、方法返回值、参数、父类/接口）
        Set<String> rawTypes = collectTypeReferences(tokens, mainType);
        TypeNameResolver resolver = new TypeNameResolver(packageName, imports);
        Set<String> resolvedDeps = new HashSet<>();
        for (String raw : rawTypes) {
            if (TypeNameResolver.isPrimitive(raw)) continue;
            String resolved = resolver.resolve(raw);
            if (resolved != null) {
                resolvedDeps.add(resolved);
            }
        }

        DependencyGraph graph = new DependencyGraph();
        for (String dep : resolvedDeps) {
            graph.addDependency(fqName, dep);
        }
        Map<String, ClassInfo> classInfoMap = new HashMap<>();
        classInfoMap.put(fqName, classInfo);
        DependencyResult result = new DependencyResult(classInfoMap, graph, 0L);
        return new ExtractedResult(classInfo, resolvedDeps, result);
    }

    // ----- 解析辅助方法 -----

    private String parsePackage(List<SimpleJavaLexer.Token> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).type == SimpleJavaLexer.TokenType.KEYWORD && "package".equals(tokens.get(i).text)) {
                StringBuilder pkg = new StringBuilder();
                int j = i + 1;
                while (j < tokens.size()) {
                    if (tokens.get(j).type == SimpleJavaLexer.TokenType.IDENTIFIER) {
                        pkg.append(tokens.get(j).text);
                    } else if (".".equals(tokens.get(j).text)) {
                        pkg.append('.');
                    } else if (";".equals(tokens.get(j).text)) {
                        return pkg.toString();
                    } else {
                        break;
                    }
                    j++;
                }
            }
        }
        return "";
    }

    private List<String> parseImports(List<SimpleJavaLexer.Token> tokens) {
        List<String> imports = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).type == SimpleJavaLexer.TokenType.KEYWORD && "import".equals(tokens.get(i).text)) {
                StringBuilder imp = new StringBuilder();
                int j = i + 1;
                while (j < tokens.size()) {
                    String text = tokens.get(j).text;
                    if (tokens.get(j).type == SimpleJavaLexer.TokenType.IDENTIFIER || ".".equals(text) || "*".equals(text)) {
                        imp.append(text);
                    } else if (";".equals(text)) {
                        imports.add(imp.toString());
                        break;
                    } else {
                        break;
                    }
                    j++;
                }
            }
        }
        return imports;
    }

    private static class TypeDeclaration {
        String kind; // "class", "interface", "enum", "@interface"
        String name;
        List<String> extendedTypes = new ArrayList<>();
        List<String> implementedTypes = new ArrayList<>();
        int startTokenIndex;
        int endTokenIndex; // 粗略边界，用于后续收集类型引用
    }

    private TypeDeclaration parseTypeDeclaration(List<SimpleJavaLexer.Token> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            SimpleJavaLexer.Token t = tokens.get(i);
            if (t.type == SimpleJavaLexer.TokenType.KEYWORD) {
                String kw = t.text;
                if (kw.equals("class") || kw.equals("interface") || kw.equals("enum")) {
                    TypeDeclaration decl = new TypeDeclaration();
                    decl.kind = kw;
                    // 跳过可能存在的注解
                    i++;
                    while (i < tokens.size() && tokens.get(i).type == SimpleJavaLexer.TokenType.IDENTIFIER) {
                        decl.name = tokens.get(i).text;
                        i++;
                        break;
                    }
                    // 处理 extends / implements
                    while (i < tokens.size() && !"{".equals(tokens.get(i).text)) {
                        if ("extends".equals(tokens.get(i).text)) {
                            i++;
                            String ext = parseTypeName(tokens, i);
                            if (ext != null) decl.extendedTypes.add(ext);
                        } else if ("implements".equals(tokens.get(i).text)) {
                            i++;
                            String impl = parseTypeName(tokens, i);
                            if (impl != null) decl.implementedTypes.add(impl);
                        }
                        i++;
                    }
                    decl.startTokenIndex = i;
                    // 简单找到匹配的 '}' 作为结束（简化）
                    int braceCount = 0;
                    while (i < tokens.size()) {
                        if ("{".equals(tokens.get(i).text)) braceCount++;
                        else if ("}".equals(tokens.get(i).text)) {
                            braceCount--;
                            if (braceCount == 0) break;
                        }
                        i++;
                    }
                    decl.endTokenIndex = i;
                    return decl;
                } else if (kw.equals("@interface")) {
                    TypeDeclaration decl = new TypeDeclaration();
                    decl.kind = "@interface";
                    i++;
                    if (i < tokens.size() && tokens.get(i).type == SimpleJavaLexer.TokenType.IDENTIFIER) {
                        decl.name = tokens.get(i).text;
                    }
                    decl.startTokenIndex = i;
                    int braceCount = 0;
                    while (i < tokens.size()) {
                        if ("{".equals(tokens.get(i).text)) braceCount++;
                        else if ("}".equals(tokens.get(i).text)) {
                            braceCount--;
                            if (braceCount == 0) break;
                        }
                        i++;
                    }
                    decl.endTokenIndex = i;
                    return decl;
                }
            }
        }
        return null;
    }

    private String parseTypeName(List<SimpleJavaLexer.Token> tokens, int start) {
        StringBuilder sb = new StringBuilder();
        int i = start;
        while (i < tokens.size()) {
            String text = tokens.get(i).text;
            if (tokens.get(i).type == SimpleJavaLexer.TokenType.IDENTIFIER) {
                sb.append(text);
            } else if (".".equals(text)) {
                sb.append('.');
            } else if (",".equals(text) || "{".equals(text) || "extends".equals(text) || "implements".equals(text)) {
                break;
            } else {
                // 可能是泛型 < 等，忽略
                break;
            }
            i++;
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private ClassInfo.ClassType mapClassType(String kind) {
        switch (kind) {
            case "interface": return ClassInfo.ClassType.INTERFACE;
            case "enum": return ClassInfo.ClassType.ENUM;
            case "@interface": return ClassInfo.ClassType.ANNOTATION;
            default: return ClassInfo.ClassType.CLASS;
        }
    }

    private Set<String> collectTypeReferences(List<SimpleJavaLexer.Token> tokens, TypeDeclaration mainType) {
        Set<String> types = new HashSet<>();
        // 添加父类和接口
        types.addAll(mainType.extendedTypes);
        types.addAll(mainType.implementedTypes);

        // 在类的 body 范围内扫描字段和方法
        int start = mainType.startTokenIndex;
        int end = mainType.endTokenIndex;
        for (int i = start; i <= end && i < tokens.size(); i++) {
            SimpleJavaLexer.Token t = tokens.get(i);
            // 跳过修饰符
            if (t.type == SimpleJavaLexer.TokenType.KEYWORD && isModifier(t.text)) continue;
            // 检测字段声明: 类型 标识符 [= ...] ;
            if (t.type == SimpleJavaLexer.TokenType.IDENTIFIER && isPossibleTypeName(t.text)) {
                // 检查下一个 token 是否是标识符（字段名）
                int nextIdx = i + 1;
                if (nextIdx < tokens.size() && tokens.get(nextIdx).type == SimpleJavaLexer.TokenType.IDENTIFIER) {
                    String fieldType = t.text;
                    types.add(fieldType);
                }
            }
            // 检测方法返回类型
            if (t.type == SimpleJavaLexer.TokenType.IDENTIFIER && isPossibleTypeName(t.text)) {
                int nextIdx = i + 1;
                if (nextIdx < tokens.size() && tokens.get(nextIdx).type == SimpleJavaLexer.TokenType.IDENTIFIER) {
                    // 可能是方法返回类型 方法名(
                    int afterName = nextIdx + 1;
                    if (afterName < tokens.size() && "(".equals(tokens.get(afterName).text)) {
                        types.add(t.text);
                    }
                }
            }
            // 检测方法参数：在 '(' 和 ')' 之间查找类型
            if ("(".equals(t.text)) {
                int paramStart = i + 1;
                int braceCount = 1;
                int j = paramStart;
                while (j < tokens.size() && braceCount > 0) {
                    if ("(".equals(tokens.get(j).text)) braceCount++;
                    else if (")".equals(tokens.get(j).text)) braceCount--;
                    else if (braceCount == 1 && tokens.get(j).type == SimpleJavaLexer.TokenType.IDENTIFIER) {
                        // 可能是参数类型（后面跟标识符或逗号）
                        types.add(tokens.get(j).text);
                    }
                    j++;
                }
                i = j;
            }
        }
        return types;
    }

    private boolean isModifier(String word) {
        return Set.of("public", "protected", "private", "static", "final", "abstract", "synchronized",
                "volatile", "transient", "native", "strictfp").contains(word);
    }

    private boolean isPossibleTypeName(String word) {
        // 简单启发：首字母大写或为常见类型
        if (Character.isUpperCase(word.charAt(0))) return true;
        return Set.of("int", "long", "double", "float", "boolean", "char", "byte", "short", "void").contains(word);
    }


}