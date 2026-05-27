package net.momirealms.sparrow.yaml.serializer.auto.factory;

import net.momirealms.sparrow.yaml.serializer.NodeSerializer;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Map;

/**
 * ASM 解析后的类元数据, 供后续字节码生成阶段使用.
 */
final class ClassMeta {
    String internalName; // JVM 内部类名, 例如 java/lang/String.
    String className; // Java 完整类名, 例如 java.lang.String.
    String superName; // 父类 JVM 内部类名.
    boolean isRecord; // 当前类是否为 record.
    boolean isInterface; // 当前类是否为接口.
    boolean isAbstract; // 当前类是否为抽象类.
    List<FieldEntry> fields; // 可参与序列化分析的字段元数据.
    List<ConstructorEntry> constructors; // 构造器元数据.

    /**
     * 字段元数据, 保存 ASM 描述符、泛型类型和 YAML 注解信息.
     */
    static final class FieldEntry {
        String name; // 字段名.
        String descriptor; // JVM 字段描述符.
        String signature; // 泛型签名, 可能为 null.
        Type genericType; // 反射解析出的泛型类型.
        Class<?> ownerClass; // 声明该字段的类.
        String ownerInternalName; // 字段声明类的 JVM 内部名.
        Field reflectiveField; // 对应的反射字段, 合成构造器参数绑定为 null.
        Map<TypeVariable<?>, Type> typeVariables; // 声明类所在继承层级的泛型映射.
        int access; // ASM access flags.
        boolean hasYamlProperty; // 字段是否声明了 @YamlProperty.
        boolean hasYamlIgnore; // 字段是否声明了 @YamlIgnore.
        String yamlPropertyValue; // @YamlProperty 中声明的 YAML 键名.
    }

    /**
     * 构造器元数据, 保存构造器描述符和注解标记.
     */
    static final class ConstructorEntry {
        String descriptor; // JVM 方法描述符.
        boolean hasYamlConstructor; // 构造器是否声明了 @YamlConstructor.
        List<String> paramDescriptors; // 构造器参数描述符列表.
    }

    /**
     * 字段与实际 NodeSerializer 的绑定关系, 供生成类通过数组索引快速访问.
     */
    static final class FieldBinding {
        final FieldEntry field; // 字段元数据; 构造器参数占位绑定也会用合成 FieldEntry 表示.
        final NodeSerializer<?> serializer; // 字段或构造器参数对应的序列化器.
        final int index; // 在生成类 sers 数组中的索引.
        final int accessorIndex; // 在生成类 accessors 数组中的索引, 构造器参数绑定为 -1.

        FieldBinding(FieldEntry field, NodeSerializer<?> serializer, int index) {
            this(field, serializer, index, -1);
        }

        FieldBinding(FieldEntry field, NodeSerializer<?> serializer, int index, int accessorIndex) {
            this.field = field;
            this.serializer = serializer;
            this.index = index;
            this.accessorIndex = accessorIndex;
        }

        /**
         * 获取字段最终映射到 YAML 中的键名.
         */
        String yamlKey() {
            return field.hasYamlProperty ? field.yamlPropertyValue : field.name;
        }
    }
}
