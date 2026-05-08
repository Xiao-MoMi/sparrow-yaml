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

/**
 * 节点解码器, 负责将一个 YamlNode 解码为目标 JavaBean.
 *
 * @param <T> 目标 Java 类型
 */
public interface NodeDecoder<T> {

    /**
     * 将一个 YamlNode 解码为目标 JavaBean.
     *
     * @param node YamlNode 节点实例
     * @return 解码后的目标对象. 如果节点不合法或解析失败, 应该返回 null 而不是抛出异常.
     */
    @Nullable
    T deserialize(@Nullable YamlNode<?> node);

    /**
     * 将当前 NodeDecoder&lt;T&gt; 转换为一个新的 NodeDecoder&lt;R&gt;.
     * 该方法通过提供一个从类型 T 转换到类型 R 的函数, 使得现有的解码器可以产出新的类型 R.
     *
     * @param to  从类型 T 转换为类型 R 的函数
     * @param <R> 新的目标 Java 类型
     * @return 转换后的新节点解码器 NodeDecoder&lt;R&gt;
     * @throws Exception 如果在应用转换函数时发生异常, 将被捕获并返回 null
     */
    default <R> NodeDecoder<R> map(Function<T, R> to) {
        return node -> {
            T decoded = NodeDecoder.this.deserialize(node);
            if (decoded == null) return null;
            try {
                return to.apply(decoded);
            } catch (Exception e) {
                return null;
            }
        };
    }

    /**
     * 将当前处理单个元素的 NodeDecoder&lt;T&gt; 转化为处理列表的 NodeDecoder&lt;List&lt;T&gt;&gt;.
     * 该方法会遍历 SequenceNode 中的子节点列表, 并对每个元素调用当前的 decode 方法进行解码.
     * 如果其中任何一个元素解析失败返回 null, 则整个列表的解析都将视为失败并返回 null.
     *
     * @return 处理列表的节点解码器
     */
    default NodeDecoder<List<T>> listOf() {
        return node -> {
            if (!(node instanceof SequenceNode seq)) return null;
            List<YamlNode<?>> nodeList = seq.value();
            if (nodeList == null) return null;

            List<T> result = new ArrayList<>(nodeList.size());
            for (YamlNode<?> elementNode : nodeList) {
                T decoded = NodeDecoder.this.deserialize(elementNode);
                if (decoded == null) return null; // 列表中有元素解析失败, 视为整个列表解析失败
                result.add(decoded);
            }
            return result;
        };
    }

    /**
     * 将当前处理单个元素的 NodeDecoder&lt;T&gt; 转化为处理Map的 NodeDecoder&lt;Map&lt;String, T&gt;&gt;.
     * 该方法会遍历 SectionNode 中的键值对映射, 并对每个值节点调用当前的 decode 方法进行解码.
     * 如果其中任何一个元素解析失败返回 null, 则整个Map的解析都将视为失败并返回 null.
     */
    default NodeDecoder<Map<String, T>> mapOf() {
        return node -> {
            if (!(node instanceof SectionNode section)) return null;
            Map<Object, YamlNode<?>> nodeMap = section.value();
            if (nodeMap == null) return null;

            Map<String, T> result = new LinkedHashMap<>(nodeMap.size());
            for (Map.Entry<Object, YamlNode<?>> entry : nodeMap.entrySet()) {
                String key = String.valueOf(entry.getKey());
                T decoded = NodeDecoder.this.deserialize(entry.getValue());
                if (decoded == null) return null; // Map中有元素解析失败, 视为整个Map解析失败
                result.put(key, decoded);
            }
            return result;
        };
    }
}