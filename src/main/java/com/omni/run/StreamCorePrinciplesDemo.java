/*
package com.omni.run;

import java.util.*;
import java.util.function.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

*/
/**
 * ============================================================================
 * Java Stream API 核心原理与设计思路演示（纯技术示例，无业务模型）
 * ============================================================================
 *
 * 【解决的核心问题】
 * 1. 传统迭代（for/while）代码冗长、难以并行
 * 2. 集合操作链（过滤、映射、排序等）需要创建大量临时中间集合
 * 3. 并行处理需要手动管理线程和同步，容易出错
 * 4. 代码可读性差，意图不明确
 *
 * 【设计思路】
 * 1. 函数式编程风格：将操作表示为高阶函数（如 Predicate, Function）
 * 2. 流水线模式：中间操作不立即执行，而是构建操作链，终端操作触发执行
 * 3. 惰性求值：只有需要结果时才真正计算，避免无效计算
 * 4. 内部迭代：由 Stream 内部控制迭代过程，开发者只需声明"做什么"
 * 5. 并行支持：通过 Fork/Join 框架自动拆分任务，透明地利用多核
 *
 * 【核心角色】
 * - 数据源：集合、数组、生成器函数等
 * - 中间操作：filter, map, flatMap, sorted, distinct, limit, skip, peek
 * - 终端操作：forEach, collect, reduce, count, anyMatch, findFirst 等
 * - 收集器：Collector 接口，封装了可变归约的逻辑（如 toList, groupingBy）
 *
 * ============================================================================
 *//*

public class StreamCorePrinciplesDemo {

    // ======================== 核心接口定义 ========================

    */
/**
     * Stream 核心接口
     * @param <T> 元素类型
     *//*

    interface Stream<T> {
        // 中间操作（返回 Stream，支持链式调用）
        Stream<T> filter(Predicate<? super T> predicate);
        <R> Stream<R> map(Function<? super T, ? extends R> mapper);
        Stream<T> distinct();
        Stream<T> sorted(Comparator<? super T> comparator);
        Stream<T> limit(long maxSize);
        Stream<T> skip(long n);
        Stream<T> peek(Consumer<? super T> action);

        // 终端操作（触发实际执行）
        void forEach(Consumer<? super T> action);
        <A> A collect(Collector<? super T, A> collector);
        Optional<T> reduce(BinaryOperator<T> accumulator);
        long count();
        boolean anyMatch(Predicate<? super T> predicate);
        boolean allMatch(Predicate<? super T> predicate);
        boolean noneMatch(Predicate<? super T> predicate);
        Optional<T> findFirst();
        List<T> toList();
    }

    */
/**
     * 收集器接口
     * @param <T> 输入元素类型
     * @param <A> 累加器类型
     *//*

    interface Collector<T, A> {
        Supplier<A> supplier();                    // 创建新容器
        BiConsumer<A, T> accumulator();            // 将元素加入容器
        BinaryOperator<A> combiner();              // 合并两个容器（并行用）
        Function<A, ? extends List<T>> finisher(); // 转换最终结果
    }

    // ======================== 具体实现 ========================

    */
/**
     * 流水线阶段抽象（链表结构）
     *//*

    static abstract class PipelineStage<T, E_OUT> implements Stream<E_OUT> {
        protected PipelineStage<?, T> previousStage;
        protected boolean isParallel = false;
        protected boolean isTerminated = false;

        public PipelineStage(PipelineStage<?, T> previousStage) {
            this.previousStage = previousStage;
        }

        abstract Spliterator<E_OUT> wrapSpliterator(Spliterator<T> sourceSpliterator);

        void evaluate(Consumer<? super E_OUT> consumer) {
            Spliterator<T> sourceSplit = getSourceSpliterator();
            Spliterator<E_OUT> wrappedSplit = wrapSpliterator(sourceSplit);

            if (isParallel) {
                parallelEvaluate(wrappedSplit, consumer);
            } else {
                sequentialEvaluate(wrappedSplit, consumer);
            }
        }

        private Spliterator<T> getSourceSpliterator() {
            if (previousStage == null) {
                throw new IllegalStateException("No source stage");
            }
            if (previousStage instanceof SourceStage) {
                return ((SourceStage<T>) previousStage).spliterator;
            }
            return previousStage.wrapSpliterator(previousStage.getSourceSpliterator());
        }

        private void sequentialEvaluate(Spliterator<E_OUT> split, Consumer<? super E_OUT> consumer) {
            split.forEachRemaining(consumer);
        }

        private void parallelEvaluate(Spliterator<E_OUT> split, Consumer<? super E_OUT> consumer) {
            ForkJoinPool pool = ForkJoinPool.commonPool();
            try {
                pool.submit(() -> split.forEachRemaining(consumer)).join();
            } catch (Exception e) {
                sequentialEvaluate(split, consumer);
            }
        }
    }

    */
