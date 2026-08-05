package net.momirealms.sparrow.yaml.serializer.auto.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Configuration {
    Naming naming() default Naming.AS_IS;

    enum Naming {
        AS_IS('\0'),
        SNAKE_CASE('_'),
        KEBAB_CASE('-');

        private final char separator;

        Naming(char separator) {
            this.separator = separator;
        }

        public String convert(String name) {
            if (this == AS_IS) {
                return name;
            }
            StringBuilder result = new StringBuilder(name.length() + 4);
            for (int i = 0; i < name.length(); i++) {
                char current = name.charAt(i);
                if (Character.isUpperCase(current) && isWordBoundary(name, i)) {
                    result.append(this.separator);
                }
                result.append(Character.toLowerCase(current));
            }
            return result.toString();
        }

        public static Naming of(Class<?> type) {
            Configuration configuration = type.getAnnotation(Configuration.class);
            return configuration != null ? configuration.naming() : AS_IS;
        }

        private static boolean isWordBoundary(String name, int index) {
            if (index == 0) {
                return false;
            }
            char previous = name.charAt(index - 1);
            if (Character.isLowerCase(previous) || Character.isDigit(previous)) {
                return true;
            }
            return Character.isUpperCase(previous)
                    && index + 1 < name.length()
                    && Character.isLowerCase(name.charAt(index + 1));
        }
    }
}
