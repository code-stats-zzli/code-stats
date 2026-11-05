package com.omni.business.common.model.support;

import java.util.*;

public class ProjectModelData {
    private final Map<String, Map<Character, Double>> trigramProbs = new HashMap<>();
    private final Set<Character> vocab = new HashSet<>();
    private static final String DEFAULT_PROJECT_DESC =
            "CodeStats is a self-developed Java web framework with IoC container, MVC, embedded Tomcat, " +
                    "JDBC connection pool, MyBatis-style mapper, and code analysis engine. It supports database browsing, " +
                    "directory management, AI agent, and code statistics. The framework uses annotation-driven development, " +
                    "with @Controller, @Service, @Repository, @Autowired. DispatcherServlet handles request mapping. " +
                    "Pipeline-Valve chain processes requests. WebappClassLoader implements class isolation. " +
                    "The project includes modules: database client, file manager, code analyzer, and intelligent agent.";

    public ProjectModelData() {
        // 直接使用默认描述，不再扫描项目源码以避免正则栈溢出
        String corpus = DEFAULT_PROJECT_DESC.toLowerCase();
        train(corpus);
    }

    private void train(String text) {
        for (char c : text.toCharArray()) {
            if (isValidChar(c)) vocab.add(c);
        }
        vocab.add('^');
        vocab.add('$');

        List<Character> chars = new ArrayList<>();
        chars.add('^');
        chars.add('^');
        for (char c : text.toCharArray()) {
            if (isValidChar(c)) chars.add(c);
            else chars.add(' ');
        }
        chars.add('$');
        chars.add('$');

        Map<String, Map<Character, Integer>> counts = new HashMap<>();
        for (int i = 0; i < chars.size() - 2; i++) {
            char c1 = chars.get(i);
            char c2 = chars.get(i+1);
            char c3 = chars.get(i+2);
            if (!vocab.contains(c1)) c1 = ' ';
            if (!vocab.contains(c2)) c2 = ' ';
            if (!vocab.contains(c3)) c3 = ' ';
            String key = "" + c1 + c2;
            counts.computeIfAbsent(key, k -> new HashMap<>())
                    .merge(c3, 1, Integer::sum);
        }

        for (Map.Entry<String, Map<Character, Integer>> entry : counts.entrySet()) {
            String key = entry.getKey();
            Map<Character, Integer> cntMap = entry.getValue();
            Map<Character, Double> probMap = new HashMap<>();
            int total = cntMap.values().stream().mapToInt(Integer::intValue).sum();
            for (Map.Entry<Character, Integer> e : cntMap.entrySet()) {
                probMap.put(e.getKey(), (e.getValue() + 1.0) / (total + vocab.size()));
            }
            for (char c : vocab) {
                if (!probMap.containsKey(c)) {
                    probMap.put(c, 1.0 / (total + vocab.size()));
                }
            }
            trigramProbs.put(key, probMap);
        }
        System.out.println("Trained trigram model with " + trigramProbs.size() + " contexts");
    }

    private boolean isValidChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == ' ' || c == '.' || c == ',' || c == '?' || c == '!';
    }

    public Map<String, Map<Character, Double>> getTrigramProbs() {
        return trigramProbs;
    }

    public Set<Character> getVocab() {
        return vocab;
    }
}