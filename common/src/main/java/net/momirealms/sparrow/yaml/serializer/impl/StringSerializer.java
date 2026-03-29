package net.momirealms.sparrow.yaml.serializer.impl;

import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;

public class StringSerializer implements NodeSerializer<String> {

    @Override
    public String deserialize(YamlNode<?> node) {
        if (node instanceof ScalarNode scalarNode) {
            Object value = scalarNode.value();
            return String.valueOf(value);
        }
        return null;
    }

    @Override
    public Object serialize(String value) {
        return value;
    }

}
