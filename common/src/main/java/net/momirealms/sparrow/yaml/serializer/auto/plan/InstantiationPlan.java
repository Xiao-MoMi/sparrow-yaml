package net.momirealms.sparrow.yaml.serializer.auto.plan;

import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.serializer.auto.member.SerializableMember;

import java.util.List;

public interface InstantiationPlan<T> {

    /**
     * 获取参与自动序列化和后续字段注入的所有成员列表.
     * 该列表包含可注入的字段成员、记录类成员(对于 Record)等, 是序列化和反序列化后期处理.
     *
     * @return 需要被编码或进行字段注入的 SerializableMember 成员列表
     */
    List<SerializableMember> members();

    /**
     * 根据 YAML Section 节点创建目标对象.
     * 具体策略可以选择调用构造器、无参构造, 或绕过构造器分配实例.
     *
     * @param sectionNode 当前对象对应的 YAML Section 节点
     * @return 实例化成功后的目标对象
     */
    T instantiate(SectionNode sectionNode);

    /**
     * 判断实例化后是否还需要向指定成员注入字段值.
     */
    default boolean shouldInject(SerializableMember member) {
        return member.isInjectable() && !member.isIgnored();
    }
}
