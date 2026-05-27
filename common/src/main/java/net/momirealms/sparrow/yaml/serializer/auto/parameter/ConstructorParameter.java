package net.momirealms.sparrow.yaml.serializer.auto.parameter;

import net.momirealms.sparrow.yaml.serializer.NodeSerializer;

import java.lang.reflect.Type;

/**
 * 表示在通过有参构造函数实例化对象时, 所需要解析和传递的一个具体参数配置.
 * 该接口定义了该参数对应的 YAML 键名、参数的规范化类型以及用于解码该节点的序列化器.
 */
public interface ConstructorParameter {

    /**
     * 获取此参数在 YAML 中对应的键名.
     * 如果该参数对应被忽略(例如注解 @YamlIgnore), 那么它应该返回 null, 
     * 并且反序列化引擎在处理该参数时通常会提供类型的默认值(例如 null 或基础类型默认值).
     *
     * @return YAML 中的对应键名, 如果被忽略则返回 null
     */
    String name();

    /**
     * 获取此构造器参数经过解析和规范化后的泛型 Java 类型.
     * 
     * @return 规范化后的 Java Type 实例
     */
    Type type();

    /**
     * 获取用于解码此构造器参数对应 YAML 节点的节点序列化器.
     *
     * @return 解析得到的 NodeSerializer 实例
     */
    NodeSerializer<?> serializer();
}
