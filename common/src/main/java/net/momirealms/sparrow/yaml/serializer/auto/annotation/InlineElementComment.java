package net.momirealms.sparrow.yaml.serializer.auto.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(InlineElementComments.class)
public @interface InlineElementComment {

    int index();

    String[] value();
}
