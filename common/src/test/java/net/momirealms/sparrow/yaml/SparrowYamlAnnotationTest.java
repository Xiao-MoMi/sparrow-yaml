package net.momirealms.sparrow.yaml;

import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.AfterComment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.InlineComment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlConstructor;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlProperty;
import net.momirealms.sparrow.yaml.mapper.YamlMapper;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
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
        assertEquals("localhost", config.result().getHost());
        assertEquals(9090, config.result().getPort());
        assertEquals(List.of("test1", "test2"), config.result().getUsers());
    }

    @Test
    void should_WriteEmptyScalarAndBlankLine_When_UsingFormattingAnnotations() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlMapperFactory factory = YamlMapperFactory.builder().sparrowYaml(sparrowYaml).build();
        YamlMapper<FormattingConfig> mapper = factory.create(FormattingConfig.class, FormattingConfig::new);

        Path tempFile = Files.createTempFile("formatting_config", ".yml");
        mapper.save(tempFile, new FormattingConfig());

        String yamlString = Files.readString(tempFile).replace("\r\n", "\n");

        assertTrue(yamlString.contains("locale: ''"));
        assertTrue(yamlString.contains("locale: ''\n\n# Debug\ndebug:"));

        YamlMapper.Result<FormattingConfig> loaded = mapper.load(tempFile);
        assertNotNull(loaded);
        assertNull(loaded.result().getLocale());
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

        InheritedConfig loaded = mapper.load(tempFile).result();
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
        assertNotSame(first.result(), second.result(), "每次 load 都应返回新的配置实例");
        assertEquals("fresh", first.result().getHost());
        assertEquals("fresh", second.result().getHost());
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

        TestConfig first = mapper.load(tempFile).result();
        Files.writeString(tempFile, """
                host: "after-with-different-size"
                server-port: 9091
                users:
                  - "changed"
                """);

        TestConfig second = mapper.load(tempFile).result();

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
        assertNotNull(result.result(), "Result 应包含配置实例");
        assertEquals("with-doc", result.result().getHost());
        assertEquals(9092, result.result().getPort());
    }

    @Test
    void should_CreateMissingConfig_When_MapperLoadUsesSharedUpgradeFlow() throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        YamlMapperFactory factory = YamlMapperFactory.builder().sparrowYaml(sparrowYaml).build();
        YamlMapper<VersionedConfig> mapper = factory.create(VersionedConfig.class, VersionedConfig::new);

        Path tempDir = Files.createTempDirectory("mapper_missing_config");
        Path configPath = tempDir.resolve("nested").resolve("config.yml");

        VersionedConfig config = mapper.load(configPath).result();

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

        VersionedConfig config = mapper.load(configPath).result();
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

        VersionedConfig config = mapper.load(configPath).result();
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

        VersionedConfig config = mapper.load(configPath).result();

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

        VersionedConfig config = mapper.load(configPath).result();
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

        ImmutableConfig config = mapper.load(tempFile).result();
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

        ImmutableConfig config = mapper.load(tempFile).result();

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
}
