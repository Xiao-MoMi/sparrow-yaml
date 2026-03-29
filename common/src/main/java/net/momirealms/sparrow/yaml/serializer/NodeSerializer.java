package net.momirealms.sparrow.yaml.serializer;

import net.momirealms.sparrow.yaml.node.YamlNode;
import org.jetbrains.annotations.Nullable;

/**
 * 节点序列化器, 传入具体的Node节点, 将其通过该类序列化成目标 JavaBean
 * @param <T>
 */
public interface NodeSerializer<T> {

    /**
     * 将一个 {@link YamlNode} 反序列化成目标 JavaBean;
     * @param node YamlNode 节点;
     * @return JavaBean;
     */
    @Nullable
    T deserialize(YamlNode<?> node);

    /**
     * 将一个 JavaBean 转换成 SnakeYAML 能够识别的原始对象;
     * 可以转成: (LinkedHashMap/ArrayList/Scalar)
     * @param value JavaBean
     * @return 原始底层对象;
     */
    @Nullable
    Object serialize(T value);

}
