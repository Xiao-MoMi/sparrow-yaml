package net.momirealms.sparrow.yaml.mapper;

import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.exception.AutoSerializerException;
import net.momirealms.sparrow.yaml.exception.MissingNodeException;
import net.momirealms.sparrow.yaml.exception.NodeParsingException;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.node.SequenceNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.AfterComment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.InlineComment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlConstructor;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlIgnore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlProperty;
import net.momirealms.sparrow.yaml.upgrade.UpgradeSchema;
import net.momirealms.sparrow.yaml.util.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.snakeyaml.engine.v2.comments.CommentLine;
import org.snakeyaml.engine.v2.comments.CommentType;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 运行期配置文档映射器.
 *
 * <p>通过 AutoSerializer 完成对象与 YAML 基础值之间的转换, 并在保存文档时补充 {@link Comment} 注解声明的注释.</p>
 *
 * @param <T> 配置类类型
 */
final class RuntimeConfigDocumentMapper<T> implements ConfigDocumentMapper<T> {
    private final Class<T> type; // 配置类类型
    private final List<CommentBinding> comments; // 保存时需要应用到各层级键的注释
    private final List<RequiredProperty> requiredProperties;
    private final Map<Route, Type> memberTypes;       // 配置成员路由到声明类型, 包含嵌套配置类中的成员
    private final List<Route> dynamicSectionRoutes; // 子节点属于动态用户数据的 Section 路由, 即 Map 字段对应的路由