/**
     * 数据源阶段
     *//*

    static class SourceStage<T> extends PipelineStage<T, T> {
        private final Spliterator<T> spliterator;

        @SuppressWarnings("unchecked")
        public SourceStage(Collection<T> collection) {
            super(null);
            this.spliterator = collection.spliterator();
            this.isParallel = false;
        }

        @Override
        Spliterator<T> wrapSpliterator(Spliterator<T> sourceSpliterator) {
            return sourceSpliterator;
        }

        @Override
        public Stream<T> filter(Predicate<? super T> predicate) {
            return new FilterStage<>(this, predicate);
        }

        @Override
        public <R> Stream<R> map(Function<? super T, ? extends R> mapper) {
            return new MapStage<>(this, mapper);
        }

        @Override
        public Stream<T> distinct() {
            return new DistinctStage<>(this);
        }

        @Override
        public Stream<T> sorted(Comparator<? super T> comparator) {
            return new SortedStage<>(this, comparator);
        }

        @Override
        public Stream<T> limit(long maxSize) {
            return new LimitStage<>(this, maxSize);
        }

        @Override
        public Stream<T> skip(long n) {
            return new SkipStage<>(this, n);
        }

        @Override
        public Stream<T> peek(Consumer<? super T> action) {
            return new PeekStage<>(this, action);
        }

        @Override
        public void forEach(Consumer<? super T> action) {
            evaluate(action);
        }

        @Override
        public <A> A collect(Collector<? super T, A> collector) {
            A container = collector.supplier().get();
            evaluate(item -> collector.accumulator().accept(container, item));
            return (A) collector.finisher().apply(container);
        }

        @Override
        public Optional<T> reduce(BinaryOperator<T> accumulator) {
            AtomicReference<T> result = new AtomicReference<>(null);
            AtomicBoolean hasValue = new AtomicBoolean(false);
            evaluate(item -> {
                if (!hasValue.get()) {
                    result.set(item);
                    hasValue.set(true);
                } else {
                    result.set(accumulator.apply(result.get(), item));
                }
            });
            return hasValue.get() ? Optional.of(result.get()) : Optional.empty();
        }

        @Override
        public long count() {
            long[] count = {0};
            evaluate(item -> count[0]++);
            return count[0];
        }

        @Override
        public boolean anyMatch(Predicate<? super T> predicate) {
            AtomicBoolean found = new AtomicBoolean(false);
            evaluate(item -> {
                if (!found.get() && predicate.test(item)) {
                    found.set(true);
                }
            });
            return found.get();
        }

        @Override
        public boolean allMatch(Predicate<? super T> predicate) {
            AtomicBoolean allMatch = new AtomicBoolean(true);
            evaluate(item -> {
                if (allMatch.get() && !predicate.test(item)) {
                    allMatch.set(false);
                }
            });
            return allMatch.get();
        }

        @Override
        public boolean noneMatch(Predicate<? super T> predicate) {
            AtomicBoolean found = new AtomicBoolean(false);
            evaluate(item -> {
                if (!found.get() && predicate.test(item)) {
                    found.set(true);
                }
            });
            return !found.get();
        }

        @Override
        public Optional<T> findFirst() {
            AtomicReference<T> first = new AtomicReference<>(null);
            evaluate(item -> {
                if (first.get() == null) {
                    first.set(item);
                }
            });
            return first.get() != null ? Optional.of(first.get()) : Optional.empty();
        }

        @Override
        public List<T> toList() {
            List<T> list = new ArrayList<>();
            evaluate(list::add);
            return list;
        }
    }

    */
/**
     * Filter 中间操作阶段
     *//*

    static class FilterStage<T> extends PipelineStage<T, T> {
        private final Predicate<? super T> predicate;

        public FilterStage(PipelineStage<?, T> previous, Predicate<? super T> predicate) {
            super(previous);
            this.predicate = predicate;
            this.isParallel = previous.isParallel;
        }

        @Override
        Spliterator<T> wrapSpliterator(Spliterator<T> sourceSpliterator) {
            return new FilterSpliterator<>(sourceSpliterator, predicate);
        }

        @Override
        public Stream<T> filter(Predicate<? super T> predicate) {
            return new FilterStage<>(this, predicate);
        }

        @Override
        public <R> Stream<R> map(Function<? super T, ? extends R> mapper) {
            return new MapStage<>(this, mapper);
        }

        @Override
        public Stream<T> distinct() {
            return new DistinctStage<>(this);
        }

        @Override
        public Stream<T> sorted(Comparator<? super T> comparator) {
            return new SortedStage<>(this, comparator);
        }

        @Override
        public Stream<T> limit(long maxSize) {
            return new LimitStage<>(this, maxSize);
        }

        @Override
        public Stream<T> skip(long n) {
            return new SkipStage<>(this, n);
        }

        @Override
        public Stream<T> peek(Consumer<? super T> action) {
            return new PeekStage<>(this, action);
        }

        @Override
        public void forEach(Consumer<? super T> action) {
            if (isTerminated) throw new IllegalStateException("Stream already operated");
            isTerminated = true;
            evaluate(action);
        }

        @Override
        public <A> A collect(Collector<? super T, A> collector) {
            A container = collector.supplier().get();
            forEach(item -> collector.accumulator().accept(container, item));
            return collector.finisher().apply(container);
        }

        @Override
        public Optional<T> reduce(BinaryOperator<T> accumulator) {
            AtomicReference<T> result = new AtomicReference<>(null);
            AtomicBoolean hasValue = new AtomicBoolean(false);
            forEach(item -> {
                if (!hasValue.get()) {
                    result.set(item);
                    hasValue.set(true);
                } else {
                    result.set(accumulator.apply(result.get(), item));
                }
            });
            return hasValue.get() ? Optional.of(result.get()) : Optional.empty();
        }

        @Override
        public long count() {
            long[] count = {0};
            forEach(item -> count[0]++);
            return count[0];
        }

        @Override
        public boolean anyMatch(Predicate<? super T> predicate) {
            AtomicBoolean found = new AtomicBoolean(false);
            forEach(item -> {
                if (!found.get() && predicate.test(item)) {
                    found.set(true);
                }
            });
            return found.get();
        }

        @Override
        public boolean allMatch(Predicate<? super T> predicate) {
            AtomicBoolean allMatch = new AtomicBoolean(true);
            forEach(item -> {
                if (allMatch.get() && !predicate.test(item)) {
                    allMatch.set(false);
                }
            });
            return allMatch.get();
        }

        @Override
        public boolean noneMatch(Predicate<? super T> predicate) {
            AtomicBoolean found = new AtomicBoolean(false);
            forEach(item -> {
                if (!found.get() && predicate.test(item)) {
                    found.set(true);
                }
            });
            return !found.get();
        }

        @Override
        public Optional<T> findFirst() {
            AtomicReference<T> first = new AtomicReference<>(null);
            forEach(item -> {
                if (first.get() == null) {
                    first.set(item);
                }
            });
            return first.get() != null ? Optional.of(first.get()) : Optional.empty();
        }

        @Override
        public List<T> toList() {
            List<T> list = new ArrayList<>();
            forEach(list::add);
            return list;
        }
    }

    */
