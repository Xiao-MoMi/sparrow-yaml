package net.momirealms.sparrow.yaml.serializer.auto.resolver;

import net.momirealms.sparrow.yaml.exception.AutoSerializerException;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.NodeSerializers;
import net.momirealms.sparrow.yaml.serializer.auto.AutoSerializerContext;
import net.momirealms.sparrow.yaml.serializer.auto.factory.AutoSerializerFactory;
import net.momirealms.sparrow.yaml.util.TypeUtils;

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReflectionTypeSerializerResolver implements TypeSerializerResolver {
    private final AutoSerializerFactory factory;

    public ReflectionTypeSerializerResolver(AutoSerializerFactory factory) {
        this.factory = factory;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public NodeSerializer<?> resolve(Type type, AutoSerializerContext context) {
        Type normalized = TypeUtils.normalize(type, Map.of());
        NodeSerializer<?> registered = context.getRegistry().get(normalized);
        if (registered != null) {
            return registered;
        }
        if (context.isResolving(normalized)) {
            return NodeSerializers.lazy(() -> context.getRegistry().get(normalized));
        }

        Class<?> rawType = TypeUtils.rawType(normalized);
        if (rawType == Object.class) {
            throw new AutoSerializerException("Cannot automatically serialize raw java.lang.Object");
        }
        if (rawType.isEnum()) {
            return NodeSerializers.enumCodec((Class<? extends Enum>) rawType);
        }
        if (rawType == List.class) {
            Type elementType = TypeUtils.parameter(normalized, 0);
            return ((NodeSerializer) resolve(elementType, context)).listOf();
        }
        if (rawType == Set.class) {
            Type elementType = TypeUtils.parameter(normalized, 0);
            return ((NodeSerializer) resolve(elementType, context)).setOf();
        }
        if (rawType == Map.class) {
            Type keyType = TypeUtils.parameter(normalized, 0);
            if (keyType != String.class) {
                throw new AutoSerializerException("Only Map<String, T> is supported: " + normalized.getTypeName());
            }
            Type valueType = TypeUtils.parameter(normalized, 1);
            return ((NodeSerializer) resolve(valueType, context)).mapOf();
        }

        if (rawType.isArray()) {
            throw new AutoSerializerException("No serializer registered for array type " + rawType.getName() + "; register a composed serializer via SerializerRegistry before using it as a field type");
        }
        if (rawType.isInterface()) {
            throw new AutoSerializerException("No serializer registered for interface " + rawType.getName() + "; register a composed serializer via SerializerRegistry before using it as a field type");
        }
        if (Modifier.isAbstract(rawType.getModifiers())) {
            throw new AutoSerializerException("No serializer registered for abstract class " + rawType.getName() + "; register a composed serializer via SerializerRegistry before using it as a field type");
        }

        return factory.createInternal(normalized, context, null);
    }
}
