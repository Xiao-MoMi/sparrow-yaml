package net.momirealms.sparrow.yaml;

import net.momirealms.sparrow.yaml.serializer.SerializerRegistry;
import net.momirealms.sparrow.yaml.upgrade.YamlUpgradePipeline;
import org.jetbrains.annotations.Nullable;
import org.snakeyaml.engine.v2.api.*;
import org.snakeyaml.engine.v2.representer.StandardRepresenter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;

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

    public SerializerRegistry serializers() {
        return this.serializers;
    }

    /**
     * 读取本地文件, 传入定义文件, 然后尝试更新和保存文件, 最后返回一个完成更新的Yaml文档.
     *
     * @param localFile 本地文件
     * @param defDocument 定义文件
     * @param pipeline 更新管道
     * @param backup 是否要备份文件
     * @return 更新完成的Yaml文档
     */
    public YamlDocument upgradeFile(File localFile, YamlDocument defDocument, YamlUpgradePipeline pipeline, boolean backup) throws IOException {
        Objects.requireNonNull(localFile, "localFile");
        Function<Path, Path> backupPathResolver = backup ? defaultBackupPathResolver() : null;
        return this.upgradeFile(localFile, defDocument, pipeline, backupPathResolver);
    }

    public YamlDocument upgradeFile(File localFile, YamlDocument defDocument, YamlUpgradePipeline pipeline, @Nullable Path backupPath) throws IOException {
        return this.upgradeFile(localFile, defDocument, pipeline, backupPath == null ? null : path -> backupPath);
    }

    public YamlDocument upgradeFile(File localFile, YamlDocument defDocument, YamlUpgradePipeline pipeline, @Nullable Function<Path, Path> backupPathResolver) throws IOException {
        Objects.requireNonNull(localFile, "localFile");
        Objects.requireNonNull(defDocument, "defDocument");
        Objects.requireNonNull(pipeline, "pipeline");

        Path localPath = localFile.toPath();
        Path backupPath = backupPathResolver != null ? backupPathResolver.apply(localPath) : null;
        backupFileIfNeeded(localPath, backupPath);

        YamlDocument localDocument = Files.exists(localPath) ? load(localPath) : null;
        YamlDocument upgradedDocument;
        if (localDocument == null) {
            pipeline.writeTargetVersion(defDocument);
            upgradedDocument = defDocument;
        } else {
            upgradedDocument = pipeline.upgrade(localDocument, defDocument);
        }
        saveDocument(localPath, upgradedDocument);
        return upgradedDocument;
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

    private static void saveDocument(Path path, YamlDocument document) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        document.save(path);
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
        private final DumpSettingsBuilder dumpSettingsBuilder = DumpSettings.builder().setDumpComments(true).setDefaultFlowStyle(org.snakeyaml.engine.v2.common.FlowStyle.BLOCK);
        private boolean allowObjectKey = false;

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
         * 默认情况下, 所有加载的键都通过 Object.toString() 转换为字符串, (例如 5 -> "5").
         * @param value DefaultValue: false
         */
        public Builder setAllowObjectKeys(boolean value) {
            this.allowObjectKey = value;
            return this;
        }

        public SparrowYaml build() {
            return new SparrowYaml(
                    loadSettingsBuilder.build(),
                    dumpSettingsBuilder.build(),
                    allowObjectKey
            );
        }

    }

}
