package net.momirealms.sparrow.yaml.node;

import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.snakeyaml.engine.v2.comments.CommentLine;
import org.snakeyaml.engine.v2.nodes.Node;

import java.util.*;

public interface YamlNode<T> {

    /**
     * 获取当前 Node 的 Key;
     * @return Key
     */
    @Nullable
    Object key();

    /**
     * 获取当前 Node 的 Key;
     * @return Key
     */
    @Nullable
    Object keyRouteElement();

    /**
     * 获取当前 Node 的 Value;
     * @return Value
     */
    T value();

    /**
     * 返回当前节点的父节点, 如果当前节点是根节点, 则返回 null;
     * @return 父节点;
     */
    @Nullable
    ParentNode<?> parentNode();

    /**
     * 设置当前节点的父节点;
     */
    void parentNode(ParentNode<?> parentNode);

    /**
     * 当前Node在整个Yaml节点的路径;
     * @return 当前节点的路径
     */
    @Nullable
    Route route();

    /**
     * 搜索 YamlDocument 维护的序列化器; </br>
     * 尝试将当前 YamlNode 读取转换成一个 JavaBean 对象; </br>
     * 如果序列化器没有找到, 则会抛出 {@link UnsupportedOperationException}. </br>
     * @param clazz 目标类
     * @return 目标 JavaBean
     */
    @Nullable
    default <R> R getAs(Class<R> clazz) {
        NodeSerializer<R> serializer = this.root().sparrowYaml().getSerializer(clazz);
        if (serializer == null) {
            throw new UnsupportedOperationException("未注册序列化器的Class类型: " + clazz.getSimpleName());
        }
        return serializer.deserialize(this);
    }

    /**
     * 搜索 YamlDocument 维护的序列化器; </br>
     * 尝试将当前 YamlNode 读取转换成一个 JavaBean 对象; </br>
     * 如果序列化器没有找到, 则会抛出 {@link UnsupportedOperationException}. </br>
     * @param clazz 目标类
     * @param defaultValue 当无法搜索到序列化器或结果为空时, 使用的默认值;
     * @return 目标 JavaBean
     */
    @NotNull
    default <R> R getAsOrDefault(Class<R> clazz, R defaultValue) {
        R value = this.getAs(clazz);
        return value != null ? value : defaultValue;
    }

    /**
     * 搜索 YamlDocument 维护的序列化器; </br>
     * 尝试将当前 YamlNode 读取转换成一个 Optional 对象; </br>
     * 如果序列化器没有找到, 则会抛出 {@link UnsupportedOperationException}. </br>
     * @param clazz 目标类
     * @return 搜索结果; 当无法搜索到序列化器或结果为空时, 返回 Optional.empty();
     */
    @NotNull
    default <R> Optional<R> getAsOptional(Class<R> clazz) {
        return Optional.ofNullable(this.getAs(clazz));
    }

    /**
     * 搜索 YamlDocument 维护的序列化器; </br>
     * 尝试将当前 YamlNode 读取转换成一个 List 对象, 其中元素使用 elementClass 对应的序列化器; </br>
     * 这个方法无法解析异构列表, 如果遇到非目标元素则会直接返回 null.
     * @param elementClass 元素的类型
     * @return List, 其中元素是 elementClass.
     */
    @Nullable
    default <R> List<R> getAsList(Class<R> elementClass) {
        if (this instanceof SequenceNode sequenceNode) {
            // 如果没值则返回 null, 空列表则返回 List.of().
            List<YamlNode<?>> nodeList = sequenceNode.value();
            if (nodeList == null) return null;
            if (nodeList.isEmpty()) return List.of();
            // 遍历元素, 如果元素中有无法被反序列化的, 则整个结果返回 null.
            List<R> result = new ArrayList<>(nodeList.size());
            for (int i = 0; i < nodeList.size(); i++) {
                YamlNode<?> yamlNode = nodeList.get(i);
                R deserialize = yamlNode.getAs(elementClass);
                if (deserialize == null) {
                    return null;
                }
                result.add(deserialize);
            }
            return result;
        }
        return null;
    }

    /**
     * 搜索 YamlDocument 维护的序列化器; </br>
     * 尝试将当前 YamlNode 读取转换成一个 List 对象, 其中元素使用 elementClass 对应的序列化器; </br>
     * 这个方法无法解析异构列表, 如果遇到非目标元素则会直接返回 defaultValue.
     * @param elementClass 元素的类型
     * @return List, 其中元素是 elementClass.
     */
    @NotNull
    default <R> List<R> getAsListOrDefault(Class<R> elementClass, List<R> defaultValue) {
        List<R> result = this.getAsList(elementClass);
        return result == null ? defaultValue : result;
    }

