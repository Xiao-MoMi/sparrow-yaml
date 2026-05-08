package net.momirealms.sparrow.yaml.serializer.auto.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于在映射到 YAML 节点的字段前插入空行.
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface BlankLineBefore {

    int value() default 1;
}
