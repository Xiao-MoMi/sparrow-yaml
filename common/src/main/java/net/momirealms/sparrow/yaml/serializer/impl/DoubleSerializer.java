package net.momirealms.sparrow.yaml.serializer.impl;

import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;

public class DoubleSerializer implements NodeSerializer<Double> {

    @Override
    public Double deserialize(YamlNode<?> node) {
        if (node instanceof ScalarNode scalarNode) {
            Object value = scalarNode.value();
            if (value instanceof Double i) {
                return i;
            }
            else if (value instanceof Number n) {
                return n.doubleValue();
            }
            else if (value instanceof String s) {
                try {
                    return Double.parseDouble(s.replace("_", ""));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            else if (value instanceof Boolean b) {
                return b ? 1.0 : 0.0;
            }
        }
        return null;
    }

    @Override
    public Object serialize(Double value) {
        return value;
    }

}
