package net.momirealms.sparrow.yaml.serializer;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 节点编码器, 负责将一个 JavaBean 编码为 YamlNode 能够识别的底层对象.
 *
 * @param <T> 目标 Java 类型
 */
public interface NodeEncoder<T> {

    /**
     * 将一个 JavaBean 编码为能够被 YamlNode 识别的原始底层对象 (如 Map, List, String, Number 等).
     * 当外部调用 SectionNode.setSubNode(key, object) 时, 自动将这个原始对象转换为对应的节点.
     *
     * @param value 需要被编码的 JavaBean 实例
     * @return 编码后的原始底层对象, 如果传入的值为 null 或编码失败则可能返回 null
     */
    @Nullable
    Object serialize(@Nullable T value);

    /**
     * 将当前 NodeEncoder&lt;T&gt; 转换为一个新的 NodeEncoder&lt;R&gt;.
     * 该方法通过提供一个从类型 R 转换到类型 T 的函数, 使得现有的编码器可以处理新的类型 R.
     *
     * @param from 从类型 R 转换为类型 T 的函数
     * @param <R>  新的目标 Java 类型
     * @return 转换后的新节点编码器 NodeEncoder&lt;R&gt;
     * @throws Exception 如果在应用转换函数时发生异常, 将被捕获并返回 null
     */
    default <R> NodeEncoder<R> contraMap(Function<R, T> from) {
        return value -> {
            if (value == null) return null;
            try {
                return NodeEncoder.this.serialize(from.apply(value));
            } catch (Exception e) {
                return null;
            }
        };
    }

    /**
     * 组合操作: 将当前处理单个元素的 NodeEncoder&lt;T&gt; 转化为 NodeEncoder&lt;List&lt;T&gt;&gt;.
     */
    default NodeEncoder<List<T>> listOf() {
        return value -> {
            if (value == null) return null;
            List<Object> result = new ArrayList<>(value.size());
            for (T element : value) {
                result.add(NodeEncoder.this.serialize(element));
            }
            return result;
        };
    }

    /**
     * 将当前处理单个元素的 NodeEncoder&lt;T&gt; 转化为 NodeEncoder&lt;Map&lt;String, T&gt;&gt;.
     */
    default NodeEncoder<Map<String, T>> mapOf() {
        return value -> {
            if (value == null) return null;
            Map<String, Object> result = new LinkedHashMap<>(value.size());
            for (Map.Entry<String, T> entry : value.entrySet()) {
                result.put(entry.getKey(), NodeEncoder.this.serialize(entry.getValue()));
            }
            return result;
        };
    }
}