/**
     * Map 中间操作阶段
     *//*

    static class MapStage<T, R> extends PipelineStage<T, R> {
        private final Function<? super T, ? extends R> mapper;

        public MapStage(PipelineStage<?, T> previous, Function<? super T, ? extends R> mapper) {
            super(previous);
            this.mapper = mapper;
            this.isParallel = previous.isParallel;
        }

        @Override
        Spliterator<R> wrapSpliterator(Spliterator<T> sourceSpliterator) {
            return new MapSpliterator<>(sourceSpliterator, mapper);
        }

        @Override
        public Stream<R> filter(Predicate<? super R> predicate) {
            return new FilterStage<>(this, predicate);
        }

        @Override
        public <R2> Stream<R2> map(Function<? super R, ? extends R2> mapper) {
            return new MapStage<>(this, mapper);
        }

        @Override
        public Stream<R> distinct() {
            return new DistinctStage<>(this);
        }

        @Override
        public Stream<R> sorted(Comparator<? super R> comparator) {
            return new SortedStage<>(this, comparator);
        }

        @Override
        public Stream<R> limit(long maxSize) {
            return new LimitStage<>(this, maxSize);
        }

        @Override
        public Stream<R> skip(long n) {
            return new SkipStage<>(this, n);
        }

        @Override
        public Stream<R> peek(Consumer<? super R> action) {
            return new PeekStage<>(this, action);
        }

        @Override
        public void forEach(Consumer<? super R> action) {
            if (isTerminated) throw new IllegalStateException("Stream already operated");
            isTerminated = true;
            evaluate(action);
        }

        @Override
        public <A> A collect(Collector<? super R, A> collector) {
            A container = collector.supplier().get();
            forEach(item -> collector.accumulator().accept(container, item));
            return collector.finisher().apply(container);
        }

        @Override
        public Optional<R> reduce(BinaryOperator<R> accumulator) {
            AtomicReference<R> result = new AtomicReference<>(null);
            AtomicBoolean hasValue = new AtomicBoolean(false);
            forEach(item -> {
                if (!hasValue.get()) {
                    result.set(item);
                    hasValue.set(true);
                } else {
                    result.set(accumulator.apply(result.get(), item));
                }
            });
            return hasValue.get() ? Optional.of(result.get()) : Optional.empty();
        }

        @Override
        public long count() {
            long[] count = {0};
            forEach(item -> count[0]++);
            return count[0];
        }

        @Override
        public boolean anyMatch(Predicate<? super R> predicate) {
            AtomicBoolean found = new AtomicBoolean(false);
            forEach(item -> {
                if (!found.get() && predicate.test(item)) {
                    found.set(true);
                }
            });
            return found.get();
        }

        @Override
        public boolean allMatch(Predicate<? super R> predicate) {
            AtomicBoolean allMatch = new AtomicBoolean(true);
            forEach(item -> {
                if (allMatch.get() && !predicate.test(item)) {
                    allMatch.set(false);
                }
            });
            return allMatch.get();
        }

        @Override
        public boolean noneMatch(Predicate<? super R> predicate) {
            AtomicBoolean found = new AtomicBoolean(false);
            forEach(item -> {
                if (!found.get() && predicate.test(item)) {
                    found.set(true);
                }
            });
            return !found.get();
        }

        @Override
        public Optional<R> findFirst() {
            AtomicReference<R> first = new AtomicReference<>(null);
            forEach(item -> {
                if (first.get() == null) {
                    first.set(item);
                }
            });
            return first.get() != null ? Optional.of(first.get()) : Optional.empty();
        }

        @Override
        public List<R> toList() {
            List<R> list = new ArrayList<>();
            forEach(list::add);
            return list;
        }
    }

    */
/**
     * Distinct 中间操作阶段
     *//*

    static class DistinctStage<T> extends PipelineStage<T, T> {
        public DistinctStage(PipelineStage<?, T> previous) {
            super(previous);
            this.isParallel = previous.isParallel;
        }

        @Override
        Spliterator<T> wrapSpliterator(Spliterator<T> sourceSpliterator) {
            return new DistinctSpliterator<>(sourceSpliterator);
        }

        @Override
        public Stream<T> filter(Predicate<? super T> predicate) {
            return new FilterStage<>(this, predicate);
        }

        @Override
        public <R> Stream<R> map(Function<? super T, ? extends R> mapper) {
            return new MapStage<>(this, mapper);
        }

        @Override
        public Stream<T> distinct() {
            return this;
        }

        @Override
        public Stream<T> sorted(Comparator<? super T> comparator) {
            return new SortedStage<>(this, comparator);
        }

        @Override
        public Stream<T> limit(long maxSize) {
            return new LimitStage<>(this, maxSize);
        }

        @Override
        public Stream<T> skip(long n) {
            return new SkipStage<>(this, n);
        }

        @Override
        public Stream<T> peek(Consumer<? super T> action) {
            return new PeekStage<>(this, action);
        }

        @Override
        public void forEach(Consumer<? super T> action) {
            if (isTerminated) throw new IllegalStateException("Stream already operated");
            isTerminated = true;
            evaluate(action);
        }

        @Override
        public <A> A collect(Collector<? super T, A> collector) {
            A container = collector.supplier().get();
            forEach(item -> collector.accumulator().accept(container, item));
            return collector.finisher().apply(container);
        }

        @Override
        public Optional<T> reduce(BinaryOperator<T> accumulator) {
            AtomicReference<T> result = new AtomicReference<>(null);
            AtomicBoolean hasValue = new AtomicBoolean(false);
            forEach(item -> {
                if (!hasValue.get()) {
                    result.set(item);
                    hasValue.set(true);
                } else {
                    result.set(accumulator.apply(result.get(), item));
                }
            });
            return hasValue.get() ? Optional.of(result.get()) : Optional.empty();
        }

        @Override
        public long count() {
            long[] count = {0};
            forEach(item -> count[0]++);
            return count[0];
        }

        @Override
        public boolean anyMatch(Predicate<? super T> predicate) {
            AtomicBoolean found = new AtomicBoolean(false);
            forEach(item -> {
                if (!found.get() && predicate.test(item)) {
                    found.set(true);
                }
            });
            return found.get();
        }

        @Override
        public boolean allMatch(Predicate<? super T> predicate) {
            AtomicBoolean allMatch = new AtomicBoolean(true);
            forEach(item -> {
                if (allMatch.get() && !predicate.test(item)) {
                    allMatch.set(false);
                }
            });
            return allMatch.get();
        }

        @Override
        public boolean noneMatch(Predicate<? super T> predicate) {
            AtomicBoolean found = new AtomicBoolean(false);
            forEach(item -> {
                if (!found.get() && predicate.test(item)) {
                    found.set(true);
                }
            });
            return !found.get();
        }

        @Override
        public Optional<T> findFirst() {
            AtomicReference<T> first = new AtomicReference<>(null);
            forEach(item -> {
                if (first.get() == null) {
                    first.set(item);
                }
            });
            return first.get() != null ? Optional.of(first.get()) : Optional.empty();
        }

        @Override
        public List<T> toList() {
            List<T> list = new ArrayList<>();
            forEach(list::add);
            return list;
        }
    }

    */
