package net.momirealms.sparrow.yaml.serializer.auto;

import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.SerializerRegistry;
import net.momirealms.sparrow.yaml.serializer.auto.resolver.TypeSerializerResolver;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

/**
 * 用于单词序列化器自动生成会话的上下文对象.
 * 它负责维护全局的注册表实例以及当前正在解析的类型集合, 借此处理复杂的递归数据结构并支持基于 lazy 的延迟初始化.
 */
public class AutoSerializerContext {
    private final SerializerRegistry registry;
    private final Set<Type> resolvingTypes = new HashSet<>();
    private final TypeSerializerResolver resolver;

    /**
     * @param registry 全局序列化器注册表, 允许在自动生成过程中复用已有的序列化器
     * @param resolver 负责将 Java Type 转换为对应 NodeSerializer 的解析器实现
     */
    public AutoSerializerContext(SerializerRegistry registry, TypeSerializerResolver resolver) {
        this.registry = registry;
        this.resolver = resolver;
    }

    /**
     * 获取全局的序列化器注册表.
     *
     * @return 绑定的 SerializerRegistry 实例
     */
    public SerializerRegistry getRegistry() {
        return registry;
    }

    /**
     * 检查给定的类型当前是否正处于解析状态中.
     * 此机制用于侦测递归引用(如在类型 A 中包含类型 A 的字段), 如果正在解析则可通过返回 lazy 代理打断死循环.
     *
     * @param type 待检查的 Java 类型
     * @return 如果该类型正在被解析则返回 true, 否则返回 false
     */
    public boolean isResolving(Type type) {
        return resolvingTypes.contains(type);
    }

    /**
     * 将给定的类型标记为正在解析.
     * 该操作通常在对某类型字段和构造函数进行深度解析前被调用.
     *
     * @param type 即将被解析的 Java 类型
     */
    public void pushResolving(Type type) {
        resolvingTypes.add(type);
    }

    /**
     * 将给定的类型从正在解析的集合中移除.
     * 该操作通常在某类型的深度解析彻底完成之后被调用.
     *
     * @param type 已经完成解析的 Java 类型
     */
    public void popResolving(Type type) {
        resolvingTypes.remove(type);
    }

    /**
     * 在当前的上下文环境中, 对给定的类型解析出对应的 NodeSerializer 实例.
     * 该方法会将调用委派给绑定的 TypeSerializerResolver, 并在必要时检查递归.
     *
     * @param type 需要解析的 Java 类型
     * @param <T>  目标数据类型
     * @return 对应于该类型的 NodeSerializer 实例
     * @throws net.momirealms.sparrow.yaml.exception.AutoSerializerException 若解析失败将抛出此异常
     */
    @SuppressWarnings("unchecked")
    public <T> NodeSerializer<T> resolve(Type type) {
        return (NodeSerializer<T>) resolver.resolve(type, this);
    }
}
