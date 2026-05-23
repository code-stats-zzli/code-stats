package com.omni.framework.codestats.dependency.extractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 极简 Java 词法扫描器：识别关键字、标识符、字符串、注释等，供依赖提取使用
 */
public class SimpleJavaLexer {
    private final String source;
    private int pos = 0;
    private final int length;

    public SimpleJavaLexer(String source) {
        this.source = source;
        this.length = source.length();
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (pos < length) {
            char ch = source.charAt(pos);
            if (Character.isWhitespace(ch)) {
                pos++;
                continue;
            }
            // 处理注释：跳过单行和多行注释内容
            if (ch == '/' && pos + 1 < length && source.charAt(pos + 1) == '/') {
                // 单行注释
                pos += 2;
                while (pos < length && source.charAt(pos) != '\n') pos++;
                continue;
            }
            if (ch == '/' && pos + 1 < length && source.charAt(pos + 1) == '*') {
                pos += 2;
                while (pos + 1 < length && !(source.charAt(pos) == '*' && source.charAt(pos + 1) == '/')) pos++;
                pos += 2; // skip "*/"
                continue;
            }
            // 字符串字面量：跳过内容
            if (ch == '"') {
                pos++;
                while (pos < length && source.charAt(pos) != '"') pos++;
                pos++;
                continue;
            }
            // 字符字面量
            if (ch == '\'') {
                pos++;
                while (pos < length && source.charAt(pos) != '\'') pos++;
                pos++;
                continue;
            }
            // 数字字面量：跳过
            if (Character.isDigit(ch)) {
                while (pos < length && (Character.isDigit(source.charAt(pos)) || source.charAt(pos) == '.'))
                    pos++;
                continue;
            }
            // 标识符或关键字
            if (Character.isJavaIdentifierStart(ch)) {
                int start = pos;
                while (pos < length && Character.isJavaIdentifierPart(source.charAt(pos))) pos++;
                String word = source.substring(start, pos);
                TokenType type = isKeyword(word) ? TokenType.KEYWORD : TokenType.IDENTIFIER;
                tokens.add(new Token(type, word, start));
                continue;
            }
            // 其他单字符符号（如 { } ( ) ; , < > = 等）但我们只需粗略分类，不详细记录
            tokens.add(new Token(TokenType.SYMBOL, String.valueOf(ch), pos));
            pos++;
        }
        return tokens;
    }

    private boolean isKeyword(String word) {
        // 常用 Java 关键字
        return Set.of(
                "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
                "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
                "finally", "float", "for", "goto", "if", "implements", "import", "instanceof",
                "int", "interface", "long", "native", "new", "package", "private", "protected",
                "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized",
                "this", "throw", "throws", "transient", "try", "void", "volatile", "while"
        ).contains(word);
    }

    public enum TokenType { KEYWORD, IDENTIFIER, SYMBOL }

    public static class Token {
        public final TokenType type;
        public final String text;
        public final int position;
        public Token(TokenType type, String text, int position) {
            this.type = type;
            this.text = text;
            this.position = position;
        }
        @Override
        public String toString() {
            return type + ":" + text;
        }
    }
}