/**
     * Sorted 中间操作阶段
     *//*

    static class SortedStage<T> extends PipelineStage<T, T> {
        private final Comparator<? super T> comparator;

        public SortedStage(PipelineStage<?, T> previous, Comparator<? super T> comparator) {
            super(previous);
            this.comparator = comparator;
            this.isParallel = previous.isParallel;
        }

        @Override
        Spliterator<T> wrapSpliterator(Spliterator<T> sourceSpliterator) {
            return new SortedSpliterator<>(sourceSpliterator, comparator);
        }

        @Override
        public Stream<T> filter(Predicate<? super T> predicate) {
            return new FilterStage<>(this, predicate);
        }

        @Override
        public <R> Stream<R> map(Function<? super T, ? extends R> mapper) {
            return new MapStage<>(this, mapper);
        }

        @Override
        public Stream<T> distinct() {
            return new DistinctStage<>(this);
        }

        @Override
        public Stream<T> sorted(Comparator<? super T> comparator) {
            return new SortedStage<>(this, comparator);
        }

        @Override
        public Stream<T> limit(long maxSize) {
            return new LimitStage<>(this, maxSize);
        }

        @Override
        public Stream<T> skip(long n) {
            return new SkipStage<>(this, n);
        }

        @Override
        public Stream<T> peek(Consumer<? super T> action) {
            return new PeekStage<>(this, action);
        }

        @Override
        public void forEach(Consumer<? super T> action) {
            if (isTerminated) throw new IllegalStateException("Stream already operated");
            isTerminated = true;
            evaluate(action);
        }

        @Override
        public <A> A collect(Collector<? super T, A> collector) {
            A container = collector.supplier().get();
            forEach(item -> collector.accumulator().accept(container, item));
            return collector.finisher().apply(container);
        }

        @Override
        public Optional<T> reduce(BinaryOperator<T> accumulator) {
            AtomicReference<T> result = new AtomicReference<>(null);
            AtomicBoolean hasValue = new AtomicBoolean(false);
            forEach(item -> {
                if (!hasValue.get()) {
                    result.set(item);
                    hasValue.set(true);
                } else {
                    result.set(accumulator.apply(result.get(), item));
                }
            });
            return hasValue.get() ? Optional.of(result.get()) : Optional.empty();
        }

        @Override
        public long count() {
            long[] count = {0};
            forEach(item -> count[0]++);
            return count[0];
        }

        @Override
        public boolean anyMatch(Predicate<? super T> predicate) {
            AtomicBoolean found = new AtomicBoolean(false);
            forEach(item -> {
                if (!found.get() && predicate.test(item)) {
                    found.set(true);
                }
            });
            return found.get();
        }

        @Override
        public boolean allMatch(Predicate<? super T> predicate) {
            AtomicBoolean allMatch = new AtomicBoolean(true);
            forEach(item -> {
                if (allMatch.get() && !predicate.test(item)) {
                    allMatch.set(false);
                }
            });
            return allMatch.get();
        }

        @Override
        public boolean noneMatch(Predicate<? super T> predicate) {
            AtomicBoolean found = new AtomicBoolean(false);
            forEach(item -> {
                if (!found.get() && predicate.test(item)) {
                    found.set(true);
                }
            });
            return !found.get();
        }

        @Override
        public Optional<T> findFirst() {
            AtomicReference<T> first = new AtomicReference<>(null);
            forEach(item -> {
                if (first.get() == null) {
                    first.set(item);
                }
            });
            return first.get() != null ? Optional.of(first.get()) : Optional.empty();
        }

        @Override
        public List<T> toList() {
            List<T> list = new ArrayList<>();
            forEach(list::add);
            return list;
        }
    }

    */
/**
     * Limit 中间操作阶段
     *//*

    static class LimitStage<T> extends PipelineStage<T, T> {
        private final long maxSize;

        public LimitStage(PipelineStage<?, T> previous, long maxSize) {
            super(previous);
            this.maxSize = maxSize;
            this.isParallel = previous.isParallel;
        }

        @Override
        Spliterator<T> wrapSpliterator(Spliterator<T> sourceSpliterator) {
            return new LimitSpliterator<>(sourceSpliterator, maxSize);
        }

        @Override
        public Stream<T> filter(Predicate<? super T> predicate) {
            return new FilterStage<>(this, predicate);
        }

        @Override
        public <R> Stream<R> map(Function<? super T, ? extends R> mapper) {
            return new MapStage<>(this, mapper);
        }

        @Override
        public Stream<T> distinct() {
            return new DistinctStage<>(this);
        }

        @Override
        public Stream<T> sorted(Comparator<? super T> comparator) {
            return new SortedStage<>(this, comparator);
        }

        @Override
        public Stream<T> limit(long maxSize) {
            return new LimitStage<>(this, Math.min(this.maxSize, maxSize));
        }

        @Override
        public Stream<T> skip(long n) {
            return new SkipStage<>(this, n);
        }

        @Override
        public Stream<T> peek(Consumer<? super T> action) {
            return new PeekStage<>(this, action);
        }

        @Override
        public void forEach(Consumer<? super T> action) {
            if (isTerminated) throw new IllegalStateException("Stream already operated");
            isTerminated = true;
            evaluate(action);
        }

        @Override
        public <A> A collect(Collector<? super T, A> collector) {
            A container = collector.supplier().get();
            forEach(item -> collector.accumulator().accept(container, item));
            return collector.finisher().apply(container);
        }

        @Override
        public Optional<T> reduce(BinaryOperator<T> accumulator) {
            AtomicReference<T> result = new AtomicReference<>(null);
            AtomicBoolean hasValue = new AtomicBoolean(false);
            forEach(item -> {
                if (!hasValue.get()) {
                    result.set(item);
                    hasValue.set(true);
                } else {
                    result.set(accumulator.apply(result.get(), item));
                }
            });
            return hasValue.get() ? Optional.of(result.get()) : Optional.empty();
        }

        @Override
        public long count() {
            long[] count = {0};
            forEach(item -> count[0]++);
            return count[0];
        }

        @Override
        public boolean anyMatch(Predicate<? super T> predicate) {
            AtomicBoolean found = new AtomicBoolean(false);
            forEach(item -> {
                if (!found.get() && predicate.test(item)) {
                    found.set(true);
                }
            });
            return found.get();
        }

        @Override
        public boolean allMatch(Predicate<? super T> predicate) {
            AtomicBoolean allMatch = new AtomicBoolean(true);
            forEach(item -> {
                if (allMatch.get() && !predicate.test(item)) {
                    allMatch.set(false);
                }
            });
            return allMatch.get();
        }

        @Override
        public boolean noneMatch(Predicate<? super T> predicate) {
            AtomicBoolean found = new AtomicBoolean(false);
            forEach(item -> {
                if (!found.get() && predicate.test(item)) {
                    found.set(true);
                }
            });
            return !found.get();
        }

        @Override
        public Optional<T> findFirst() {
            AtomicReference<T> first = new AtomicReference<>(null);
            forEach(item -> {
                if (first.get() == null) {
                    first.set(item);
                }
            });
            return first.get() != null ? Optional.of(first.get()) : Optional.empty();
        }

        @Override
        public List<T> toList() {
            List<T> list = new ArrayList<>();
            forEach(list::add);
            return list;
        }
    }

    */
