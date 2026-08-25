package net.momirealms.sparrow.yaml.node;

import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.exception.MissingNodeException;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.route.element.IndexElement;
import net.momirealms.sparrow.yaml.route.element.RouteElement;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.NodeSerializers;
import net.momirealms.sparrow.yaml.serializer.TypeRef;
import net.momirealms.sparrow.yaml.util.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

public interface ParentNode<T extends YamlNode<?>> {

    /**
     * 获取当前节点的 YamlNode 节点实现. <br>
     * 你可以通过以下方法来判断当前节点实现: <br>
     *  {@link YamlNode#isSection()} <br>
     *  {@link YamlNode#isSequence()} <br>
     * @return YamlNode 节点
     */
    T yamlNode();

    /**
     * 按精确路由读取字符串, missing 或 YAML null 返回 null.
     *
     * @param route 精确路由
     * @return 转换后的字符串
     */
    @Nullable
    default String getString(@NotNull Route route) {
        YamlNode<?> node = this.getNodeOrNull(route);
        if (node == null || node.value() == null) {
            return null;
        }
        return node.get(String.class);
    }

    @NotNull
    default String getStringOrDefault(@NotNull String defaultValue, @NotNull Route route) {
        YamlNode<?> node = this.getNodeOrNull(route);
        return node == null || node.value() == null ? defaultValue : node.get(String.class);
    }

    @NotNull
    default String getStringOrDefault(@NotNull Supplier<String> defaultValueSupplier, @NotNull Route route) {
        YamlNode<?> node = this.getNodeOrNull(route);
        return node == null || node.value() == null ? defaultValueSupplier.get() : node.get(String.class);
    }

    /**
     * 按精确路由读取 boolean, missing 或 YAML null 返回 false.
     *
     * @param route 精确路由
     * @return 转换后的 boolean
     */
    default boolean getBoolean(@NotNull Route route) {
        return this.getBooleanOrDefault(false, route);
    }

    default boolean getBooleanOrDefault(boolean defaultValue, @NotNull Route route) {
        YamlNode<?> node = this.getNodeOrNull(route);
        return node == null || node.value() == null ? defaultValue : node.get(Boolean.class);
    }

    default boolean getBooleanOrDefault(@NotNull Supplier<Boolean> defaultValueSupplier, @NotNull Route route) {
        YamlNode<?> node = this.getNodeOrNull(route);
        return node == null || node.value() == null ? defaultValueSupplier.get() : node.get(Boolean.class);
    }

    /**
     * 按精确路由读取 int, missing 或 YAML null 返回 0.
     *
     * @param route 精确路由
     * @return 转换后的 int
     */
    default int getInt(@NotNull Route route) {
        return this.getIntOrDefault(0, route);
    }

    default int getIntOrDefault(int defaultValue, @NotNull Route route) {
        YamlNode<?> node = this.getNodeOrNull(route);
        return node == null || node.value() == null ? defaultValue : node.get(Integer.class);
    }

    default int getIntOrDefault(@NotNull Supplier<Integer> defaultValueSupplier, @NotNull Route route) {
        YamlNode<?> node = this.getNodeOrNull(route);
        return node == null || node.value() == null ? defaultValueSupplier.get() : node.get(Integer.class);
    }

    /**
     * 按精确路由读取 long, missing 或 YAML null 返回 0.
     *
     * @param route 精确路由
     * @return 转换后的 long
     */
    default long getLong(@NotNull Route route) {
        return this.getLongOrDefault(0L, route);
    }

    default long getLongOrDefault(long defaultValue, @NotNull Route route) {
        YamlNode<?> node = this.getNodeOrNull(route);
        return node == null || node.value() == null ? defaultValue : node.get(Long.class);
    }

    default long getLongOrDefault(@NotNull Supplier<Long> defaultValueSupplier, @NotNull Route route) {
        YamlNode<?> node = this.getNodeOrNull(route);
        return node == null || node.value() == null ? defaultValueSupplier.get() : node.get(Long.class);
    }

