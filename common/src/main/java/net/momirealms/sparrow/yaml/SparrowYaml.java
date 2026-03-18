package net.momirealms.sparrow.yaml;

import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.SerializerRegistry;
import org.jetbrains.annotations.Nullable;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.DumpSettingsBuilder;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.LoadSettingsBuilder;
import org.snakeyaml.engine.v2.representer.StandardRepresenter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class SparrowYaml {
    private final LoadSettings loadSettings;
    private final DumpSettings dumpSettings;

    public final boolean allowObjectKey;

    private final StandardRepresenter representer;
    private final SerializerRegistry serializers = new SerializerRegistry(this);

    private SparrowYaml(
            LoadSettings loadSettings,
            DumpSettings dumpSettings,
            boolean allowObjectKey
    ) {
        this.loadSettings = loadSettings;
        this.dumpSettings = dumpSettings;
        this.allowObjectKey = allowObjectKey;
        this.representer =  new StandardRepresenter(dumpSettings);
    }

    /**
     * 注册新的自定义序列化器;
     * @param clazz 目标类
     * @param serializer 序列化器
     * @return 是否注册成功
     */
    public <T> boolean registerSerializer(Class<T> clazz, NodeSerializer<T> serializer) {
        return this.serializers.register(clazz, serializer);
    }

    /**
     * 注销已存在的自定义序列化器, 返回注销的序列化器;
     * @param clazz 目标类
     * @return 被移除的序列化器
     */
    @Nullable
    public <T> NodeSerializer<T> unregisterSerializer(Class<T> clazz) {
        return this.serializers.unregister(clazz);
    }

    /**
     * 获取一个自定义序列化器;
     */
    @Nullable
    public <T> NodeSerializer<T> getSerializer(Class<T> clazz) {
        return this.serializers.get(clazz);
    }

    /**
     * 从 File 中读取并创建一个 YamlDocument;
     * @param file YAML 文件
     */
    public YamlDocument load(File file) throws IOException {
        Objects.requireNonNull(file, "File cannot be null");
        try (InputStream inputStream = new FileInputStream(file)) {
            return load(inputStream);
        }
    }

    /**
     * 从 Path 中读取并创建一个 YamlDocument;
     * @param path 文件路径
     */
    public YamlDocument load(Path path) throws IOException {
        Objects.requireNonNull(path, "Path cannot be null");
        try (InputStream inputStream = Files.newInputStream(path)) {
            return load(inputStream);
        }
    }

    /**
     * 从 String 中读取并创建一个 YamlDocument;
     * @param content Yaml 内容
     * @return YamlDocument
     */
    public YamlDocument load(String content) throws IOException {
        Objects.requireNonNull(content, "Content cannot be null");
        try (InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
            return load(inputStream);
        }
    }

    /**
     * 从 字节数组 中读取并创建一个 YamlDocument;
     * @param bytes YAML 内容的字节数组
     * @return YamlDocument
     */
    public YamlDocument load(byte[] bytes) throws IOException {
        Objects.requireNonNull(bytes, "Bytes cannot be null");
        try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
            return load(inputStream);
        }
    }

    /**
     * 从 InputStream 中读取并创建一个 YamlDocument;
     * @param inputStream 输入流对象
     * @return YamlDocument
     */
    public YamlDocument load(InputStream inputStream) throws IOException {
        return new YamlDocument(this, inputStream);
    }

    /**
     * 从 Jar 的资源文件中读取并创建一个 YamlDocument;
     * @param resourceName 资源文件, 如 "config.yml"
     * @return YamlDocument
     */
    public YamlDocument loadFromResource(String resourceName) throws IOException {
        Objects.requireNonNull(resourceName, "Resource name cannot be null");
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName);
        if (inputStream == null) {
            throw new IOException("Resource not found: " + resourceName);
        }
        try (inputStream) {
            return load(inputStream);
        }
    }

    public LoadSettings loadSettings() {
        return this.loadSettings;
    }

    public DumpSettings dumpSettings() {
        return this.dumpSettings;
    }

    public StandardRepresenter standardRepresenter() {
        return this.representer;
    }

    /**
     * 建造者, 用于创建构建 SparrowYaml 对象的 builder;
     * @return SparrowYaml.Builder
     */
    public static SparrowYaml.Builder builder() {
        return new SparrowYaml.Builder();
    }

    public static class Builder {
        private final LoadSettingsBuilder loadSettingsBuilder = LoadSettings.builder().setAllowDuplicateKeys(false).setParseComments(true);
        private final DumpSettingsBuilder dumpSettingsBuilder = DumpSettings.builder().setDumpComments(true);
        private boolean allowObjectKey = false;
        private final Map<Class<?>, NodeSerializer<?>> prepareRegisterSerializers = new ConcurrentHashMap<>();

        /**
         * 是否允许读取重复的 Key ?
         * 如果允许则后读的Key会覆盖先读的Key;
         * 如果拒绝, 那么在读到相同的Key时, 会抛出异常;
         * @param value DefaultValue: false
         */
        public Builder setAllowDuplicateKeys(boolean value) {
            loadSettingsBuilder.setAllowDuplicateKeys(value);
            return this;
        }

        /**
         * 是否允许出现 Object 作为 Key? (除 Null 外).
         * 默认情况下, 所有加载的键都通过 Object.toString() 转换为字符串, (例如 5 -> "5" ).
         * @param value DefaultValue: false
         */
        public Builder setAllowObjectKeys(boolean value) {
            this.allowObjectKey = value;
            return this;
        }

        /**
         * 注册新的自定义序列化器;
         * @param clazz 目标类
         * @param serializer 序列化器
         * @return 是否注册成功
         */
        public <T> boolean registerSerializer(Class<T> clazz, NodeSerializer<T> serializer) {
            if (!prepareRegisterSerializers.containsKey(clazz)) {
                prepareRegisterSerializers.put(clazz, serializer);
                return true;
            }
            return false;
        }

        /**
         * 注销已存在的自定义序列化器, 返回注销的序列化器;
         * @param clazz 目标类
         * @return 被移除的序列化器
         */
        @Nullable
        @SuppressWarnings("unchecked")
        public <T> NodeSerializer<T> unregisterSerializer(Class<T> clazz) {
            return (NodeSerializer<T>) prepareRegisterSerializers.remove(clazz);
        }

        /**
         * 获取一个自定义序列化器;
         */
        @Nullable
        @SuppressWarnings("unchecked")
        public <T> NodeSerializer<T> getSerializer(Class<T> clazz) {
            return (NodeSerializer<T>) this.prepareRegisterSerializers.get(clazz);
        }

        /**
         * 构建 SparrowYaml
         */
        public SparrowYaml build() {
            SparrowYaml sparrowYaml = new SparrowYaml(
                    loadSettingsBuilder.build(),
                    dumpSettingsBuilder.build(),
                    allowObjectKey
            );
            prepareRegisterSerializers.forEach(sparrowYaml.serializers::registerUnsafe);
            return sparrowYaml;
        }

    }

}
