package com.omni.framework.codestats.dependency.extractor;

import com.omni.framework.codestats.dependency.model.ClassInfo;
import com.omni.framework.codestats.dependency.model.DependencyGraph;
import com.omni.framework.codestats.dependency.model.DependencyResult;
import com.omni.framework.codestats.dependency.model.ExtractedResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class JavaDependencyExtractor {

    public ExtractedResult extract(Path file) throws IOException {
        String source = Files.readString(file);
        SimpleJavaLexer lexer = new SimpleJavaLexer(source);
        List<SimpleJavaLexer.Token> tokens = lexer.tokenize();

        String packageName = parsePackage(tokens);
        List<String> imports = parseImports(tokens);
        TypeDeclaration mainType = parseTypeDeclaration(tokens);
        if (mainType == null || mainType.name == null || mainType.name.isEmpty()) {
            throw new IOException("无法找到类型声明: " + file);
        }

        String fqName = packageName.isEmpty() ? mainType.name : packageName + "." + mainType.name;
        ClassInfo.ClassType classType = mapClassType(mainType.kind);
        ClassInfo classInfo = new ClassInfo(fqName, packageName, mainType.name, classType);

        Set<String> rawTypes = collectTypeReferences(tokens, mainType);
        TypeNameResolver resolver = new TypeNameResolver(packageName, imports);
        Set<String> resolvedDeps = new HashSet<>();
        for (String raw : rawTypes) {
            if (TypeNameResolver.isPrimitive(raw)) continue;
            String resolved = resolver.resolve(raw);
            if (resolved != null) resolvedDeps.add(resolved);
        }

        DependencyGraph graph = new DependencyGraph();
        for (String dep : resolvedDeps) graph.addDependency(fqName, dep);

        Map<String, ClassInfo> classInfoMap = new HashMap<>();
        classInfoMap.put(fqName, classInfo);
        DependencyResult result = new DependencyResult(classInfoMap, graph, 0L);
        return new ExtractedResult(classInfo, resolvedDeps, result);
    }

    private String parsePackage(List<SimpleJavaLexer.Token> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).type == SimpleJavaLexer.TokenType.KEYWORD && "package".equals(tokens.get(i).text)) {
                StringBuilder pkg = new StringBuilder();
                int j = i + 1;
                while (j < tokens.size()) {
                    if (tokens.get(j).type == SimpleJavaLexer.TokenType.IDENTIFIER) pkg.append(tokens.get(j).text);
                    else if (".".equals(tokens.get(j).text)) pkg.append('.');
                    else if (";".equals(tokens.get(j).text)) return pkg.toString();
                    else break;
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
        String kind;
        String name;
        List<String> extendedTypes = new ArrayList<>();
        List<String> implementedTypes = new ArrayList<>();
        int startTokenIndex;
        int endTokenIndex;
    }

    private TypeDeclaration parseTypeDeclaration(List<SimpleJavaLexer.Token> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            SimpleJavaLexer.Token t = tokens.get(i);
            if (t.type == SimpleJavaLexer.TokenType.KEYWORD) {
                String kw = t.text;
                if (kw.equals("class") || kw.equals("interface") || kw.equals("enum")) {
                    TypeDeclaration decl = new TypeDeclaration();
                    decl.kind = kw;
                    i++;
                    while (i < tokens.size() && tokens.get(i).type == SimpleJavaLexer.TokenType.IDENTIFIER) {
                        decl.name = tokens.get(i).text;
                        i++;
                        break;
                    }
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
            if (tokens.get(i).type == SimpleJavaLexer.TokenType.IDENTIFIER) sb.append(text);
            else if (".".equals(text)) sb.append('.');
            else break;
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

    /**
     * ★ [P1] 修正类型引用收集：
     *  - 显式处理 new Foo / new Foo<...>()
     *  - 泛型声明 Foo<...>
     *  - 内部类/静态引用 Foo.Bar
     *  - 字段/方法返回类型 Foo name / Foo name(
     *  - 不再把纯方法调用 foo(...) 误判为类型
     */
    private Set<String> collectTypeReferences(List<SimpleJavaLexer.Token> tokens, TypeDeclaration mainType) {
        Set<String> types = new HashSet<>();
        types.addAll(mainType.extendedTypes);
        types.addAll(mainType.implementedTypes);

        int start = mainType.startTokenIndex;
        int end = Math.min(mainType.endTokenIndex, tokens.size() - 1);

        for (int i = start; i <= end; i++) {
            SimpleJavaLexer.Token t = tokens.get(i);
            if (t.type != SimpleJavaLexer.TokenType.IDENTIFIER) continue;
            String word = t.text;
            if (!isPossibleTypeName(word)) continue;

            // 情况 1: new Foo
            if (i >= 1 && tokens.get(i - 1).type == SimpleJavaLexer.TokenType.KEYWORD
                    && "new".equals(tokens.get(i - 1).text)) {
                types.add(word);
                continue;
            }

            // 情况 2: 字段/方法返回类型 Foo name
            if (i + 1 <= end && tokens.get(i + 1).type == SimpleJavaLexer.TokenType.IDENTIFIER) {
                types.add(word);
                continue;
            }

            // 情况 3: 泛型 Foo<...>
            if (i + 1 <= end && tokens.get(i + 1).type == SimpleJavaLexer.TokenType.SYMBOL
                    && "<".equals(tokens.get(i + 1).text)) {
                types.add(word);
                continue;
            }

            // 情况 4: 内部类 Foo.Bar
            if (i + 2 <= end && tokens.get(i + 1).type == SimpleJavaLexer.TokenType.SYMBOL
                    && ".".equals(tokens.get(i + 1).text)
                    && tokens.get(i + 2).type == SimpleJavaLexer.TokenType.IDENTIFIER
                    && isPossibleTypeName(tokens.get(i + 2).text)) {
                types.add(word);
            }
        }
        return types;
    }

    private boolean isPossibleTypeName(String word) {
        if (word == null || word.isEmpty()) return false;
        if (Character.isUpperCase(word.charAt(0))) return true;
        return Set.of("int", "long", "double", "float", "boolean", "char", "byte", "short", "void").contains(word);
    }
}