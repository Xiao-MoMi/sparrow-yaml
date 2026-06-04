package net.momirealms.sparrow.yaml.serializer.auto.annotation;

import java.lang.annotation.*;

/**
 * 用于向集合或数组中的元素添加注释
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ElementComments.class)
public @interface ElementComment {

    int index();

    String[] value();
}
