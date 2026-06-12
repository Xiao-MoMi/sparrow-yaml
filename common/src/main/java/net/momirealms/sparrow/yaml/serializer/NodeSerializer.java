package net.momirealms.sparrow.yaml.serializer;

import net.momirealms.sparrow.yaml.exception.AlternativesNodeException;
import net.momirealms.sparrow.yaml.exception.AlternativesNodeException.Failure;
import net.momirealms.sparrow.yaml.exception.InvalidNodeException;
import net.momirealms.sparrow.yaml.exception.MissingNodeException;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.node.SequenceNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.serializer.builder.Element;
import net.momirealms.sparrow.yaml.serializer.builder.Field;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public final class NodeSerializer<T> {
    private final Class<?> clazz;
    private final Decoder<T> decoder;
    private final Encoder<T> encoder;
    private final List<Decoder<T>> alternatives; // 只读候选, 不包含 primary

    private NodeSerializer(Class<?> clazz, Decoder<T> decoder, Encoder<T> encoder, List<Decoder<T>> alternatives) {
        this.clazz = clazz;
        this.decoder = decoder;
        this.encoder = encoder;
        this.alternatives = List.copyOf(alternatives);
    }

    @ApiStatus.Internal
    public static <T> NodeSerializer<T> createInternal(Class<?> clazz, Decoder<T> decoder, Encoder<T> encoder) {
        return new NodeSerializer<>(clazz, decoder, encoder, List.of());
    }

    /**
     * 将 YAML 节点解码为目标 Java 值.
     */
    @NotNull
    public T deserialize(@Nullable YamlNode<?> node) {
        T decoded = alternatives.isEmpty()
                ? decoder.deserialize(node)
                : decodeWithAlternatives(node, clazz, decoder, alternatives);
        if (decoded == null) {
            throw new InvalidNodeException(node, clazz);
        }
        return decoded;
    }

    /**
     * 将 Java 值编码为可写入 YAML 节点的基础对象.
     */
    @Nullable
    public Object serialize(@Nullable T value) {
        if (value == null) {
            return null;
        }
        Object encoded = encoder.serialize(value);
        if (encoded == null) {
            throw new InvalidNodeException(null, value.getClass(), clazz);
        }
        return encoded;
    }

    /**
     * 返回当前 serializer 期望解码出的 Java 类型.
     */
    public Class<?> targetType() {
        return clazz;
    }

    /**
     * 将当前 serializer 双向映射到另一个值类型.
     */
    @SuppressWarnings("unchecked")
    public <R> NodeSerializer<R> xmap(Function<? super T, ? extends R> to, Function<? super R, ? extends T> from) {
        return xmap((Class<R>) Object.class, to, from);
    }

    /**
     * 将当前 serializer 双向映射到指定目标类型.
     */
    public <R> NodeSerializer<R> xmap(Class<R> type, Function<? super T, ? extends R> to, Function<? super R, ? extends T> from) {
        List<Decoder<R>> mappedAlternatives = new ArrayList<>(alternatives.size());
        for (Decoder<T> alternative : alternatives) {
            mappedAlternatives.add(node -> mapDecoded(node, type, alternative, to));
        }
        return new NodeSerializer<>(
                type,
                node -> mapDecoded(node, type, decoder, to),
                value -> {
                    try {
                        return NodeSerializer.this.serialize(from.apply(value));
                    } catch (MissingNodeException | InvalidNodeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new InvalidNodeException(null, value.getClass(), type, e);
                    }
                },
                mappedAlternatives
        );
    }

    /**
     * 追加一个只读候选, 并将其结果转换为当前目标类型.
     */
    public <A> NodeSerializer<T> withAlternative(NodeSerializer<A> alternative, Function<? super A, ? extends T> converter) {
        List<Decoder<T>> mergedAlternatives = new ArrayList<>(alternatives.size() + 1 + alternative.alternatives.size());
        mergedAlternatives.addAll(alternatives);
        mergedAlternatives.add(node -> mapDecoded(node, clazz, alternative.decoder, converter));
        for (Decoder<A> candidate : alternative.alternatives) {
            mergedAlternatives.add(node -> mapDecoded(node, clazz, candidate, converter));
        }
        return new NodeSerializer<>(
                clazz,
                decoder,
                encoder,
                mergedAlternatives
        );
    }

    public <A extends T> NodeSerializer<T> withAlternative(NodeSerializer<A> alternative) {
        return withAlternative(alternative, Function.identity());
    }

    /**
     * 将当前 serializer 组合为 List serializer.
     */
    public NodeSerializer<List<T>> listOf() {
        return createInternal(
                List.class,
                node -> {
                    if (!(node instanceof SequenceNode seq)) {
                        throw new InvalidNodeException(node, List.class);
                    }
                    List<YamlNode<?>> nodeList = seq.value();
                    if (nodeList == null) {
                        throw new InvalidNodeException(node, List.class);
                    }

                    List<T> result = new ArrayList<>(nodeList.size());
                    for (YamlNode<?> elementNode : nodeList) {
                        T decoded = NodeSerializer.this.deserialize(elementNode);
                        result.add(decoded);
                    }
                    return result;
                },
                value -> {
                    List<Object> result = new ArrayList<>(value.size());
                    for (T element : value) {
                        result.add(NodeSerializer.this.serialize(element));
                    }
                    return result;
                }
        );
    }

    /**
     * 将当前 serializer 组合为 Set serializer, 解码时保留 YAML 序列的遍历顺序.
     */
    public NodeSerializer<Set<T>> setOf() {
        return createInternal(
                Set.class,
                node -> {
                    if (!(node instanceof SequenceNode seq)) {
                        throw new InvalidNodeException(node, Set.class);
                    }
                    List<YamlNode<?>> nodeList = seq.value();
                    if (nodeList == null) {
                        throw new InvalidNodeException(node, Set.class);
                    }

                    Set<T> result = new LinkedHashSet<>();
                    for (YamlNode<?> elementNode : nodeList) {
                        T decoded = NodeSerializer.this.deserialize(elementNode);
                        result.add(decoded);
                    }
                    return result;
                },
                value -> {
                    List<Object> result = new ArrayList<>(value.size());
                    for (T element : value) {
                        result.add(NodeSerializer.this.serialize(element));
                    }
                    return result;
                }
        );
    }

    /**
     * 将当前 serializer 组合为 Map<String, T> serializer.
     */
    public NodeSerializer<Map<String, T>> mapOf() {
        return createInternal(
                Map.class,
                node -> {
                    if (!(node instanceof SectionNode section)) {
                        throw new InvalidNodeException(node, Map.class);
                    }
                    Map<Object, YamlNode<?>> nodeMap = section.value();
                    if (nodeMap == null) {
                        throw new InvalidNodeException(node, Map.class);
                    }

                    Map<String, T> result = new LinkedHashMap<>(Math.max((int) (nodeMap.size() / 0.75f) + 1, 16));
                    for (Map.Entry<Object, YamlNode<?>> entry : nodeMap.entrySet()) {
                        T decoded = NodeSerializer.this.deserialize(entry.getValue());
                        result.put(String.valueOf(entry.getKey()), decoded);
                    }
                    return result;
                },
                value -> {
                    Map<String, Object> result = new LinkedHashMap<>(Math.max((int) (value.size() / 0.75f) + 1, 16));
                    for (Map.Entry<String, T> entry : value.entrySet()) {
                        result.put(entry.getKey(), NodeSerializer.this.serialize(entry.getValue()));
                    }
                    return result;
                }
        );
    }

    /**
     * 声明必填 mapping 字段.
     */
    public Field<T> required(String name) {
        return Field.required(name, this);
    }

    /**
     * 声明缺失或 YAML null 时返回 Optional.empty() 的 mapping 字段.
     */
    public Field<Optional<T>> optional(String name) {
        return Field.optional(name, this);
    }

    /**
     * 声明缺失或 YAML null 时使用默认值的 mapping 字段.
     */
    public Field<T> optional(String name, T defaultValue) {
        return Field.optional(name, this, defaultValue);
    }

    /**
     * 声明必填 sequence 元素.
     */
    public Element<T> required(int index) {
        return Element.required(index, this);
    }

    /**
     * 声明缺失或 YAML null 时返回 Optional.empty() 的 sequence 元素.
     */
    public Element<Optional<T>> optional(int index) {
        return Element.optional(index, this);
    }

    /**
     * 声明缺失或 YAML null 时使用默认值的 sequence 元素.
     */
    public Element<T> optional(int index, T defaultValue) {
        return Element.optional(index, this, defaultValue);
    }

    /**
     * 延迟获取实际 serializer, 用于递归结构.
     */
    static <T> NodeSerializer<T> lazy(Supplier<NodeSerializer<T>> supplier) {
        return createInternal(
                Object.class,
                new Decoder<>() {
                    private NodeSerializer<T> delegate;

                    @Override
                    public T deserialize(@Nullable YamlNode<?> node) {
                        return delegate().deserialize(node);
                    }

                    private NodeSerializer<T> delegate() {
                        if (delegate == null) {
                            delegate = supplier.get();
                        }
                        return delegate;
                    }
                },
                new Encoder<>() {
                    private NodeSerializer<T> delegate;

                    @Override
                    public Object serialize(@Nullable T value) {
                        return delegate().serialize(value);
                    }

                    private NodeSerializer<T> delegate() {
                        if (delegate == null) {
                            delegate = supplier.get();
                        }
                        return delegate;
                    }
                }
        );
    }

    // 解码后执行值映射, 并把映射阶段的异常统一转为节点解析异常.
    private static <A, R> R mapDecoded(YamlNode<?> node, Class<?> targetType, Decoder<A> decoder, Function<? super A, ? extends R> mapper) {
        A decoded = decoder.deserialize(node);
        if (decoded == null) {
            throw new InvalidNodeException(node, targetType);
        }
        try {
            R result = mapper.apply(decoded);
            if (result == null) {
                throw new InvalidNodeException(node, targetType);
            }
            return result;
        } catch (MissingNodeException | InvalidNodeException e) {
            throw e;
        } catch (Throwable e) {
            throw new InvalidNodeException(node, targetType, e);
        }
    }

    // 按 primary -> alternatives 的顺序尝试解码, 全部失败时保留原始失败列表.
    private static <T> T decodeWithAlternatives(YamlNode<?> node, Class<?> targetType, Decoder<T> primary, List<Decoder<T>> alternatives) {
        List<Failure> failures = new ArrayList<>(alternatives.size() + 1);
        try {
            return decodeCandidate(node, targetType, primary);
        } catch (MissingNodeException | InvalidNodeException e) {
            failures.add(new Failure(e));
        }
        for (Decoder<T> alternative : alternatives) {
            try {
                return decodeCandidate(node, targetType, alternative);
            } catch (MissingNodeException | InvalidNodeException e) {
                failures.add(new Failure(e));
            }
        }
        // 全部失败
        if (failures.size() == 1) {
            throw failures.get(0).exception();
        }
        throw new AlternativesNodeException(node, targetType, failures, "Alternatives candidates failed: " + failureSummary(failures));
    }

    // 解码候选不能用 null 表示失败, null 会按无效节点处理.
    private static <T> T decodeCandidate(YamlNode<?> node, Class<?> targetType, Decoder<T> decoder) {
        T decoded = decoder.deserialize(node);
        if (decoded == null) {
            throw new InvalidNodeException(node, targetType);
        }
        return decoded;
    }

    // 生成面向日志的候选失败摘要.
    private static String failureSummary(List<Failure> failures) {
        List<String> messages = new ArrayList<>(failures.size());
        for (int i = 0; i < failures.size(); i++) {
            messages.add("#" + i + ": " + failures.get(i).message());
        }
        return String.join("; ", messages);
    }

    @FunctionalInterface
    @ApiStatus.Internal
    public interface Decoder<T> {
        @Nullable
        T deserialize(@Nullable YamlNode<?> node);
    }

    @FunctionalInterface
    @ApiStatus.Internal
    public interface Encoder<T> {
        @Nullable
        Object serialize(@Nullable T value);
    }
}
