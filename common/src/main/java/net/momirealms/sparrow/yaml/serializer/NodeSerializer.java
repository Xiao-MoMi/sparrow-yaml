package net.momirealms.sparrow.yaml.serializer;

import net.momirealms.sparrow.yaml.exception.InvalidNodeException;
import net.momirealms.sparrow.yaml.exception.MissingNodeException;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.node.SequenceNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 不透明的 YAML 节点序列化器.
 *
 * <p>调用方只能通过 {@link NodeSerializers} 和本类提供的组合方法创建实例, 不能自行实现该类型.</p>
 *
 * @param <T> 目标 Java 类型
 */
public final class NodeSerializer<T> {

    private final Decoder<T> decoder;
    private final Encoder<T> encoder;
    private final Class<?> targetType; // 当前 serializer 期望解码出的 Java 类型

    private NodeSerializer(Class<?> targetType, Decoder<T> decoder, Encoder<T> encoder) {
        this.targetType = Objects.requireNonNull(targetType, "targetType");
        this.decoder = Objects.requireNonNull(decoder, "decoder");
        this.encoder = Objects.requireNonNull(encoder, "encoder");
    }

    @ApiStatus.Internal
    public static <T> NodeSerializer<T> createInternal(Class<?> targetType, Decoder<T> decoder, Encoder<T> encoder) {
        return new NodeSerializer<>(targetType, decoder, encoder);
    }

    /**
     * 将 YAML 节点解码为目标 Java 值.
     *
     * <p>缺失或空值通常抛出解析异常.</p>
     */
    @NotNull
    public T deserialize(@Nullable YamlNode<?> node) {
        T decoded = decoder.deserialize(node);
        if (decoded == null) {
            throw new InvalidNodeException(node, targetType);
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
            throw new InvalidNodeException(null, value.getClass(), targetType);
        }
        return encoded;
    }

    /**
     * 返回当前 serializer 期望解码出的 Java 类型.
     */
    public Class<?> targetType() {
        return targetType;
    }

    /**
     * 将当前 serializer 双向映射到另一个值类型.
     */
    public <R> NodeSerializer<R> xmap(Function<? super T, ? extends R> to, Function<? super R, ? extends T> from) {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(from, "from");
        return createInternal(
                Object.class,
                node -> {
                    T decoded = NodeSerializer.this.deserialize(node);
                    try {
                        R result = to.apply(decoded);
                        if (result == null) {
                            throw new InvalidNodeException(node, Object.class);
                        }
                        return result;
                    } catch (MissingNodeException | InvalidNodeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new InvalidNodeException(node, Object.class, e);
                    }
                },
                value -> {
                    try {
                        return NodeSerializer.this.serialize(from.apply(value));
                    } catch (MissingNodeException | InvalidNodeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new InvalidNodeException(null, value.getClass(), Object.class, e);
                    }
                }
        );
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
     * 将当前 serializer 声明为 mapping builder 中的字段.
     */
    public NodeSerializers.Field<T> fieldOf(String name) {
        return new NodeSerializers.Field<>(name, this, false, null, false, null);
    }

    /**
     * 将当前 serializer 声明为 sequence builder 中的元素.
     */
    public NodeSerializers.Element<T> element(int index) {
        return new NodeSerializers.Element<>(index, this, false, null, false, null);
    }

    /**
     * 延迟获取实际 serializer, 用于递归结构.
     */
    static <T> NodeSerializer<T> lazy(Supplier<NodeSerializer<T>> supplier) {
        Objects.requireNonNull(supplier, "supplier");
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
