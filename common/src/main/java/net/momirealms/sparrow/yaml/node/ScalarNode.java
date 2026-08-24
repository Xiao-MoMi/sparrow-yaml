package net.momirealms.sparrow.yaml.node;

import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.NodeSerializers;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.snakeyaml.engine.v2.nodes.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ScalarNode extends AbstractYamlNode<Object> {

    public ScalarNode(
            @NotNull ParentNode<?> parent,
            @Nullable Node keyNode,
            @NotNull Node valueNode,
            @Nullable Object key,
            @Nullable Object value
    ) {
        super(parent, keyNode, valueNode, key, value);
    }

    private ScalarNode(
            @Nullable Node keyNode,
            @NotNull Node valueNode,
            @Nullable Object value
    ) {
        super();
        this.keyNode = keyNode;
        this.valueNode = valueNode;
        this.value = value;
    }

    public static ScalarNode create(@NotNull YamlDocument root, @Nullable Object value) {
        return create(root.sparrowYaml(), value);
    }
    
    public static ScalarNode create(@NotNull SparrowYaml sparrowYaml, @Nullable Object value) {
        Node valueNode = sparrowYaml.standardRepresenter().represent(value);
        return new ScalarNode(null, valueNode, value);
    }

    /**
     * 用于新建节点时创建一个值为 null 的节点;
     */
    @ApiStatus.Internal
    public static ScalarNode emptyNode(@NotNull SparrowYaml sparrowYaml) {
        Node valueNode = sparrowYaml.standardRepresenter().represent(null);
        return new ScalarNode(null, valueNode, null);
    }

    /**
     * 读取当前节点的字符串, YAML null 返回 null.
     *
     * @return 转换后的字符串
     */
    @Nullable
    public String getString() {
        return this.value == null ? null : this.get(String.class);
    }

    @NotNull
    public String getStringOrDefault(@NotNull String defaultValue) {
        return this.value == null ? defaultValue : this.get(String.class);
    }

    @NotNull
    public String getStringOrDefault(@NotNull Supplier<String> defaultValueSupplier) {
        return this.value == null ? defaultValueSupplier.get() : this.get(String.class);
    }

    /**
     * 读取当前节点的 boolean, YAML null 返回 false.
     *
     * @return 转换后的 boolean
     */
    public boolean getBoolean() {
        return this.getBooleanOrDefault(false);
    }

    public boolean getBooleanOrDefault(boolean defaultValue) {
        return this.value == null ? defaultValue : this.get(Boolean.class);
    }

    public boolean getBooleanOrDefault(@NotNull Supplier<Boolean> defaultValueSupplier) {
        return this.value == null ? defaultValueSupplier.get() : this.get(Boolean.class);
    }

    /**
     * 读取当前节点的 int, YAML null 返回 0.
     *
     * @return 转换后的 int
     */
    public int getInt() {
        return this.getIntOrDefault(0);
    }

    public int getIntOrDefault(int defaultValue) {
        return this.value == null ? defaultValue : this.get(Integer.class);
    }

    public int getIntOrDefault(@NotNull Supplier<Integer> defaultValueSupplier) {
        return this.value == null ? defaultValueSupplier.get() : this.get(Integer.class);
    }

    /**
     * 读取当前节点的 long, YAML null 返回 0.
     *
     * @return 转换后的 long
     */
    public long getLong() {
        return this.getLongOrDefault(0L);
    }

    public long getLongOrDefault(long defaultValue) {
        return this.value == null ? defaultValue : this.get(Long.class);
    }

    public long getLongOrDefault(@NotNull Supplier<Long> defaultValueSupplier) {
        return this.value == null ? defaultValueSupplier.get() : this.get(Long.class);
    }

    /**
     * 读取当前节点的 float, YAML null 返回 0.
     *
     * @return 转换后的 float
     */
    public float getFloat() {
        return this.getFloatOrDefault(0F);
    }

    public float getFloatOrDefault(float defaultValue) {
        return this.value == null ? defaultValue : this.get(Float.class);
    }

    public float getFloatOrDefault(@NotNull Supplier<Float> defaultValueSupplier) {
        return this.value == null ? defaultValueSupplier.get() : this.get(Float.class);
    }

    /**
     * 读取当前节点的 double, YAML null 返回 0.
     *
     * @return 转换后的 double
     */
    public double getDouble() {
        return this.getDoubleOrDefault(0D);
    }

    public double getDoubleOrDefault(double defaultValue) {
        return this.value == null ? defaultValue : this.get(Double.class);
    }

    public double getDoubleOrDefault(@NotNull Supplier<Double> defaultValueSupplier) {
        return this.value == null ? defaultValueSupplier.get() : this.get(Double.class);
    }

    /**
     * 读取当前节点的枚举, YAML null 返回 null.
     *
     * @param enumType 枚举类型
     * @param <E> 枚举类型
     * @return 转换后的枚举
     */
    @Nullable
    public <E extends Enum<E>> E getEnum(@NotNull Class<E> enumType) {
        return this.value == null ? null : this.get(this.enumSerializer(enumType));
    }

    @NotNull
    public <E extends Enum<E>> E getEnumOrDefault(@NotNull Enum<E> defaultValue, @NotNull Class<E> enumType) {
        return this.value == null ? enumType.cast(defaultValue) : this.get(this.enumSerializer(enumType));
    }

    @NotNull
    public <E extends Enum<E>> E getEnumOrDefault(@NotNull Supplier<E> defaultValueSupplier, @NotNull Class<E> enumType) {
        return this.value == null ? defaultValueSupplier.get() : this.get(this.enumSerializer(enumType));
    }

    @NotNull
    private <E extends Enum<E>> NodeSerializer<E> enumSerializer(@NotNull Class<E> enumType) {
        NodeSerializer<E> serializer = this.root().sparrowYaml().serializers().get(enumType);
        return serializer != null ? serializer : NodeSerializers.enumCodec(enumType);
    }

    @Override
    public void setValue(Object value) {
        if (value instanceof Map || value instanceof List) {
            throw new IllegalArgumentException("不能给 ScalarNode 设置 Map 或 List 的值!");
        }
        this.value = value;
        // 双向同步到底层的 SnakeYAML Node 中
        if (this.valueNode instanceof org.snakeyaml.engine.v2.nodes.ScalarNode) {
            YamlDocument root = this.root();
            List<AbstractYamlNode<?>> references = root.nodeReferences(this);
            SparrowYaml sparrowYaml = root.sparrowYaml();
            org.snakeyaml.engine.v2.nodes.ScalarNode represented =
                (org.snakeyaml.engine.v2.nodes.ScalarNode) sparrowYaml.standardRepresenter().represent(value);
            represented.setBlockComments(this.beforeValueComments == null ? null : new ArrayList<>(this.beforeValueComments));
            represented.setInLineComments(this.inlineValueComments == null ? null : new ArrayList<>(this.inlineValueComments));
            represented.setEndComments(this.afterValueComments == null ? null : new ArrayList<>(this.afterValueComments));
            for (int i = 0; i < references.size(); i++) {
                AbstractYamlNode<?> reference = references.get(i);
                if (!(reference instanceof ScalarNode alias)) {
                    continue;
                }
                Node previousNode = alias.valueNode;
                alias.value = value;
                alias.valueNode = represented;
                alias.initComments(alias.keyNode, represented);
                root.rebindNode(alias, previousNode);
            }
            for (int i = 0; i < references.size(); i++) {
                AbstractYamlNode<?> reference = references.get(i);
                if (!(reference instanceof ScalarNode alias)) {
                    continue;
                }
                if (alias.parent instanceof SectionNode section) {
                    section.syncValueNode(alias);
                } else if (alias.parent instanceof SequenceNode sequence) {
                    sequence.syncValueNode(alias);
                }
            }
        }
    }

    @Override
    public YamlNodeType<?> yamlNodeType() {
        return YamlNodeType.SCALAR;
    }

    @Override
    public boolean isRoot() {
        return false;
    }

    @Override
    public boolean isSection() {
        return false;
    }

    @Override
    public boolean isSequence() {
        return false;
    }

    @Override
    public boolean isScalar() {
        return true;
    }

}
