package net.momirealms.sparrow.yaml.node;

import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.SerializerRegistry;
import net.momirealms.sparrow.yaml.serializer.TypeRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.snakeyaml.engine.v2.comments.CommentLine;
import org.snakeyaml.engine.v2.nodes.CollectionNode;
import org.snakeyaml.engine.v2.nodes.Node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface YamlNode<T> {

    /**
     * 获取当前 Node 的 Key;
     * @return Key
     */
    @Nullable
    Object key();
    
    /**
     * 设置当前 Node 的 Key;
     * 该方法通常由框架内部调用, 不建议外部使用;
     */
    @ApiStatus.Internal
    void key(Object key);

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
     * 使用指定的 NodeSerializer 将当前 YamlNode 转换成目标对象。
     * @param serializer 指定的序列化器
     * @return 目标对象
     */
    @Nullable
    default <R> R get(NodeSerializer<R> serializer) {
        return serializer.deserialize(this);
    }

    /**
     * 根据指定的 Class 从 {@link SerializerRegistry} 获取对应的反序列化器，将当前 YamlNode 转换成目标对象。
     * @param clazz 目标对象的 Class
     * @return 目标对象
     */
    @Nullable
    default <R> R get(Class<R> clazz) {
        NodeSerializer<R> serializer = root().sparrowYaml().serializers().get(clazz);
        if (serializer != null) {
            return serializer.deserialize(this);
        }
        throw new UnsupportedOperationException("not found registered serializer for " + clazz);
    }

    @Nullable
    default <R> R get(TypeRef<R> typeRef) {
        NodeSerializer<R> serializer = root().sparrowYaml().serializers().get(typeRef);
        if (serializer != null) {
            return serializer.deserialize(this);
        }
        throw new UnsupportedOperationException("not found registered serializer for " + typeRef);
    }

    /**
     * 根据指定的 Class 从 {@link SerializerRegistry} 获取对应的序列化器，将目标对象序列化并设置到当前 YamlNode。
     * @param clazz 目标对象的 Class
     * @param value 目标对象
     */
    @SuppressWarnings("unchecked")
    default <R> void set(Class<R> clazz, R value) {
        NodeSerializer<R> serializer = root().sparrowYaml().serializers().get(clazz);
        if (serializer != null) {
            this.setValue((T) serializer.serialize(value));
        } else {
            this.setValue((T) value);
        }
    }

    @SuppressWarnings("unchecked")
    default <R> void set(TypeRef<R> typeRef, R value) {
        NodeSerializer<R> serializer = root().sparrowYaml().serializers().get(typeRef);
        if (serializer != null) {
            this.setValue((T) serializer.serialize(value));
        } else {
            this.setValue((T) value);
        }
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
     * 设置当前节点的 Key 的行前注释.
     * @param comments 注释列表
     */
    void setBeforeKeyComments(List<CommentLine> comments);

    /**
     * 设置当前节点的 Key 的行中注释.
     * @param comments 注释列表
     */
    void setInlineKeyComments(List<CommentLine> comments);

    /**
     * 设置当前节点的 Key 的行尾注释.
     * @param comments 注释列表
     */
    void setAfterKeyComments(List<CommentLine> comments);

    /**
     * 设置当前节点的 Value 的行前注释.
     * @param comments 注释列表
     */
    void setBeforeValueComments(List<CommentLine> comments);

    /**
     * 设置当前节点的 Value 的行中注释.
     * @param comments 注释列表
     */
    void setInlineValueComments(List<CommentLine> comments);

    /**
     * 设置当前节点的 Value 的行尾注释.
     * @param comments 注释列表
     */
    void setAfterValueComments(List<CommentLine> comments);

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

    /**
     * 提取当前节点可用于重新表示的 Java 值.
     */
    default Object representValue() {
        if (this instanceof ScalarNode scalar) {
            return scalar.value() == null ? "null" : scalar.value();
        } else if (this instanceof SectionNode section) {
            Map<Object, Object> map = new LinkedHashMap<>();
            for (Map.Entry<Object, YamlNode<?>> entry : section.value().entrySet()) {
                map.put(entry.getKey(), entry.getValue().representValue());
            }
            return map;
        } else if (this instanceof SequenceNode sequence) {
            List<Object> list = new ArrayList<>();
            for (YamlNode<?> element : sequence.value()) {
                list.add(element.representValue());
            }
            return list;
        }
        return "null";
    }

    /**
     * 复制当前节点的表层注释到目标节点.
     */
    default void copyCommentsTo(@Nullable YamlNode<?> target) {
        if (this instanceof AbstractYamlNode<?> sourceNode && target instanceof AbstractYamlNode<?> targetNode) {
            targetNode.setBeforeKeyComments(copyCommentLines(sourceNode.beforeKeyComments()));
            targetNode.setInlineKeyComments(copyCommentLines(sourceNode.inlineKeyComments()));
            targetNode.setAfterKeyComments(copyCommentLines(sourceNode.afterKeyComments()));
            targetNode.setBeforeValueComments(copyCommentLines(sourceNode.beforeValueComments()));
            targetNode.setInlineValueComments(copyCommentLines(sourceNode.inlineValueComments()));
            targetNode.setAfterValueComments(copyCommentLines(sourceNode.afterValueComments()));
        }
    }

    /**
     * 仅复制当前节点中非空的注释到目标节点.
     */
    default void copyNonEmptyCommentsTo(@Nullable YamlNode<?> target) {
        if (this instanceof AbstractYamlNode<?> sourceNode && target instanceof AbstractYamlNode<?> targetNode) {
            if (sourceNode.beforeKeyComments() != null && !sourceNode.beforeKeyComments().isEmpty()) {
                targetNode.setBeforeKeyComments(copyCommentLines(sourceNode.beforeKeyComments()));
            }
            if (sourceNode.inlineKeyComments() != null && !sourceNode.inlineKeyComments().isEmpty()) {
                targetNode.setInlineKeyComments(copyCommentLines(sourceNode.inlineKeyComments()));
            }
            if (sourceNode.afterKeyComments() != null && !sourceNode.afterKeyComments().isEmpty()) {
                targetNode.setAfterKeyComments(copyCommentLines(sourceNode.afterKeyComments()));
            }
            if (sourceNode.beforeValueComments() != null && !sourceNode.beforeValueComments().isEmpty()) {
                targetNode.setBeforeValueComments(copyCommentLines(sourceNode.beforeValueComments()));
            }
            if (sourceNode.inlineValueComments() != null && !sourceNode.inlineValueComments().isEmpty()) {
                targetNode.setInlineValueComments(copyCommentLines(sourceNode.inlineValueComments()));
            }
            if (sourceNode.afterValueComments() != null && !sourceNode.afterValueComments().isEmpty()) {
                targetNode.setAfterValueComments(copyCommentLines(sourceNode.afterValueComments()));
            }
        }
    }

    /**
     * 深度复制当前节点及其子节点的全部注释到目标节点.
     */
    default void deepCopyCommentsTo(@Nullable YamlNode<?> target) {
        if (target == null) return;
        this.copyCommentsTo(target);
        if (this instanceof SectionNode sourceSection && target instanceof SectionNode targetSection) {
            for (Map.Entry<Object, YamlNode<?>> entry : sourceSection.value().entrySet()) {
                YamlNode<?> targetChild = targetSection.value().get(entry.getKey());
                if (targetChild != null) {
                    entry.getValue().deepCopyCommentsTo(targetChild);
                }
            }
        } else if (this instanceof SequenceNode sourceSeq && target instanceof SequenceNode targetSeq) {
            for (int i = 0; i < Math.min(sourceSeq.size(), targetSeq.size()); i++) {
                YamlNode<?> sourceChild = sourceSeq.value().get(i);
                YamlNode<?> targetChild = targetSeq.value().get(i);
                sourceChild.deepCopyCommentsTo(targetChild);
            }
        }
    }

    /**
     * 将当前节点的集合 flow style 同步到目标节点.
     */
    default void preserveFlowStyleTo(@Nullable YamlNode<?> target) {
        if (target != null && target.internalValueNode() != null && this.internalValueNode() != null) {
            if (target.internalValueNode() instanceof CollectionNode<?> targetCollection
                    && this.internalValueNode() instanceof CollectionNode<?> sourceCollection) {
                targetCollection.setFlowStyle(sourceCollection.getFlowStyle());
            }
        }
    }

    private static List<CommentLine> copyCommentLines(@Nullable List<CommentLine> comments) {
        return comments == null ? null : new ArrayList<>(comments);
    }

}