    /**
     * 按精确路由读取 float, missing 或 YAML null 返回 0.
     *
     * @param route 精确路由
     * @return 转换后的 float
     */
    default float getFloat(@NotNull Route route) {
        return this.getFloatOrDefault(0F, route);
    }

    default float getFloatOrDefault(float defaultValue, @NotNull Route route) {
        YamlNode<?> node = this.getNodeOrNull(route);
        return node == null || node.value() == null ? defaultValue : node.get(Float.class);
    }

    default float getFloatOrDefault(@NotNull Supplier<Float> defaultValueSupplier, @NotNull Route route) {
        YamlNode<?> node = this.getNodeOrNull(route);
        return node == null || node.value() == null ? defaultValueSupplier.get() : node.get(Float.class);
    }

    /**
     * 按精确路由读取 double, missing 或 YAML null 返回 0.
     *
     * @param route 精确路由
     * @return 转换后的 double
     */
    default double getDouble(@NotNull Route route) {
        return this.getDoubleOrDefault(0D, route);
    }

    default double getDoubleOrDefault(double defaultValue, @NotNull Route route) {
        YamlNode<?> node = this.getNodeOrNull(route);
        return node == null || node.value() == null ? defaultValue : node.get(Double.class);
    }

    default double getDoubleOrDefault(@NotNull Supplier<Double> defaultValueSupplier, @NotNull Route route) {
        YamlNode<?> node = this.getNodeOrNull(route);
        return node == null || node.value() == null ? defaultValueSupplier.get() : node.get(Double.class);
    }

    /**
     * 按精确路由严格读取字符串列表, missing 或 YAML null 返回 null.
     *
     * @param route 精确路由
     * @return 转换后的字符串列表
     */
    @Nullable
    default List<String> getStringList(@NotNull Route route) {
        return this.getList(NodeSerializers.STRING, route);
    }

    /**
     * 按精确路由使用已注册的元素类型严格读取列表, missing 或 YAML null 返回 null.
     *
     * @param elementType 元素类型
     * @param route 精确路由
     * @param <R> 元素类型
     * @return 解析后的列表
     */
    @Nullable
    default <R> List<R> getList(@NotNull Class<R> elementType, @NotNull Route route) {
        YamlNode<?> node = this.getNodeOrNull(route);
        if (node == null || node.value() == null) {
            return null;
        }
        return node.get(this.registeredSerializer(elementType).listOf());
    }

    @Nullable
    default <R> List<R> getList(@NotNull NodeSerializer<R> elementSerializer, @NotNull Route route) {
        YamlNode<?> node = this.getNodeOrNull(route);
        if (node == null || node.value() == null) {
            return null;
        }
        return node.get(elementSerializer.listOf());
    }

    @Nullable
    default <R> List<R> getList(@NotNull TypeRef<R> elementType, @NotNull Route route) {
        YamlNode<?> node = this.getNodeOrNull(route);
        if (node == null || node.value() == null) {
            return null;
        }
        return node.get(this.registeredSerializer(elementType).listOf());
    }

    /**
     * 按精确路由使用已注册的 key/value 类型严格读取 Map 列表.
     *
     * @param keyType Map key 类型
     * @param valueType Map value 类型
     * @param route 精确路由
     * @param <K> Map key 类型
     * @param <V> Map value 类型
     * @return 解析后的 Map 列表, missing 或 YAML null 返回 null
     */
    @Nullable
    default <K, V> List<Map<K, V>> getMapList(@NotNull Class<K> keyType, @NotNull Class<V> valueType, @NotNull Route route) {
        YamlNode<?> node = this.getNodeOrNull(route);
        if (node == null || node.value() == null) {
            return null;
        }
        NodeSerializer<K> keySerializer = this.registeredSerializer(keyType);
        NodeSerializer<V> valueSerializer = this.registeredSerializer(valueType);
        return node.get(valueSerializer.mapOf(keySerializer).listOf());
    }

