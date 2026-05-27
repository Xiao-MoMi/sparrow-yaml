package net.momirealms.sparrow.yaml.serializer.auto.member;

import net.momirealms.sparrow.yaml.exception.AutoSerializerException;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;

import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;

/**
 * 代表 Java Record 记录类中一个组件的序列化成员封装.
 * 负责从 Record 实例中读取属性并序列化.
 * Record 的组件本质上是 final 字段, 由于 Record 通常通过全参构造器实例化(不需要字段注入).
 */
public class RecordMember implements SerializableMember {
    private final String name;
    private final Type type;
    private final NodeSerializer<?> serializer;
    private final RecordComponent component;
    private final boolean ignored;

    /**
     * 构造函数.
     *
     * @param name       YAML 中的键名
     * @param type       泛型类型
     * @param serializer 解析到的节点序列化器
     * @param component  对应的反射 RecordComponent 实例
     * @param ignored    是否被标记为忽略
     */
    public RecordMember(String name, Type type, NodeSerializer<?> serializer, RecordComponent component, boolean ignored) {
        this.name = name;
        this.type = type;
        this.serializer = serializer;
        this.component = component;
        this.ignored = ignored;
        if (this.component != null) {
            this.component.getAccessor().setAccessible(true);
        }
    }

    /**
     * 创建一个 RecordMember 实例.
     *
     * @param name       YAML 键名
     * @param type       泛型类型
     * @param serializer 解析到的序列化器
     * @param component  对应的反射 RecordComponent 实例
     * @return RecordMember 实例
     */
    public static RecordMember active(String name, Type type, NodeSerializer<?> serializer, RecordComponent component) {
        return new RecordMember(name, type, serializer, component, false);
    }

    /**
     * 创建一个被忽略的 RecordMember 实例.
     *
     * @param type 组件的类型
     * @return 新建的被忽略的 RecordMember 实例
     */
    public static RecordMember ignored(Class<?> type) {
        return new RecordMember(null, type, null, null, true);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Type type() {
        return type;
    }

    @Override
    public NodeSerializer<?> serializer() {
        return serializer;
    }

    @Override
    public boolean isIgnored() {
        return ignored;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object encode(Object target) {
        try {
            Object fieldValue = component.getAccessor().invoke(target);
            return ((NodeSerializer<Object>) serializer).serialize(fieldValue);
        } catch (ReflectiveOperationException e) {
            throw new AutoSerializerException("Cannot read record component " + name, e);
        }
    }

    @Override
    public void set(Object target, Object value) {
        throw new AutoSerializerException("Cannot inject into record component: " + name);
    }

    @Override
    public boolean isInjectable() {
        return false;
    }
}
