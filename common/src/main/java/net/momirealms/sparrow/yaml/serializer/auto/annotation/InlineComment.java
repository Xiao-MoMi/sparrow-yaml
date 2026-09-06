package net.momirealms.sparrow.yaml.serializer.auto.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface InlineComment {

    /**
     * 按顺序写入行中注释, 每段文本中的 {@code \n}、{@code \r\n} 和 {@code \r} 均拆为独立注释行.
     * 后续行按行中注释位置对齐, 空段保留为裸 {@code #}, 文本开头已有的空格保留.
     *
     * @return 注释文本数组
     */
    String[] value();
}
