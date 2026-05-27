package net.momirealms.sparrow.yaml.serializer.auto.factory;

import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.SerializerRegistry;
import net.momirealms.sparrow.yaml.serializer.auto.AutoSerializerBinding;
import net.momirealms.sparrow.yaml.serializer.auto.AutoSerializerContext;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;

/**
 * 自动序列化器工厂接口.
 * 负责基于传入的类型信息和绑定配置, 自动生成对应的 NodeSerializer 实例.
 */
public interface AutoSerializerFactory {

    <T> NodeSerializer<T> create(Type type, SerializerRegistry registry, AutoSerializerBinding binding);

    <T> NodeSerializer<T> createInternal(Type type, AutoSerializerContext context, @Nullable AutoSerializerBinding binding);
}
