package net.momirealms.sparrow.yaml.serializer.impl;

import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;

public class LongSerializer implements NodeSerializer<Long> {

    @Override
    public Long deserialize(YamlNode<?> node) {
        if (node instanceof ScalarNode scalarNode) {
            Object value = scalarNode.value();
            if (value instanceof Long i) {
                return i;
            }
            else if (value instanceof Number n) {
                return n.longValue();
            }
            else if (value instanceof String s) {
                try {
                    return Long.parseLong(s.replace("_", ""));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            else if (value instanceof Boolean b) {
                return b ? 1L : 0L;
            }
        }
        return null;
    }

    @Override
    public Object serialize(Long value) {
        return value;
    }

}
