package net.momirealms.sparrow.yaml.serializer.auto.member;

import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.exception.AutoSerializerException;

import java.lang.reflect.Type;

/**
 * 描述在自动序列化过程中需要被处理的一个类的字段.
 * 定义 YAML 键名、Java 泛型类型、序列化器以及如何读取/写入该成员值的行为.
 */
public interface SerializableMember {

    /**
     * 获取此成员在 YAML 中对应的键名.
     *
     * @return YAML 键名. 如果该成员被忽略(例如标注了 @YamlIgnore), 则可能返回 null
     */
    String name();

    /**
     * 获取此成员的规范化泛型 Java 类型.
     * 该类型已经过处理, 其中的类型变量 T 已被解析为具体类型.
     *
     * @return 规范化后的 Java Type 实例
     */
    Type type();

    /**
     * 获取已解析的用于处理此成员类型的节点序列化器.
     *
     * @return 解析后的 NodeSerializer 实例
     */
    NodeSerializer<?> serializer();

    /**
     * 判断在序列化/反序列化过程中是否应该忽略此成员.
     *
     * @return 如果该成员应被忽略则返回 true, 否则返回 false
     */
    boolean isIgnored();

    /**
     * 从给定的目标对象实例中读取此成员的值, 并使用绑定的序列化器将其编码为 YAML 兼容的底层对象.
     *
     * @param target 目标对象实例
     * @return 编码后的 YAML 底层对象(如 Map, List, String 等)
     * @throws AutoSerializerException 如果在反射读取值时发生异常
     */
    Object encode(Object target);

    /**
     * 将解码后的值注入到目标对象的字段.
     *
     * @param target 目标对象实例
     * @param value  解码后的 Java 对象值
     * @throws AutoSerializerException 如果该成员在注入时发生异常
     */
    void set(Object target, Object value);

    /**
     * 判断此成员是否支持被注入设置值.
     */
    boolean isInjectable();
}
