package net.momirealms.sparrow.yaml.node;

import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.snakeyaml.engine.v2.comments.CommentLine;
import org.snakeyaml.engine.v2.nodes.Node;

import java.util.List;
import java.util.Optional;

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
     * @param defaultValue 当无法搜索到序列化器或结果为空时, 使用的默认值;
     * @return 目标 JavaBean
     */
    @NotNull
    default <R> R getAs(Class<R> clazz, R defaultValue) {
        NodeSerializer<R> serializer = this.root().sparrowYaml().getSerializer(clazz);
        if (serializer == null) {
            throw new UnsupportedOperationException("找不到");
        }
        R value = serializer.deserialize(this);
        if (value == null) {
            return defaultValue;
        }
        return value;
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
        NodeSerializer<R> serializer = this.root().sparrowYaml().getSerializer(clazz);
        if (serializer == null) {
            throw new UnsupportedOperationException("找不到");
        }
        R value = serializer.deserialize(this);
        return Optional.ofNullable(value);
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