    @Nullable
    default <K, V> List<Map<K, V>> getMapList(@NotNull TypeRef<K> keyType, @NotNull TypeRef<V> valueType, @NotNull Route route) {
        YamlNode<?> node = this.getNodeOrNull(route);
        if (node == null || node.value() == null) {
            return null;
        }
        NodeSerializer<K> keySerializer = this.registeredSerializer(keyType);
        NodeSerializer<V> valueSerializer = this.registeredSerializer(valueType);
        return node.get(valueSerializer.mapOf(keySerializer).listOf());
    }

    @Nullable
    default <K, V> List<Map<K, V>> getMapList(@NotNull NodeSerializer<K> keySerializer, @NotNull NodeSerializer<V> valueSerializer, @NotNull Route route) {
        return this.getList(valueSerializer.mapOf(keySerializer), route);
    }

    /**
     * 按精确路由读取枚举, missing 或 YAML null 返回 null.
     *
     * @param enumType 枚举类型
     * @param route 精确路由
     * @param <E> 枚举类型
     * @return 转换后的枚举
     */
    @Nullable
    default <E extends Enum<E>> E getEnum(@NotNull Class<E> enumType, @NotNull Route route) {
        YamlNode<?> node = this.getNodeOrNull(route);
        return node == null || node.value() == null ? null : node.get(this.enumSerializer(enumType));
    }

    @NotNull
    default <E extends Enum<E>> E getEnumOrDefault(@NotNull Enum<E> defaultValue, @NotNull Class<E> enumType, @NotNull Route route) {
        YamlNode<?> node = this.getNodeOrNull(route);
        return node == null || node.value() == null ? enumType.cast(defaultValue) : node.get(this.enumSerializer(enumType));
    }

    @NotNull
    default <E extends Enum<E>> E getEnumOrDefault(@NotNull Supplier<E> defaultValueSupplier, @NotNull Class<E> enumType, @NotNull Route route) {
        YamlNode<?> node = this.getNodeOrNull(route);
        return node == null || node.value() == null ? defaultValueSupplier.get() : node.get(this.enumSerializer(enumType));
    }

    /**
     * 按精确路由递归提取 Java 值, missing 或 YAML null 返回 null.
     *
     * @param route 精确路由
     * @return Java 值
     */
    @Nullable
    default Object getValue(@NotNull Route route) {
        YamlNode<?> node = this.getNodeOrNull(route);
        return node == null ? null : node.representValue();
    }

    /**
     * 根据路由, 从当前节点出发, 获得目标路由的节点;
     * 然后尝试使用指定的 NodeSerializer 进行解析.
     * @param serializer 序列化器
     * @param route 路由
     * @return 目标 JavaBean
     */
    default <R> R get(NodeSerializer<R> serializer, Route route) {
        YamlNode<?> yamlNode = this.getNodeOrNull(route);
        if (yamlNode == null) {
            throw missing(route, serializer.targetType());
        }
        return yamlNode.get(serializer);
    }

    default <R> R get(Class<R> clazz, Route route) {
        YamlNode<?> yamlNode = this.getNodeOrNull(route);
        if (yamlNode == null) {
            throw missing(route, clazz);
        }
        return yamlNode.get(clazz);
    }

    default <R> R get(TypeRef<R> typeRef, Route route) {
        YamlNode<?> yamlNode = this.getNodeOrNull(route);
        if (yamlNode == null) {
            throw missing(route, TypeUtils.rawType(typeRef.type()));
        }
        return yamlNode.get(typeRef);
    }

    @Nullable
    default <R> R getOrDefault(@Nullable R defaultValue, @NotNull NodeSerializer<R> serializer, @NotNull Route route) {
        YamlNode<?> yamlNode = this.getNodeOrNull(route);
        return yamlNode != null ? yamlNode.get(serializer) : defaultValue;
    }

    @Nullable
    default <R> R getOrDefault(@Nullable R defaultValue, @NotNull Class<R> clazz, @NotNull Route route) {
        YamlNode<?> yamlNode = this.getNodeOrNull(route);
        return yamlNode != null ? yamlNode.get(clazz) : defaultValue;
    }