/**
     * Skip 中间操作阶段
     *//*

    static class SkipStage<T> extends PipelineStage<T, T> {
        private final long n;

        public SkipStage(PipelineStage<?, T> previous, long n) {
            super(previous);
            this.n = n;
            this.isParallel = previous.isParallel;
        }

        @Override
        Spliterator<T> wrapSpliterator(Spliterator<T> sourceSpliterator) {
            return new SkipSpliterator<>(sourceSpliterator, n);
        }

        @Override
        public Stream<T> filter(Predicate<? super T> predicate) {
            return new FilterStage<>(this, predicate);
        }

        @Override
        public <R> Stream<R> map(Function<? super T, ? extends R> mapper) {
            return new MapStage<>(this, mapper);
        }

        @Override
        public Stream<T> distinct() {
            return new DistinctStage<>(this);
        }

        @Override
        public Stream<T> sorted(Comparator<? super T> comparator) {
            return new SortedStage<>(this, comparator);
        }

        @Override
        public Stream<T> limit(long maxSize) {
            return new LimitStage<>(this, maxSize);
        }

        @Override
        public Stream<T> skip(long n) {
            return new SkipStage<>(this, this.n + n);
        }

        @Override
        public Stream<T> peek(Consumer<? super T> action) {
            return new PeekStage<>(this, action);
        }

        @Override
        public void forEach(Consumer<? super T> action) {
            if (isTerminated) throw new IllegalStateException("Stream already operated");
            isTerminated = true;
            evaluate(action);
        }

        @Override
        public <A> A collect(Collector<? super T, A> collector) {
            A container = collector.supplier().get();
            forEach(item -> collector.accumulator().accept(container, item));
            return collector.finisher().apply(container);
        }

        @Override
        public Optional<T> reduce(BinaryOperator<T> accumulator) {
            AtomicReference<T> result = new AtomicReference<>(null);
            AtomicBoolean hasValue = new AtomicBoolean(false);
            forEach(item -> {
                if (!hasValue.get()) {
                    result.set(item);
                    hasValue.set(true);
                } else {
                    result.set(accumulator.apply(result.get(), item));
                }
            });
            return hasValue.get() ? Optional.of(result.get()) : Optional.empty();
        }

        @Override
        public long count() {
            long[] count = {0};
            forEach(item -> count[0]++);
            return count[0];
        }

        @Override
        public boolean anyMatch(Predicate<? super T> predicate) {
            AtomicBoolean found = new AtomicBoolean(false);
            forEach(item -> {
                if (!found.get() && predicate.test(item)) {
                    found.set(true);
                }
            });
            return found.get();
        }

        @Override
        public boolean allMatch(Predicate<? super T> predicate) {
            AtomicBoolean allMatch = new AtomicBoolean(true);
            forEach(item -> {
                if (allMatch.get() && !predicate.test(item)) {
                    allMatch.set(false);
                }
            });
            return allMatch.get();
        }

        @Override
        public boolean noneMatch(Predicate<? super T> predicate) {
            AtomicBoolean found = new AtomicBoolean(false);
            forEach(item -> {
                if (!found.get() && predicate.test(item)) {
                    found.set(true);
                }
            });
            return !found.get();
        }

        @Override
        public Optional<T> findFirst() {
            AtomicReference<T> first = new AtomicReference<>(null);
            forEach(item -> {
                if (first.get() == null) {
                    first.set(item);
                }
            });
            return first.get() != null ? Optional.of(first.get()) : Optional.empty();
        }

        @Override
        public List<T> toList() {
            List<T> list = new ArrayList<>();
            forEach(list::add);
            return list;
        }
    }

    */
/**
     * Peek 中间操作阶段（用于调试）
     *//*

    static class PeekStage<T> extends PipelineStage<T, T> {
        private final Consumer<? super T> action;

        public PeekStage(PipelineStage<?, T> previous, Consumer<? super T> action) {
            super(previous);
            this.action = action;
            this.isParallel = previous.isParallel;
        }

        @Override
        Spliterator<T> wrapSpliterator(Spliterator<T> sourceSpliterator) {
            return new PeekSpliterator<>(sourceSpliterator, action);
        }

        @Override
        public Stream<T> filter(Predicate<? super T> predicate) {
            return new FilterStage<>(this, predicate);
        }

        @Override
        public <R> Stream<R> map(Function<? super T, ? extends R> mapper) {
            return new MapStage<>(this, mapper);
        }

        @Override
        public Stream<T> distinct() {
            return new DistinctStage<>(this);
        }

        @Override
        public Stream<T> sorted(Comparator<? super T> comparator) {
            return new SortedStage<>(this, comparator);
        }

        @Override
        public Stream<T> limit(long maxSize) {
            return new LimitStage<>(this, maxSize);
        }

        @Override
        public Stream<T> skip(long n) {
            return new SkipStage<>(this, n);
        }

        @Override
        public Stream<T> peek(Consumer<? super T> action) {
            return new PeekStage<>(this, action);
        }

        @Override
        public void forEach(Consumer<? super T> action) {
            if (isTerminated) throw new IllegalStateException("Stream already operated");
            isTerminated = true;
            evaluate(action);
        }

        @Override
        public <A> A collect(Collector<? super T, A> collector) {
            A container = collector.supplier().get();
            forEach(item -> collector.accumulator().accept(container, item));
            return collector.finisher().apply(container);
        }

        @Override
        public Optional<T> reduce(BinaryOperator<T> accumulator) {
            AtomicReference<T> result = new AtomicReference<>(null);
            AtomicBoolean hasValue = new AtomicBoolean(false);
            forEach(item -> {
                if (!hasValue.get()) {
                    result.set(item);
                    hasValue.set(true);
                } else {
                    result.set(accumulator.apply(result.get(), item));
                }
            });
            return hasValue.get() ? Optional.of(result.get()) : Optional.empty();
        }

        @Override
        public long count() {
            long[] count = {0};
            forEach(item -> count[0]++);
            return count[0];
        }

        @Override
        public boolean anyMatch(Predicate<? super T> predicate) {
            AtomicBoolean found = new AtomicBoolean(false);
            forEach(item -> {
                if (!found.get() && predicate.test(item)) {
                    found.set(true);
                }
            });
            return found.get();
        }

        @Override
        public boolean allMatch(Predicate<? super T> predicate) {
            AtomicBoolean allMatch = new AtomicBoolean(true);
            forEach(item -> {
                if (allMatch.get() && !predicate.test(item)) {
                    allMatch.set(false);
                }
            });
            return allMatch.get();
        }

        @Override
        public boolean noneMatch(Predicate<? super T> predicate) {
            AtomicBoolean found = new AtomicBoolean(false);
            forEach(item -> {
                if (!found.get() && predicate.test(item)) {
                    found.set(true);
                }
            });
            return !found.get();
        }

        @Override
        public Optional<T> findFirst() {
            AtomicReference<T> first = new AtomicReference<>(null);
            forEach(item -> {
                if (first.get() == null) {
                    first.set(item);
                }
            });
            return first.get() != null ? Optional.of(first.get()) : Optional.empty();
        }

        @Override
        public List<T> toList() {
            List<T> list = new ArrayList<>();
            forEach(list::add);
            return list;
        }
    }

    // ======================== 分割迭代器实现（核心遍历机制） ========================

    */
