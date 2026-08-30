package net.momirealms.sparrow.yaml.mapper;

import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.internal.YamlFileLifecycle;
import net.momirealms.sparrow.yaml.upgrade.YamlUpgradePipeline;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final Class<T> clazz;

    public YamlMapper(
            Class<T> clazz,
            SparrowYaml sparrowYaml,
            ConfigDocumentMapper<T> documentMapper,
            YamlUpgradePipeline upgradePipeline,
            boolean backupOnUpgrade,
            Function<Path, Path> backupPathResolver,
            Supplier<T> defaultInstanceSupplier
    ) {
        this.clazz = clazz;
        this.sparrowYaml = sparrowYaml;
        this.documentMapper = documentMapper;
        this.upgradePipeline = upgradePipeline;
        this.backupOnUpgrade = backupOnUpgrade;
        this.backupPathResolver = Objects.requireNonNull(backupPathResolver, "backupPathResolver");
        this.defaultInstanceSupplier = Objects.requireNonNull(defaultInstanceSupplier, "defaultInstanceSupplier");
    }

    public Class<T> clazz() {
        return this.clazz;
    }

    /**
     * 重新读取配置文件, 返回本次加载的文档与配置值.
     * 文件不存在时会用默认实例创建文件, 版本落后时会先升级再返回.
     *
     * @param path 配置文件路径
     * @return 本次加载的文档与配置值
     * @throws IOException 当读取、备份或保存失败时
     */
    @NotNull
    public synchronized Result<T> load(@NotNull Path path) throws IOException {
        DefaultDocumentSupplier defaultDocumentSupplier = new DefaultDocumentSupplier();
        YamlFileLifecycle.Result fileResult = YamlFileLifecycle.load(
                this.sparrowYaml,
                path,
                defaultDocumentSupplier,
                this.upgradePipeline,
                this.documentMapper.dynamicSectionRoutes(),
                this.backupOnUpgrade ? this.backupPathResolver : null
        );
        T loadedInstance = fileResult.created()
                ? defaultDocumentSupplier.value()
                : this.documentMapper.fromDocument(fileResult.document(), this.sparrowYaml);
        return new Result<>(fileResult.document(), loadedInstance);
    }

    /**
     * 重新读取配置文件并直接返回配置值.
     *
     * @param path 配置文件路径
     * @return 本次加载的配置值
     * @throws IOException 当读取、备份或保存失败时
    */
    @NotNull
    public T loadValue(@NotNull Path path) throws IOException {
        return this.load(path).value();
    }

    /**
     * 将对象保存至指定的配置文件中.
     *
     * @param path     配置文件路径
     * @param instance 要保存的配置对象实例
     */
    public synchronized void save(Path path, T instance) throws IOException {
        Objects.requireNonNull(path, "path");
        YamlDocument document = documentMapper.toDocument(instance, null, sparrowYaml);
        if (path.getParent() != null && !Files.exists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }
        document.save(path);
    }

    public synchronized void save(Path path, YamlDocument existing, T instance) throws IOException {
        Objects.requireNonNull(path, "path");
        YamlDocument document = documentMapper.toDocument(instance, existing, sparrowYaml);
        if (path.getParent() != null && !Files.exists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }
        document.save(path);
    }

    private final class DefaultDocumentSupplier implements Supplier<YamlDocument> {
        private T value;

        @Override
        public YamlDocument get() {
            this.value = YamlMapper.this.defaultInstanceSupplier.get();
            return YamlMapper.this.documentMapper.toDocument(this.value, null, YamlMapper.this.sparrowYaml);
        }

        private T value() {
            return this.value;
        }
    }

    public record Result<T>(YamlDocument document, T value) {
    }
}
