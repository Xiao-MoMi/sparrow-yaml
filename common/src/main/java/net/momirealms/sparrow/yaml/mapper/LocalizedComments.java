package net.momirealms.sparrow.yaml.mapper;

import net.momirealms.sparrow.yaml.exception.AutoSerializerException;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.HashMap;
import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

final class LocalizedComments {
    private static final String[] NO_TEXT = new String[0];
    private static final LocalizedComments EMPTY = new LocalizedComments(Map.of());

    private final Map<String, String[]> translations;

    private LocalizedComments(Map<String, String[]> translations) {
        this.translations = translations;
    }

    // 收集完整翻译声明, 供共享同一配置类的不同语言 Mapper 使用
    static <A extends Annotation> LocalizedComments read(
            AnnotatedElement element,
            Class<A> annotationType,
            Function<A, String> language,
            Function<A, String[]> text
    ) {
        A[] annotations = element.getDeclaredAnnotationsByType(annotationType);
        if (annotations.length == 0) return EMPTY;

        Map<String, String[]> translations = new HashMap<>(annotations.length);
        for (int i = 0; i < annotations.length; i++) {
            A annotation = annotations[i];
            String tag = language.apply(annotation);
            if (!tag.isEmpty()) {
                try {
                    tag = new Locale.Builder().setLanguageTag(tag.replace('_', '-')).build().stripExtensions().toLanguageTag();
                } catch (IllformedLocaleException exception) {
                    throw new AutoSerializerException("Invalid @" + annotationType.getSimpleName() + " language '" + tag + "' on " + element, exception);
                }
            }
            if (translations.putIfAbsent(tag, text.apply(annotation)) != null) {
                throw new AutoSerializerException("Duplicate @" + annotationType.getSimpleName() + " language '" + tag + "' on " + element);
            }
        }
        return new LocalizedComments(Map.copyOf(translations));
    }

    boolean isEmpty() {
        return this.translations.isEmpty();
    }

    String[] select(String[] languageTags) {
        for (int i = 0; i < languageTags.length; i++) {
            String[] text = this.translations.get(languageTags[i]);
            // 空数组同样表示已命中, 可用于某种语言下省略整段注释
            if (text != null) return text;
        }
        return NO_TEXT;
    }

    // 完整标签 -> 显式文字体系 -> 纯语言 -> fallback, 顺序与注解排列无关
    static String[] languageTags(Locale locale) {
        Locale normalized = locale.stripExtensions();
        String language = normalized.getLanguage();
        String script = normalized.getScript();
        return new String[] {
                normalized.toLanguageTag(),
                script.isEmpty() ? language : language + "-" + script,
                language,
                ""
        };
    }
}
