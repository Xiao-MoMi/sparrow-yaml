package net.momirealms.sparrow.yaml.node;

import java.util.List;
import java.util.Map;

public final class YamlNodeType<T> {
    private YamlNodeType() {}

    public static final YamlNodeType<Object> SCALAR = new YamlNodeType<>();
    public static final YamlNodeType<List<YamlNode<?>>> SEQUENCE = new YamlNodeType<>();
    public static final YamlNodeType<Map<Object, YamlNode<?>>> SECTION = new YamlNodeType<>();

    /**
     * 传入节点的值, 返回一个
     * @param value
     * @return
     */
    public YamlNode<?> valueOf(T value) {
        return null;
    }

}