/**
     * 分割迭代器接口
     *//*

    interface Spliterator<T> {
        boolean tryAdvance(Consumer<? super T> action);
        void forEachRemaining(Consumer<? super T> action);
        Spliterator<T> trySplit();
        long estimateSize();
    }

    */
/**
     * 数组分割迭代器
     *//*

    static class ArraySpliterator<T> implements Spliterator<T> {
        private final T[] array;
        private int index;
        private final int fence;

        @SuppressWarnings("unchecked")
        public ArraySpliterator(Collection<T> collection) {
            this.array = (T[]) collection.toArray();
            this.index = 0;
            this.fence = array.length;
        }

        private ArraySpliterator(T[] array, int index, int fence) {
            this.array = array;
            this.index = index;
            this.fence = fence;
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            if (index < fence) {
                action.accept(array[index++]);
                return true;
            }
            return false;
        }

        @Override
        public void forEachRemaining(Consumer<? super T> action) {
            while (index < fence) {
                action.accept(array[index++]);
            }
        }

        @Override
        public Spliterator<T> trySplit() {
            int mid = (index + fence) >>> 1;
            if (mid <= index) {
                return null;
            }
            ArraySpliterator<T> split = new ArraySpliterator<>(array, index, mid);
            this.index = mid;
            return split;
        }

        @Override
        public long estimateSize() {
            return fence - index;
        }
    }

    */
/**
     * Filter 分割迭代器
     *//*

    static class FilterSpliterator<T> implements Spliterator<T> {
        private final Spliterator<T> source;
        private final Predicate<? super T> predicate;

        public FilterSpliterator(Spliterator<T> source, Predicate<? super T> predicate) {
            this.source = source;
            this.predicate = predicate;
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            while (source.tryAdvance(item -> {
                if (predicate.test(item)) {
                    action.accept(item);
                }
            })) {
                return true;
            }
            return false;
        }

        @Override
        public void forEachRemaining(Consumer<? super T> action) {
            source.forEachRemaining(item -> {
                if (predicate.test(item)) {
                    action.accept(item);
                }
            });
        }

        @Override
        public Spliterator<T> trySplit() {
            Spliterator<T> leftSplit = source.trySplit();
            return leftSplit == null ? null : new FilterSpliterator<>(leftSplit, predicate);
        }

        @Override
        public long estimateSize() {
            return source.estimateSize() / 2;
        }
    }

    */
/**
     * Map 分割迭代器
     *//*

    static class MapSpliterator<T, R> implements Spliterator<R> {
        private final Spliterator<T> source;
        private final Function<? super T, ? extends R> mapper;

        public MapSpliterator(Spliterator<T> source, Function<? super T, ? extends R> mapper) {
            this.source = source;
            this.mapper = mapper;
        }

        @Override
        public boolean tryAdvance(Consumer<? super R> action) {
            return source.tryAdvance(item -> action.accept(mapper.apply(item)));
        }

        @Override
        public void forEachRemaining(Consumer<? super R> action) {
            source.forEachRemaining(item -> action.accept(mapper.apply(item)));
        }

        @Override
        public Spliterator<R> trySplit() {
            Spliterator<T> leftSplit = source.trySplit();
            return leftSplit == null ? null : new MapSpliterator<>(leftSplit, mapper);
        }

        @Override
        public long estimateSize() {
            return source.estimateSize();
        }
    }

    */
/**
     * Distinct 分割迭代器
     *//*

    static class DistinctSpliterator<T> implements Spliterator<T> {
        private final Spliterator<T> source;
        private Set<T> seen;

        public DistinctSpliterator(Spliterator<T> source) {
            this.source = source;
            this.seen = new HashSet<>();
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            while (source.tryAdvance(item -> {
                if (seen.add(item)) {
                    action.accept(item);
                }
            })) {
                return true;
            }
            return false;
        }

        @Override
        public void forEachRemaining(Consumer<? super T> action) {
            source.forEachRemaining(item -> {
                if (seen.add(item)) {
                    action.accept(item);
                }
            });
        }

        @Override
        public Spliterator<T> trySplit() {
            Spliterator<T> leftSplit = source.trySplit();
            return leftSplit == null ? null : new DistinctSpliterator<>(leftSplit);
        }

        @Override
        public long estimateSize() {
            return source.estimateSize();
        }
    }

    */
/**
     * Sorted 分割迭代器（全量排序实现）
     *//*

    static class SortedSpliterator<T> implements Spliterator<T> {
        private final List<T> sortedList;
        private int index;

        public SortedSpliterator(Spliterator<T> source, Comparator<? super T> comparator) {
            List<T> list = new ArrayList<>();
            source.forEachRemaining(list::add);
            list.sort(comparator);
            this.sortedList = list;
            this.index = 0;
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            if (index < sortedList.size()) {
                action.accept(sortedList.get(index++));
                return true;
            }
            return false;
        }

        @Override
        public void forEachRemaining(Consumer<? super T> action) {
            while (index < sortedList.size()) {
                action.accept(sortedList.get(index++));
            }
        }

        @Override
        public Spliterator<T> trySplit() {
            int mid = (index + sortedList.size()) >>> 1;
            if (mid <= index) {
                return null;
            }
            List<T> leftList = sortedList.subList(index, mid);
            this.index = mid;
            return new ListSpliterator<>(leftList);
        }

        @Override
        public long estimateSize() {
            return sortedList.size() - index;
        }
    }

    */
