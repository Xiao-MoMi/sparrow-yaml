package net.momirealms.sparrow.yaml.mapper;

import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.upgrade.YamlUpgradePipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public class YamlMapper<T> {
    private final SparrowYaml sparrowYaml;
    private final ConfigDocumentMapper<T> documentMapper;
    private final YamlUpgradePipeline upgradePipeline;
    private final boolean backupOnUpgrade;
    private final Function<Path, Path> backupPathResolver;
    private final Supplier<T> defaultInstanceSupplier;
    private Path configFilePath;
    private T cachedInstance;
    private long lastModified = -1;
    private long size = -1;

    public YamlMapper(
            Class<T> clazz,
            SparrowYaml sparrowYaml,
            ConfigDocumentMapper<T> documentMapper,
            YamlUpgradePipeline upgradePipeline,
            boolean backupOnUpgrade,
            Function<Path, Path> backupPathResolver,
            Supplier<T> defaultInstanceSupplier
    ) {
        this.sparrowYaml = sparrowYaml;
        this.documentMapper = documentMapper;
        this.upgradePipeline = upgradePipeline;
        this.backupOnUpgrade = backupOnUpgrade;
        this.backupPathResolver = Objects.requireNonNull(backupPathResolver, "backupPathResolver");
        this.defaultInstanceSupplier = Objects.requireNonNull(defaultInstanceSupplier, "defaultInstanceSupplier");
    }

    /**
     * 加载指定的配置文件。
     * 如果文件不存在，会自动使用配置类的默认值创建模板并保存。
     * 如果文件存在，且指定了 upgradePipeline，会根据版本检查是否需要合并升级。
     *
     * @param path 配置文件路径
     * @return 实例化并填充完毕的配置对象
     * @throws IOException 如果出现 IO 异常
     */
    public synchronized T load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.exists(path)) {
            return loadForce(path);
        }

        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        long currentLastModified = attributes.lastModifiedTime().toMillis();
        long currentSize = attributes.size();
        if (cachedInstance != null
                && normalizePath(path).equals(configFilePath)
                && currentLastModified == lastModified
                && currentSize == size) {
            return cachedInstance;
        }

        return loadForce(path);
    }

    /**
     * 强制加载指定的配置文件, 忽略缓存的文件属性.
     * 如果文件不存在, 会使用配置类默认值创建并保存文件.
     * 如果配置了升级管线, 会在版本不一致时执行升级并保存结果.
     *
     * @param path 配置文件路径
     * @return 实例化并填充完毕的配置对象
     * @throws IOException 如果出现 IO 异常
     */
    public synchronized T loadForce(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        // 不存在则直接保存.
        if (!Files.exists(path)) {
            T defaultInstance = this.defaultInstanceSupplier.get();
            YamlDocument defaultDocument = documentMapper.toDocument(defaultInstance, sparrowYaml);
            if (upgradePipeline != null) upgradePipeline.writeTargetVersion(defaultDocument);
            saveDocument(path, defaultDocument);
            updateCache(path, defaultInstance);
            return defaultInstance;
        }
        // 加载本地的配置, 然后尝试升级.
        YamlDocument localDocument = sparrowYaml.load(path);
        if (upgradePipeline != null) {
            T defaultInstance = this.defaultInstanceSupplier.get();
            YamlDocument defDocument = documentMapper.toDocument(defaultInstance, sparrowYaml);
            if (upgradePipeline.needsUpgrade(localDocument, defDocument)) {
                YamlDocument upgradedDocument = upgradePipeline.upgrade(localDocument, defDocument);
                backupBeforeUpgrade(path);
                saveDocument(path, upgradedDocument);
                T upgradedInstance = documentMapper.fromDocument(upgradedDocument, sparrowYaml);
                updateCache(path, upgradedInstance);
                return upgradedInstance;
            }
        }
        // 将文档读取为对象
        T loadedInstance = documentMapper.fromDocument(localDocument, sparrowYaml);
        updateCache(path, loadedInstance);
        return loadedInstance;
    }

    /**
     * 将对象保存至指定的配置文件中.
     *
     * @param path     配置文件路径
     * @param instance 要保存的配置对象实例
     */
    public synchronized void save(Path path, T instance) throws IOException {
        Objects.requireNonNull(path, "path");
        YamlDocument document = documentMapper.toDocument(instance, sparrowYaml);
        if (path.getParent() != null && !Files.exists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }
        document.save(path);
        updateCache(path, instance);
    }

    // 更新配置文件前的备份.
    private void backupBeforeUpgrade(Path path) throws IOException {
        if (!backupOnUpgrade) {
            return;
        }
        Path backupPath = Objects.requireNonNull(backupPathResolver.apply(path), "backupPathResolver result");
        sparrowYaml.backupFile(path, backupPath);
    }

    /**
     * 将文档保存到指定的文件中.
     *
     * @param path      配置文件路径
     * @param document  要保存的配置文档
     */
    private static void saveDocument(Path path, YamlDocument document) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        document.save(path);
    }

    private void updateCache(Path path, T instance) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        this.configFilePath = normalizePath(path);
        this.cachedInstance = instance;
        this.lastModified = attributes.lastModifiedTime().toMillis();
        this.size = attributes.size();
    }

    private static Path normalizePath(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