    @Nullable
    default <R> R getOrDefault(@NotNull Supplier<? extends R> defaultValueSupplier, @NotNull NodeSerializer<R> serializer, @NotNull Route route) {
        YamlNode<?> yamlNode = this.getNodeOrNull(route);
        return yamlNode != null ? yamlNode.get(serializer) : defaultValueSupplier.get();
    }

    @Nullable
    default <R> R getOrDefault(@NotNull Supplier<? extends R> defaultValueSupplier, @NotNull Class<R> clazz, @NotNull Route route) {
        YamlNode<?> yamlNode = this.getNodeOrNull(route);
        return yamlNode != null ? yamlNode.get(clazz) : defaultValueSupplier.get();
    }

    @Nullable
    default <R> R getOrDefault(@Nullable R defaultValue, @NotNull TypeRef<R> typeRef, @NotNull Route route) {
        YamlNode<?> yamlNode = this.getNodeOrNull(route);
        return yamlNode != null ? yamlNode.get(typeRef) : defaultValue;
    }

    @Nullable
    default <R> R getOrDefault(@NotNull Supplier<? extends R> defaultValueSupplier, @NotNull TypeRef<R> typeRef, @NotNull Route route) {
        YamlNode<?> yamlNode = this.getNodeOrNull(route);
        return yamlNode != null ? yamlNode.get(typeRef) : defaultValueSupplier.get();
    }

    private static MissingNodeException missing(Route route, Class<?> targetType) {
        Object key = route.length() == 0 ? null : route.getRouteElement(route.length() - 1).key();
        return new MissingNodeException(key, route, targetType);
    }

    /**
     * 根据路由, 从当前节点出发, 获得目标路由的节点;
     * 如果目标节点不存在, 则返回 null 值;
     * @param route 路由
     * @return 目标节点
     */
    @Nullable
    default YamlNode<?> getNodeOrNull(@NotNull Route route) {
        YamlNode<?> currentNode = this.yamlNode();
        // 循环搜索路由元素
        for (int i = 0; i < route.length(); i++) {
            RouteElement<?> routeElement = route.getRouteElement(i);
            // 如果当前节点是 Sequence 节点, 并且路由是 IndexElement, 则进行搜索获取下一个节点;
            if (currentNode instanceof SequenceNode sequenceNode && routeElement instanceof IndexElement indexElement) {
                int routeKey = indexElement.key();
                List<YamlNode<?>> sequenceList = sequenceNode.value();
                currentNode = routeKey <= sequenceList.size() - 1 ? sequenceList.get(routeKey) : null;
            }
            // 如果当前节点是 Section 节点, 则进行搜索获取下一个节点;
            else if (currentNode instanceof SectionNode sectionNode) {
                Object routeKey = sectionNode.adaptKey(routeElement.key());
                currentNode = sectionNode.value().get(routeKey);
            }
            else {
                return null;
            }
            // 如果目标是空节点 / 错误的匹配目标
            if (currentNode == null) {
                return null;
            }
        }
        return currentNode;
    }

    @Nullable
    default YamlNode<?> getNodeOrNull(@NotNull Object... route) {
        return this.getNodeOrNull(Route.from(route));
    }

    @NotNull
    default Optional<YamlNode<?>> getNodeOptional(@NotNull Route route) {
        return Optional.ofNullable(this.getNodeOrNull(route));
    }

    @NotNull
    default Optional<YamlNode<?>> getNodeOptional(@NotNull Object... route) {
        return this.getNodeOptional(Route.from(route));
    }

    /**
     * 根据路由, 从当前节点出发, 获得目标路由的节点;
     * 如果目标节点不存在, 或目标节点类型不匹配, 则返回 Null;
     * @param route 路由
     * @return 目标节点
     */
    @Nullable
    default ScalarNode getScalarOrNull(@NotNull Route route) {
        YamlNode<?> yamlNode = this.getNodeOrNull(route);
        if (yamlNode instanceof ScalarNode scalarNode) {
            return scalarNode;
        }
        return null;
    }

