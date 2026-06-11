package net.momirealms.sparrow.yaml.mapper;

import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.exception.AutoSerializerException;
import net.momirealms.sparrow.yaml.exception.MissingNodeException;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.node.SequenceNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.AfterComment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.InlineComment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlConstructor;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlIgnore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlProperty;
import net.momirealms.sparrow.yaml.util.TypeUtils;
import org.snakeyaml.engine.v2.comments.CommentLine;
import org.snakeyaml.engine.v2.comments.CommentType;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 运行期配置文档映射器.
 *
 * <p>通过 AutoSerializer 完成对象与 YAML 基础值之间的转换, 并在保存文档时补充 {@link Comment} 注解声明的注释.</p>
 *
 * @param <T> 配置类类型
 */
final class RuntimeConfigDocumentMapper<T> implements ConfigDocumentMapper<T> {
    private final Class<T> type; // 配置类类型
    private final List<CommentBinding> comments; // 保存时需要应用到顶层键的注释
    private final List<RequiredProperty> requiredProperties;

    RuntimeConfigDocumentMapper(Class<T> type) {
        this.type = type;
        this.comments = collectComments(type);
        this.requiredProperties = collectRequiredProperties(type);
    }

    /**
     * 将配置对象编码为新的 YAML 文档, 并应用字段注释.
     */
    @Override
    public YamlDocument toDocument(T instance, YamlDocument existing, SparrowYaml yaml) {
        try {
            YamlDocument doc = existing == null ? yaml.load("") : existing;
            Map<Route, NodeComments> existingComments = existing == null ? Map.of() : snapshotComments(existing);
            NodeSerializer<T> serializer = yaml.serializers().register(type);
            Object encoded = serializer.serialize(instance);

            if (encoded == null) {
                return doc;
            }
            if (!(encoded instanceof Map<?, ?> map)) {
                throw new AutoSerializerException("Configuration serializer for " + type.getName() + " must encode to a mapping, got " + encoded.getClass().getName());
            }

            // 先写入节点, 再应用注释, 避免注释目标节点不存在.
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                doc.setAndGet(Route.from(String.valueOf(entry.getKey())), entry.getValue());
            }
            restoreComments(doc, existingComments);
            applyComments(doc);

            return doc;
        } catch (IOException e) {
            throw new AutoSerializerException("Cannot create YAML document for " + type.getName(), e);
        }
    }

    /**
     * 使用 AutoSerializer 从 YAML 文档反序列化配置对象.
     */
    @Override
    public T fromDocument(YamlDocument document, SparrowYaml yaml) {
        validateRequiredProperties(document);
        NodeSerializer<T> serializer = yaml.serializers().register(type);
        return serializer.deserialize(document);
    }

    private void validateRequiredProperties(YamlDocument document) {
        for (RequiredProperty requiredProperty : requiredProperties) {
            if (document.getNodeOrNull(requiredProperty.key()) == null) {
                throw new MissingNodeException(requiredProperty.key(), document, requiredProperty.targetType());
            }
        }
    }

    /**
     * 将收集到的 Comment 注解应用到文档顶层节点.
     */
    private void applyComments(YamlDocument doc) {
        for (CommentBinding binding : comments) {
            YamlNode<?> node = doc.getNodeOrNull(binding.key);
            if (node == null) {
                continue;
            }
            List<CommentLine> beforeKeyComments = beforeKeyComments(binding.blankLinesBefore, binding.before);
            if (!beforeKeyComments.isEmpty() && isNullOrEmpty(node.beforeKeyComments())) {
                node.setBeforeKeyComments(beforeKeyComments);
            }
            if (binding.inline.length > 0 && isNullOrEmpty(node.inlineKeyComments())) {
                node.setInlineKeyComments(commentLines(binding.inline, CommentType.IN_LINE));
            }
            if (binding.after.length > 0 && isNullOrEmpty(node.afterKeyComments())) {
                node.setAfterKeyComments(commentLines(binding.after, CommentType.BLOCK));
            }
        }
    }

    private static Map<Route, NodeComments> snapshotComments(YamlNode<?> node) {
        Map<Route, NodeComments> comments = new HashMap<>();
        snapshotComments(node, comments);
        return comments;
    }

    private static void snapshotComments(YamlNode<?> node, Map<Route, NodeComments> comments) {
        Route route = node.route();
        NodeComments nodeComments = NodeComments.from(node);
        if (route != null && nodeComments.hasAny()) {
            comments.put(route, nodeComments);
        }
        if (node instanceof SectionNode sectionNode) {
            for (YamlNode<?> child : sectionNode.value().values()) {
                snapshotComments(child, comments);
            }
        } else if (node instanceof SequenceNode sequenceNode) {
            for (YamlNode<?> child : sequenceNode.value()) {
                snapshotComments(child, comments);
            }
        }
    }

    private static void restoreComments(YamlDocument doc, Map<Route, NodeComments> comments) {
        for (Map.Entry<Route, NodeComments> entry : comments.entrySet()) {
            YamlNode<?> node = doc.getNodeOrNull(entry.getKey());
            if (node != null) {
                entry.getValue().applyTo(node);
            }
        }
    }

    private static boolean isNullOrEmpty(List<CommentLine> comments) {
        return comments == null || comments.isEmpty();
    }

    /**
     * 收集字段或 record 组件上的 Comment 注解.
     */
    private static List<CommentBinding> collectComments(Class<?> type) {
        List<CommentBinding> result = new ArrayList<>();
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                if (component.isAnnotationPresent(YamlIgnore.class)) {
                    continue;
                }
                Comment comment = component.getAnnotation(Comment.class);
                InlineComment inlineComment = component.getAnnotation(InlineComment.class);
                AfterComment afterComment = component.getAnnotation(AfterComment.class);
                BlankLineBefore blankLineBefore = component.getAnnotation(BlankLineBefore.class);
                if (comment != null || inlineComment != null || afterComment != null || blankLineBefore != null) {
                    result.add(new CommentBinding(
                            yamlKey(component),
                            blankLinesBefore(blankLineBefore),
                            before(comment),
                            inline(inlineComment),
                            after(afterComment)
                    ));
                }
            }
            return result;
        }

        for (Field field : declaredFieldsInHierarchy(type)) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isAnnotationPresent(YamlIgnore.class)) {
                continue;
            }
            Comment comment = field.getAnnotation(Comment.class);
            InlineComment inlineComment = field.getAnnotation(InlineComment.class);
            AfterComment afterComment = field.getAnnotation(AfterComment.class);
            BlankLineBefore blankLineBefore = field.getAnnotation(BlankLineBefore.class);
            if (comment != null || inlineComment != null || afterComment != null || blankLineBefore != null) {
                result.add(new CommentBinding(
                        yamlKey(field),
                        blankLinesBefore(blankLineBefore),
                        before(comment),
                        inline(inlineComment),
                        after(afterComment)
                ));
            }
        }
        return result;
    }

    /**
     * 按父类到子类的顺序返回字段, 与自动序列化的字段输出顺序保持一致.
     */
    private static List<RequiredProperty> collectRequiredProperties(Class<?> type) {
        List<RequiredProperty> result = new ArrayList<>();
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (!constructor.isAnnotationPresent(YamlConstructor.class)) {
                continue;
            }
            for (Parameter parameter : constructor.getParameters()) {
                if (parameter.isAnnotationPresent(YamlIgnore.class)) {
                    continue;
                }
                result.add(new RequiredProperty(yamlKey(parameter), boxedType(TypeUtils.rawType(parameter.getParameterizedType()))));
            }
        }
        return List.copyOf(result);
    }

    private static List<Field> declaredFieldsInHierarchy(Class<?> type) {
        LinkedList<Field> result = new LinkedList<>();
        Class<?> currentType = type;
        while (currentType != null && currentType != Object.class) {
            Field[] fields = currentType.getDeclaredFields();
            for (int i = fields.length - 1; i >= 0; i--) {
                result.addFirst(fields[i]);
            }
            currentType = currentType.getSuperclass();
        }
        return result;
    }

    /**
     * 解析字段映射到 YAML 中的键名.
     */
    private static String yamlKey(Field field) {
        YamlProperty property = field.getAnnotation(YamlProperty.class);
        return property != null ? property.value() : field.getName();
    }

    /**
     * 解析 record 组件映射到 YAML 中的键名.
     */
    private static String yamlKey(RecordComponent component) {
        YamlProperty property = component.getAnnotation(YamlProperty.class);
        return property != null ? property.value() : component.getName();
    }

    /**
     * 将注解字符串转换为 SnakeYAML 注释行.
     */
    private static String yamlKey(Parameter parameter) {
        YamlProperty property = parameter.getAnnotation(YamlProperty.class);
        return property != null ? property.value() : parameter.getName();
    }

    private static Class<?> boxedType(Class<?> type) {
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static List<CommentLine> beforeKeyComments(int blankLinesBefore, String[] comments) {
        int blankLineCount = Math.max(blankLinesBefore, 0);
        List<CommentLine> result = new ArrayList<>(blankLineCount + comments.length);
        for (int i = 0; i < blankLineCount; i++) {
            result.add(new CommentLine(Optional.empty(), Optional.empty(), "", CommentType.BLANK_LINE));
        }
        result.addAll(commentLines(comments, CommentType.BLOCK));
        return result;
    }

    private static List<CommentLine> commentLines(String[] comments, CommentType type) {
        List<CommentLine> result = new ArrayList<>(comments.length);
        for (String comment : comments) {
            String value = comment.isEmpty() || comment.startsWith(" ") ? comment : " " + comment;
            result.add(new CommentLine(Optional.empty(), Optional.empty(), value, type));
        }
        return result;
    }

    private static int blankLinesBefore(BlankLineBefore annotation) {
        return annotation == null ? 0 : annotation.value();
    }

    private static String[] before(Comment comment) {
        return comment == null ? new String[0] : comment.value();
    }

    private static String[] inline(InlineComment comment) {
        return comment == null ? new String[0] : comment.value();
    }

    private static String[] after(AfterComment comment) {
        return comment == null ? new String[0] : comment.value();
    }

    /**
     * 单个 YAML 键对应的注释绑定.
     */
    private record CommentBinding(String key, int blankLinesBefore, String[] before, String[] inline, String[] after) {
    }

    private record RequiredProperty(String key, Class<?> targetType) {
    }

    private record NodeComments(
            List<CommentLine> beforeKey,
            List<CommentLine> inlineKey,
            List<CommentLine> afterKey,
            List<CommentLine> beforeValue,
            List<CommentLine> inlineValue,
            List<CommentLine> afterValue
    ) {

        static NodeComments from(YamlNode<?> node) {
            return new NodeComments(
                    copy(node.beforeKeyComments()),
                    copy(node.inlineKeyComments()),
                    copy(node.afterKeyComments()),
                    copy(node.beforeValueComments()),
                    copy(node.inlineValueComments()),
                    copy(node.afterValueComments())
            );
        }

        boolean hasAny() {
            return !isNullOrEmpty(beforeKey)
                    || !isNullOrEmpty(inlineKey)
                    || !isNullOrEmpty(afterKey)
                    || !isNullOrEmpty(beforeValue)
                    || !isNullOrEmpty(inlineValue)
                    || !isNullOrEmpty(afterValue);
        }

        void applyTo(YamlNode<?> node) {
            node.setBeforeKeyComments(copy(beforeKey));
            node.setInlineKeyComments(copy(inlineKey));
            node.setAfterKeyComments(copy(afterKey));
            node.setBeforeValueComments(copy(beforeValue));
            node.setInlineValueComments(copy(inlineValue));
            node.setAfterValueComments(copy(afterValue));
        }

        private static List<CommentLine> copy(List<CommentLine> comments) {
            return comments == null ? null : new ArrayList<>(comments);
        }
    }
}
