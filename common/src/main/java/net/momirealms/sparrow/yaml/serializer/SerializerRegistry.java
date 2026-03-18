package net.momirealms.sparrow.yaml.serializer;

import net.momirealms.sparrow.yaml.SparrowYaml;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 序列化器注册表, 管理 {@link SparrowYaml} 持有的自定义序列化器;
 * 每个 {@link SparrowYaml} 对象都会持有一个该对象;
 */
public class SerializerRegistry {
    private final SparrowYaml holder;
    private final Map<Class<?>, NodeSerializer<?>> serializers = new ConcurrentHashMap<>();

    public SerializerRegistry(SparrowYaml holder) {
        this.holder = holder;
    }

    /**
     * 注册新的序列化器;
     * @param clazz 目标类
     * @param serializer 序列化器实现
     */
    public <T> boolean register(Class<T> clazz, NodeSerializer<T> serializer) {
        if (!serializers.containsKey(clazz)) {
            serializers.put(clazz, serializer);
            return true;
        }
        return false;
    }

    /**
     * 强制注册新的序列化器;
     * @param clazz 目标类
     * @param serializer 序列化器实现
     */
    public void registerUnsafe(Class<?> clazz, NodeSerializer<?> serializer) {
        serializers.put(clazz, serializer);
    }

    /**
     * 注销已存在的序列化器
     * @param clazz 目标类
     * @return 被移除的序列化器
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> NodeSerializer<T> unregister(Class<T> clazz) {
        return (NodeSerializer<T>) serializers.remove(clazz);
    }

    /**
     * 根据Class获取已注册的序列化器;
     * @param clazz 目标类
     * @return 序列化器
     */
    @SuppressWarnings("unchecked")
    public <T> NodeSerializer<T> get(Class<T> clazz) {
        return (NodeSerializer<T>) serializers.get(clazz);
    }

    /**
     * 持有这个序列化器注册表的 SparrowYaml 对象;
     * @return SparrowYaml
     */
    public SparrowYaml holder() {
        return holder;
    }

}
