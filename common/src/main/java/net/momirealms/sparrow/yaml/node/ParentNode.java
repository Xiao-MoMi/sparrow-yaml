package net.momirealms.sparrow.yaml.node;

import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.serializer.NodeDecoder;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.serializer.TypeRef;
import net.momirealms.sparrow.yaml.route.element.IndexElement;
import net.momirealms.sparrow.yaml.route.element.KeyElement;
import net.momirealms.sparrow.yaml.route.element.RouteElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
     * 根据路由, 从当前节点出发, 获得目标路由的节点;
     * 然后尝试使用指定的 Decoder 进行解析.
     * @param decoder 解码器
     * @param route 路由
     * @return 目标 JavaBean
     */
    @Nullable
    default <R> R get(NodeDecoder<R> decoder, Object... route) {
        YamlNode<?> yamlNode = this.getNodeOrNull(route);
        return yamlNode != null ? yamlNode.get(decoder) : null;
    }

    /**
     * 根据路由, 从当前节点出发, 获得目标路由的节点;
     * 然后根据 Class 从 SerializerRegistry 中获取反序列化器进行解析.
     * @param clazz 目标对象的 Class
     * @param route 路由
     * @return 目标 JavaBean
     */
    @Nullable
    default <R> R get(Class<R> clazz, Object... route) {
        YamlNode<?> yamlNode = this.getNodeOrNull(route);
        return yamlNode != null ? yamlNode.get(clazz) : null;
    }

    @Nullable
    default <R> R get(TypeRef<R> typeRef, Object... route) {
        YamlNode<?> yamlNode = this.getNodeOrNull(route);
        return yamlNode != null ? yamlNode.get(typeRef) : null;
    }

    /**
     * 根据路由, 从当前节点出发, 获得目标路由的节点;
     * 然后尝试使用指定的 Codec 进行解析.
     * @param decoder 编解码器
     * @param route 路由
     * @return 目标 JavaBean
     */
    @Nullable
    default <R> R getOrDefault(NodeDecoder<R> decoder, R defaultValue, Object... route) {
        YamlNode<?> yamlNode = this.getNodeOrNull(route);
        return yamlNode != null ? yamlNode.get(decoder) : defaultValue;
    }

    /**
     * 根据路由, 从当前节点出发, 获得目标路由的节点;
     * 然后根据 Class 从 SerializerRegistry 中获取反序列化器进行解析.
     * @param clazz 目标对象的 Class
     * @param defaultValue 默认值
     * @param route 路由
     * @return 目标 JavaBean
     */
    @Nullable
    default <R> R getOrDefault(Class<R> clazz, R defaultValue, Object... route) {
        YamlNode<?> yamlNode = this.getNodeOrNull(route);
        return yamlNode != null ? yamlNode.get(clazz) : defaultValue;
    }

    @Nullable
    default <R> R getOrDefault(TypeRef<R> typeRef, R defaultValue, Object... route) {
        YamlNode<?> yamlNode = this.getNodeOrNull(route);
        return yamlNode != null ? yamlNode.get(typeRef) : defaultValue;
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
        for (int i = 0; i < Objects.requireNonNull(route, "路由不可为 null !").routeKeys().length; i++) {
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
    default Optional<YamlNode<?>> getNodeOptional(@NotNull Object... route) {
        return Optional.ofNullable(this.getNodeOrNull(Route.from(route)));
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

    default <R> YamlNode<?> set(Class<R> clazz, @Nullable R value, @NotNull Object... route) {
        return this.set(clazz, value, Route.from(route));
    }

    default <R> YamlNode<?> set(TypeRef<R> typeRef, @Nullable R value, @NotNull Object... route) {
        return this.set(typeRef, value, Route.from(route));
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

    default <R> YamlNode<?> setAndGet(Class<R> clazz, @Nullable R value, @NotNull Object... route) {
        return this.setAndGet(clazz, value, Route.from(route));
    }

    default <R> YamlNode<?> setAndGet(TypeRef<R> typeRef, @Nullable R value, @NotNull Object... route) {
        return this.setAndGet(typeRef, value, Route.from(route));
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
        YamlDocument root = currentNode.root();

        // 循环搜索路由元素
        int routeLength = route.routeKeys().length;
        for (int i = 0; i < routeLength; i++) {
            RouteElement<?> nextRouteElement = route.getRouteElement(i);
            boolean isLastElement = (i == routeLength - 1);

            // 如果当前节点是 Sequence 节点, 则检查当前路由是不是 IndexElement, 则进行搜索获取下一个节点;
            if (currentNode instanceof SequenceNode sequenceNode) {
                // 拦截不匹配的路由元素.
                if (!(nextRouteElement instanceof IndexElement indexElement)) {
                    throw new UnsupportedOperationException("当前节点类型为 SequenceNode , 所以 Route 的第一个元素必须是一个 IndexElement ,但是实际是: " + nextRouteElement.getClass().getSimpleName());
                }

                int nextRouteKey = indexElement.key();
                YamlNode<?> targetNode = sequenceNode.getNodeOrNull(nextRouteKey);

                // 没有节点就新建
                if (targetNode == null) {
                    // 如果末尾节点, 则直接建立叶子节点的 Node;
                    if (isLastElement) {
                        sequenceNode.setSubNode(nextRouteKey, value);
                        targetNode = sequenceNode.getNodeOrNull(nextRouteKey);
                    } else {
                        // 获取下下个节点元素, 确定目标节点类型
                        RouteElement<?> createRouteElement = route.getRouteElement(i + 1);

                        // 第一种情况, 是 IndexElement, 则建立 SequenceNode;
                        if (createRouteElement instanceof IndexElement) {
                            targetNode = SequenceNode.createEmpty(root);
                        }
                        // 第二种情况, 是 KeyElement, 则建立 SectionNode;
                        else if (createRouteElement instanceof KeyElement) {
                            targetNode = SectionNode.createEmpty(root);
                        }
                        
                        sequenceNode.setSubNode(nextRouteKey, targetNode);
                    }
                }

                // 记录下一个节点
                currentNode = targetNode;
            }
            // 如果当前节点是 Section 节点, 则进行搜索获取下一个节点;
            else if (currentNode instanceof SectionNode sectionNode) {
                Object nextRouteKey = nextRouteElement.key();
                YamlNode<?> targetNode = sectionNode.getNodeOrNull(nextRouteElement);

                // 没有节点就新建
                if (targetNode == null) {
                    // 是末尾节点, 则直接建立叶子节点的 Node;
                    if (isLastElement) {
                        sectionNode.setSubNode(nextRouteKey, value);
                        targetNode = sectionNode.getNodeOrNull(nextRouteKey);
                    } else {
                        // 获取下下个节点元素, 确定目标节点类型;
                        RouteElement<?> createRouteElement = route.getRouteElementOrNull(i + 1);

                        // 第一种情况, 是 IndexElement, 则建立 SequenceNode;
                        if (createRouteElement instanceof IndexElement) {
                            targetNode = SequenceNode.createEmpty(root);
                        }
                        // 第二种情况, 是 KeyElement, 则建立 SectionNode;
                        else if (createRouteElement instanceof KeyElement) {
                            targetNode = SectionNode.createEmpty(root);
                        }
                        
                        sectionNode.setSubNode(nextRouteKey, targetNode);
                    }
                }

                // 记录下一个节点
                currentNode = targetNode;
            }
            // 理论上不会进入
            else {
                throw new UnsupportedOperationException("当前节点不支持寻找或创建子节点，或路由类型不匹配。节点类型: " + currentNode.getClass().getSimpleName() + ", 路由元素: " + nextRouteElement.getClass().getSimpleName());
            }
        }
        // 循环结束返回创建完成的 Node;
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

}
