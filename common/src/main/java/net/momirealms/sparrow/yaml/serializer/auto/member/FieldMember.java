package net.momirealms.sparrow.yaml.serializer.auto.member;

import net.momirealms.sparrow.yaml.exception.AutoSerializerException;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.auto.accessor.FieldAccessor;
import net.momirealms.sparrow.yaml.serializer.auto.accessor.FieldAccessors;

import java.lang.reflect.Field;
import java.lang.reflect.Type;

/**
 * 代表普通 JavaBean 或类中一个反射字段的序列化成员封装.
 * 负责从实例中读取字段值进行序列化, 并在支持注入的情况下向实例中写入反序列化后的值.
 */
public class FieldMember implements SerializableMember {
    private final String name;
    private final Type type;
    private final NodeSerializer<?> serializer;
    private final Field field;
    private final boolean injectable;
    private final FieldAccessor accessor;

    /**
     * @param name       YAML 中的键名
     * @param type       该字段的泛型类型
     * @param serializer 处理该字段类型的节点序列化器
     * @param field      对应的反射 Field 实例
     * @param injectable 是否支持字段注入
     * @param isFinal    是否为 final 字段
     */
    public FieldMember(String name, Type type, NodeSerializer<?> serializer, Field field, boolean injectable, boolean isFinal) {
        this.name = name;
        this.type = type;
        this.serializer = serializer;
        this.field = field;
        this.accessor = FieldAccessors.of(field);
        this.injectable = injectable && accessor.canSet();
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
        return false; // 被忽略的字段不会被包装为 FieldMember
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object encode(Object target) {
        try {
            Object fieldValue = accessor.get(target);
            return ((NodeSerializer<Object>) serializer).serialize(fieldValue);
        } catch (RuntimeException e) {
            throw new AutoSerializerException("Cannot read field " + field.getDeclaringClass().getName() + "." + field.getName(), e);
        }
    }

    @Override
    public void set(Object target, Object value) {
        if (!injectable) {
            throw new AutoSerializerException("Cannot inject into non-injectable field " + field.getDeclaringClass().getName() + "." + field.getName());
        }

        try {
            accessor.set(target, value);
        } catch (RuntimeException e) {
            throw new AutoSerializerException("Cannot set field " + field.getDeclaringClass().getName() + "." + field.getName(), e);
        }
    }

    @Override
    public boolean isInjectable() {
        return injectable;
    }
}
