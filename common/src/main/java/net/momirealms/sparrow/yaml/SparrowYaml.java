package net.momirealms.sparrow.yaml;

import net.momirealms.sparrow.yaml.internal.YamlFileLifecycle;
import net.momirealms.sparrow.yaml.mapper.YamlMapper;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
import net.momirealms.sparrow.yaml.serializer.SerializerRegistry;
import net.momirealms.sparrow.yaml.serializer.auto.AutoSerializerMode;
import net.momirealms.sparrow.yaml.upgrade.YamlUpgradePipeline;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.snakeyaml.engine.v2.api.*;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.common.NonPrintableStyle;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.representer.StandardRepresenter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public class SparrowYaml {
    private final LoadSettings loadSettings;
    private final DumpSettings dumpSettings;
    public final boolean allowObjectKey;
    private final StandardRepresenter representer;
    private final SerializerRegistry serializers;

    private SparrowYaml(
            LoadSettings loadSettings,
            DumpSettings dumpSettings,
            boolean allowObjectKey,
            AutoSerializerMode autoSerializerMode
    ) {
        this.loadSettings = loadSettings;
        this.dumpSettings = dumpSettings;
        this.allowObjectKey = allowObjectKey;
        this.representer =  new StandardRepresenter(dumpSettings);
        this.serializers = new SerializerRegistry(this, autoSerializerMode);
    }

    /**
     * 创建不包含任何节点的 YAML 文档.
     *
     * @return 新文档
     */
    @NotNull
    public YamlDocument createDocument() {
        return new YamlDocument(this);
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
     * 从 Sparrow YAML 所属 ClassLoader 的资源中读取文档.
     *
     * @param resourceName classpath 根目录下的资源名, 如 "config.yml"
     * @return 读取到的 YAML 文档
     * @throws IOException 当资源不存在或读取失败时
     */
    @NotNull
    public YamlDocument loadFromResource(@NotNull String resourceName) throws IOException {
        return this.loadFromResource(this.getClass().getClassLoader(), resourceName);
    }

    /**
     * 从指定类所属 ClassLoader 的资源中读取文档.
     *
     * @param resourceAnchor 定义资源归属 ClassLoader 的类, <strong>必须由非 bootstrap ClassLoader 加载</strong>
     * @param resourceName classpath 根目录下的资源名, 如 "config.yml"
     * @return 读取到的 YAML 文档
     * @throws IOException 当资源不存在或读取失败时
     */
    @NotNull
    public YamlDocument loadFromResource(@NotNull Class<?> resourceAnchor, @NotNull String resourceName) throws IOException {
        return this.loadFromResource(resourceAnchor.getClassLoader(), resourceName);
    }

    /**
     * 从指定 ClassLoader 的资源中读取文档.
     *
     * @param classLoader 资源所属的 ClassLoader
     * @param resourceName classpath 根目录下的资源名, 如 "config.yml"
     * @return 读取到的 YAML 文档
     * @throws IOException 当资源不存在或读取失败时
     */
    @NotNull
    public YamlDocument loadFromResource(@NotNull ClassLoader classLoader, @NotNull String resourceName) throws IOException {
        InputStream inputStream = classLoader.getResourceAsStream(resourceName);
        if (inputStream == null) {
            throw new IOException("Resource not found: " + resourceName);
        }
        try (inputStream) {
            return this.load(inputStream);
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

    public SerializerRegistry serializers() {
        return this.serializers;
    }

    /**
     * 使用当前 SparrowYaml 的设置为配置类创建文件 Mapper.
     *
     * @param clazz 配置类
     * @param defaultInstanceSupplier 默认配置实例提供器
     * @param <T> 配置类型
     * @return 使用当前 SparrowYaml 的 Mapper
     */
    @NotNull
    public <T> YamlMapper<T> mapper(@NotNull Class<T> clazz, @NotNull Supplier<T> defaultInstanceSupplier) {
        return YamlMapperFactory.builder().sparrowYaml(this).build().create(clazz, defaultInstanceSupplier);
    }

    /**
     * 通过 File 读取或创建本地文档, 并在版本落后时升级和保存.
     *
     * @param localFile 本地文件
     * @param defDocument 当前版本的默认文档
     * @param pipeline 更新管道
     * @param backup 是否在实际升级时备份原文件
     * @return 当前版本的本地文档
     * @throws IOException 当读取、备份或保存失败时
     */
    @NotNull
    public YamlDocument upgradeFile(@NotNull File localFile, @NotNull YamlDocument defDocument, @NotNull YamlUpgradePipeline pipeline, boolean backup) throws IOException {
        return this.upgradeFile(localFile.toPath(), defDocument, pipeline, backup);
    }

    /**
     * 通过 File 和固定备份路径读取或升级本地文档.
     *
     * @param localFile 本地文件
     * @param defDocument 当前版本的默认文档
     * @param pipeline 更新管道
     * @param backupPath 实际升级时使用的备份路径, null 表示不备份
     * @return 当前版本的本地文档
     * @throws IOException 当读取、备份或保存失败时
     */
    @NotNull
    public YamlDocument upgradeFile(@NotNull File localFile, @NotNull YamlDocument defDocument, @NotNull YamlUpgradePipeline pipeline, @Nullable Path backupPath) throws IOException {
        return this.upgradeFile(localFile.toPath(), defDocument, pipeline, backupPath);
    }

    /**
     * 通过 File 和动态备份路径读取或升级本地文档.
     *
     * @param localFile 本地文件
     * @param defDocument 当前版本的默认文档
     * @param pipeline 更新管道
     * @param backupPathResolver 实际升级时调用的备份路径解析器, <strong>被调用时必须返回非 null Path</strong>, null 表示不备份
     * @return 当前版本的本地文档
     * @throws IOException 当读取、备份或保存失败时
     */
    @NotNull
    public YamlDocument upgradeFile(@NotNull File localFile, @NotNull YamlDocument defDocument, @NotNull YamlUpgradePipeline pipeline, @Nullable Function<Path, Path> backupPathResolver) throws IOException {
        return this.upgradeFile(localFile.toPath(), defDocument, pipeline, backupPathResolver);
    }

    /**
     * 读取或创建本地文件, 并在版本落后时升级和保存文档.
     *
     * @param localPath 本地文件路径
     * @param defDocument 当前版本的默认文档
     * @param pipeline 更新管道
     * @param backup 是否在实际升级时备份原文件
     * @return 当前版本的本地文档
     * @throws IOException 当读取、备份或保存失败时
     */
    @NotNull
    public YamlDocument upgradeFile(@NotNull Path localPath, @NotNull YamlDocument defDocument, @NotNull YamlUpgradePipeline pipeline, boolean backup) throws IOException {
        Function<Path, Path> backupPathResolver = backup ? defaultBackupPathResolver() : null;
        return this.upgradeFile(localPath, defDocument, pipeline, backupPathResolver);
    }

    @NotNull
    public YamlDocument upgradeFile(@NotNull Path localPath, @NotNull YamlDocument defDocument, @NotNull YamlUpgradePipeline pipeline, @Nullable Path backupPath) throws IOException {
        return this.upgradeFile(localPath, defDocument, pipeline, backupPath == null ? null : path -> backupPath);
    }

    @NotNull
    public YamlDocument upgradeFile(@NotNull Path localPath, @NotNull YamlDocument defDocument, @NotNull YamlUpgradePipeline pipeline, @Nullable Function<Path, Path> backupPathResolver) throws IOException {
        return YamlFileLifecycle.load(this, localPath, () -> defDocument, pipeline, List.of(), backupPathResolver).document();
    }

    // 默认备份配置路径解析器
    public static Function<Path, Path> defaultBackupPathResolver() {
        return localPath -> {
            Objects.requireNonNull(localPath, "localPath");
            Path parent = localPath.getParent();
            if (parent == null) {
                parent = Path.of(".");
            }
            return parent.resolve(localPath.getFileName() + ".bak." + System.currentTimeMillis());
        };
    }

    /**
     * 将指定文件备份到目标路径.
     *
     * @param localPath 需要备份的源文件路径
     * @param backupPath 备份文件路径
     */
    public void backupFile(Path localPath, Path backupPath) throws IOException {
        Objects.requireNonNull(backupPath, "backupPath");
        backupFileIfNeeded(localPath, backupPath);
    }

    private static void backupFileIfNeeded(Path localPath, @Nullable Path backupPath) throws IOException {
        Objects.requireNonNull(localPath, "localPath");
        if (backupPath == null) {
            return;
        }

        Path normalizedLocalPath = localPath.toAbsolutePath().normalize();
        Path normalizedBackupPath = backupPath.toAbsolutePath().normalize();
        if (normalizedBackupPath.equals(normalizedLocalPath)) {
            throw new IllegalArgumentException("localFile path can not same with backup path!");
        }
        if (!Files.exists(localPath)) {
            return;
        }

        Path backupParent = backupPath.getParent();
        if (backupParent != null) {
            Files.createDirectories(backupParent);
        }
        Files.copy(localPath, backupPath);
    }

    /**
     * 建造者, 用于创建构建 SparrowYaml 对象的 builder;
     * @return SparrowYaml.Builder
     */
    public static SparrowYaml.Builder builder() {
        return new SparrowYaml.Builder();
    }

    /**
     * 使用默认设置创建 SparrowYaml.
     *
     * @return 新的 SparrowYaml 实例
     */
    @NotNull
    public static SparrowYaml create() {
        return builder().build();
    }

    public static class Builder {
        private final LoadSettingsBuilder loadSettingsBuilder = LoadSettings.builder()
                .setAllowDuplicateKeys(false)
                .setParseComments(true);
        private final DumpSettingsBuilder dumpSettingsBuilder = DumpSettings.builder()
                .setDumpComments(true)
                .setDefaultFlowStyle(FlowStyle.BLOCK);
        private boolean allowObjectKey = false;
        private AutoSerializerMode autoSerializerMode = AutoSerializerMode.ADAPTIVE;

        // ──────────── GeneralSettings ────────────

        public Builder setAutoSerializerMode(AutoSerializerMode autoSerializerMode) {
            this.autoSerializerMode = Objects.requireNonNull(autoSerializerMode, "autoSerializerMode");
            return this;
        }

        // ──────────── LoadSettings ────────────

        /**
         * 是否允许读取重复的 Key ?
         * 如果允许则后读的Key会覆盖先读的Key;
         * 如果拒绝, 那么在读到相同的Key时, 会抛出异常;
         * @param value DefaultValue: false
         */
        public Builder setAllowDuplicateKeys(boolean value) {
            this.loadSettingsBuilder.setAllowDuplicateKeys(value);
            return this;
        }

        /**
         * 是否允许出现 Object 作为 Key? (除 Null 外).
         * 默认情况下, 所有加载的键都通过 Object.toString() 转换为字符串, (例如 5 -> "5").
         * @param value DefaultValue: false
         */
        public Builder setAllowObjectKeys(boolean value) {
            this.allowObjectKey = value;
            return this;
        }

        /**
         * 限制解析的最大代码点数量, 防止恶意超大文件耗尽内存.
         * 设为 0 表示不限制.
         * @param value DefaultValue: 0 (不限制)
         */
        public Builder setCodePointLimit(int value) {
            this.loadSettingsBuilder.setCodePointLimit(value);
            return this;
        }

        /**
         * 限制集合中允许的最大别名数量, 防止别名炸弹攻击.
         * @param value DefaultValue: 50
         */
        public Builder setMaxAliasesForCollections(int value) {
            this.loadSettingsBuilder.setMaxAliasesForCollections(value);
            return this;
        }

        // ──────────── DumpSettings ────────────

        /**
         * 设置 Dump 时的默认 Style.
         * @param flowStyle DefaultValue: BLOCK
         */
        public Builder setDefaultFlowStyle(FlowStyle flowStyle) {
            this.dumpSettingsBuilder.setDefaultFlowStyle(flowStyle);
            return this;
        }

        /**
         * 设置 Scalar 的默认标量样式.
         * @param scalarStyle DefaultValue: PLAIN
         */
        public Builder setScalarStyle(ScalarStyle scalarStyle) {
            this.dumpSettingsBuilder.setDefaultScalarStyle(scalarStyle);
            return this;
        }

        /**
         * 设置缩进宽度 (空格数).
         * @param indent DefaultValue: 2
         */
        public Builder setIndent(int indent) {
            this.dumpSettingsBuilder.setIndent(indent);
            return this;
        }

        /**
         * 设置指示符 (如 -, ?, :) 的缩进宽度.
         * @param indent DefaultValue: 0
         */
        public Builder setIndicatorIndent(int indent) {
            this.dumpSettingsBuilder.setIndicatorIndent(indent);
            return this;
        }

        /**
         * 缩进计数是否包含指示符长度.
         * 启用后, 内容缩进会与指示符对齐而非额外缩进.
         * @param indentWithIndicator DefaultValue: false
         */
        public Builder setIndentWithIndicator(boolean indentWithIndicator) {
            this.dumpSettingsBuilder.setIndentWithIndicator(indentWithIndicator);
            return this;
        }

        /**
         * 是否拆分超长行.
         * @param split DefaultValue: true
         */
        public Builder setSplitLines(boolean split) {
            this.dumpSettingsBuilder.setSplitLines(split);
            return this;
        }

        /**
         * 设置每行的最大宽度, 超出后自动换行.
         * @param width DefaultValue: 80
         */
        public Builder setWidth(int width) {
            this.dumpSettingsBuilder.setWidth(width);
            return this;
        }

        /**
         * 设置简单 Key 的最大长度.
         * 超过此长度的 Key 将自动转为 ? 块语法以避免歧义.
         * @param maxSimpleKeyLength DefaultValue: 128
         */
        public Builder setMaxSimpleKeyLength(int maxSimpleKeyLength) {
            this.dumpSettingsBuilder.setMaxSimpleKeyLength(maxSimpleKeyLength);
            return this;
        }

        /**
         * 是否在文档开头显式输出 --- 标记.
         * @param explicitStart DefaultValue: false
         */
        public Builder setExplicitStart(boolean explicitStart) {
            this.dumpSettingsBuilder.setExplicitStart(explicitStart);
            return this;
        }

        /**
         * 是否在文档结尾显式输出 ... 标记.
         * @param explicitEnd DefaultValue: false
         */
        public Builder setExplicitEnd(boolean explicitEnd) {
            this.dumpSettingsBuilder.setExplicitEnd(explicitEnd);
            return this;
        }

        /**
         * 设置换行符, 例如 \n (Unix) 或 \r\n (Windows).
         * @param lineBreak DefaultValue: \n
         */
        public Builder setLineBreak(String lineBreak) {
            this.dumpSettingsBuilder.setBestLineBreak(lineBreak);
            return this;
        }

        /**
         * 是否允许多行 Flow 样式的序列/映射.
         * @param multiLineFlow DefaultValue: false
         */
        public Builder setMultiLineFlow(boolean multiLineFlow) {
            this.dumpSettingsBuilder.setMultiLineFlow(multiLineFlow);
            return this;
        }

        /**
         * 设置无法直接打印的字符的处理方式.
         * @param style DefaultValue: ESCAPE
         */
        public Builder setNonPrintableStyle(NonPrintableStyle style) {
            this.dumpSettingsBuilder.setNonPrintableStyle(style);
            return this;
        }

        /**
         * Dump 时是否解引用锚点/别名, 展开为内联内容.
         * 传入 null 表示由 SnakeYAML 自行决定.
         * @param dereferenceAliases DefaultValue: null (自动)
         */
        public Builder setDereferenceAliases(Boolean dereferenceAliases) {
            this.dumpSettingsBuilder.setDereferenceAliases(dereferenceAliases);
            return this;
        }

        /**
         * 构建 SparrowYaml 实例.
         */
        public SparrowYaml build() {
            return new SparrowYaml(
                    this.loadSettingsBuilder.build(),
                    this.dumpSettingsBuilder.build(),
                    this.allowObjectKey,
                    this.autoSerializerMode
            );
        }
    }

}