    @Nullable
    default ScalarNode getScalarOrNull(@NotNull Object... route) {
        return this.getScalarOrNull(Route.from(route));
    }

    /**
     * 根据路由, 从当前节点出发, 获得目标路由的节点;
     * 如果目标节点不存在, 或目标节点类型不匹配, 则返回 Null;
     * @param route 路由
     * @return 目标节点
     */
    @Nullable
    default SectionNode getSectionOrNull(@NotNull Route route) {
        YamlNode<?> yamlNode = this.getNodeOrNull(route);
        if (yamlNode instanceof SectionNode sectionNode) {
            return sectionNode;
        }
        return null;
    }

    @Nullable
    default SectionNode getSectionOrNull(@NotNull Object... route) {
        return this.getSectionOrNull(Route.from(route));
    }

    /**
     * 根据路由, 从当前节点出发, 获得目标路由的节点;
     * 如果目标节点不存在, 或目标节点类型不匹配, 则返回 Null;
     * @param route 路由
     * @return 目标节点
     */
    @Nullable
    default SequenceNode getSequenceOrNull(@NotNull Route route) {
        YamlNode<?> yamlNode = this.getNodeOrNull(route);
        if (yamlNode instanceof SequenceNode sequenceNode) {
            return sequenceNode;
        }
        return null;
    }

    @Nullable
    default SequenceNode getSequenceOrNull(@NotNull Object... route) {
        return this.getSequenceOrNull(Route.from(route));
    }

    /**
     * 根据路由, 从当前节点出发, 获得目标路由的节点;
     * 如果目标节点不存在, 则抛出传入的自定义异常; 如果没有传入异常则抛出 {@link NoSuchElementException}
     * @param route 路由
     * @param exception 抛出的异常
     * @return 目标节点
     */
    @NotNull
    default YamlNode<?> getNodeOrThrow(@Nullable Throwable exception, @NotNull Route route) throws Throwable {
        YamlNode<?> yamlNode = this.getNodeOrNull(route);
        if (yamlNode == null) {
            throw exception != null ? exception : new NoSuchElementException("不存在的节点: " + route);
        }
        return yamlNode;
    }

    @NotNull
    default YamlNode<?> getNodeOrThrow(@Nullable Throwable exception, @NotNull Object... route) throws Throwable {
        return this.getNodeOrThrow(exception, Route.from(route));
    }

    @NotNull
    default YamlNode<?> getNodeOrThrow(@NotNull Object... route) throws Throwable {
        return this.getNodeOrThrow(null, route);
    }

    /**
     * 根据路由, 从当前节点出发, 获得目标路由的节点;
     * 如果目标节点不存在, 或目标节点类型不匹配, 则返回 Null;
     * @param route 路由
     * @return 目标节点
     */
    @Nullable
    default ScalarNode getScalarOrThrow(@NotNull Route route, @Nullable Throwable exception) throws Throwable {
        YamlNode<?> yamlNode = this.getNodeOrNull(route);
        if (yamlNode instanceof ScalarNode scalarNode) {
            return scalarNode;
        }
        throw exception != null ? exception : new NoSuchElementException("不存在的节点: " + route);
    }

    @Nullable
    default ScalarNode getScalarOrThrow(@Nullable Throwable exception, @NotNull Object... route) throws Throwable {
        return this.getScalarOrThrow(Route.from(route), exception);
    }

    /**
     * 根据路由, 从当前节点出发, 获得目标路由的节点;
     * 如果目标节点不存在, 或目标节点类型不匹配, 则返回 Null;
     * @param route 路由
     * @return 目标节点
     */
    @Nullable
    default SectionNode getSectionOrThrow(@NotNull Route route, @Nullable Throwable exception) throws Throwable {
        YamlNode<?> yamlNode = this.getNodeOrNull(route);
        if (yamlNode instanceof SectionNode sectionNode) {
            return sectionNode;
        }
        throw exception != null ? exception : new NoSuchElementException("不存在的节点: " + route);
    }

