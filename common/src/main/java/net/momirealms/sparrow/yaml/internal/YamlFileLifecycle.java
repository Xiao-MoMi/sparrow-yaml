package net.momirealms.sparrow.yaml.internal;

import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.upgrade.UpgradeSchema;
import net.momirealms.sparrow.yaml.upgrade.YamlUpgradePipeline;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class YamlFileLifecycle {
    private YamlFileLifecycle() {
    }

    @NotNull
    public static Result load(
            @NotNull SparrowYaml yaml,
            @NotNull Path path,
            @NotNull Supplier<YamlDocument> defaultDocumentSupplier,
            @Nullable YamlUpgradePipeline pipeline,
            @NotNull UpgradeSchema schema,
            @Nullable Function<Path, Path> backupPathResolver
    ) throws IOException {
        if (!Files.exists(path)) {
            YamlDocument document = defaultDocumentSupplier.get();
            if (pipeline != null) {
                pipeline.writeTargetVersion(document);
            }
            save(path, document);
            return new Result(document, true, false, null);
        }

        YamlDocument localDocument = yaml.load(path);
        if (pipeline == null) {
            return new Result(localDocument, false, false, null);
        }

        YamlDocument defaultDocument = defaultDocumentSupplier.get();
        if (!pipeline.needsUpgrade(localDocument, defaultDocument)) {
            return new Result(localDocument, false, false, null);
        }

        YamlDocument upgradedDocument = pipeline.upgrade(localDocument, defaultDocument, schema);
        Path backupPath = null;
        if (backupPathResolver != null) {
            backupPath = backupPathResolver.apply(path);
            yaml.backupFile(path, backupPath);
        }
        save(path, upgradedDocument);
        return new Result(upgradedDocument, false, true, backupPath);
    }

    private static void save(Path path, YamlDocument document) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        document.save(path);
    }

    public record Result(
            @NotNull YamlDocument document,
            boolean created,
            boolean upgraded,
            @Nullable Path backupPath
    ) {
    }
}
