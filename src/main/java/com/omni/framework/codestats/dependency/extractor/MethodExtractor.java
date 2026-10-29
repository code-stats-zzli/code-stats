package com.omni.framework.codestats.dependency.extractor;

import com.omni.framework.codestats.dependency.extractor.SimpleJavaLexer.Token;
import com.omni.framework.codestats.dependency.extractor.SimpleJavaLexer.TokenType;
import com.omni.framework.codestats.dependency.model.MethodInfo;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class MethodExtractor {
    private static final Logger log = LoggerFactory.getLogger(MethodExtractor.class);

    private static final Set<String> MODIFIERS = new HashSet<>(Arrays.asList(
            "public", "protected", "private", "static", "final",
            "abstract", "synchronized", "native", "strictfp", "default"
    ));

    private final String filePath;
    private final String source;
    private final List<Token> tokens;
    private final int[] lineStarts;
    private final List<MethodInfo> methods = new ArrayList<>();
    private final List<RawCall> rawCalls = new ArrayList<>();

    public static class RawCall {
        public final String callerId;
        public final String calleeName;
        public final String receiverTypeHint;
        public final int argCount;
        public final int line;
        public final boolean isConstructor;
        RawCall(String callerId, String calleeName, String receiverTypeHint,
                int argCount, int line, boolean isConstructor) {
            this.callerId = callerId;
            this.calleeName = calleeName;
            this.receiverTypeHint = receiverTypeHint;
            this.argCount = argCount;
            this.line = line;
            this.isConstructor = isConstructor;
        }
    }

    public MethodExtractor(Path javaFile) throws IOException {
        this.filePath = javaFile.toString();
        this.source = Files.readString(javaFile);
        this.tokens = new SimpleJavaLexer(source).tokenize();
        this.lineStarts = computeLineStarts(source);
    }

    public List<MethodInfo> getMethods() { return methods; }
    public List<RawCall> getRawCalls() { return rawCalls; }
    public String getFilePath() { return filePath; }

    public void extract() {
        String pkg = parsePackageName();
        Deque<String> classStack = new ArrayDeque<>();
        Deque<Map<String, String>> fieldTypeStack = new ArrayDeque<>();
        int braceDepth = 0;
        int classBodyDepth = -1;
        String currentMethodId = null;
        int methodBodyEndBrace = -1;

        int i = 0;
        int n = tokens.size();
        while (i < n) {
            Token t = tokens.get(i);

            if (t.type == TokenType.KEYWORD
                    && ("class".equals(t.text) || "interface".equals(t.text) || "enum".equals(t.text))) {
                int nameIdx = i + 1;
                if (nameIdx < n && tokens.get(nameIdx).type == TokenType.IDENTIFIER) {
                    String simpleName = tokens.get(nameIdx).text;
                    String qualifiedName = classStack.isEmpty()
                            ? (pkg.isEmpty() ? simpleName : pkg + "." + simpleName)
                            : classStack.peek() + "." + simpleName;
                    classStack.push(qualifiedName);
                    fieldTypeStack.push(new HashMap<>());
                }
                i++;
                continue;
            }

            if (t.type == TokenType.SYMBOL) {
                if ("{".equals(t.text)) {
                    braceDepth++;
                    if (braceDepth == classStack.size() && classBodyDepth < braceDepth) classBodyDepth = braceDepth;
                    i++;
                    continue;
                }
                if ("}".equals(t.text)) {
                    if (currentMethodId != null && braceDepth == methodBodyEndBrace) {
                        currentMethodId = null;
                        methodBodyEndBrace = -1;
                    }
                    if (!classStack.isEmpty() && braceDepth == classStack.size()) {
                        classStack.pop();
                        if (!fieldTypeStack.isEmpty()) fieldTypeStack.pop();
                        classBodyDepth = classStack.size();
                    }
                    braceDepth--;
                    i++;
                    continue;
                }
            }

            if (currentMethodId == null && !classStack.isEmpty()
                    && braceDepth == classBodyDepth && braceDepth == classStack.size()) {

                if (t.type == TokenType.IDENTIFIER && i + 2 < n
                        && tokens.get(i + 1).type == TokenType.IDENTIFIER) {
                    Token after2 = tokens.get(i + 2);
                    if (after2.type == TokenType.SYMBOL
                            && (";".equals(after2.text) || "=".equals(after2.text) || ",".equals(after2.text))) {
                        Map<String, String> currentFieldMap = fieldTypeStack.peek();
                        if (currentFieldMap != null) currentFieldMap.put(tokens.get(i + 1).text, t.text);
                    }
                }

                if (t.type == TokenType.IDENTIFIER && i + 1 < n
                        && tokens.get(i + 1).type == TokenType.SYMBOL
                        && "(".equals(tokens.get(i + 1).text)
                        && i >= 1) {

                    Token prev = tokens.get(i - 1);
                    boolean isDecl = !(prev.type == TokenType.SYMBOL && ".".equals(prev.text))
                            && !(prev.type == TokenType.KEYWORD && "new".equals(prev.text));
                    if (isDecl) {
                        int closeParen = findMatchingParen(i + 1);
                        if (closeParen > 0) {
                            int afterParen = closeParen + 1;
                            while (afterParen < n
                                    && tokens.get(afterParen).type == TokenType.KEYWORD
                                    && "throws".equals(tokens.get(afterParen).text)) {
                                afterParen++;
                                while (afterParen < n && !";".equals(tokens.get(afterParen).text)
                                        && !"{".equals(tokens.get(afterParen).text)) afterParen++;
                            }
                            if (afterParen < n) {
                                Token after = tokens.get(afterParen);
                                if (after.type == TokenType.SYMBOL
                                        && ("{".equals(after.text) || ";".equals(after.text))) {
                                    String methodName = t.text;
                                    String returnType = (prev.type == TokenType.IDENTIFIER
                                            || (prev.type == TokenType.KEYWORD && !MODIFIERS.contains(prev.text)))
                                            ? prev.text : "void";
                                    boolean isStatic = false;
                                    for (int k = Math.max(0, i - 5); k < i; k++) {
                                        if (tokens.get(k).type == TokenType.KEYWORD
                                                && "static".equals(tokens.get(k).text)) { isStatic = true; break; }
                                    }
                                    List<String> params = parseParamTypes(i + 1, closeParen);
                                    int line = lineOf(t.position);
                                    String className = classStack.peek();
                                    String id = makeId(className, methodName, params);
                                    MethodInfo mi = new MethodInfo(id, className, methodName,
                                            params, returnType, line, false, isStatic);
                                    methods.add(mi);

                                    if ("{".equals(after.text)) {
                                        currentMethodId = id;
                                        methodBodyEndBrace = braceDepth + 1;
                                    }
                                    i = afterParen + 1;
                                    continue;
                                }
                            }
                        }
                    }
                }
            }

            if (currentMethodId != null) {
                if (t.type == TokenType.IDENTIFIER && i + 1 < n
                        && tokens.get(i + 1).type == TokenType.SYMBOL
                        && "(".equals(tokens.get(i + 1).text)) {

                    String calleeName = t.text;
                    String receiverType = null;
                    boolean isCtor = false;

                    if (i >= 1) {
                        Token p = tokens.get(i - 1);
                        if (p.type == TokenType.SYMBOL && ".".equals(p.text) && i >= 2) {
                            Token receiver = tokens.get(i - 2);
                            if (receiver.type == TokenType.IDENTIFIER) {
                                if ("this".equals(receiver.text)) {
                                    receiverType = classStack.peek();
                                } else {
                                    Map<String, String> fm = fieldTypeStack.peek();
                                    if (fm != null && fm.containsKey(receiver.text)) receiverType = fm.get(receiver.text);
                                }
                            }
                        } else if (p.type == TokenType.KEYWORD && "new".equals(p.text)) {
                            isCtor = true;
                            calleeName = "<init>";
                            receiverType = t.text;
                        }
                    }

                    int closeParen = findMatchingParen(i + 1);
                    if (closeParen > 0) {
                        int argCount = countArgs(i + 1, closeParen);
                        int line = lineOf(t.position);
                        rawCalls.add(new RawCall(currentMethodId, calleeName, receiverType, argCount, line, isCtor));
                    }
                }
            }

            i++;
        }
    }

    private String parsePackageName() {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).type == TokenType.KEYWORD && "package".equals(tokens.get(i).text)) {
                StringBuilder sb = new StringBuilder();
                int j = i + 1;
                while (j < tokens.size()) {
                    Token t = tokens.get(j);
                    if (";".equals(t.text)) break;
                    sb.append(t.text);
                    j++;
                }
                return sb.toString();
            }
        }
        return "";
    }

    private int findMatchingParen(int openIdx) {
        if (openIdx >= tokens.size()) return -1;
        if (!"(".equals(tokens.get(openIdx).text)) return -1;
        int depth = 0;
        for (int i = openIdx; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.type != TokenType.SYMBOL) continue;
            if ("(".equals(t.text)) depth++;
            else if (")".equals(t.text)) depth--;
            if (depth == 0) return i;
        }
        return -1;
    }

    /**
     * ★ [P1] 参数类型解析修复：
     *  - 原实现把 token 直接拼接，得到 "List<String>list"（类型+变量名粘在一起）
     *  - 现改为：按顶层逗号切分参数 → 跳过 final → 顶层最后一个 IDENTIFIER 视为变量名 → 其余 token 拼接为类型
     */
    private List<String> parseParamTypes(int openIdx, int closeIdx) {
        List<String> types = new ArrayList<>();
        if (closeIdx <= openIdx + 1) return types;

        List<int[]> paramRanges = new ArrayList<>();
        int depth = 0;
        int segStart = openIdx + 1;
        for (int i = openIdx + 1; i < closeIdx; i++) {
            Token t = tokens.get(i);
            if (t.type == TokenType.SYMBOL) {
                if ("<".equals(t.text) || "(".equals(t.text) || "[".equals(t.text)) depth++;
                else if (">".equals(t.text) || ")".equals(t.text) || "]".equals(t.text)) depth--;
                else if (",".equals(t.text) && depth == 0) {
                    paramRanges.add(new int[]{segStart, i});
                    segStart = i + 1;
                }
            }
        }
        if (segStart < closeIdx) paramRanges.add(new int[]{segStart, closeIdx});

        for (int[] range : paramRanges) {
            String type = extractTypeFromParam(range[0], range[1]);
            if (type != null && !type.isEmpty()) types.add(type);
        }
        return types;
    }

    private String extractTypeFromParam(int start, int end) {
        int i = start;
        // 跳过 final
        while (i < end && tokens.get(i).type == TokenType.KEYWORD && "final".equals(tokens.get(i).text)) i++;

        int depth = 0;
        int lastIdentifierAtDepth0 = -1;
        for (int j = i; j < end; j++) {
            Token t = tokens.get(j);
            if (t.type == TokenType.SYMBOL) {
                if ("<".equals(t.text) || "[".equals(t.text)) depth++;
                else if (">".equals(t.text) || "]".equals(t.text)) depth--;
            } else if (t.type == TokenType.IDENTIFIER && depth == 0) {
                lastIdentifierAtDepth0 = j;
            }
        }
        int typeEnd = (lastIdentifierAtDepth0 > i) ? lastIdentifierAtDepth0 : end;
        StringBuilder type = new StringBuilder();
        for (int j = i; j < typeEnd; j++) type.append(tokens.get(j).text);
        String t = type.toString();
        // varargs 归一化为数组
        if (t.endsWith("...")) t = t.substring(0, t.length() - 3) + "[]";
        return t;
    }

    private int countArgs(int openIdx, int closeIdx) {
        if (closeIdx <= openIdx + 1) return 0;
        int count = 1, depth = 0;
        for (int i = openIdx + 1; i < closeIdx; i++) {
            Token t = tokens.get(i);
            if (t.type == TokenType.SYMBOL) {
                if ("(".equals(t.text) || "<".equals(t.text)) depth++;
                else if (")".equals(t.text) || ">".equals(t.text)) depth--;
                else if (",".equals(t.text) && depth == 0) count++;
            }
        }
        return count;
    }

    private int lineOf(int position) {
        int lo = 0, hi = lineStarts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (lineStarts[mid] <= position) lo = mid;
            else hi = mid - 1;
        }
        return lo + 1;
    }

    private static int[] computeLineStarts(String src) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < src.length(); i++) {
            if (src.charAt(i) == '\n') starts.add(i + 1);
        }
        int[] arr = new int[starts.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = starts.get(i);
        return arr;
    }

    private static String makeId(String className, String methodName, List<String> params) {
        return className + "." + methodName + "(" + String.join(",", params) + ")";
    }
}