/**
     * Limit 分割迭代器
     *//*

    static class LimitSpliterator<T> implements Spliterator<T> {
        private final Spliterator<T> source;
        private long remaining;

        public LimitSpliterator(Spliterator<T> source, long maxSize) {
            this.source = source;
            this.remaining = maxSize;
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            if (remaining > 0 && source.tryAdvance(action)) {
                remaining--;
                return true;
            }
            return false;
        }

        @Override
        public void forEachRemaining(Consumer<? super T> action) {
            while (remaining > 0 && source.tryAdvance(action)) {
                remaining--;
            }
        }

        @Override
        public Spliterator<T> trySplit() {
            return null;
        }

        @Override
        public long estimateSize() {
            long size = source.estimateSize();
            return size < 0 ? remaining : Math.min(remaining, size);
        }
    }

    */
/**
     * Skip 分割迭代器
     *//*

    static class SkipSpliterator<T> implements Spliterator<T> {
        private final Spliterator<T> source;
        private long toSkip;
        private boolean skipped = false;

        public SkipSpliterator(Spliterator<T> source, long n) {
            this.source = source;
            this.toSkip = n;
        }

        private void skip() {
            if (!skipped) {
                for (long i = 0; i < toSkip && source.tryAdvance(item -> {}); i++);
                skipped = true;
            }
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            skip();
            return source.tryAdvance(action);
        }

        @Override
        public void forEachRemaining(Consumer<? super T> action) {
            skip();
            source.forEachRemaining(action);
        }

        @Override
        public Spliterator<T> trySplit() {
            return null;
        }

        @Override
        public long estimateSize() {
            long size = source.estimateSize();
            return size < 0 ? size : Math.max(0, size - toSkip);
        }
    }

    */
/**
     * Peek 分割迭代器
     *//*

    static class PeekSpliterator<T> implements Spliterator<T> {
        private final Spliterator<T> source;
        private final Consumer<? super T> action;

        public PeekSpliterator(Spliterator<T> source, Consumer<? super T> action) {
            this.source = source;
            this.action = action;
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            return source.tryAdvance(item -> {
                this.action.accept(item);
                action.accept(item);
            });
        }

        @Override
        public void forEachRemaining(Consumer<? super T> action) {
            source.forEachRemaining(item -> {
                this.action.accept(item);
                action.accept(item);
            });
        }

        @Override
        public Spliterator<T> trySplit() {
            Spliterator<T> leftSplit = source.trySplit();
            return leftSplit == null ? null : new PeekSpliterator<>(leftSplit, this.action);
        }

        @Override
        public long estimateSize() {
            return source.estimateSize();
        }
    }

    */
/**
     * 列表分割迭代器
     *//*

    static class ListSpliterator<T> implements Spliterator<T> {
        private final List<T> list;
        private int index;
        private final int fence;

        public ListSpliterator(List<T> list) {
            this.list = list;
            this.index = 0;
            this.fence = list.size();
        }

        private ListSpliterator(List<T> list, int index, int fence) {
            this.list = list;
            this.index = index;
            this.fence = fence;
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            if (index < fence) {
                action.accept(list.get(index++));
                return true;
            }
            return false;
        }

        @Override
        public void forEachRemaining(Consumer<? super T> action) {
            while (index < fence) {
                action.accept(list.get(index++));
            }
        }

        @Override
        public Spliterator<T> trySplit() {
            int mid = (index + fence) >>> 1;
            if (mid <= index) {
                return null;
            }
            ListSpliterator<T> split = new ListSpliterator<>(list, index, mid);
            this.index = mid;
            return split;
        }

        @Override
        public long estimateSize() {
            return fence - index;
        }
    }

    // ======================== 收集器实现 ========================

    */
/**
     * 简单列表收集器
     *//*

    static class ToListCollector<T> implements Collector<T, List<T>> {
        @Override
        public Supplier<List<T>> supplier() {
            return ArrayList::new;
        }

        @Override
        public BiConsumer<List<T>, T> accumulator() {
            return List::add;
        }

        @Override
        public BinaryOperator<List<T>> combiner() {
            return (left, right) -> {
                left.addAll(right);
                return left;
            };
        }

        @Override
        public Function<List<T>, ? extends List<T>> finisher() {
            return Function.identity();
        }
    }

    */
/**
     * 分组收集器
     *//*

    static class GroupingCollector<T, K> implements Collector<T, Map<K, List<T>>> {
        private final Function<? super T, ? extends K> classifier;

        public GroupingCollector(Function<? super T, ? extends K> classifier) {
            this.classifier = classifier;
        }

        @Override
        public Supplier<Map<K, List<T>>> supplier() {
            return HashMap::new;
        }

        @Override
        public BiConsumer<Map<K, List<T>>, T> accumulator() {
            return (map, item) -> {
                K key = classifier.apply(item);
                map.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
            };
        }

        @Override
        public BinaryOperator<Map<K, List<T>>> combiner() {
            return (left, right) -> {
                right.forEach((key, value) -> {
                    left.computeIfAbsent(key, k -> new ArrayList<>()).addAll(value);
                });
                return left;
            };
        }

        @Override
        public Function<Map<K, List<T>>, ? extends Map<K, List<T>>> finisher() {
            return Function.identity();
        }
    }

    // ======================== 工具类 ========================

    */