    @Nullable
    default SectionNode getSectionOrThrow(@Nullable Throwable exception, @NotNull Object... route) throws Throwable {
        return this.getSectionOrThrow(Route.from(route), exception);
    }

    /**
     * 根据路由, 从当前节点出发, 获得目标路由的节点;
     * 如果目标节点不存在, 或目标节点类型不匹配, 则返回 Null;
     * @param route 路由
     * @return 目标节点
     */
    @Nullable
    default SequenceNode getSequenceOrThrow(@NotNull Route route, @Nullable Throwable exception) throws Throwable {
        YamlNode<?> yamlNode = this.getNodeOrNull(route);
        if (yamlNode instanceof SequenceNode sequenceNode) {
            return sequenceNode;
        }
        throw exception != null ? exception : new NoSuchElementException("不存在的节点: " + route);
    }

    @Nullable
    default SequenceNode getSequenceOrThrow(@Nullable Throwable exception, @NotNull Object... route) throws Throwable {
        return this.getSequenceOrThrow(Route.from(route), exception);
    }

    /**
     * 检查精确路由对应的节点是否存在, YAML null 也视为存在.
     *
     * @param route 精确路由
     * @return 节点是否存在
     */
    default boolean contains(@NotNull Route route) {
        return this.getNodeOrNull(route) != null;
    }

    /**
     * 检查路径元素指定的节点是否存在.
     *
     * @param route 路径元素
     * @return 节点是否存在
     */
    default boolean contains(@NotNull Object... route) {
        return this.contains(Route.from(route));
    }

    @Nullable
    YamlNode<?> removeSubNode(Object key);

    @Nullable
    default YamlNode<?> removeNode(@NotNull Route route) {
        if (route.length() == 0) {
            return null; // Cannot remove root
        }

        Route parentRoute = Route.empty();
        for (int i = 0; i < route.length() - 1; i++) {
            parentRoute = Route.addTo(parentRoute, route.getRouteElement(i).key());
        }

        YamlNode<?> parentNode = this.getNodeOrNull(parentRoute);
        if (parentNode == null) return null;

        Object keyToRemove = route.getRouteElement(route.length() - 1).key();
        
        if (parentNode instanceof SectionNode sectionNode) {
            return sectionNode.removeSubNode(keyToRemove);
        } else if (parentNode instanceof SequenceNode sequenceNode) {
            if (keyToRemove instanceof Integer index) {
                return sequenceNode.removeSubNode(index);
            }
        }
        return null;
    }

    @Nullable
    default YamlNode<?> removeNode(@NotNull Object... route) {
        return this.removeNode(Route.from(route));
    }

    /**
     * 根据路由, 从当前节点出发, 寻找子节点, 然后使用 SerializerRegistry 获取对应的序列化器序列化后设置其的值;
     * 如果目标节点途径的节点不存在, 则自动创建路径上所有的节点;
     * 如果目标节点路径上的节点存在, 但节点和路由中预期的节点类型不一致, 则会强行覆盖节点;
     * @param clazz 目标对象的 Class
     * @param value 目标对象
     * @param route 从当前节点出发的路由
     * @return 创建完成的叶子节点
     */
    default <R> YamlNode<?> set(Class<R> clazz, @Nullable R value, @NotNull Route route) {
        NodeSerializer<R> serializer = this.yamlNode().root().sparrowYaml().serializers().get(clazz);
        if (serializer != null) {
            return this.set(route, serializer.serialize(value));
        }
        return this.set(route, value);
    }

    default <R> YamlNode<?> set(TypeRef<R> typeRef, @Nullable R value, @NotNull Route route) {
        NodeSerializer<R> serializer = this.yamlNode().root().sparrowYaml().serializers().get(typeRef);
        if (serializer != null) {
            return this.set(route, serializer.serialize(value));
        }
        return this.set(route, value);
    }

