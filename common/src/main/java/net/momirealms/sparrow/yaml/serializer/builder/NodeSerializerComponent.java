package net.momirealms.sparrow.yaml.serializer.builder;

import net.momirealms.sparrow.yaml.node.YamlNode;

/**
 * builder 内部使用的字段或序列元素组件.
 */
interface NodeSerializerComponent<T, A> {

    NodeSerializerDecodeResult decode(YamlNode<?> node);

    void encode(T source, Object target);
}
