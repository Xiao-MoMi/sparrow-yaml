package net.momirealms.sparrow.yaml.upgrade.version;

import net.momirealms.sparrow.yaml.YamlDocument;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 使用固定目标版本的版本策略.
 */
public class FixedTargetVersionExtractor implements VersionExtractor {
    private final VersionExtractor delegate;
    private final Supplier<String> targetVersionSupplier;

    public FixedTargetVersionExtractor(VersionExtractor delegate, String targetVersion) {
        this(delegate, () -> targetVersion);
    }

    public FixedTargetVersionExtractor(VersionExtractor delegate, Supplier<String> targetVersionSupplier) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.targetVersionSupplier = Objects.requireNonNull(targetVersionSupplier, "targetVersionSupplier");
    }

    @Override
    public String extractVersion(YamlDocument doc) {
        return delegate.extractVersion(doc);
    }

    @Override
    public String extractTargetVersion(YamlDocument defDoc) {
        return Objects.requireNonNull(targetVersionSupplier.get(), "targetVersionSupplier result");
    }

    @Override
    public void writeVersion(YamlDocument doc, String version) {
        delegate.writeVersion(doc, version);
    }
}
