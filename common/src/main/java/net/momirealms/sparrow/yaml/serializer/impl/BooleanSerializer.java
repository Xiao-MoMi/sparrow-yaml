package net.momirealms.sparrow.yaml.serializer.impl;

import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;

public class BooleanSerializer implements NodeSerializer<Boolean> {

    @Override
    public Boolean deserialize(YamlNode<?> node) {
        if (node instanceof ScalarNode scalarNode) {
            Object value = scalarNode.value();
            if (value instanceof Boolean b) {
                return b;
            }
            else if (value instanceof Number n) {
                if (n.byteValue() == 0) return false;
                if (n.byteValue() > 0) return true;
                return null;
            }
            else if (value instanceof String s) {
                if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes") || s.equalsIgnoreCase("on")) return true;
                if (s.equalsIgnoreCase("false") || s.equalsIgnoreCase("no") || s.equalsIgnoreCase("off")) return false;
                return null;
            }
        }
        return null;
    }

    @Override
    public Object serialize(Boolean value) {
        return value;
    }

}
