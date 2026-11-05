package com.omni.framework.codestats.dependency.extractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 极简 Java 词法扫描器。
 * 修复：
 *  - 识别转义字符（\"  \'  \\）
 *  - 识别 Java 15+ 文本块（""" ... """）
 *  - 字符串/字符/数字 也产出 Token（下游按需忽略），避免文本块内的关键字被误判
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
            if (Character.isWhitespace(ch)) { pos++; continue; }

            // 单行注释
            if (ch == '/' && pos + 1 < length && source.charAt(pos + 1) == '/') {
                pos += 2;
                while (pos < length && source.charAt(pos) != '\n') pos++;
                continue;
            }
            // 多行注释
            if (ch == '/' && pos + 1 < length && source.charAt(pos + 1) == '*') {
                pos += 2;
                while (pos + 1 < length && !(source.charAt(pos) == '*' && source.charAt(pos + 1) == '/')) pos++;
                pos += 2;
                continue;
            }
            // 文本块 """
            if (ch == '"' && pos + 2 < length
                    && source.charAt(pos + 1) == '"' && source.charAt(pos + 2) == '"') {
                int start = pos;
                pos += 3;
                while (pos + 2 < length
                        && !(source.charAt(pos) == '"' && source.charAt(pos + 1) == '"' && source.charAt(pos + 2) == '"')) {
                    if (source.charAt(pos) == '\\' && pos + 1 < length) pos += 2;
                    else pos++;
                }
                pos = Math.min(pos + 3, length);
                tokens.add(new Token(TokenType.TEXT_BLOCK, source.substring(start, pos), start));
                continue;
            }
            // 字符串
            if (ch == '"') {
                int start = pos;
                pos++;
                while (pos < length) {
                    char c = source.charAt(pos);
                    if (c == '\\' && pos + 1 < length) { pos += 2; continue; }
                    if (c == '"') { pos++; break; }
                    pos++;
                }
                tokens.add(new Token(TokenType.STRING, source.substring(start, pos), start));
                continue;
            }
            // 字符
            if (ch == '\'') {
                int start = pos;
                pos++;
                while (pos < length) {
                    char c = source.charAt(pos);
                    if (c == '\\' && pos + 1 < length) { pos += 2; continue; }
                    if (c == '\'') { pos++; break; }
                    pos++;
                }
                tokens.add(new Token(TokenType.CHAR, source.substring(start, pos), start));
                continue;
            }
            // 数字（简化：十进制 + 0x 十六进制 + 小数点）
            if (Character.isDigit(ch)) {
                int start = pos;
                boolean hex = false;
                if (ch == '0' && pos + 1 < length
                        && (source.charAt(pos + 1) == 'x' || source.charAt(pos + 1) == 'X')) {
                    hex = true;
                    pos += 2;
                }
                while (pos < length) {
                    char c = source.charAt(pos);
                    if (Character.isDigit(c)) { pos++; continue; }
                    if (hex && ((c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) { pos++; continue; }
                    if (!hex && c == '.') { pos++; continue; }
                    if (c == '_') { pos++; continue; }
                    if (c == 'L' || c == 'l' || c == 'F' || c == 'f' || c == 'D' || c == 'd') { pos++; continue; }
                    break;
                }
                tokens.add(new Token(TokenType.NUMBER, source.substring(start, pos), start));
                continue;
            }
            // 标识符 / 关键字
            if (Character.isJavaIdentifierStart(ch)) {
                int start = pos;
                while (pos < length && Character.isJavaIdentifierPart(source.charAt(pos))) pos++;
                String word = source.substring(start, pos);
                TokenType type = isKeyword(word) ? TokenType.KEYWORD : TokenType.IDENTIFIER;
                tokens.add(new Token(type, word, start));
                continue;
            }
            tokens.add(new Token(TokenType.SYMBOL, String.valueOf(ch), pos));
            pos++;
        }
        return tokens;
    }

    private boolean isKeyword(String word) {
        return Set.of(
                "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
                "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
                "finally", "float", "for", "goto", "if", "implements", "import", "instanceof",
                "int", "interface", "long", "native", "new", "package", "private", "protected",
                "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized",
                "this", "throw", "throws", "transient", "try", "void", "volatile", "while"
        ).contains(word);
    }

    public enum TokenType { KEYWORD, IDENTIFIER, SYMBOL, STRING, CHAR, TEXT_BLOCK, NUMBER }

    public static class Token {
        public final TokenType type;
        public final String text;
        public final int position;
        public Token(TokenType type, String text, int position) {
            this.type = type;
            this.text = text;
            this.position = position;
        }
        @Override public String toString() { return type + ":" + text; }
    }
}