    /**
     * 搜索 YamlDocument 维护的序列化器; </br>
     * 尝试将当前 YamlNode 读取转换成一个 List 对象, 其中元素使用 elementClass 对应的序列化器; </br>
     * 这个方法无法解析异构列表, 如果遇到非目标元素将会返回 Optional.empty().
     * @param elementClass 元素的类型
     * @return List, 其中元素是 elementClass.
     */
    @NotNull
    default <R> Optional<List<R>> getAsListOptional(Class<R> elementClass) {
        return Optional.ofNullable(this.getAsList(elementClass));
    }

    /**
     * 搜索 YamlDocument 维护的序列化器; </br>
     * 尝试将当前 YamlNode 读取转换成一个 Map 对象, 其中Value使用 elementClass 对应的序列化器; </br>
     * 如果遇到非目标元素则会直接返回 null.
     * @param elementClass 元素的类型
     * @return List, 其中元素是 elementClass.
     */
    @Nullable
    default <R> Map<String, R> getAsMap(Class<R> elementClass) {
        if (this instanceof SectionNode sectionNode) {
            // 如果没值则返回 null, 空列表则返回 List.of().
            Map<Object, YamlNode<?>> nodeMap = sectionNode.value();
            if (nodeMap == null) return null;
            if (nodeMap.isEmpty()) return Map.of();
            // 遍历元素, 如果元素中有无法被反序列化的, 则整个结果返回 null.
            Map<String, R> result = new LinkedHashMap<>(nodeMap.size());
            for (Map.Entry<Object, YamlNode<?>> entry : nodeMap.entrySet()) {
                YamlNode<?> valueNode = entry.getValue();
                String key = String.valueOf(entry.getKey());
                R valueDeserialize = valueNode.getAs(elementClass);
                if (key == null || valueDeserialize == null) return null;
                result.put(key, valueDeserialize);
            }
            return result;
        }
        return null;
    }

    /**
     * 搜索 YamlDocument 维护的序列化器; </br>
     * 尝试将当前 YamlNode 读取转换成一个 Map 对象, 其中元素使用 elementClass 对应的序列化器; </br>
     * 如果遇到非目标元素则会直接返回 defaultValue.
     * @param elementClass 元素的类型
     * @return List, 其中元素是 elementClass.
     */
    @NotNull
    default <R> Map<String, R> getAsMapOrDefault(Class<R> elementClass, Map<String, R> defaultValue) {
        Map<String, R> result = this.getAsMap(elementClass);
        return result == null ? defaultValue : result;
    }

    /**
     * 搜索 YamlDocument 维护的序列化器; </br>
     * 尝试将当前 YamlNode 读取转换成一个 Map 对象, 其中元素使用 elementClass 对应的序列化器; </br>
     * 如果遇到非目标元素将会返回 Optional.empty().
     * @param elementClass 元素的类型
     * @return List, 其中元素是 elementClass.
     */
    @NotNull
    default <R> Optional<Map<String, R>> getAsMapOptional(Class<R> elementClass) {
        return Optional.ofNullable(this.getAsMap(elementClass));
    }

    /**
     * 设置当前节点的值;
     * @param value 节点值
     */
    void setValue(T value);

    /**
     * 获取当前节点的类型
     * @return 节点类型
     */
    YamlNodeType<?> yamlNodeType();

    /**
     * 检查当前节点是否为根节点;
     * @return Boolean
     */
    boolean isRoot();

    /**
     * 持有这个节点的 YamlDocument 对象;
     * @return YamlDocument 对象;
     */
    @NotNull
    YamlDocument root();

    /**
     * 检查当前 Node 是否是 Section 节点;
     * @return Boolean
     */
    boolean isSection();

    /**
     * 检查当前 Node 是否是 Sequence 节点;
     * @return Boolean
     */
    boolean isSequence();

    /**
     * 检查当前 Node 是否是 Scalar 节点;
     * @return Boolean
     */
    boolean isScalar();

    /**
     * 当前YamlNode维护的SnakeYaml的Node对象;
     * 因YamlNode可能为虚拟节点, 所以其可能为 Null;
     * @return SnakeYaml NodeTuple
     */
    @Nullable
    Node internalKeyNode();

    /**
     * 当前YamlNode维护的SnakeYaml的Node对象;
     * 因YamlNode可能为虚拟节点, 所以其可能为 Null;
     * @return SnakeYaml NodeTuple
     */
    @Nullable
    Node internalValueNode();

    /**
     * 对于当前节点的 Key 的行前注释.
     * @return 注释
     */
    List<CommentLine> beforeKeyComments();

    /**
     * 对于当前节点的 Key 的行中注释.
     * @return 注释
     */
    List<CommentLine> inlineKeyComments();

    /**
     * 对于当前节点的 Key 的行尾注释.
     * @return 注释
     */
    List<CommentLine> afterKeyComments();

    /**
     * 对于当前节点的 Value 的行前注释.
     * @return 注释
     */
    List<CommentLine> beforeValueComments();

    /**
     * 对于当前节点的 Value 的行中注释.
     * @return 注释
     */
    List<CommentLine> inlineValueComments();

    /**
     * 对于当前节点的 Value 的行尾注释.
     * @return 注释
     */
    List<CommentLine> afterValueComments();

}
