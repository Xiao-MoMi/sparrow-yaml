package net.momirealms.sparrow.yaml.serializer;

import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.serializer.auto.AutoSerializerBinding;
import net.momirealms.sparrow.yaml.serializer.auto.factory.AutoSerializerFactory;
import net.momirealms.sparrow.yaml.serializer.auto.factory.ReflectionAutoSerializerFactory;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 序列化器注册表, 管理 {@link SparrowYaml} 持有的自定义序列化器.
 * 每个 {@link SparrowYaml} 对象都会持有一个该对象.
 */
public class SerializerRegistry {
    private final SparrowYaml holder;
    private final Map<Type, NodeSerializer<?>> serializers = new ConcurrentHashMap<>();
    private final AutoSerializerFactory autoSerializerFactory = new ReflectionAutoSerializerFactory();

    public SerializerRegistry(SparrowYaml holder) {
        this.holder = holder;
        this.registerBaseSerializers();
    }

    /**
     * 注册新的序列化器 (针对 Class 类型).
     * 如果该类型尚未注册过序列化器, 则进行注册并返回 true.
     *
     * @param clazz      目标类的 Class 实例
     * @param serializer 序列化器实现
     * @param <T>        目标类型
     * @return 如果注册成功返回 true, 如果该类型已存在序列化器则返回 false
     */
    public <T> boolean register(Class<T> clazz, NodeSerializer<T> serializer) {
        if (!serializers.containsKey(clazz)) {
            serializers.put(clazz, serializer);
            return true;
        }
        return false;
    }

    /**
     * 注册新的序列化器 (针对携带泛型信息的 TypeRef 类型).
     * 如果该泛型类型尚未注册过序列化器, 则进行注册并返回 true.
     *
     * @param typeRef    目标泛型类型的引用对象
     * @param serializer 序列化器实现
     * @param <T>        目标类型
     * @return 如果注册成功返回 true, 如果该类型已存在序列化器则返回 false
     */
    public <T> boolean register(TypeRef<T> typeRef, NodeSerializer<T> serializer) {
        Type type = typeRef.type();
        if (!serializers.containsKey(type)) {
            serializers.put(type, serializer);
            return true;
        }
        return false;
    }

    /**
     * 强制注册或覆盖序列化器, 不进行类型检查.
     *
     * @param type       目标的 Java Type
     * @param serializer 序列化器实现
     */
    public void registerUnsafe(Type type, NodeSerializer<?> serializer) {
        serializers.put(type, serializer);
    }

    /**
     * 注销已存在的序列化器 (针对 Class 类型).
     *
     * @param clazz 目标类的 Class 实例
     * @param <T>   目标类型
     * @return 被移除的序列化器, 如果不存在则返回 null
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> NodeSerializer<T> unregister(Class<T> clazz) {
        return (NodeSerializer<T>) serializers.remove(clazz);
    }

    /**
     * 注销已存在的序列化器 (针对携带泛型信息的 TypeRef 类型).
     *
     * @param typeRef 目标泛型类型的引用对象
     * @param <T>     目标类型
     * @return 被移除的序列化器, 如果不存在则返回 null
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> NodeSerializer<T> unregister(TypeRef<T> typeRef) {
        return (NodeSerializer<T>) serializers.remove(typeRef.type());
    }

    /**
     * 根据 Class 获取已注册的序列化器.
     *
     * @param clazz 目标类的 Class 实例
     * @param <T>   目标类型
     * @return 对应的序列化器, 如果未注册则返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> NodeSerializer<T> get(Class<T> clazz) {
        return (NodeSerializer<T>) serializers.get(clazz);
    }

    /**
     * 根据 TypeRef 获取已注册的序列化器.
     *
     * @param typeRef 目标泛型类型的引用对象
     * @param <T>     目标类型
     * @return 对应的序列化器, 如果未注册则返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> NodeSerializer<T> get(TypeRef<T> typeRef) {
        return (NodeSerializer<T>) serializers.get(typeRef.type());
    }

    /**
     * 根据 Java Type 获取已注册的序列化器.
     *
     * @param type 目标的 Java Type
     * @param <T>  目标类型
     * @return 对应的序列化器, 如果未注册则返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> NodeSerializer<T> get(Type type) {
        return (NodeSerializer<T>) serializers.get(type);
    }

    /**
     * 根据传入的 Class, 结合自定义绑定配置尝试自动生成并注册序列化器.
     *
     * @param clazz           目标类的 Class 实例
     * @param bindingConsumer 自定义绑定配置的回调函数, 允许在自动生成前干预配置
     * @param <T>             目标类型
     * @return 自动生成或现存的序列化器
     */
    public <T> NodeSerializer<T> registerAuto(Class<T> clazz, Consumer<AutoSerializerBinding> bindingConsumer) {
        // 如果已经生成过, 则直接返回.
        NodeSerializer<T> existing = this.get(clazz);
        if (existing != null) {
            return existing;
        }
        // 尝试生成
        AutoSerializerBinding binding = this.createBinding(bindingConsumer);
        return autoSerializerFactory.create(clazz, this, binding);
    }

