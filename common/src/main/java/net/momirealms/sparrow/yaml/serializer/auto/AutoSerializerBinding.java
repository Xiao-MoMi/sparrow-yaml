package net.momirealms.sparrow.yaml.serializer.auto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 序列化绑定配置类, 手动指定反序列化时使用的构造函数以及参数映射关系.
 * // todo 这个类的build方法可以再改改
 */
public class AutoSerializerBinding {
    private ConstructorBinding constructorBinding;

    /**
     * 指定反序列化时使用的构造函数参数类型列表.
     *
     * @param parameterTypes 构造函数的参数类型数组
     * @return 返回一个 ConstructorBinding 实例, 以便通过链式调用继续配置参数名称
     */
    public ConstructorBinding constructor(Class<?>... parameterTypes) {
        this.constructorBinding = new ConstructorBinding(parameterTypes);
        return this.constructorBinding;
    }

    /**
     * 获取当前配置的构造器绑定信息.
     *
     * @return 当前的构造器绑定信息, 若未配置则返回 null
     */
    public ConstructorBinding constructorBinding() {
        return this.constructorBinding;
    }

    /**
     * 构造器绑定信息的内部配置类.
     * 负责存储所选构造器的参数类型数组和对应的 YAML 键名列表.
     */
    public static class ConstructorBinding {
        private final Class<?>[] parameterTypes;
        private final List<String> parameterNames = new ArrayList<>();

        public ConstructorBinding(Class<?>[] parameterTypes) {
            this.parameterTypes = Objects.requireNonNull(parameterTypes, "parameterTypes").clone();
        }

        /**
         * 按构造器参数顺序, 逐个添加参数在 YAML 中对应的键名.
         *
         * @param name YAML 中的键名
         * @return 当前 ConstructorBinding 实例, 支持链式调用
         * @throws NullPointerException 如果键名为 null
         */
        public ConstructorBinding param(String name) {
            this.parameterNames.add(Objects.requireNonNull(name, "name"));
            return this;
        }

        public Class<?>[] parameterTypes() {
            return this.parameterTypes.clone();
        }

        public List<String> parameterNames() {
            return Collections.unmodifiableList(this.parameterNames);
        }
    }
}