    default <R> YamlNode<?> set(NodeSerializer<R> serializer, @Nullable R value, @NotNull Route route) {
        return this.set(route, serializer.serialize(value));
    }

    default <R> YamlNode<?> set(Class<R> clazz, @Nullable R value, @NotNull Object... route) {
        return this.set(clazz, value, Route.from(route));
    }

    default <R> YamlNode<?> set(TypeRef<R> typeRef, @Nullable R value, @NotNull Object... route) {
        return this.set(typeRef, value, Route.from(route));
    }

    default <R> YamlNode<?> set(NodeSerializer<R> serializer, @Nullable R value, @NotNull Object... route) {
        return this.set(serializer, value, Route.from(route));
    }

    /**
     * 在指定路由处使用 SerializerRegistry 获取对应的序列化器序列化后设置节点, 并返回写入后的节点对象.
     * @param clazz 目标对象的 Class
     * @param value 目标对象
     * @param route 路由
     */
    default <R> YamlNode<?> setAndGet(Class<R> clazz, @Nullable R value, @NotNull Route route) {
        NodeSerializer<R> serializer = this.yamlNode().root().sparrowYaml().serializers().get(clazz);
        if (serializer != null) {
            return this.setAndGet(route, serializer.serialize(value));
        }
        return this.setAndGet(route, value);
    }

    default <R> YamlNode<?> setAndGet(TypeRef<R> typeRef, @Nullable R value, @NotNull Route route) {
        NodeSerializer<R> serializer = this.yamlNode().root().sparrowYaml().serializers().get(typeRef);
        if (serializer != null) {
            return this.setAndGet(route, serializer.serialize(value));
        }
        return this.setAndGet(route, value);
    }

    default <R> YamlNode<?> setAndGet(NodeSerializer<R> serializer, @Nullable R value, @NotNull Route route) {
        return this.setAndGet(route, serializer.serialize(value));
    }

    default <R> YamlNode<?> setAndGet(Class<R> clazz, @Nullable R value, @NotNull Object... route) {
        return this.setAndGet(clazz, value, Route.from(route));
    }

    default <R> YamlNode<?> setAndGet(TypeRef<R> typeRef, @Nullable R value, @NotNull Object... route) {
        return this.setAndGet(typeRef, value, Route.from(route));
    }

    default <R> YamlNode<?> setAndGet(NodeSerializer<R> serializer, @Nullable R value, @NotNull Object... route) {
        return this.setAndGet(serializer, value, Route.from(route));
    }

