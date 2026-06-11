package net.momirealms.sparrow.yaml;

import net.momirealms.sparrow.yaml.exception.InvalidNodeException;
import net.momirealms.sparrow.yaml.exception.MissingNodeException;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.AfterComment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.InlineComment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlConstructor;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlProperty;
import net.momirealms.sparrow.yaml.mapper.YamlMapper;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
import net.momirealms.sparrow.yaml.node.SectionNode;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.upgrade.YamlUpgradePipeline;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparrowYamlAnnotationTest {

    @Configuration
    public static class TestConfig {
        @Comment("This is a host")
        private String host = "127.0.0.1";

        @Comment({"Port number", "Must be > 1024"})
        @YamlProperty("server-port")
        private int port = 8080;

        @InlineComment("List of users")
        @AfterComment("Generated users section")
        private List<String> users = List.of("admin", "guest");

        @Comment("Nested Config")
        private NestedConfig nested = new NestedConfig();

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public List<String> getUsers() { return users; }
        public void setUsers(List<String> users) { this.users = users; }
        public NestedConfig getNested() { return nested; }
        public void setNested(NestedConfig nested) { this.nested = nested; }
    }

    public static class NestedConfig {
        @Comment("Enable something")
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    @Configuration
    public static class FormattingConfig {
        @Comment("Forces locale")
        private Locale locale = null;

        @BlankLineBefore
        @Comment("Debug")
        private NestedConfig debug = new NestedConfig();

        public Locale getLocale() { return locale; }
        public NestedConfig getDebug() { return debug; }
    }

    @Configuration
    public static class VersionedConfig {
        @YamlProperty("config-version")
        private String version = "2";

        private String value = "default";

        private String added = "created";

        public String getVersion() { return version; }
        public String getValue() { return value; }
        public String getAdded() { return added; }
    }

    public static class InheritedBaseConfig {
        @Comment("Base host")
        protected String host = "127.0.0.1";

        @BlankLineBefore
        @Comment("Base locale")
        protected Locale locale = Locale.US;

        public String getHost() { return host; }
        public Locale getLocale() { return locale; }
    }

    @Configuration
    public static class InheritedConfig extends InheritedBaseConfig {
        @Comment("Child port")
        private int port = 25565;

        public int getPort() { return port; }
    }

    @Configuration
    public static class ImmutableConfig {
        @YamlProperty("server-name")
        @Comment("Immutable name")
        private final String name;

        private final int limit;

        @YamlConstructor
        public ImmutableConfig(
                @YamlProperty("server-name") String name,
                @YamlProperty("limit") int limit
        ) {
            this.name = name;
            this.limit = limit;
        }

        public String getName() { return name; }
        public int getLimit() { return limit; }
    }

    @Configuration
    public static class RequiredConstructorConfig {
        private final int port;

        @YamlConstructor
        public RequiredConstructorConfig(@YamlProperty("server-port") int port) {
            this.port = port;
        }

        public int getPort() { return port; }
    }

    @Configuration
    public record RecordConfig(
            @Comment("Record host") String host,
            @InlineComment("Record port")
            @AfterComment("Generated record port") int port
    ) {
    }

    @Test
    void testLoadObjectAndInjectComments() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlMapperFactory factory = YamlMapperFactory.builder().sparrowYaml(sparrowYaml).build();
        YamlMapper<TestConfig> mapper = factory.create(TestConfig.class, TestConfig::new);

        TestConfig config = new TestConfig();
        config.setHost("192.168.1.1");
        
        Path tempFile = Files.createTempFile("test_config", ".yml");
        mapper.save(tempFile, config);
        
        String yamlString = Files.readString(tempFile);
        System.out.println(yamlString);

        assertTrue(yamlString.contains("# This is a host"));
        assertTrue(yamlString.contains("host: 192.168.1.1"));
        
        assertTrue(yamlString.contains("# Port number"));
        assertTrue(yamlString.contains("# Must be > 1024"));
        assertTrue(yamlString.contains("server-port: 8080"));
        
        assertTrue(yamlString.contains("# Nested Config"));
        assertTrue(yamlString.contains("nested:"));
        assertTrue(yamlString.contains("enabled: true"));
        
        assertTrue(yamlString.contains("users:"));
        assertTrue(yamlString.contains("# Generated users section"));
    }

    @Test
    void should_SaveNewTestConfigIntoResourcesDirectory() throws Exception {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlMapperFactory factory = YamlMapperFactory.builder().sparrowYaml(sparrowYaml).build();
        YamlMapper<TestConfig> mapper = factory.create(TestConfig.class, TestConfig::new);

        withMissingResourcePath("generated-test-config.yml", configPath -> {
            TestConfig config = new TestConfig();
            config.setHost("resources-host");

            mapper.save(configPath, config);

            assertTrue(Files.exists(configPath));
            String saved = Files.readString(configPath).replace("\r\n", "\n");
            assertTrue(saved.contains("# This is a host\nhost: resources-host"), saved);
            assertTrue(saved.contains("# Port number\n# Must be > 1024\nserver-port: 8080"), saved);
            assertTrue(saved.contains("users:"), saved);
            assertTrue(saved.contains("# Nested Config\nnested:"), saved);
        });
    }

    @Test
    void should_ThrowInvalidNodeException_When_ResourceTestConfigContainsWrongType() throws Exception {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlMapperFactory factory = YamlMapperFactory.builder().sparrowYaml(sparrowYaml).build();
        YamlMapper<TestConfig> mapper = factory.create(TestConfig.class, TestConfig::new);

        withResourceFile("invalid-test-config.yml", """
                host: "resource"
                server-port:
                  nested: true
                users:
                  - "admin"
                """, configPath -> {
            InvalidNodeException failure = assertThrows(InvalidNodeException.class, () -> mapper.load(configPath));

            assertEquals(Route.from("server-port"), failure.path());
            assertEquals(SectionNode.class, failure.actualType());
            assertEquals(Integer.class, failure.targetType());
            assertEquals("Invalid YAML value at path \"server-port\": actual Map, expected Integer", failure.getMessage());
        });
    }

    @Test
    void should_ThrowMissingNodeException_When_ResourceConstructorConfigMissesRequiredValue() throws Exception {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlMapperFactory factory = YamlMapperFactory.builder().sparrowYaml(sparrowYaml).build();
        YamlMapper<RequiredConstructorConfig> mapper = factory.create(
                RequiredConstructorConfig.class,
                () -> new RequiredConstructorConfig(8080)
        );

        withResourceFile("missing-required-constructor-config.yml", """
                unused: true
                """, configPath -> {
            MissingNodeException failure = assertThrows(MissingNodeException.class, () -> mapper.load(configPath));

            assertEquals("server-port", failure.key());
            assertEquals(Route.from("server-port"), failure.path());
            assertEquals(Integer.class, failure.targetType());
            assertEquals("Missing YAML value 'server-port' at path \"server-port\", expected Integer", failure.getMessage());
        });
    }

    @Test
    void should_PreserveLocalComments_When_ResourceTestConfigIsSavedAgain() throws Exception {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlMapperFactory factory = YamlMapperFactory.builder().sparrowYaml(sparrowYaml).build();
        YamlMapper<TestConfig> mapper = factory.create(TestConfig.class, TestConfig::new);

        withResourceFile("commented-test-config.yml", """
                # Local host comment
                host: "resource"
                # Local port comment
                server-port: 9090
                # Local users comment
                users:
                  - "local"
                nested:
                  # Local nested enabled comment
                  enabled: false
                """, configPath -> {
            YamlMapper.Result<TestConfig> loaded = mapper.load(configPath);

            loaded.value().host = "new resource";
            mapper.save(configPath, loaded.document(), loaded.value());

            String saved = Files.readString(configPath).replace("\r\n", "\n");
            assertTrue(saved.contains("# Local host comment\nhost: new resource"), saved);
            assertTrue(saved.contains("# Local port comment\nserver-port: 9090"), saved);
            assertTrue(saved.contains("# Local users comment\nusers:"), saved);
            assertTrue(saved.contains("# Local nested enabled comment\n  enabled: false"), saved);
        });
    }

    @Test
    void should_PreserveLocalCommentsOnUnchangedNodes_When_ResourceConfigIsUpgraded() throws Exception {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlMapperFactory factory = YamlMapperFactory.builder()
                .sparrowYaml(sparrowYaml)
                .upgradePipeline(YamlUpgradePipeline.builder().build())
                .build();
        YamlMapper<VersionedConfig> mapper = factory.create(VersionedConfig.class, VersionedConfig::new);

        withResourceFile("commented-versioned-config.yml", """
                config-version: 1
                # Local value comment
                value: local
                # Local legacy comment
                legacy: old
                """, configPath -> {
            VersionedConfig config = mapper.load(configPath).value();
            String saved = Files.readString(configPath).replace("\r\n", "\n");

            assertEquals("2", config.getVersion());
            assertEquals("local", config.getValue());
            assertEquals("created", config.getAdded());
            assertTrue(saved.contains("# Local value comment\nvalue: local"), saved);
            assertTrue(saved.contains("added: created"), saved);
            assertFalse(saved.contains("legacy:"), saved);
        });
    }

    @Test
    void testDeserialize() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlMapperFactory factory = YamlMapperFactory.builder().sparrowYaml(sparrowYaml).build();
        YamlMapper<TestConfig> mapper = factory.create(TestConfig.class, TestConfig::new);

        String yamlContent = """
                host: "localhost"
                server-port: 9090
                users:
                  - "test1"
                  - "test2"
                """;
        
        Path tempFile = Files.createTempFile("test_deserialize", ".yml");
        Files.writeString(tempFile, yamlContent);
        
        YamlMapper.Result<TestConfig> config = mapper.load(tempFile);
        
        assertNotNull(config);
        assertEquals("localhost", config.value().getHost());
        assertEquals(9090, config.value().getPort());
        assertEquals(List.of("test1", "test2"), config.value().getUsers());
    }

    @Test
    void should_WriteEmptyScalarAndBlankLine_When_UsingFormattingAnnotations() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlMapperFactory factory = YamlMapperFactory.builder().sparrowYaml(sparrowYaml).build();
        YamlMapper<FormattingConfig> mapper = factory.create(FormattingConfig.class, FormattingConfig::new);

        Path tempFile = Files.createTempFile("formatting_config", ".yml");
        mapper.save(tempFile, new FormattingConfig());

        String yamlString = Files.readString(tempFile).replace("\r\n", "\n");

        assertFalse(yamlString.contains("locale:"));
        assertTrue(yamlString.contains("# Debug\ndebug:"));

        YamlMapper.Result<FormattingConfig> loaded = mapper.load(tempFile);
        assertNotNull(loaded);
        assertNull(loaded.value().getLocale());
    }

    @Test
    void should_ApplyParentFieldComments_When_SavingInheritedConfig() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlMapperFactory factory = YamlMapperFactory.builder().sparrowYaml(sparrowYaml).build();
        YamlMapper<InheritedConfig> mapper = factory.create(InheritedConfig.class, InheritedConfig::new);

        Path tempFile = Files.createTempFile("inherited_config", ".yml");
        mapper.save(tempFile, new InheritedConfig());

        String yamlString = Files.readString(tempFile).replace("\r\n", "\n");

        assertTrue(yamlString.contains("# Base host\nhost: 127.0.0.1"));
        assertTrue(yamlString.contains("\n\n# Base locale\nlocale: en_US"));
        assertTrue(yamlString.contains("# Child port\nport: 25565"));
        assertTrue(yamlString.indexOf("host:") < yamlString.indexOf("port:"), "父类字段应排在子类字段之前");

        Files.writeString(tempFile, """
                host: "0.0.0.0"
                locale: zh_CN
                port: 24454
                """);

        InheritedConfig loaded = mapper.load(tempFile).value();
        assertEquals("0.0.0.0", loaded.getHost());
        assertEquals(new Locale("zh", "CN"), loaded.getLocale());
        assertEquals(24454, loaded.getPort());
    }

    @Test
    void should_AlwaysReturnNewResult_When_LoadIsCalled() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlMapperFactory factory = YamlMapperFactory.builder().sparrowYaml(sparrowYaml).build();
        YamlMapper<TestConfig> mapper = factory.create(TestConfig.class, TestConfig::new);

        Path tempFile = Files.createTempFile("no_cache_config", ".yml");
        Files.writeString(tempFile, """
                host: "fresh"
                server-port: 9090
                users:
                  - "test"
                """);

        YamlMapper.Result<TestConfig> first = mapper.load(tempFile);
        YamlMapper.Result<TestConfig> second = mapper.load(tempFile);

        assertNotSame(first, second, "每次 load 都应返回新的 Result 对象（无缓存）");
        assertNotSame(first.value(), second.value(), "每次 load 都应返回新的配置实例");
        assertEquals("fresh", first.value().getHost());
        assertEquals("fresh", second.value().getHost());
    }

    @Test
    void should_LoadUpdatedContent_When_FileIsModified() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlMapperFactory factory = YamlMapperFactory.builder().sparrowYaml(sparrowYaml).build();
        YamlMapper<TestConfig> mapper = factory.create(TestConfig.class, TestConfig::new);

        Path tempFile = Files.createTempFile("changed_config", ".yml");
        Files.writeString(tempFile, """
                host: "before"
                server-port: 9090
                """);

        TestConfig first = mapper.load(tempFile).value();
        Files.writeString(tempFile, """
                host: "after-with-different-size"
                server-port: 9091
                users:
                  - "changed"
                """);

        TestConfig second = mapper.load(tempFile).value();

        assertNotSame(first, second, "文件修改后 load 应返回新的配置对象");
        assertEquals("after-with-different-size", second.getHost());
        assertEquals(9091, second.getPort());
        assertEquals(List.of("changed"), second.getUsers());
    }

    @Test
    void should_ReturnBothDocumentAndInstance_When_LoadIsCalled() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlMapperFactory factory = YamlMapperFactory.builder().sparrowYaml(sparrowYaml).build();
        YamlMapper<TestConfig> mapper = factory.create(TestConfig.class, TestConfig::new);

        Path tempFile = Files.createTempFile("result_config", ".yml");
        Files.writeString(tempFile, """
                host: "with-doc"
                server-port: 9092
                """);

        YamlMapper.Result<TestConfig> result = mapper.load(tempFile);

        assertNotNull(result);
        assertNotNull(result.document(), "Result 应包含 YamlDocument");
        assertNotNull(result.value(), "Result 应包含配置实例");
        assertEquals("with-doc", result.value().getHost());
        assertEquals(9092, result.value().getPort());
    }

    @Test
    void should_CreateMissingConfig_When_MapperLoadUsesSharedUpgradeFlow() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlMapperFactory factory = YamlMapperFactory.builder().sparrowYaml(sparrowYaml).build();
        YamlMapper<VersionedConfig> mapper = factory.create(VersionedConfig.class, VersionedConfig::new);

        Path tempDir = Files.createTempDirectory("mapper_missing_config");
        Path configPath = tempDir.resolve("nested").resolve("config.yml");

        VersionedConfig config = mapper.load(configPath).value();

        assertTrue(Files.exists(configPath));
        assertEquals("2", config.getVersion());
        assertEquals("default", config.getValue());
        assertEquals("created", config.getAdded());
    }

    @Test
    void should_UpgradeExistingConfig_When_MapperHasUpgradePipeline() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlMapperFactory factory = YamlMapperFactory.builder()
                .sparrowYaml(sparrowYaml)
                .upgradePipeline(YamlUpgradePipeline.builder().build())
                .build();
        YamlMapper<VersionedConfig> mapper = factory.create(VersionedConfig.class, VersionedConfig::new);

        Path configPath = Files.createTempFile("mapper_upgrade_config", ".yml");
        Files.writeString(configPath, """
                config-version: 1
                value: local
                legacy: old
                """);

        VersionedConfig config = mapper.load(configPath).value();
        YamlDocument savedDocument = sparrowYaml.load(configPath);

        assertEquals("2", config.getVersion());
        assertEquals("local", config.getValue());
        assertEquals("created", config.getAdded());
        assertEquals("2", savedDocument.getNodeOrNull("config-version").value().toString());
        assertEquals("local", savedDocument.getNodeOrNull("value").value());
        assertEquals("created", savedDocument.getNodeOrNull("added").value());
        assertNull(savedDocument.getNodeOrNull("legacy"));
    }

    @Test
    void should_CreateBackup_When_MapperBackupOnUpgradeIsEnabled() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlMapperFactory factory = YamlMapperFactory.builder()
                .sparrowYaml(sparrowYaml)
                .upgradePipeline(YamlUpgradePipeline.builder().build())
                .backupPathResolver(path -> path.resolveSibling(path.getFileName() + ".bak"))
                .backupOnUpgrade(true)
                .build();
        YamlMapper<VersionedConfig> mapper = factory.create(VersionedConfig.class, VersionedConfig::new);

        Path configPath = Files.createTempFile("mapper_backup_config", ".yml");
        Path backupPath = configPath.resolveSibling(configPath.getFileName() + ".bak");
        Files.writeString(configPath, """
                config-version: 1
                value: local
                legacy: old
                """);

        VersionedConfig config = mapper.load(configPath).value();
        String backup = Files.readString(backupPath);
        YamlDocument savedDocument = sparrowYaml.load(configPath);

        assertEquals("2", config.getVersion());
        assertTrue(Files.exists(backupPath));
        assertTrue(backup.contains("config-version: 1"));
        assertTrue(backup.contains("legacy: old"));
        assertEquals("2", savedDocument.getNodeOrNull("config-version").value().toString());
        assertNull(savedDocument.getNodeOrNull("legacy"));
    }

    @Test
    void should_NotCreateBackup_When_MapperBackupOnUpgradeIsEnabledButVersionIsCurrent() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlMapperFactory factory = YamlMapperFactory.builder()
                .sparrowYaml(sparrowYaml)
                .upgradePipeline(YamlUpgradePipeline.builder().build())
                .backupPathResolver(path -> path.resolveSibling(path.getFileName() + ".bak"))
                .backupOnUpgrade(true)
                .build();
        YamlMapper<VersionedConfig> mapper = factory.create(VersionedConfig.class, VersionedConfig::new);

        Path configPath = Files.createTempFile("mapper_current_backup_config", ".yml");
        Path backupPath = configPath.resolveSibling(configPath.getFileName() + ".bak");
        Files.writeString(configPath, """
                config-version: 2
                value: local
                legacy: keep
                """);

        VersionedConfig config = mapper.load(configPath).value();

        assertEquals("2", config.getVersion());
        assertFalse(Files.exists(backupPath));
        assertTrue(Files.readString(configPath).contains("legacy: keep"));
    }

    @Test
    void should_NotRewriteSameVersionConfig_When_MapperHasUpgradePipeline() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlMapperFactory factory = YamlMapperFactory.builder()
                .sparrowYaml(sparrowYaml)
                .upgradePipeline(YamlUpgradePipeline.builder().build())
                .build();
        YamlMapper<VersionedConfig> mapper = factory.create(VersionedConfig.class, VersionedConfig::new);

        Path configPath = Files.createTempFile("mapper_same_version_config", ".yml");
        Files.writeString(configPath, """
                config-version: 2
                value: local
                legacy: keep
                """);

        VersionedConfig config = mapper.load(configPath).value();
        String saved = Files.readString(configPath);

        assertEquals("2", config.getVersion());
        assertEquals("local", config.getValue());
        assertEquals("created", config.getAdded());
        assertTrue(saved.contains("legacy: keep"));
        assertFalse(saved.contains("added:"));
    }

    @Test
    void should_CreateMissingImmutableConfig_When_DefaultSupplierIsProvided() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlMapperFactory factory = YamlMapperFactory.builder().sparrowYaml(sparrowYaml).build();
        YamlMapper<ImmutableConfig> mapper = factory.create(
                ImmutableConfig.class,
                () -> new ImmutableConfig("supplier-default", 32)
        );

        Path tempFile = Files.createTempFile("immutable_default_config", ".yml");
        Files.delete(tempFile);

        ImmutableConfig config = mapper.load(tempFile).value();
        String saved = Files.readString(tempFile);

        assertEquals("supplier-default", config.getName());
        assertEquals(32, config.getLimit());
        assertTrue(saved.contains("server-name: supplier-default"));
        assertTrue(saved.contains("limit: 32"));
    }

    @Test
    void testRuntimeMapperHandlesImmutableConfig() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlMapperFactory factory = YamlMapperFactory.builder().sparrowYaml(sparrowYaml).build();
        YamlMapper<ImmutableConfig> mapper = factory.create(ImmutableConfig.class, () -> new ImmutableConfig("default", 0));

        String yamlContent = """
                server-name: "runtime"
                limit: 64
                """;

        Path tempFile = Files.createTempFile("immutable_config", ".yml");
        Files.writeString(tempFile, yamlContent);

        ImmutableConfig config = mapper.load(tempFile).value();

        assertNotNull(config);
        assertEquals("runtime", config.getName());
        assertEquals(64, config.getLimit());

        mapper.save(tempFile, config);
        String saved = Files.readString(tempFile);
        assertTrue(saved.contains("# Immutable name"));
        assertTrue(saved.contains("server-name: runtime"));
    }

    @Test
    void should_ApplyRecordComponentComments_When_SavingRecordConfig() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlMapperFactory factory = YamlMapperFactory.builder().sparrowYaml(sparrowYaml).build();
        YamlMapper<RecordConfig> mapper = factory.create(RecordConfig.class, () -> new RecordConfig("default", 25565));

        Path tempFile = Files.createTempFile("record_config", ".yml");
        mapper.save(tempFile, new RecordConfig("localhost", 24454));

        String yamlString = Files.readString(tempFile).replace("\r\n", "\n");

        assertTrue(yamlString.contains("# Record host\nhost: localhost"), yamlString);
        assertTrue(yamlString.contains("port:"), yamlString);
        assertTrue(yamlString.contains("24454"), yamlString);
        assertTrue(yamlString.contains("# Record port"), yamlString);
        assertTrue(yamlString.contains("# Generated record port"), yamlString);
    }

    private static void withMissingResourcePath(String fileName, ResourcePathTest test) throws Exception {
        withResourcePath(fileName, path -> {
            Files.deleteIfExists(path);
            test.run(path);
        });
    }

    private static void withResourceFile(String fileName, String content, ResourcePathTest test) throws Exception {
        withResourcePath(fileName, path -> {
            Files.writeString(path, content);
            test.run(path);
        });
    }

    private static void withResourcePath(String fileName, ResourcePathTest test) throws Exception {
        Path path = testResourcesDir().resolve(fileName);
        byte[] previousContent = Files.exists(path) ? Files.readAllBytes(path) : null;
        try {
            Files.createDirectories(path.getParent());
            test.run(path);
        } finally {
            if (previousContent == null) {
                Files.deleteIfExists(path);
            } else {
                Files.write(path, previousContent);
            }
        }
    }

    private static Path testResourcesDir() {
        List<Path> candidates = List.of(
                Path.of("src", "test", "resources"),
                Path.of("common", "src", "test", "resources")
        );
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Cannot locate test resources directory");
    }

    @FunctionalInterface
    private interface ResourcePathTest {
        void run(Path path) throws Exception;
    }
}
