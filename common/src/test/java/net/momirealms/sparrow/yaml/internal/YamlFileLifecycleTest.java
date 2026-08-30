package net.momirealms.sparrow.yaml.internal;

import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.exception.YamlUpgradeException;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.upgrade.YamlUpgradePipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlFileLifecycleTest {

    @Test
    void should_ReportCreatedWithoutResolvingBackup_When_TargetIsMissing(@TempDir Path tempDir) throws Exception {
        SparrowYaml yaml = SparrowYaml.create();
        Path path = tempDir.resolve("nested").resolve("config.yml");
        YamlDocument defaults = yaml.load("config-version: 2\nvalue: default\n");
        AtomicInteger resolverCalls = new AtomicInteger();

        YamlFileLifecycle.Result result = YamlFileLifecycle.load(
                yaml,
                path,
                () -> defaults,
                YamlUpgradePipeline.builder().build(),
                List.of(),
                target -> {
                    resolverCalls.incrementAndGet();
                    return target.resolveSibling(target.getFileName() + ".bak");
                }
        );

        assertSame(defaults, result.document());
        assertTrue(result.created());
        assertFalse(result.upgraded());
        assertNull(result.backupPath());
        assertEquals(0, resolverCalls.get());
        assertEquals("2", yaml.load(path).getString(Route.from("config-version")));
    }

    @Test
    void should_ReportUnchangedWithoutResolvingBackup_When_VersionIsCurrent(@TempDir Path tempDir) throws Exception {
        SparrowYaml yaml = SparrowYaml.create();
        Path path = tempDir.resolve("config.yml");
        Files.writeString(path, "config-version: 2\nvalue: local\n");
        YamlDocument defaults = yaml.load("config-version: 2\nvalue: default\n");
        AtomicInteger resolverCalls = new AtomicInteger();

        YamlFileLifecycle.Result result = YamlFileLifecycle.load(
                yaml,
                path,
                () -> defaults,
                YamlUpgradePipeline.builder().build(),
                List.of(),
                target -> {
                    resolverCalls.incrementAndGet();
                    return target.resolveSibling(target.getFileName() + ".bak");
                }
        );

        assertFalse(result.created());
        assertFalse(result.upgraded());
        assertNull(result.backupPath());
        assertEquals(0, resolverCalls.get());
        assertEquals("local", result.document().getString(Route.from("value")));
    }

    @Test
    void should_ReportUpgradeAndBackupPath_When_VersionIsOutdated(@TempDir Path tempDir) throws Exception {
        SparrowYaml yaml = SparrowYaml.create();
        Path path = tempDir.resolve("config.yml");
        Path backupPath = tempDir.resolve("backup").resolve("config.yml.bak");
        Files.writeString(path, "config-version: 1\nvalue: local\nlegacy: keep\n");
        YamlDocument defaults = yaml.load("config-version: 2\nvalue: default\nadded: yes\n");

        YamlFileLifecycle.Result result = YamlFileLifecycle.load(
                yaml,
                path,
                () -> defaults,
                YamlUpgradePipeline.builder().build(),
                List.of(),
                target -> backupPath
        );

        assertFalse(result.created());
        assertTrue(result.upgraded());
        assertEquals(backupPath, result.backupPath());
        assertTrue(Files.readString(backupPath).contains("legacy: keep"));
        assertEquals("2", yaml.load(path).getString(Route.from("config-version")));
    }

    @Test
    void should_LeaveFileAndBackupUntouched_When_UpgradeFails(@TempDir Path tempDir) throws Exception {
        SparrowYaml yaml = SparrowYaml.create();
        Path path = tempDir.resolve("config.yml");
        Path backupPath = tempDir.resolve("config.yml.bak");
        String original = "config-version: 1\nvalue: local\n";
        Files.writeString(path, original);
        YamlDocument defaults = yaml.load("config-version: 2\nvalue: default\n");
        AtomicInteger resolverCalls = new AtomicInteger();
        YamlUpgradePipeline pipeline = YamlUpgradePipeline.builder()
                .addPatch(version -> true, builder -> builder.patch((def, local, context) -> {
                    throw new IllegalStateException("failed patch");
                }))
                .build();

        assertThrows(YamlUpgradeException.class, () -> YamlFileLifecycle.load(
                yaml,
                path,
                () -> defaults,
                pipeline,
                List.of(),
                target -> {
                    resolverCalls.incrementAndGet();
                    return backupPath;
                }
        ));

        assertEquals(0, resolverCalls.get());
        assertFalse(Files.exists(backupPath));
        assertEquals(original, Files.readString(path));
    }
}
