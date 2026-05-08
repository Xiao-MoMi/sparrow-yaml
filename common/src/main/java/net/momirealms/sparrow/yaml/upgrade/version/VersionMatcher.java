package net.momirealms.sparrow.yaml.upgrade.version;

@FunctionalInterface
public interface VersionMatcher {

    // 根据本地版本决定是否应用当前 Patch
    boolean matches(String localVersion);
}
