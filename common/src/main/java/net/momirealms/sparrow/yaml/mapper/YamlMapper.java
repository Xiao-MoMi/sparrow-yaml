package net.momirealms.sparrow.yaml.mapper;

import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.upgrade.YamlUpgradePipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

public class YamlMapper<T> {
    private final SparrowYaml sparrowYaml;
    private final ConfigDocumentMapper<T> documentMapper;
    private final YamlUpgradePipeline upgradePipeline;
    private Path configFilePath;
    private T cachedInstance;
    private long lastModified = -1;
    private long size = -1;

    public YamlMapper(Class<T> clazz, SparrowYaml sparrowYaml, ConfigDocumentMapper<T> documentMapper, YamlUpgradePipeline upgradePipeline) {
        this.sparrowYaml = sparrowYaml;
        this.documentMapper = documentMapper;
        this.upgradePipeline = upgradePipeline;
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
        if (!Files.exists(path)) {
            T defaultInstance = documentMapper.fromDocument(sparrowYaml.load(""), sparrowYaml);
            YamlDocument defaultDocument = documentMapper.toDocument(defaultInstance, sparrowYaml);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            defaultDocument.save(path);
            updateCache(path, defaultInstance);
            return defaultInstance;
        }

        YamlDocument localDocument = sparrowYaml.load(path);
        if (upgradePipeline != null) {
            String localVersion = upgradePipeline.getVersionExtractor().extractVersion(localDocument);
            T defaultInstance = documentMapper.fromDocument(sparrowYaml.load(""), sparrowYaml);
            YamlDocument defDocument = documentMapper.toDocument(defaultInstance, sparrowYaml);
            String defVersion = upgradePipeline.getVersionExtractor().extractVersion(defDocument);

            if (!localVersion.equals(defVersion)) {
                // 执行合并升级
                YamlDocument upgradedDocument = upgradePipeline.upgrade(localDocument, defDocument);
                upgradedDocument.save(path);
                T upgradedInstance = documentMapper.fromDocument(upgradedDocument, sparrowYaml);
                updateCache(path, upgradedInstance);
                return upgradedInstance;
            }
        }

        T loadedInstance = documentMapper.fromDocument(localDocument, sparrowYaml);
        updateCache(path, loadedInstance);
        return loadedInstance;
    }

    /**
     * 将对象保存至指定的配置文件中.
     *
     * @param path     配置文件路径
     * @param instance 要保存的配置对象实例
     * @throws IOException 如果出现 IO 异常
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
