package com.omni.framework.expression.function;

import com.omni.framework.expression.ExpressionEngine;
import com.omni.framework.expression.ExpressionException;
import com.omni.framework.expression.function.meta.FunctionCategory;
import com.omni.framework.expression.function.meta.FunctionDefinition;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 扩展函数注册中心（属于框架层）。
 *
 * 职责：
 *  1. 向 {@link ExpressionEngine#registerGlobalFunction} 注册字符/数字/日期/类型/逻辑函数
 *  2. 对外提供函数元数据列表（前端函数库直接消费）
 *
 * 注册与元数据在同一个 buildCategory() 方法中构建，避免漂移。
 *
 * 注意：会覆盖 BuiltinFunctions 中同名但参数更少的内置版本（如 max/min/toInt）。
 */
public final class ExtendedFunctionRegistry {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);
    private static final List<FunctionCategory> CATEGORIES = new ArrayList<>();

    private ExtendedFunctionRegistry() {}

    /* ==================== 公共入口 ==================== */

    public static synchronized void registerDefaults() {
        if (!REGISTERED.compareAndSet(false, true)) return;
        CATEGORIES.clear();
        CATEGORIES.add(buildStringCategory());
        CATEGORIES.add(buildNumberCategory());
        CATEGORIES.add(buildDateCategory());
        CATEGORIES.add(buildTypeCategory());
        CATEGORIES.add(buildLogicCategory());
    }

    /** 返回分类列表（顺序固定：string → math → date → convert → logic） */
    public static List<FunctionCategory> listCategories() {
        return Collections.unmodifiableList(CATEGORIES);
    }

    /* ==================== 字符处理 ==================== */

    private static FunctionCategory buildStringCategory() {
        List<FunctionDefinition> fns = new ArrayList<>();

        fns.add(register("substring", "substring(s, start [, end])",
                "截取子串（左闭右开）", "substring($name, 0, 3)", args -> {
                    String s = str(args.length > 0 ? args[0] : null);
                    int start = args.length > 1 ? toInt(args[1]) : 0;
                    start = Math.max(0, Math.min(start, s.length()));
                    if (args.length >= 3) {
                        int end = toInt(args[2]);
                        end = Math.max(start, Math.min(end, s.length()));
                        return s.substring(start, end);
                    }
                    return s.substring(start);
                }));

        fns.add(register("indexOf", "indexOf(s, sub)", "子串位置，不存在返回 -1",
                "indexOf($name, 'a')",
                args -> str(args[0]).indexOf(str(args[1]))));

        fns.add(register("lastIndexOf", "lastIndexOf(s, sub)", "子串最后出现位置",
                "lastIndexOf($name, 'a')",
                args -> str(args[0]).lastIndexOf(str(args[1]))));

        fns.add(register("contains", "contains(s, sub)", "是否包含子串",
                "contains($name, 'Tom')",
                args -> str(args[0]).contains(str(args[1]))));

        fns.add(register("startsWith", "startsWith(s, prefix)", "是否以某串开头",
                "startsWith($name, 'T')",
                args -> str(args[0]).startsWith(str(args[1]))));

        fns.add(register("endsWith", "endsWith(s, suffix)", "是否以某串结尾",
                "endsWith($name, 'm')",
                args -> str(args[0]).endsWith(str(args[1]))));

        fns.add(register("replace", "replace(s, from, to)", "替换子串",
                "replace($name, 'Tom', 'Jerry')",
                args -> str(args[0]).replace(str(args[1]), str(args[2]))));

        fns.add(register("replaceAll", "replaceAll(s, regex, to)", "正则替换",
                "replaceAll('a1b2', '[0-9]', '#')",
                args -> str(args[0]).replaceAll(str(args[1]), str(args[2]))));

        fns.add(register("concat", "concat(a, b, ...)", "拼接多个字符串",
                "concat('Hello, ', $name)", args -> {
                    StringBuilder sb = new StringBuilder();
                    for (Object a : args) sb.append(str(a));
                    return sb.toString();
                }));

        fns.add(register("repeat", "repeat(s, n)", "重复字符串 n 次",
                "repeat('ab', 3)", args -> {
                    String s = str(args[0]);
                    int n = Math.max(0, Math.min(1000, toInt(args[1])));
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < n; i++) sb.append(s);
                    return sb.toString();
                }));

        fns.add(register("split", "split(s, sep)", "按分隔符拆成数组",
                "split($tags, ',')", args -> {
                    String s = str(args[0]);
                    String sep = args.length > 1 ? str(args[1]) : ",";
                    return Arrays.asList(s.split(Pattern.quote(sep)));
                }));

        fns.add(register("join", "join(arr [, sep])", "数组用分隔符连接",
                "join($tags, '-')", args -> {
                    if (args.length == 0) return "";
                    Object first = args[0];
                    if (first instanceof Collection) {
                        String sep = args.length > 1 ? str(args[1]) : ",";
                        return ((Collection<?>) first).stream()
                                .map(ExtendedFunctionRegistry::str)
                                .collect(Collectors.joining(sep));
                    }
                    return str(first);
                }));

        fns.add(register("padLeft", "padLeft(s, len [, pad])", "左侧补字符",
                "padLeft('7', 3, '0')", args -> {
                    String s = str(args[0]);
                    int len = toInt(args[1]);
                    char pad = args.length > 2 && !str(args[2]).isEmpty() ? str(args[2]).charAt(0) : ' ';
                    if (s.length() >= len) return s;
                    StringBuilder sb = new StringBuilder();
                    for (int i = s.length(); i < len; i++) sb.append(pad);
                    return sb.append(s).toString();
                }));

        fns.add(register("padRight", "padRight(s, len [, pad])", "右侧补字符",
                "padRight('7', 3, '0')", args -> {
                    String s = str(args[0]);
                    int len = toInt(args[1]);
                    char pad = args.length > 2 && !str(args[2]).isEmpty() ? str(args[2]).charAt(0) : ' ';
                    if (s.length() >= len) return s;
                    StringBuilder sb = new StringBuilder(s);
                    for (int i = s.length(); i < len; i++) sb.append(pad);
                    return sb.toString();
                }));

        return new FunctionCategory("string", "📝 字符处理",
                "截取、查找、替换、拼接、分割", fns);
    }

    /* ==================== 数字处理 ==================== */

    private static FunctionCategory buildNumberCategory() {
        List<FunctionDefinition> fns = new ArrayList<>();

        fns.add(register("abs", "abs(n)", "绝对值", "abs(-5)",
                args -> Math.abs(toDouble(args[0]))));

        fns.add(register("round", "round(n [, digits])", "四舍五入，可指定小数位",
                "round(3.14159, 2)", args -> {
                    if (args.length == 0) return 0;
                    double n = toDouble(args[0]);
                    if (args.length >= 2) {
                        int digits = Math.max(0, Math.min(10, toInt(args[1])));
                        double factor = Math.pow(10, digits);
                        return Math.round(n * factor) / factor;
                    }
                    return (long) Math.round(n);
                }));

        fns.add(register("ceil", "ceil(n)", "向上取整", "ceil(3.2)",
                args -> (long) Math.ceil(toDouble(args[0]))));

        fns.add(register("floor", "floor(n)", "向下取整", "floor(3.8)",
                args -> (long) Math.floor(toDouble(args[0]))));

        fns.add(register("max", "max(a, b, ...)", "取最大值", "max(1, 9, 5)",
                args -> {
                    if (args.length == 0) return 0;
                    double m = toDouble(args[0]);
                    for (int i = 1; i < args.length; i++) m = Math.max(m, toDouble(args[i]));
                    return m;
                }));

        fns.add(register("min", "min(a, b, ...)", "取最小值", "min(1, 9, 5)",
                args -> {
                    if (args.length == 0) return 0;
                    double m = toDouble(args[0]);
                    for (int i = 1; i < args.length; i++) m = Math.min(m, toDouble(args[i]));
                    return m;
                }));

        fns.add(register("pow", "pow(a, b)", "a 的 b 次方", "pow(2, 10)",
                args -> Math.pow(toDouble(args[0]), toDouble(args[1]))));

        fns.add(register("sqrt", "sqrt(n)", "平方根", "sqrt(16)",
                args -> Math.sqrt(toDouble(args[0]))));

        fns.add(register("random", "random()", "0~1 随机数", "random()",
                args -> Math.random()));

        fns.add(register("formatNumber", "formatNumber(n [, pattern])", "数字格式化",
                "formatNumber(1234567.891, '#,##0.00')", args -> {
                    if (args.length == 0) return "";
                    String pattern = args.length > 1 ? str(args[1]) : "#,##0.00";
                    return new DecimalFormat(pattern).format(toDouble(args[0]));
                }));

        return new FunctionCategory("math", "🔢 数字处理",
                "四则运算、舍入、统计、格式化", fns);
    }

    /* ==================== 日期处理 ==================== */

    private static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static FunctionCategory buildDateCategory() {
        List<FunctionDefinition> fns = new ArrayList<>();

        fns.add(register("now", "now()", "当前日期时间 yyyy-MM-dd HH:mm:ss", "now()",
                args -> LocalDateTime.now().format(DATETIME_FMT)));

        fns.add(register("today", "today()", "今天日期 yyyy-MM-dd", "today()",
                args -> LocalDate.now().toString()));

        fns.add(register("timestamp", "timestamp()", "当前毫秒时间戳", "timestamp()",
                args -> System.currentTimeMillis()));

        fns.add(register("year", "year(d)", "年份", "year(today())",
                args -> parseLocalDate(args[0]).getYear()));

        fns.add(register("month", "month(d)", "月份 1~12", "month($date)",
                args -> parseLocalDate(args[0]).getMonthValue()));

        fns.add(register("day", "day(d)", "日 1~31", "day($date)",
                args -> parseLocalDate(args[0]).getDayOfMonth()));

        fns.add(register("formatDate", "formatDate(d, pattern)", "按模式格式化",
                "formatDate(today(), 'yyyy年MM月dd日')", args -> {
                    if (args.length == 0) return "";
                    Object raw = args[0];
                    String pattern = args.length > 1 ? str(args[1]) : "yyyy-MM-dd";
                    DateTimeFormatter fmt = DateTimeFormatter.ofPattern(pattern);
                    if (raw instanceof LocalDateTime) return ((LocalDateTime) raw).format(fmt);
                    if (raw instanceof LocalDate) return ((LocalDate) raw).format(fmt);
                    String s = str(raw).trim();
                    try {
                        return LocalDateTime.parse(s.replace(' ', 'T')).format(fmt);
                    } catch (DateTimeParseException ignored) {}
                    return parseLocalDate(raw).format(fmt);
                }));

        fns.add(register("addDays", "addDays(d, n)", "日期加 n 天",
                "addDays(today(), 7)", args -> {
                    LocalDate d = parseLocalDate(args[0]);
                    return d.plusDays(toInt(args[1])).toString();
                }));

        fns.add(register("addMonths", "addMonths(d, n)", "日期加 n 月",
                "addMonths(today(), 1)", args -> {
                    LocalDate d = parseLocalDate(args[0]);
                    return d.plusMonths(toInt(args[1])).toString();
                }));

        fns.add(register("addYears", "addYears(d, n)", "日期加 n 年",
                "addYears(today(), 1)", args -> {
                    LocalDate d = parseLocalDate(args[0]);
                    return d.plusYears(toInt(args[1])).toString();
                }));

        fns.add(register("diffDays", "diffDays(d1, d2)", "两日期相差天数",
                "diffDays('2024-01-01', '2024-12-31')",
                args -> ChronoUnit.DAYS.between(parseLocalDate(args[0]), parseLocalDate(args[1]))));

        return new FunctionCategory("date", "📅 日期处理",
                "获取、解析、格式化、计算日期", fns);
    }

    /* ==================== 类型转换 ==================== */

    private static FunctionCategory buildTypeCategory() {
        List<FunctionDefinition> fns = new ArrayList<>();

        fns.add(register("toInt", "toInt(x)", "转整数", "toInt('42')",
                args -> toInt(args.length > 0 ? args[0] : null)));

        fns.add(register("toDouble", "toDouble(x)", "转小数", "toDouble('3.14')",
                args -> toDouble(args.length > 0 ? args[0] : null)));

        fns.add(register("toString", "toString(x)", "转字符串", "toString(123)",
                args -> args.length > 0 ? str(args[0]) : ""));

        fns.add(register("toBoolean", "toBoolean(x)", "转布尔", "toBoolean('true')",
                args -> {
                    if (args.length == 0 || args[0] == null) return false;
                    Object o = args[0];
                    if (o instanceof Boolean) return o;
                    String s = str(o).trim().toLowerCase();
                    return "true".equals(s) || "1".equals(s) || "yes".equals(s) || "y".equals(s);
                }));

        return new FunctionCategory("convert", "🔄 类型转换",
                "字符串 / 数字 / 布尔 互转", fns);
    }

    /* ==================== 逻辑判断 ==================== */

    private static FunctionCategory buildLogicCategory() {
        List<FunctionDefinition> fns = new ArrayList<>();

        fns.add(register("isNull", "isNull(x)", "是否为 null", "isNull($name)",
                args -> args.length == 0 || args[0] == null));

        fns.add(register("isEmpty", "isEmpty(x)", "是否为空（null/空串/空集合/空Map）",
                "isEmpty($name)", args -> {
                    if (args.length == 0 || args[0] == null) return true;
                    Object o = args[0];
                    if (o instanceof CharSequence) return ((CharSequence) o).length() == 0;
                    if (o instanceof Collection) return ((Collection<?>) o).isEmpty();
                    if (o instanceof Map) return ((Map<?, ?>) o).isEmpty();
                    return false;
                }));

        fns.add(register("ifNull", "ifNull(a, b)", "a 为 null 时返回 b",
                "ifNull($name, '匿名')", args -> {
                    if (args.length == 0) return null;
                    return args[0] != null ? args[0] : (args.length > 1 ? args[1] : null);
                }));

        fns.add(register("coalesce", "coalesce(a, b, ...)", "返回第一个非 null 值",
                "coalesce($a, $b, '默认')", args -> {
                    for (Object a : args) if (a != null) return a;
                    return null;
                }));

        return new FunctionCategory("logic", "🧠 逻辑判断",
                "空值判断、默认值兜底", fns);
    }

    /* ==================== 注册辅助 ==================== */

    private static FunctionDefinition register(String name, String signature, String desc,
                                               String example,
                                               com.omni.framework.expression.function.FunctionResolver resolver) {
        ExpressionEngine.registerGlobalFunction(name, resolver);
        return new FunctionDefinition(name, signature, desc, example);
    }

    /* ==================== 类型辅助 ==================== */

    private static String str(Object o) {
        if (o == null) return "";
        if (o instanceof Double) {
            double d = (Double) o;
            if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                return String.valueOf((long) d);
            }
        }
        if (o instanceof Float) {
            float f = (Float) o;
            if (f == Math.floor(f)) return String.valueOf((long) f);
        }
        return String.valueOf(o);
    }

    private static int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        String s = str(o).trim();
        try {
            if (s.contains(".")) return (int) Double.parseDouble(s);
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static double toDouble(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            return Double.parseDouble(str(o).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static LocalDate parseLocalDate(Object o) {
        if (o == null) return LocalDate.now();
        if (o instanceof LocalDate) return (LocalDate) o;
        if (o instanceof LocalDateTime) return ((LocalDateTime) o).toLocalDate();
        String s = str(o).trim();
        if (s.length() >= 10) {
            String datePart = s.substring(0, 10);
            try { return LocalDate.parse(datePart); } catch (Exception ignored) {}
            try { return LocalDate.parse(datePart.replace('/', '-')); } catch (Exception ignored) {}
        }
        try { return LocalDate.parse(s); } catch (Exception ignored) {}
        throw new ExpressionException("无法解析日期: " + s);
    }
}