package net.momirealms.sparrow.yaml.serializer.auto.resolver;

import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.auto.AutoSerializerContext;
import net.momirealms.sparrow.yaml.exception.AutoSerializerException;

import java.lang.reflect.Type;

public interface TypeSerializerResolver {

    /**
     * 负责将字段、参数等反射类型解析并生成一个适合处理该类型的节点序列化器.
     *
     * @param type    需要解析的 Type, 包含泛型等元数据.
     * @param context 当前的自动序列化上下文.
     * @return 对应于该类型的 NodeSerializer 实例
     * @throws AutoSerializerException 如果遇到不支持的泛型或无法解析的类型时抛出
     */
    NodeSerializer<?> resolve(Type type, AutoSerializerContext context);
}
