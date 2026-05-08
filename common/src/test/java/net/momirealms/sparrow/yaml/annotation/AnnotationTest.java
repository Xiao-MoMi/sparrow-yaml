package net.momirealms.sparrow.yaml.annotation;

import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlIgnore;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

public class AnnotationTest {

    record BlockPos(int x, int y, int z) {}

    @Configuration
    public static class BaseConfiguration {
        private String host = "127.0.0.1";
        private int port = 1234;
        private List<String> blockedAddresses = List.of("8.8.8.8", "114.114.114.114");
        private BlockPos pos;
        @YamlIgnore
        private double ignoreDouble = 3.1415926535;
    }

    public static class TestClass {
        @Comment(before = "test")
        private String field1;

        @Comment(inline = "inline test", after = "after test")
        private String field2;
    }

    public static void main(String[] args) throws Exception {
        Field field1 = TestClass.class.getDeclaredField("field1");
        Comment comment1 = field1.getAnnotation(Comment.class);
        System.out.println(Arrays.toString(comment1.before()));

        Field field2 = TestClass.class.getDeclaredField("field2");
        Comment comment2 = field2.getAnnotation(Comment.class);
        System.out.println(Arrays.toString(comment2.inline()));
        System.out.println(Arrays.toString(comment2.after()));
    }
}
