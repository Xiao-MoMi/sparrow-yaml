package net.momirealms.sparrow.yaml.serializer.impl;

import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;

public class IntSerializer implements NodeSerializer<Integer> {

    @Override
    public Integer deserialize(YamlNode<?> node) {
        if (node instanceof ScalarNode scalarNode) {
            Object value = scalarNode.value();
            if (value instanceof Integer i) {
                return i;
            }
            else if (value instanceof Number n) {
                return n.intValue();
            }
            else if (value instanceof String s) {
                try {
                    return Integer.parseInt(s.replace("_", ""));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            else if (value instanceof Boolean b) {
                return b ? 1 : 0;
            }
        }
        return null;
    }

    @Override
    public Object serialize(Integer value) {
        return value;
    }

}
