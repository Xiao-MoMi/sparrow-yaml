package net.momirealms.sparrow.yaml.serializer.auto.plan;

import net.momirealms.sparrow.yaml.exception.AutoSerializerException;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.serializer.auto.member.SerializableMember;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 基于 Unsafe.allocateInstance 的字段注入实例化策略.
 * 该策略用于没有明确构造器绑定, 且多个构造器无法自动选择的普通 class.
 */
public class UnsafeInstantiationPlan<T> implements InstantiationPlan<T> {
    private static final Unsafe UNSAFE;

    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafe.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access sun.misc.Unsafe", e);
        }
    }

    private final Class<T> rawType;
    private final List<SerializableMember> members;

    public UnsafeInstantiationPlan(Class<T> rawType, List<SerializableMember> members) {
        this.rawType = rawType;
        this.members = members;
        validateInjectableMembers();
    }

    @Override
    public List<SerializableMember> members() {
        return members;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T instantiate(SectionNode sectionNode) {
        try {
            return (T) UNSAFE.allocateInstance(rawType);
        } catch (InstantiationException e) {
            throw new AutoSerializerException("Cannot allocate " + rawType.getName(), e);
        }
    }

    private void validateInjectableMembers() {
        for (SerializableMember member : members) {
            if (!member.isIgnored() && !member.isInjectable()) {
                throw new AutoSerializerException("Cannot inject field '" + member.name()
                        + "' for " + rawType.getName()
                        + "; final fields must be bound through a constructor");
            }
        }
    }
}