    RuntimeConfigDocumentMapper(Class<T> type) {
        this.type = type;
        this.comments = collectComments(type);
        this.requiredProperties = collectRequiredProperties(type);
        this.memberTypes = collectMemberTypes(type);
        this.dynamicSectionRoutes = collectDynamicSectionRoutes(this.memberTypes);
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
            this.applyComments(doc, yaml.locale());

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
            if (document.getNodeOrNull(Route.from(requiredProperty.key())) == null) {
                throw new MissingNodeException(requiredProperty.key(), document, requiredProperty.targetType());
            }
        }
    }

    @Override
    public List<Route> dynamicSectionRoutes() {
        return this.dynamicSectionRoutes;
    }

    /**
     * 返回升级结构信息, 本地节点与模板形状不同时, 由该路由成员的序列化器判断本地写法能否读取.
     */
    @Override
    public UpgradeSchema upgradeSchema(SparrowYaml yaml) {
        return new UpgradeSchema() {
            @NotNull
            @Override
            public Collection<Route> dynamicSectionRoutes() {
                return RuntimeConfigDocumentMapper.this.dynamicSectionRoutes;
            }

            @Override
            public boolean accepts(@NotNull Route route, @NotNull YamlNode<?> localNode) {
                Type memberType = RuntimeConfigDocumentMapper.this.memberTypes.get(route);
                if (memberType == null) return false;
                // 用成员自己的序列化器试读一次, 读得出来就说明这是它接受的写法, 包括 withAlternative 登记的候选形式.
                try {
                    yaml.serializers().resolve(memberType).deserialize(localNode);
                    return true;
                } catch (NodeParsingException e) {
                    return false;
                }
            }
        };
    }

    /**
     * 将收集到的 Comment 注解应用到文档各层级节点.
     */
    private void applyComments(YamlDocument doc, Locale locale) {
        String[] languageTags = LocalizedComments.languageTags(locale);
        for (int i = 0; i < this.comments.size(); i++) {
            CommentBinding binding = this.comments.get(i);
            YamlNode<?> node = doc.getNodeOrNull(binding.route());
            if (node == null) {
                continue;
            }
            String[] before = binding.before.select(languageTags);
            String[] inline = binding.inline.select(languageTags);
            String[] after = binding.after.select(languageTags);
            List<CommentLine> beforeKeyComments = beforeKeyComments(binding.blankLinesBefore, before);
            if (!beforeKeyComments.isEmpty() && isNullOrEmpty(node.beforeKeyComments())) {
                node.setBeforeKeyComments(beforeKeyComments);
            }
            if (inline.length > 0 && isNullOrEmpty(node.inlineKeyComments())) {
                node.setInlineKeyComments(commentLines(inline, CommentType.IN_LINE));
            }
            if (after.length > 0 && isNullOrEmpty(node.afterKeyComments())) {
                node.setAfterKeyComments(commentLines(after, CommentType.BLOCK));
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
     * 递归收集字段或 record 组件上的 Comment 注解, 包括嵌套配置类中的字段.
     * 命名风格按字段所属类型解析, 不从外层配置类继承.
     */
    private static List<CommentBinding> collectComments(Class<?> type) {
        List<CommentBinding> result = new ArrayList<>();
        collectComments(type, null, new HashSet<>(), result);
        return result;
    }

    private static void collectComments(Class<?> type, Route prefix, Set<Class<?>> visiting, List<CommentBinding> result) {
        if (!visiting.add(type)) {
            return;
        }
        Configuration.Naming naming = Configuration.Naming.of(type);
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                if (component.isAnnotationPresent(YamlIgnore.class)) {
                    continue;
                }
                Route route = Route.addTo(prefix, yamlKey(component, naming));
                collectCommentBinding(component, route, result);
                collectMemberComments(component.getGenericType(), route, visiting, result);
            }
            visiting.remove(type);
            return;
        }

        for (Field field : declaredFieldsInHierarchy(type)) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isAnnotationPresent(YamlIgnore.class)) {
                continue;
            }
            Route route = Route.addTo(prefix, yamlKey(field, naming));
            collectCommentBinding(field, route, result);
            collectMemberComments(field.getGenericType(), route, visiting, result);
        }
        visiting.remove(type);
    }

    private static void collectMemberComments(Type memberType, Route route, Set<Class<?>> visiting, List<CommentBinding> result) {
        Class<?> rawType;
        try {
            rawType = TypeUtils.rawType(memberType);
        } catch (AutoSerializerException e) {
            return; // 无法确定原始类型的成员交由序列化层报告, 不参与注释收集.
        }
        if (isNestedConfigCandidate(rawType)) {
            collectComments(rawType, route, visiting, result);
        }
    }

    /**
     * 按父类到子类的顺序返回字段, 与自动序列化的字段输出顺序保持一致.
     */
    private static List<RequiredProperty> collectRequiredProperties(Class<?> type) {
        List<RequiredProperty> result = new ArrayList<>();
        Configuration.Naming naming = Configuration.Naming.of(type);
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (!constructor.isAnnotationPresent(YamlConstructor.class)) {
                continue;
            }
            for (Parameter parameter : constructor.getParameters()) {
                if (parameter.isAnnotationPresent(YamlIgnore.class)) {
                    continue;
                }
                result.add(new RequiredProperty(yamlKey(parameter, naming), boxedType(TypeUtils.rawType(parameter.getParameterizedType()))));
            }
        }
        return List.copyOf(result);
    }

    /**
     * 收集配置类各成员的路由与声明类型, 包含嵌套配置类中的成员.
     * Map 成员只登记自身, 其子节点属于动态用户数据.
     */
    private static Map<Route, Type> collectMemberTypes(Class<?> type) {
        Map<Route, Type> result = new HashMap<>();
        collectMemberTypes(type, null, new HashSet<>(), result);
        return Map.copyOf(result);
    }

    private static void collectMemberTypes(Class<?> type, Route prefix, Set<Class<?>> visiting, Map<Route, Type> result) {
        if (!visiting.add(type)) {
            return;
        }
        Configuration.Naming naming = Configuration.Naming.of(type);
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                if (component.isAnnotationPresent(YamlIgnore.class)) {
                    continue;
                }
                collectMemberType(component.getGenericType(), Route.addTo(prefix, yamlKey(component, naming)), visiting, result);
            }
        } else {
            for (Field field : declaredFieldsInHierarchy(type)) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isAnnotationPresent(YamlIgnore.class)) {
                    continue;
                }
                collectMemberType(field.getGenericType(), Route.addTo(prefix, yamlKey(field, naming)), visiting, result);
            }
        }
        visiting.remove(type);
    }

    private static void collectMemberType(Type memberType, Route route, Set<Class<?>> visiting, Map<Route, Type> result) {
        Class<?> rawType;
        try {
            rawType = TypeUtils.rawType(memberType);
        } catch (AutoSerializerException e) {
            return; // 无法确定原始类型的成员交由序列化层报告, 不参与收集.
        }
        result.put(route, memberType);
        if (rawType != Map.class && isNestedConfigCandidate(rawType)) {
            collectMemberTypes(rawType, route, visiting, result);
        }
    }

    /**
     * 从成员类型中筛出 Map 成员的路由.
     * 这些路由的子节点是动态用户数据, 升级时不应被模板内容合并或清理.
     */
    private static List<Route> collectDynamicSectionRoutes(Map<Route, Type> memberTypes) {
        List<Route> result = new ArrayList<>();
        for (Map.Entry<Route, Type> entry : memberTypes.entrySet()) {
            if (TypeUtils.rawType(entry.getValue()) == Map.class) {
                result.add(entry.getKey());
            }
        }
        return List.copyOf(result);
    }

    // 判断成员类型是否可能作为嵌套配置 mapping 参与自动序列化, 与 resolver 的类型分支保持一致.
    private static boolean isNestedConfigCandidate(Class<?> type) {
        if (type.isPrimitive() || type.isEnum() || type.isArray() || type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            return false;
        }
        String packageName = type.getPackageName();
        return !packageName.startsWith("java.") && !packageName.startsWith("javax.") && !packageName.startsWith("jdk.") && !packageName.startsWith("sun.");
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
    private static String yamlKey(Field field, Configuration.Naming naming) {
        YamlProperty property = field.getAnnotation(YamlProperty.class);
        return property != null ? property.value() : naming.convert(field.getName());
    }

    /**
     * 解析 record 组件映射到 YAML 中的键名.
     */
    private static String yamlKey(RecordComponent component, Configuration.Naming naming) {
        YamlProperty property = component.getAnnotation(YamlProperty.class);
        return property != null ? property.value() : naming.convert(component.getName());
    }

    /**
     * 将注解字符串转换为 SnakeYAML 注释行.
     */
    private static String yamlKey(Parameter parameter, Configuration.Naming naming) {
        YamlProperty property = parameter.getAnnotation(YamlProperty.class);
        return property != null ? property.value() : naming.convert(parameter.getName());
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

    // 将每段文本拆为独立注释行, 空段保留为裸 #
    private static List<CommentLine> commentLines(String[] comments, CommentType type) {
        List<CommentLine> result = new ArrayList<>(comments.length);
        for (int i = 0; i < comments.length; i++) {
            String[] lines = comments[i].split("\\r\\n|\\r|\\n", -1);
            for (int j = 0; j < lines.length; j++) {
                String line = lines[j];
                String value = line.isEmpty() || line.startsWith(" ") ? line : " " + line;
                result.add(new CommentLine(Optional.empty(), Optional.empty(), value, type));
            }
        }
        return result;
    }

    private static int blankLinesBefore(BlankLineBefore annotation) {
        return annotation == null ? 0 : annotation.value();
    }

    // 字段与 record 组件使用相同的注释收集规则
    private static void collectCommentBinding(AnnotatedElement element, Route route, List<CommentBinding> result) {
        LocalizedComments before = LocalizedComments.read(element, Comment.class, Comment::lang, Comment::value);
        LocalizedComments inline = LocalizedComments.read(element, InlineComment.class, InlineComment::lang, InlineComment::value);
        LocalizedComments after = LocalizedComments.read(element, AfterComment.class, AfterComment::lang, AfterComment::value);
        BlankLineBefore blankLineBefore = element.getAnnotation(BlankLineBefore.class);
        if (!before.isEmpty() || !inline.isEmpty() || !after.isEmpty() || blankLineBefore != null) {
            result.add(new CommentBinding(route, blankLinesBefore(blankLineBefore), before, inline, after));
        }
    }

    /**
     * 单个 YAML 键对应的注释绑定, route 为从文档根出发的完整路由.
     */
    private record CommentBinding(
            Route route,
            int blankLinesBefore,
            LocalizedComments before,
            LocalizedComments inline,
            LocalizedComments after
    ) {
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
