package net.momirealms.sparrow.yaml.serializer.auto.plan;

import net.momirealms.sparrow.yaml.exception.AutoSerializerException;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.serializer.auto.member.SerializableMember;

import java.lang.reflect.Constructor;
import java.util.List;

/**
 * 基于无参构造函数和字段依赖注入的对象实例化策略计划.
 * 该类通常用于处理没有提供任何 @YamlConstructor 注解, 但声明了无参构造函数的普通 JavaBean.
 * 在这种模式下, 反序列化首先通过无参构造器实例化对象, 然后通过反射逐个将解析出的成员值注入到实例的字段中.
 *
 * @param <T> 目标 Java 类型
 */
public class FieldInjectionPlan<T> implements InstantiationPlan<T> {

    private final Constructor<T> constructor;
    private final List<SerializableMember> members;

    /**
     * @param constructor 反射获取的无参构造器对象
     * @param members     在实例化之后, 需要通过反射注入值的所有成员列表
     */
    public FieldInjectionPlan(Constructor<T> constructor, List<SerializableMember> members) {
        this.constructor = constructor;
        this.members = members;
        this.constructor.setAccessible(true);
        validateInjectableMembers();
    }

    @Override
    public List<SerializableMember> members() {
        return members;
    }

    @Override
    public T instantiate(SectionNode sectionNode) {
        try {
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AutoSerializerException("Cannot instantiate " + constructor.getDeclaringClass().getName(), e);
        }
    }

    private void validateInjectableMembers() {
        for (SerializableMember member : members) {
            if (!member.isIgnored() && !member.isInjectable()) {
                throw new AutoSerializerException("Cannot inject field '" + member.name()
                        + "' for " + constructor.getDeclaringClass().getName()
                        + "; final fields must be bound through a constructor");
            }
        }
    }
}
