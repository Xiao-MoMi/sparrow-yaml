package net.momirealms.sparrow.yaml.serializer.impl;

import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;

import java.util.LinkedHashMap;
import java.util.Map;

public class MapSerializer<V> implements NodeSerializer<Map<String, V>> {

    private final NodeSerializer<V> valueSerializer;

    public MapSerializer(NodeSerializer<V> valueSerializer) {
        this.valueSerializer = valueSerializer;
    }

    @Override
    public Map<String, V> deserialize(YamlNode<?> node) {
        if (node instanceof SectionNode sectionNode) {
            // 如果没值则返回 null, 空列表则返回 Map.of().
            Map<Object, YamlNode<?>> nodeMap = sectionNode.value();
            if (nodeMap == null) return null;
            if (nodeMap.isEmpty()) return Map.of();
            // 遍历元素, 如果元素中有无法被反序列化的, 则整个结果返回 null.
            Map<String, V> result = new LinkedHashMap<>(nodeMap.size());
            for (Map.Entry<Object, YamlNode<?>> entry : nodeMap.entrySet()) {
                YamlNode<?> valueNode = entry.getValue();
                String key = String.valueOf(entry.getKey());
                V valueDeserialize = valueSerializer.deserialize(valueNode);
                if (key == null || valueDeserialize == null) return null;
                result.put(key, valueDeserialize);
            }
            return result;
        }
        return null;
    }

    @Override
    public Object serialize(Map<String, V> value) {
        if (value == null) return null;
        // 如果元素中有无法被序列化的, 则整个结果为空.
        Map<Object, Object> serializedMap = new LinkedHashMap<>(value.size());
        for (Map.Entry<String, V> entry : value.entrySet()) {
            String key = entry.getKey();
            Object serializedValue = valueSerializer.serialize(entry.getValue());
            if (key == null || serializedValue == null) return Map.of();
            serializedMap.put(key, serializedValue);
        }
        return serializedMap;
    }
}