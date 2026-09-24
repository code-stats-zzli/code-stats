package com.omni.framework.codestats.dependency.extractor;

import com.omni.framework.codestats.dependency.extractor.SimpleJavaLexer;
import com.omni.framework.codestats.dependency.extractor.SimpleJavaLexer.Token;
import com.omni.framework.codestats.dependency.extractor.SimpleJavaLexer.TokenType;

import com.omni.framework.codestats.dependency.model.MethodInfo;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 基于 SimpleJavaLexer 的手写方法级抽取器（零第三方依赖）。
 *
 * <h3>识别策略</h3>
 * <ol>
 *   <li>词法分析得到 Token 流</li>
 *   <li>识别类声明 → 维护类名栈</li>
 *   <li>识别方法声明（{@code 修饰符 返回类型 方法名 (参数) { 或 ;}）</li>
 *   <li>识别方法体内的调用（{@code IDENTIFIER ( }、{@code IDENTIFIER . IDENTIFIER ( }）</li>
 *   <li>字段类型推断：{@code Type fieldName; } → 用于精确匹配 receiver 类型</li>
 * </ol>
 */
public class MethodExtractor {

    private static final Logger log = LoggerFactory.getLogger(MethodExtractor.class);

    /** 排除关键字：出现在 IDENTIFIER( 位置但不是方法调用的关键字 */
    private static final Set<String> NON_CALL_KEYWORDS = new HashSet<>(Arrays.asList(
            "if", "while", "for", "switch", "catch", "synchronized",
            "return", "throw", "assert", "new"
    ));

    /** 修饰符关键字 */
    private static final Set<String> MODIFIERS = new HashSet<>(Arrays.asList(
            "public", "protected", "private", "static", "final",
            "abstract", "synchronized", "native", "strictfp", "default"
    ));

    // ====== 输入 ======
    private final String filePath;
    private final String source;
    private final List<Token> tokens;
    private final int[] lineStarts;   // 预计算行首位置，用于 O(log n) 行号查询

    // ====== 输出 ======
    private final List<MethodInfo> methods = new ArrayList<>();
    /** callerId -> 一组未解析的调用（calleeName, receiverTypeHint, line） */
    private final List<RawCall> rawCalls = new ArrayList<>();

    /** 内部：未解析的调用记录 */
    public static class RawCall {
        public final String callerId;
        public final String calleeName;
        public final String receiverTypeHint;  // 可能为 null
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

    // ==================== 主流程 ====================

    public void extract() {
        String pkg = parsePackageName();

        // 状态
        Deque<String> classStack = new ArrayDeque<>();       // 当前所在的类（外层→内层）
        Map<String, String> fieldTypes = new HashMap<>();     // 字段名 -> 简单类型名（当前类）
        Deque<Map<String, String>> fieldTypeStack = new ArrayDeque<>();

        int braceDepth = 0;
        int classBodyDepth = -1;    // 当前最内层类体的 braceDepth
        String currentMethodId = null;
        int methodBodyEndBrace = -1;

        int i = 0;
        int n = tokens.size();
        while (i < n) {
            Token t = tokens.get(i);

            // ---- 类 / 接口 / 枚举 声明 ----
            if (t.type == TokenType.KEYWORD
                    && ("class".equals(t.text) || "interface".equals(t.text) || "enum".equals(t.text))) {
                // 下一个 IDENTIFIER 是类名
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

            // ---- 花括号 ----
            if (t.type == TokenType.SYMBOL) {
                if ("{".equals(t.text)) {
                    braceDepth++;
                    // 进入类体？
                    if (braceDepth == classStack.size() && classBodyDepth < braceDepth) {
                        classBodyDepth = braceDepth;
                    }
                    i++;
                    continue;
                }
                if ("}".equals(t.text)) {
                    // 结束方法体？
                    if (currentMethodId != null && braceDepth == methodBodyEndBrace) {
                        currentMethodId = null;
                        methodBodyEndBrace = -1;
                    }
                    // 结束类体？
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

            // ---- 在类体内且未进入方法：识别方法声明 / 字段 ----
            if (currentMethodId == null && !classStack.isEmpty()
                    && braceDepth == classBodyDepth && braceDepth == classStack.size()) {

                // 字段类型：IDENTIFIER IDENTIFIER (;|=|,)
                if (t.type == TokenType.IDENTIFIER && i + 2 < n
                        && tokens.get(i + 1).type == TokenType.IDENTIFIER) {
                    Token after2 = tokens.get(i + 2);
                    if (after2.type == TokenType.SYMBOL
                            && (";".equals(after2.text) || "=".equals(after2.text)
                            || ",".equals(after2.text))) {
                        Map<String, String> currentFieldMap = fieldTypeStack.peek();
                        if (currentFieldMap != null) {
                            currentFieldMap.put(tokens.get(i + 1).text, t.text);
                        }
                    }
                }

                // 方法声明
                if (t.type == TokenType.IDENTIFIER && i + 1 < n
                        && tokens.get(i + 1).type == TokenType.SYMBOL
                        && "(".equals(tokens.get(i + 1).text)
                        && i >= 1) {

                    Token prev = tokens.get(i - 1);
                    // 前面是修饰符或类型（非 `.`）
                    boolean isDecl = !(prev.type == TokenType.SYMBOL && ".".equals(prev.text))
                            && !(prev.type == TokenType.KEYWORD && "new".equals(prev.text));
                    // 更严格：prev 是 KEYWORD 类型（void/int）或 IDENTIFIER 类型或 MODIFIERS 里的关键字
                    if (isDecl) {
                        int closeParen = findMatchingParen(i + 1);
                        if (closeParen > 0) {
                            int afterParen = closeParen + 1;
                            // 跳过 throws 子句
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
                                    // 是方法声明
                                    String methodName = t.text;
                                    String returnType = (prev.type == TokenType.IDENTIFIER
                                            || (prev.type == TokenType.KEYWORD
                                            && !MODIFIERS.contains(prev.text))) ? prev.text : "void";
                                    boolean isStatic = false;
                                    // 回看 5 个 token 判断 static
                                    for (int k = Math.max(0, i - 5); k < i; k++) {
                                        if (tokens.get(k).type == TokenType.KEYWORD
                                                && "static".equals(tokens.get(k).text)) {
                                            isStatic = true; break;
                                        }
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

            // ---- 在方法体内：识别调用 ----
            if (currentMethodId != null) {
                if (t.type == TokenType.IDENTIFIER && i + 1 < n
                        && tokens.get(i + 1).type == TokenType.SYMBOL
                        && "(".equals(tokens.get(i + 1).text)) {

                    // 排除方法声明（返回类型 + 名字 + ( ）——但它们已在上面被处理
                    // 这里只处理调用：形如 name(...) 或 receiver.name(...)
                    String calleeName = t.text;
                    String receiverType = null;
                    boolean isCtor = false;

                    // 看前面一个 token
                    if (i >= 1) {
                        Token p = tokens.get(i - 1);
                        if (p.type == TokenType.SYMBOL && ".".equals(p.text) && i >= 2) {
                            Token receiver = tokens.get(i - 2);
                            if (receiver.type == TokenType.IDENTIFIER) {
                                if ("this".equals(receiver.text)) {
                                    receiverType = classStack.peek();
                                } else {
                                    Map<String, String> fm = fieldTypeStack.peek();
                                    if (fm != null && fm.containsKey(receiver.text)) {
                                        receiverType = fm.get(receiver.text);
                                    }
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
                        rawCalls.add(new RawCall(currentMethodId, calleeName,
                                receiverType, argCount, line, isCtor));
                    }
                }
            }

            i++;
        }
    }

    // ==================== 辅助方法 ====================

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

    /** 找配对 `)` 的下标，失败返回 -1 */
    private int findMatchingParen(int openIdx) {
        if (openIdx >= tokens.size()) return -1;
        if (!"(".equals(tokens.get(openIdx).text)) return -1;
        int depth = 0;
        for (int i = openIdx; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.type != TokenType.SYMBOL) continue;
            if ("(".equals(t.text)) depth++;
            else if (".equals(t.text))".equals(t.text)) depth--;
            if (depth == 0) return i;
        }
        return -1;
    }

    /** 解析参数类型列表（形如 `String a, int b, List<User> c`） */
    private List<String> parseParamTypes(int openIdx, int closeIdx) {
        List<String> result = new ArrayList<>();
        if (closeIdx <= openIdx + 1) return result;
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = openIdx + 1; i < closeIdx; i++) {
            Token t = tokens.get(i);
            if (t.type == TokenType.SYMBOL) {
                if ("<".equals(t.text)) { depth++; current.append("<"); continue; }
                if (">".equals(t.text)) { depth--; current.append(">"); continue; }
                if (",".equals(t.text) && depth == 0) {
                    result.add(current.toString().trim());
                    current.setLength(0);
                    continue;
                }
                if ("[".equals(t.text) || "]".equals(t.text)) { current.append(t.text); continue; }
                if (".".equals(t.text)) { current.append("."); continue; }
            }
            current.append(t.text);
        }
        if (current.length() > 0) result.add(current.toString().trim());
        // 去掉参数名（最后一段）
        List<String> types = new ArrayList<>();
        for (String p : result) {
            String[] parts = p.split("\\s+");
            if (parts.length >= 2) {
                StringBuilder type = new StringBuilder();
                for (int k = 0; k < parts.length - 1; k++) {
                    if (k > 0) type.append(" ");
                    type.append(parts[k]);
                }
                types.add(type.toString());
            } else {
                types.add(p);
            }
        }
        return types;
    }

    /** 计算参数个数 */
    private int countArgs(int openIdx, int closeIdx) {
        if (closeIdx <= openIdx + 1) return 0;
        int count = 1, depth = 0;
        for (int i = openIdx + 1; i < closeIdx; i++) {
            Token t = tokens.get(i);
            if (t.type == TokenType.SYMBOL) {
                if ("(".equals(t.text) || "<".equals(t.text)) depth++;
                else if (".equals(t.text))".equals(t.text) || ">".equals(t.text)) depth--;
                else if (",".equals(t.text) && depth == 0) count++;
            }
        }
        return count;
    }

    private int lineOf(int position) {
        // 二分查找
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