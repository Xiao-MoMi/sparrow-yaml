package net.momirealms.sparrow.yaml.exception;

import net.momirealms.sparrow.yaml.upgrade.patch.Patch;
import org.jetbrains.annotations.Nullable;

public class YamlUpgradeException extends RuntimeException {

    private final String fromVersion;
    private final String toVersion;
    private final @Nullable Patch failedRule;

    public YamlUpgradeException(String fromVersion, String toVersion, @Nullable Patch failedRule, String message, Throwable cause) {
        super(message, cause);
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
        this.failedRule = failedRule;
    }

    public YamlUpgradeException(String fromVersion, String toVersion, @Nullable Patch failedRule, String message) {
        super(message);
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
        this.failedRule = failedRule;
    }

    public String getFromVersion() {
        return fromVersion;
    }

    public String getToVersion() {
        return toVersion;
    }

    @Nullable
    public Patch getFailedRule() {
        return failedRule;
    }
}