/**
     * Stream 工具类
     *//*

    static class Streams {
        public static <T> Stream<T> of(Collection<T> collection) {
            return new SourceStage<>(collection);
        }

        @SafeVarargs
        public static <T> Stream<T> of(T... elements) {
            return new SourceStage<>(Arrays.asList(elements));
        }

        public static <T> Stream<T> generate(Supplier<T> supplier) {
            List<T> list = new ArrayList<>();
            for (int i = 0; i < 10; i++) { // 限制生成数量
                list.add(supplier.get());
            }
            return new SourceStage<>(list);
        }

        public static <T> Stream<T> iterate(T seed, UnaryOperator<T> operator) {
            List<T> list = new ArrayList<>();
            T current = seed;
            for (int i = 0; i < 10; i++) { // 限制迭代次数
                list.add(current);
                current = operator.apply(current);
            }
            return new SourceStage<>(list);
        }

        public static <T> Stream<T> concat(Stream<T> a, Stream<T> b) {
            List<T> list = new ArrayList<>();
            a.forEach(list::add);
            b.forEach(list::add);
            return new SourceStage<>(list);
        }
    }

    // ======================== 演示代码 ========================

    public static void main(String[] args) {
        System.out.println("============================================================================");
        System.out.println("Java Stream API 核心原理与设计思路演示");
        System.out.println("============================================================================\n");

        // 准备测试数据
        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        // 演示1：基础操作链（惰性求值 + 流水线）
        System.out.println("【演示1】基础操作链 - Filter + Map + Limit");
        System.out.println("数据源: " + numbers);
        List<Integer> result1 = Streams.of(numbers)
                .filter(n -> n % 2 == 0)      // 过滤偶数
                .map(n -> n * n)              // 平方
                .limit(3)                     // 限制3个
                .toList();
        System.out.println("结果: " + result1);
        System.out.println("原理: 中间操作构建流水线，终端操作 toList() 触发实际执行\n");

        // 演示2：惰性求值（peek 观察执行时机）
        System.out.println("【演示2】惰性求值演示");
        System.out.println("注意: 没有终端操作时，中间操作不会执行");
        Streams.of(numbers)
                .peek(n -> System.out.println("  peek1: " + n))
                .filter(n -> n > 5)
                .peek(n -> System.out.println("  peek2: " + n));
        System.out.println("以上 peeks 不会输出，因为没有终端操作\n");

        System.out.println("添加终端操作 forEach 后:");
        Streams.of(numbers)
                .peek(n -> System.out.println("  peek1: " + n))
                .filter(n -> n > 5)
                .peek(n -> System.out.println("  peek2: " + n))
                .forEach(n -> {}); // 空操作触发执行
        System.out.println();

        // 演示3：Sorted + Distinct
        System.out.println("【演示3】排序与去重");
        List<Integer> duplicates = Arrays.asList(5, 3, 5, 1, 4, 2, 3, 2, 1);
        System.out.println("原始数据: " + duplicates);
        List<Integer> result3 = Streams.of(duplicates)
                .distinct()
                .sorted(Integer::compareTo)
                .toList();
        System.out.println("去重+排序: " + result3 + "\n");

        // 演示4：归约操作（Reduce）
        System.out.println("【演示4】归约操作 - Reduce");
        List<Integer> nums = Arrays.asList(1, 2, 3, 4, 5);
        Optional<Integer> sum = Streams.of(nums).reduce((a, b) -> a + b);
        System.out.println("数组: " + nums);
        System.out.println("求和: " + sum.orElse(0));

        Optional<Integer> product = Streams.of(nums).reduce((a, b) -> a * b);
        System.out.println("求积: " + product.orElse(1) + "\n");

        // 演示5：匹配操作
        System.out.println("【演示5】匹配操作");
        List<Integer> values = Arrays.asList(2, 4, 6, 8, 10);
        boolean allEven = Streams.of(values).allMatch(n -> n % 2 == 0);
        boolean anyGt5 = Streams.of(values).anyMatch(n -> n > 5);
        boolean noneLt0 = Streams.of(values).noneMatch(n -> n < 0);
        System.out.println("数组: " + values);
        System.out.println("全部偶数? " + allEven);
        System.out.println("存在大于5? " + anyGt5);
        System.out.println("没有负数? " + noneLt0 + "\n");

        // 演示6：收集器 - GroupingBy
        System.out.println("【演示6】收集器 - 分组");
        List<String> words = Arrays.asList("apple", "cat", "ant", "dog", "bird", "bat");
        System.out.println("单词: " + words);
        Map<Character, List<String>> grouped = Streams.of(words)
                .collect(new GroupingCollector<>(s -> s.charAt(0)));
        grouped.forEach((k, v) -> System.out.println("  '" + k + "' -> " + v));
        System.out.println();

        // 演示7：Skip + Limit（分页效果）
        System.out.println("【演示7】分页效果 - Skip + Limit");
        List<Integer> pageData = Arrays.asList(10, 20, 30, 40, 50, 60, 70, 80, 90, 100);
        System.out.println("全部数据: " + pageData);
        List<Integer> page1 = Streams.of(pageData).limit(5).toList();
        List<Integer> page2 = Streams.of(pageData).skip(5).limit(5).toList();
        System.out.println("第1页(5条): " + page1);
        System.out.println("第2页(5条): " + page2 + "\n");

        // 演示8：并行处理（使用 Fork/Join）
        System.out.println("【演示8】并行处理演示");
        List<Integer> largeList = new ArrayList<>();
        for (int i = 1; i <= 100; i++) largeList.add(i);

        long startTime = System.nanoTime();
        long sumParallel = Streams.of(largeList)
                .filter(n -> n % 2 == 0)
                .map(n -> n * 2)
                .reduce(Integer::sum)
                .orElse(0);
        long endTime = System.nanoTime();
        System.out.println("并行流计算偶数翻倍后总和: " + sumParallel);
        System.out.println("执行时间: " + (endTime - startTime) / 1000 + " μs\n");

        // 演示9：生成器与迭代
        System.out.println("【演示9】生成器与迭代");
        Stream<String> generated = Streams.generate(() -> "A");
        List<String> genList = generated.limit(5).toList();
        System.out.println("生成器(5个): " + genList);

        Stream<Integer> iterated = Streams.iterate(1, n -> n * 2);
        List<Integer> iterList = iterated.limit(6).toList();
        System.out.println("迭代器(乘2): " + iterList + "\n");

        // 演示10：复杂链式操作
        System.out.println("【演示10】复杂链式操作");
        List<String> names = Arrays.asList("Alice", "Bob", "Charlie", "David", "Eve", "Frank");
        List<String> result10 = Streams.of(names)
                .filter(s -> s.length() > 3)
                .map(String::toUpperCase)
                .sorted((a, b) -> b.compareTo(a)) // 降序
                .peek(s -> System.out.println("  处理中: " + s))
                .limit(3)
                .toList();
        System.out.println("最终结果: " + result10 + "\n");

        System.out.println("============================================================================");
        System.out.println("设计要点总结");
        System.out.println("============================================================================");
        System.out.println("1. 流水线模式: 中间操作构建链表，终端操作触发执行");
        System.out.println("2. 惰性求值: 无终端操作时，中间操作不执行");
        System.out.println("3. 内部迭代: Stream 控制迭代过程，开发者只需声明操作");
        System.out.println("4. 操作融合: 多个操作在单次遍历中完成（通过 Spliterator 链）");
        System.out.println("5. 并行支持: Spliterator.trySplit() 实现任务拆分，Fork/Join 执行");
        System.out.println("6. 无状态与有状态操作: filter/map 无状态，sorted/distinct 有状态");
        System.out.println("7. 短路操作: limit/anyMatch 可在条件满足时提前终止");
        System.out.println("============================================================================");
    }
}*/
