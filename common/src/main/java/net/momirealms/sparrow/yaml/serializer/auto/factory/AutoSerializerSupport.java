package net.momirealms.sparrow.yaml.serializer.auto.factory;

import net.momirealms.sparrow.yaml.exception.UnknownEnumValueException;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.NodeSerializers;
import net.momirealms.sparrow.yaml.serializer.auto.accessor.FieldAccessor;
import net.momirealms.sparrow.yaml.serializer.auto.member.SerializableMember;
import org.jetbrains.annotations.Nullable;

final class AutoSerializerSupport {
    private AutoSerializerSupport() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static boolean isDefaultEnumSerializer(NodeSerializer<?> serializer, Class<?> fieldType) {
        return fieldType.isEnum() && serializer == NodeSerializers.enumCodec((Class) fieldType);
    }

    @Nullable
    static Object deserializeOrKeepDefault(NodeSerializer<?> serializer, YamlNode<?> node, SerializableMember member, Object instance) {
        try {
            return serializer.deserialize(node);
        } catch (UnknownEnumValueException ignored) {
            if (member.encode(instance) == null) {
                throw ignored;
            }
            return null;
        }
    }

    @Nullable
    static Object deserializeOrKeepDefault(NodeSerializer<?> serializer, YamlNode<?> node, FieldAccessor accessor, Object instance) {
        try {
            return serializer.deserialize(node);
        } catch (UnknownEnumValueException ignored) {
            if (accessor.get(instance) == null) {
                throw ignored;
            }
            return null;
        }
    }
}
