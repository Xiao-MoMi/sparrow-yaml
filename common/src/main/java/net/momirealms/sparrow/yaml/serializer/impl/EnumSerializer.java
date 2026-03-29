package net.momirealms.sparrow.yaml.serializer.impl;

import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;

import java.util.Locale;

public class EnumSerializer<T extends Enum<T>> implements NodeSerializer<T> {
    private final Class<T> enumType;

    public EnumSerializer(Class<T> enumType) {
        this.enumType = enumType;
    }

    @Override
    public T deserialize(YamlNode<?> node) {
        if (node instanceof ScalarNode scalarNode) {
            Object value = scalarNode.value();
            String enumString = String.valueOf(value);
            try {
                return Enum.valueOf(enumType, enumString.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public Object serialize(T value) {
        if (value == null) return null;
        return value.name();
    }

}
