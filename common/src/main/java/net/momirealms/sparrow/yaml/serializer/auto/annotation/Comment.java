package net.momirealms.sparrow.yaml.serializer.auto.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于向映射到YAML节点的字段添加注释
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Comment {

    /**
     * 按顺序写入行前注释, 每段文本中的 {@code \n}、{@code \r\n} 和 {@code \r} 均拆为独立注释行.
     * 空段保留为裸 {@code #}, 文本开头已有的空格保留.
     *
     * @return 注释文本数组
     */
    String[] value();
}
