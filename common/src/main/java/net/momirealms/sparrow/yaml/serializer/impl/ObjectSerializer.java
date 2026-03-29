package net.momirealms.sparrow.yaml.serializer.impl;

import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;

public class ObjectSerializer implements NodeSerializer<Object> {

    @Override
    public Object deserialize(YamlNode<?> node) {
        return node.value();
    }

    @Override
    public Object serialize(Object value) {
        return value.toString();
    }

}
