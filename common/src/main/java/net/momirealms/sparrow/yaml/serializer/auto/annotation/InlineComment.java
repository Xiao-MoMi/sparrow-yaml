package net.momirealms.sparrow.yaml.serializer.auto.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(InlineComments.class)
public @interface InlineComment {

    /**
     * 按顺序写入行中注释, 每段文本中的 {@code \n}、{@code \r\n} 和 {@code \r} 均拆为独立注释行.
     * 后续行按行中注释位置对齐, 空段保留为裸 {@code #}, 文本开头已有的空格保留.
     *
     * @return 注释文本数组
     */
    String[] value();

    /**
     * 注释适用的语言标签, 如 {@code zh-CN}; 空字符串表示未匹配语言时使用的 fallback.
     * 同一字段的行内注释中, 每种语言和 fallback 各允许声明一次.
     *
     * @return 语言标签
     */
    String lang() default "";
}
