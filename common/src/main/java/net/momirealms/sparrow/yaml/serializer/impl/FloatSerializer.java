package net.momirealms.sparrow.yaml.serializer.impl;

import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;

public class FloatSerializer implements NodeSerializer<Float> {

    @Override
    public Float deserialize(YamlNode<?> node) {
        if (node instanceof ScalarNode scalarNode) {
            Object value = scalarNode.value();
            if (value instanceof Float i) {
                return i;
            }
            else if (value instanceof Number n) {
                return n.floatValue();
            }
            else if (value instanceof String s) {
                try {
                    return Float.parseFloat(s.replace("_", ""));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            else if (value instanceof Boolean b) {
                return b ? 1.0f : 0.0f;
            }
        }
        return null;
    }

    @Override
    public Object serialize(Float value) {
        return value;
    }

}