    public <T> NodeSerializer<T> register(Class<T> clazz) {
        return this.registerAuto(clazz, null);
    }

    /**
     * 根据传入的 TypeRef, 结合自定义绑定配置尝试自动生成并注册序列化器.
     *
     * @param typeRef         目标泛型类型的引用对象
     * @param bindingConsumer 自定义绑定配置的回调函数, 允许在自动生成前干预配置
     * @param <T>             目标类型
     * @return 自动生成或现存的序列化器
     */
    public <T> NodeSerializer<T> registerAuto(TypeRef<T> typeRef, Consumer<AutoSerializerBinding> bindingConsumer) {
        NodeSerializer<T> existing = this.get(typeRef);
        if (existing != null) {
            return existing;
        }
        AutoSerializerBinding binding = this.createBinding(bindingConsumer);
        return autoSerializerFactory.create(typeRef.type(), this, binding);
    }

    public <T> NodeSerializer<T> register(TypeRef<T> typeRef) {
        return this.registerAuto(typeRef, null);
    }

    /**
     * 创建匹配构造器的绑定配置实例.
     *
     * @param bindingConsumer 绑定配置消费函数, 为 null 则不执行配置
     * @return 新创建且应用配置后的 AutoSerializerBinding 实例
     */
    private AutoSerializerBinding createBinding(@Nullable Consumer<AutoSerializerBinding> bindingConsumer) {
        AutoSerializerBinding binding = new AutoSerializerBinding();
        if (bindingConsumer != null) {
            bindingConsumer.accept(binding);
        }
        return binding;
    }

    public SparrowYaml holder() {
        return holder;
    }

    /**
     * 注册基础类型的序列化器
     */
    public void registerBaseSerializers() {
        this.register(Object.class, NodeSerializers.OBJECT);
        this.register(int.class, NodeSerializers.INT);
        this.register(Integer.class, NodeSerializers.INT);
        this.register(float.class, NodeSerializers.FLOAT);
        this.register(Float.class, NodeSerializers.FLOAT);
        this.register(double.class, NodeSerializers.DOUBLE);
        this.register(Double.class, NodeSerializers.DOUBLE);
        this.register(long.class, NodeSerializers.LONG);
        this.register(Long.class, NodeSerializers.LONG);
        this.register(boolean.class, NodeSerializers.BOOLEAN);
        this.register(Boolean.class, NodeSerializers.BOOLEAN);
        this.register(String.class, NodeSerializers.STRING);

        this.register(java.util.UUID.class, NodeSerializers.UUID);
        this.register(java.util.Locale.class, NodeSerializers.LOCALE);
        this.register(java.util.Date.class, NodeSerializers.DATE);
        this.register(java.util.Calendar.class, NodeSerializers.CALENDAR);
        this.register(java.time.LocalDate.class, NodeSerializers.LOCAL_DATE);
        this.register(java.time.LocalTime.class, NodeSerializers.LOCAL_TIME);
        this.register(java.time.LocalDateTime.class, NodeSerializers.LOCAL_DATE_TIME);
        this.register(java.time.ZonedDateTime.class, NodeSerializers.ZONED_DATE_TIME);
        this.register(java.time.Instant.class, NodeSerializers.INSTANT);
        this.register(java.time.Duration.class, NodeSerializers.DURATION);
        this.register(java.time.Period.class, NodeSerializers.PERIOD);
    }
}
