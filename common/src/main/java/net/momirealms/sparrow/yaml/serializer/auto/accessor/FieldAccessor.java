package net.momirealms.sparrow.yaml.serializer.auto.accessor;

/**
 * 字段访问抽象, 用于屏蔽 ASM 和反射两种字段读写实现.
 * AutoSerializer 和运行期 Mapper 都通过该接口访问目标对象字段.
 */
public interface FieldAccessor {

    /**
     * 从目标实例中读取字段原始值.
     *
     * @param target 目标对象实例
     * @return 字段值, 基本类型会被装箱
     */
    Object get(Object target);

    /**
     * 将值写入目标实例字段.
     *
     * @param target 目标对象实例
     * @param value  要写入的字段值
     */
    void set(Object target, Object value);

    /**
     * 当前访问器是否支持写入字段.
     *
     * @return 支持写入时返回 true
     */
    boolean canSet();
}
