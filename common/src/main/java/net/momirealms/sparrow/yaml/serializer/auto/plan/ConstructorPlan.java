package net.momirealms.sparrow.yaml.serializer.auto.plan;

import net.momirealms.sparrow.yaml.exception.AutoSerializerException;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.serializer.auto.member.SerializableMember;
import net.momirealms.sparrow.yaml.serializer.auto.parameter.ConstructorParameter;
import net.momirealms.sparrow.yaml.util.TypeUtils;

import java.lang.reflect.Constructor;
import java.util.List;

/**
 * 基于有参构造函数的对象实例化策略计划.
 */
public class ConstructorPlan<T> implements InstantiationPlan<T> {
    private final Constructor<T> constructor; // 构造器
    private final List<ConstructorParameter> parameters; // 参数信息
    private final List<SerializableMember> members; // 注入的序列化成员

    /**
     * 接收解析完毕的反射构造器及相关参数映射. 在初始化时, 将自动放开构造器的访问权限限制.
     *
     * @param constructor 反射获取的构造器对象
     * @param parameters  实例化时该构造器所需的参数列表映射信息
     * @param members     对象所有的序列化成员(字段、Record 组件等)
     */
    public ConstructorPlan(Constructor<T> constructor, List<ConstructorParameter> parameters, List<SerializableMember> members) {
        this.constructor = constructor;
        this.parameters = parameters;
        this.members = members;
        this.constructor.setAccessible(true);
        validateMembers();
    }

    @Override
    public List<SerializableMember> members() {
        return members;
    }

    @Override
    public T instantiate(SectionNode sectionNode) {
        try {
            return constructor.newInstance(decodeArguments(sectionNode));
        } catch (ReflectiveOperationException e) {
            throw new AutoSerializerException("Cannot instantiate " + constructor.getDeclaringClass().getName(), e);
        }
    }

    @Override
    public boolean shouldInject(SerializableMember member) {
        return InstantiationPlan.super.shouldInject(member) && !isConstructorParameter(member.name());
    }

    private Object[] decodeArguments(SectionNode sectionNode) {
        Object[] args = new Object[parameters.size()];
        for (int i = 0; i < parameters.size(); i++) {
            ConstructorParameter param = parameters.get(i);
            if (param.name() == null) {
                args[i] = TypeUtils.defaultValue(TypeUtils.rawType(param.type()));
                continue;
            }
            YamlNode<?> child = sectionNode.getNodeOrNull(param.name());
            Object decoded = (child != null && param.serializer() != null) ? param.serializer().deserialize(child) : null;
            args[i] = decoded != null ? decoded : TypeUtils.defaultValue(TypeUtils.rawType(param.type()));
        }
        return args;
    }

    private boolean isConstructorParameter(String name) {
        if (name == null) {
            return false;
        }
        for (ConstructorParameter parameter : parameters) {
            if (name.equals(parameter.name())) {
                return true;
            }
        }
        return false;
    }

    private void validateMembers() {
        for (SerializableMember member : members) {
            if (!member.isIgnored() && !member.isInjectable() && !isConstructorParameter(member.name())) {
                throw new AutoSerializerException("Cannot inject field '" + member.name()
                        + "' for " + constructor.getDeclaringClass().getName()
                        + "; final fields must be bound through a constructor");
            }
        }
    }
}
