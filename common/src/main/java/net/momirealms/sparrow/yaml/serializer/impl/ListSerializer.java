package net.momirealms.sparrow.yaml.serializer.impl;

import net.momirealms.sparrow.yaml.node.SequenceNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;

import java.util.ArrayList;
import java.util.List;

public class ListSerializer<T> implements NodeSerializer<List<T>> {

    private final NodeSerializer<T> elementSerializer;

    public ListSerializer(NodeSerializer<T> elementSerializer) {
        this.elementSerializer = elementSerializer;
    }

    @Override
    public List<T> deserialize(YamlNode<?> node) {
        if (node instanceof SequenceNode sequenceNode) {
            // 如果没值则返回 null, 空列表则返回 List.of().
            List<YamlNode<?>> nodeList = sequenceNode.value();
            if (nodeList == null) return null;
            if (nodeList.isEmpty()) return List.of();
            // 遍历元素, 如果元素中有无法被反序列化的, 则整个结果返回 null.
            List<T> result = new ArrayList<>(nodeList.size());
            for (int i = 0; i < nodeList.size(); i++) {
                YamlNode<?> yamlNode = nodeList.get(i);
                T deserialize = elementSerializer.deserialize(yamlNode);
                if (deserialize == null) {
                    return null;
                }
                result.add(deserialize);
            }
            return result;
        }
        return null;
    }

    @Override
    public Object serialize(List<T> value) {
        if (value == null) return null;

        List<Object> serializedList = new ArrayList<>(value.size());
        for (T elem : value) {
            Object serializedElem = elementSerializer.serialize(elem);
            if (serializedElem == null) return List.of();
            serializedList.add(serializedElem);
        }

        return serializedList;
    }
}