    /**
     * 根据路由, 从当前节点出发, 寻找子节点, 然后设置其的值;
     * 如果目标节点途径的节点不存在, 则自动创建路径上所有的节点;
     * 如果目标节点路径上的节点存在, 但节点和路由中预期的节点类型不一致, 则会强行覆盖节点;
     * @param route 从当前节点出发的路由;
     * @param value 节点默认值
     * @return 创建完成的叶子节点
     */
    default YamlNode<?> set(@NotNull Route route, @Nullable Object value) {
        Objects.requireNonNull(route, "路由不可为 null !");

        YamlNode<?> currentNode = this.yamlNode();
        if (route.length() == 0) {
            return currentNode;
        }
        YamlDocument root = currentNode.root();

        int routeLength = route.length();
        for (int i = 0; i < routeLength; i++) {
            RouteElement<?> nextRouteElement = route.getRouteElement(i);
            boolean isLastElement = (i == routeLength - 1);

            if (currentNode instanceof SequenceNode sequenceNode) {
                if (!(nextRouteElement instanceof IndexElement indexElement)) {
                    throw new UnsupportedOperationException("当前节点类型为 SequenceNode , 所以 Route 的第一个元素必须是一个 IndexElement ,但是实际是: " + nextRouteElement.getClass().getSimpleName());
                }

                int nextRouteKey = indexElement.key();
                if (isLastElement) {
                    sequenceNode.setSubNode(nextRouteKey, value);
                    return sequenceNode.getNodeOrNull(nextRouteKey);
                }

                RouteElement<?> childRouteElement = route.getRouteElement(i + 1);
                YamlNode<?> targetNode = sequenceNode.getNodeOrNull(nextRouteKey);
                if (!matchesContainer(targetNode, childRouteElement)) {
                    targetNode = createContainer(root, childRouteElement);
                    sequenceNode.setSubNode(nextRouteKey, targetNode);
                    targetNode = sequenceNode.getNodeOrNull(nextRouteKey);
                }
                currentNode = targetNode;
            }
            else if (currentNode instanceof SectionNode sectionNode) {
                Object nextRouteKey = nextRouteElement.key();
                if (isLastElement) {
                    sectionNode.setSubNode(nextRouteKey, value);
                    return sectionNode.getNodeOrNull(nextRouteKey);
                }

                RouteElement<?> childRouteElement = route.getRouteElement(i + 1);
                YamlNode<?> targetNode = sectionNode.getNodeOrNull(nextRouteElement);
                if (!matchesContainer(targetNode, childRouteElement)) {
                    targetNode = createContainer(root, childRouteElement);
                    sectionNode.setSubNode(nextRouteKey, targetNode);
                    targetNode = sectionNode.getNodeOrNull(nextRouteElement);
                }
                currentNode = targetNode;
            }
            else {
                throw new UnsupportedOperationException("当前节点不支持寻找或创建子节点，或路由类型不匹配。节点类型: " + currentNode.getClass().getSimpleName() + ", 路由元素: " + nextRouteElement.getClass().getSimpleName());
            }
        }
        return currentNode;
    }

    /**
     * 在指定路由处设置节点, 并返回写入后的节点对象.
     */
    default YamlNode<?> setAndGet(@NotNull Route route, @Nullable Object value) {
        try {
            Route parentRoute = route.parent();
            YamlNode<?> parentNode = parentRoute == null ? this.yamlNode() : this.getNodeOrNull(parentRoute);
            if (parentNode instanceof SectionNode sectionNode) {
                sectionNode.setSubNode(route.getRouteElement(route.length() - 1).key(), value);
            } else if (parentNode instanceof SequenceNode sequenceNode) {
                if (route.getRouteElement(route.length() - 1).key() instanceof Integer idx) {
                    sequenceNode.setSubNode(idx, value);
                }
            } else {
                this.set(route, value);
            }
            return this.getNodeOrNull(route);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set node at route: " + route, e);
        }
    }

    private static boolean matchesContainer(@Nullable YamlNode<?> node, RouteElement<?> routeElement) {
        return routeElement instanceof IndexElement ? node instanceof SequenceNode : node instanceof SectionNode;
    }

    private static YamlNode<?> createContainer(YamlDocument root, RouteElement<?> routeElement) {
        return routeElement instanceof IndexElement ? SequenceNode.createEmpty(root) : SectionNode.createEmpty(root);
    }

    @NotNull
    private <E extends Enum<E>> NodeSerializer<E> enumSerializer(@NotNull Class<E> enumType) {
        NodeSerializer<E> serializer = this.yamlNode().root().sparrowYaml().serializers().get(enumType);
        return serializer != null ? serializer : NodeSerializers.enumCodec(enumType);
    }

    @NotNull
    private <R> NodeSerializer<R> registeredSerializer(@NotNull Class<R> type) {
        NodeSerializer<R> serializer = this.yamlNode().root().sparrowYaml().serializers().get(type);
        if (serializer != null) {
            return serializer;
        }
        throw new UnsupportedOperationException("not found registered serializer for " + type);
    }

    @NotNull
    private <R> NodeSerializer<R> registeredSerializer(@NotNull TypeRef<R> type) {
        NodeSerializer<R> serializer = this.yamlNode().root().sparrowYaml().serializers().get(type);
        if (serializer != null) {
            return serializer;
        }
        throw new UnsupportedOperationException("not found registered serializer for " + type);
    }
}
