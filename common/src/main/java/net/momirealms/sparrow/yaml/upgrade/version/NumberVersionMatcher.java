package net.momirealms.sparrow.yaml.upgrade.version;

public class NumberVersionMatcher implements VersionMatcher {
    private final double patchVersion;

    public NumberVersionMatcher(double patchVersion) {
        this.patchVersion = patchVersion;
    }

    public static NumberVersionMatcher of(double version) {
        return new NumberVersionMatcher(version);
    }

    // 根据数字版本号比大小决定是否应用版本Patch.
    @Override
    public boolean matches(String localVersion) {
        try {
            double localVersionNumber = Double.parseDouble(localVersion);
            return patchVersion >= localVersionNumber;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
