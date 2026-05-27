package net.momirealms.sparrow.yaml.upgrade.patch;

import net.momirealms.sparrow.yaml.YamlDocument;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 单次升级流程中由所有 Patch 共享的上下文数据.
 */
public class PatchContext {
    private final Map<String, Object> values = new LinkedHashMap<>();

    public Object put(String key, Object value) {
        return values.put(Objects.requireNonNull(key, "key"), value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) values.get(Objects.requireNonNull(key, "key"));
    }

    public <T> T get(String key, Class<T> type) {
        Object value = values.get(Objects.requireNonNull(key, "key"));
        return value == null ? null : type.cast(value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(String key, T defaultValue) {
        return (T) values.getOrDefault(Objects.requireNonNull(key, "key"), defaultValue);
    }

    public boolean contains(String key) {
        return values.containsKey(Objects.requireNonNull(key, "key"));
    }

    @SuppressWarnings("unchecked")
    public <T> T remove(String key) {
        return (T) values.remove(Objects.requireNonNull(key, "key"));
    }

    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(values);
    }

    public void clear() {
        values.clear();
    }
}
