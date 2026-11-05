// com.omni.business.assistance.agent.engine.CharNgramLM.java
package com.omni.business.common;

import java.util.*;

/**
 * 基于 3-gram 的字符级语言模型推理引擎
 * 包含概率查询、采样、文本生成等算子
 */
public class CharNgramLM {
    private final Map<String, Map<Character, Double>> trigramProbs;
    private final Set<Character> vocab;
    private final Random random = new Random();
    private static final int MAX_GENERATION_LEN = 100;

    public CharNgramLM(Map<String, Map<Character, Double>> trigramProbs, Set<Character> vocab) {
        this.trigramProbs = trigramProbs;
        this.vocab = vocab;
    }

    /**
     * 根据前两个字符获取下一个字符的概率分布
     */
    private Map<Character, Double> getNextProb(char c1, char c2) {
        String key = "" + c1 + c2;
        Map<Character, Double> probs = trigramProbs.get(key);
        if (probs == null) {
            // 平滑：回退到 unigram
            probs = new HashMap<>();
            double uniform = 1.0 / vocab.size();
            for (char c : vocab) probs.put(c, uniform);
        }
        return probs;
    }

    /**
     * 从概率分布中采样一个字符
     */
    private char sample(Map<Character, Double> probs) {
        double r = random.nextDouble();
        double cum = 0.0;
        for (Map.Entry<Character, Double> entry : probs.entrySet()) {
            cum += entry.getValue();
            if (r <= cum) return entry.getKey();
        }
        return probs.keySet().iterator().next();
    }

    /**
     * 生成文本：给定前缀，生成后续字符
     * @param prefix 初始字符串（至少需要2个字符，不足则用空格填充）
     * @param maxLen 最大生成长度
     * @return 生成的续写文本
     */
    public String generate(String prefix, int maxLen) {
        if (prefix == null) prefix = "";
        // 规范化输入
        prefix = prefix.toLowerCase();
        // 确保至少两个字符作为上下文
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < prefix.length(); i++) {
            char c = prefix.charAt(i);
            if (vocab.contains(c)) context.append(c);
            else context.append(' ');
        }
        while (context.length() < 2) context.insert(0, ' ');
        if (context.length() > 2) context = new StringBuilder(context.substring(context.length() - 2));

        StringBuilder output = new StringBuilder(prefix);
        int generated = 0;
        while (generated < maxLen) {
            char c1 = context.charAt(0);
            char c2 = context.charAt(1);
            Map<Character, Double> probs = getNextProb(c1, c2);
            char next = sample(probs);
            if (next == '$') break; // 结束符
            output.append(next);
            context.append(next);
            context.deleteCharAt(0);
            generated++;
            // 遇到句号或问号可适当停止
            if (next == '.' || next == '?') break;
        }
        return output.toString();
    }

    /**
     * 计算给定文本的 perplexity（可选，用于评估）
     */
    public double perplexity(String text) {
        text = text.toLowerCase();
        double logProb = 0.0;
        int count = 0;
        for (int i = 0; i < text.length() - 2; i++) {
            char c1 = text.charAt(i);
            char c2 = text.charAt(i+1);
            char c3 = text.charAt(i+2);
            if (!vocab.contains(c1)) c1 = ' ';
            if (!vocab.contains(c2)) c2 = ' ';
            if (!vocab.contains(c3)) c3 = ' ';
            Map<Character, Double> probs = getNextProb(c1, c2);
            Double p = probs.get(c3);
            if (p != null && p > 0) logProb += Math.log(p);
            else logProb += Math.log(1e-10);
            count++;
        }
        return Math.exp(-logProb / count);
    }
}