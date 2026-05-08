package net.momirealms.sparrow.yaml.serializer;

import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.node.SequenceNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public interface NodeSerializer<T> extends NodeDecoder<T>, NodeEncoder<T> {

    /**
     * 将当前 NodeSerializer&lt;T&gt; 转换为一个新的 NodeSerializer&lt;R&gt;.
     *
     * @param to   从 T 转换为 R 的函数 (在 decode 解码阶段使用)
     * @param from 从 R 转换为 T 的函数 (在 encode 编码阶段使用)
     * @param <R>  新的目标 Java 类型
     * @return 新的 NodeSerializer&lt;R&gt;
     */
    default <R> NodeSerializer<R> xmap(Function<T, R> to, Function<R, T> from) {
        return new NodeSerializer<R>() {
            @Override
            public R deserialize(@Nullable YamlNode<?> node) {
                T decoded = NodeSerializer.this.deserialize(node);
                if (decoded == null) return null;
                try {
                    return to.apply(decoded);
                } catch (Exception e) {
                    return null; // 映射失败时不抛异常, 返回 null 代表解析失败
                }
            }

            @Override
            public Object serialize(@Nullable R value) {
                if (value == null) return null;
                try {
                    return NodeSerializer.this.serialize(from.apply(value));
                } catch (Exception e) {
                    return null; // 编码失败时不抛异常, 返回 null
                }
            }
        };
    }

    /**
     * 将当前处理单个元素的 NodeSerializer&lt;T&gt; 转化为处理列表的 NodeSerializer&lt;List&lt;T&gt;&gt;.
     *
     * @return 列表的编解码器
     */
    @Override
    default NodeSerializer<List<T>> listOf() {
        return new NodeSerializer<List<T>>() {
            @Override
            public List<T> deserialize(@Nullable YamlNode<?> node) {
                if (!(node instanceof SequenceNode seq)) return null;
                List<YamlNode<?>> nodeList = seq.value();
                if (nodeList == null) return null;

                List<T> result = new ArrayList<>(nodeList.size());
                for (YamlNode<?> elementNode : nodeList) {
                    T decoded = NodeSerializer.this.deserialize(elementNode);
                    if (decoded == null) return null; // 列表中有元素解析失败, 视为整个列表解析失败
                    result.add(decoded);
                }
                return result;
            }

            @Override
            public Object serialize(@Nullable List<T> value) {
                if (value == null) return null;
                List<Object> result = new ArrayList<>(value.size());
                for (T element : value) {
                    result.add(NodeSerializer.this.serialize(element));
                }
                return result;
            }
        };
    }

    /**
     * 将当前处理单个元素的 NodeSerializer&lt;T&gt; 转化为处理Map的 NodeSerializer&lt;Map&lt;String, T&gt;&gt;.
     *
     * @return Map的编解码器
     */
    @Override
    default NodeSerializer<Map<String, T>> mapOf() {
        return new NodeSerializer<>() {
            @Override
            public Map<String, T> deserialize(@Nullable YamlNode<?> node) {
                if (!(node instanceof SectionNode section)) return null;
                Map<Object, YamlNode<?>> nodeMap = section.value();
                if (nodeMap == null) return null;

                Map<String, T> result = new LinkedHashMap<>(Math.max((int) (nodeMap.size() / 0.75f) + 1, 16));
                for (Map.Entry<Object, YamlNode<?>> entry : nodeMap.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    T decoded = NodeSerializer.this.deserialize(entry.getValue());
                    if (decoded == null) return null; // Map中有元素解析失败, 视为整个Map解析失败
                    result.put(key, decoded);
                }
                return result;
            }

            @Override
            public Object serialize(@Nullable Map<String, T> value) {
                if (value == null) return null;
                Map<String, Object> result = new LinkedHashMap<>(Math.max((int) (value.size() / 0.75f) + 1, 16));
                for (Map.Entry<String, T> entry : value.entrySet()) {
                    result.put(entry.getKey(), NodeSerializer.this.serialize(entry.getValue()));
                }
                return result;
            }
        };
    }

    /**
     * 延迟解析, 用于解决递归数据结构的解析.
     * 当数据结构内部包含自身时(如 Node 包含 Node), 使用 lazy 可以在真正调用 encode/decode 时再去初始化并获取真实的序列化器.
     *
     * @param supplier 提供实际序列化器的工厂函数
     * @param <T>      目标 Java 类型
     * @return 延迟计算的编解码器代理
     */
    static <T> NodeSerializer<T> lazy(Supplier<NodeSerializer<T>> supplier) {
        return new NodeSerializer<T>() {
            private NodeSerializer<T> delegate;

            private NodeSerializer<T> delegate() {
                if (delegate == null) {
                    delegate = supplier.get();
                }
                return delegate;
            }

            @Override
            public T deserialize(@Nullable YamlNode<?> node) {
                return delegate().deserialize(node);
            }

            @Override
            public Object serialize(@Nullable T value) {
                return delegate().serialize(value);
            }
        };
    }
}
