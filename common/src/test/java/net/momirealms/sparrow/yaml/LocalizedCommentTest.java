package net.momirealms.sparrow.yaml;

import net.momirealms.sparrow.yaml.exception.AutoSerializerException;
import net.momirealms.sparrow.yaml.mapper.ConfigDocumentMapper;
import net.momirealms.sparrow.yaml.mapper.YamlMapper;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.serializer.auto.AutoSerializerMode;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.AfterComment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.InlineComment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlProperty;
import net.momirealms.sparrow.yaml.upgrade.YamlUpgradePipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizedCommentTest {
    @TempDir
    Path tempDir;

    @ParameterizedTest
    @EnumSource(AutoSerializerMode.class)
    void should_IsolateYamlLanguagesAndSelectEachCommentPosition(AutoSerializerMode mode) throws Exception {
        SparrowYaml yaml = SparrowYaml.builder().setAutoSerializerMode(mode).locale(Locale.SIMPLIFIED_CHINESE).build();
        YamlMapper<LocalizedConfig> chinese = yaml.mapper(LocalizedConfig.class, LocalizedConfig::new);
        SparrowYaml japaneseYaml = SparrowYaml.builder().setAutoSerializerMode(mode).locale(Locale.JAPAN).build();
        SparrowYaml frenchYaml = SparrowYaml.builder().setAutoSerializerMode(mode).locale(Locale.FRANCE).build();
        YamlMapper<LocalizedConfig> japanese = japaneseYaml.mapper(LocalizedConfig.class, LocalizedConfig::new);
        YamlMapper<LocalizedConfig> french = YamlMapperFactory.builder().sparrowYaml(frenchYaml)
                .build().create(LocalizedConfig.class, LocalizedConfig::new);
        Path path = this.tempDir.resolve("config.yml");

        chinese.load(path);
        String initial = Files.readString(path);
        assertTrue(initial.contains("# 中文前置\n# 第二行\nvalue: # 中文行内"), initial);
        assertTrue(initial.contains("# 中文后置"), initial);
        assertFalse(initial.contains("fallback"), initial);
        Path reference = this.tempDir.resolve("reference.yml");
        yaml.mapper(ChineseConfig.class, ChineseConfig::new).save(reference, new ChineseConfig());
        assertEquals(Files.readString(reference), initial);
        assertEquals("kept", yaml.load(initial).getString(Route.from("value")));

        japanese.save(path, new LocalizedConfig());
        String translated = Files.readString(path);
        assertTrue(translated.contains("# 日本語\nvalue: # inline fallback"), translated);
        assertTrue(translated.contains("# after fallback"), translated);
        assertFalse(translated.contains("中文"), translated);

        french.save(path, new LocalizedConfig());
        assertTrue(Files.readString(path).contains("# English fallback\nvalue:"));
        chinese.save(path, new LocalizedConfig());
        assertEquals(initial, Files.readString(path));
        assertEquals(Locale.SIMPLIFIED_CHINESE, yaml.locale());
        assertEquals(Locale.JAPAN, japaneseYaml.locale());
    }

    @ParameterizedTest
    @CsvSource({
            "zh-Hans-CN, exact",
            "zh-Hans-SG, script",
            "zh-CN, region",
            "zh-TW, language",
            "zh-Hans-CN-u-nu-hanidec, exact",
            "ja-JP, fallback",
            "und, fallback"
    })
    void should_RankLanguageMatchesIndependentlyOfDeclarationOrder(String tag, String expected) throws Exception {
        SparrowYaml yaml = SparrowYaml.builder().locale(Locale.forLanguageTag(tag)).build();
        Path path = this.tempDir.resolve("ranked.yml");
        yaml.mapper(RankedConfig.class, RankedConfig::new).save(path, new RankedConfig());
        assertEquals("# " + expected + "\nvalue: kept\n", Files.readString(path));
    }

    @Test
    void should_NotMatchAnotherRegionOrRequireFallback() throws Exception {
        SparrowYaml yaml = SparrowYaml.builder().locale(Locale.UK).build();
        Path path = this.tempDir.resolve("region.yml");
        yaml.mapper(RegionOnlyConfig.class, RegionOnlyConfig::new).save(path, new RegionOnlyConfig());
        assertEquals("value: kept\n", Files.readString(path));
        SparrowYaml americanYaml = SparrowYaml.builder().locale(Locale.US).build();
        americanYaml.mapper(RegionOnlyConfig.class, RegionOnlyConfig::new).save(path, new RegionOnlyConfig());
        assertEquals("# US only\nvalue: kept\n", Files.readString(path));
    }

    @ParameterizedTest
    @EnumSource(AutoSerializerMode.class)
    void should_LocalizeInheritedNestedAndRecordComments(AutoSerializerMode mode) throws Exception {
        SparrowYaml yaml = SparrowYaml.builder().setAutoSerializerMode(mode).locale(Locale.CHINA).build();
        Path path = this.tempDir.resolve("nested.yml");
        yaml.mapper(NestedConfig.class, NestedConfig::new).save(path, new NestedConfig());
        String saved = Files.readString(path);
        assertTrue(saved.contains("# 父类\nparent: kept"), saved);
        assertTrue(saved.contains("# 中文前置"), saved);
        assertTrue(saved.contains("# 记录前置"), saved);
        assertTrue(saved.contains("# 记录行内"), saved);
        assertTrue(saved.contains("# 记录后置"), saved);
        assertFalse(saved.contains("fallback"), saved);
        assertEquals("kept", yaml.mapper(NestedConfig.class, NestedConfig::new).loadValue(path).record.value());
    }

    @Test
    void should_SuppressEmptyTranslationAndKeepUnconditionalBlankLines() throws Exception {
        SparrowYaml yaml = SparrowYaml.builder().locale(Locale.CHINA).build();
        Path path = this.tempDir.resolve("empty.yml");
        Path reference = this.tempDir.resolve("blank.yml");
        yaml.mapper(EmptyConfig.class, EmptyConfig::new).save(path, new EmptyConfig());
        yaml.mapper(BlankConfig.class, BlankConfig::new).save(reference, new BlankConfig());
        assertEquals(Files.readString(reference), Files.readString(path));
        assertTrue(Files.readString(path).contains("\n\n"));
    }

    @Test
    void should_CaptureJvmDefaultWhenBuildingYaml() throws Exception {
        Locale original = Locale.getDefault();
        Locale display = Locale.getDefault(Locale.Category.DISPLAY);
        Locale format = Locale.getDefault(Locale.Category.FORMAT);
        try {
            SparrowYaml.Builder builder = SparrowYaml.builder();
            Locale.setDefault(Locale.JAPAN);
            SparrowYaml yaml = builder.build();
            Locale.setDefault(Locale.FRANCE);
            assertEquals(Locale.JAPAN, yaml.locale());
            Path path = this.tempDir.resolve("default-locale.yml");
            yaml.mapper(LocalizedConfig.class, LocalizedConfig::new).save(path, new LocalizedConfig());
            assertTrue(Files.readString(path).contains("# 日本語"));
            assertEquals(Locale.FRANCE, SparrowYaml.create().locale());
        } finally {
            Locale.setDefault(original);
            Locale.setDefault(Locale.Category.DISPLAY, display);
            Locale.setDefault(Locale.Category.FORMAT, format);
        }
    }

    @Test
    void should_PreserveExistingCommentsAndUseYamlLanguageForMissingOnes() throws Exception {
        SparrowYaml yaml = SparrowYaml.builder().locale(Locale.CHINA).build();
        YamlMapper<LocalizedConfig> mapper = yaml.mapper(LocalizedConfig.class, LocalizedConfig::new);
        Path path = this.tempDir.resolve("existing.yml");
        String local = "# Local comment\nvalue: original\n";
        Files.writeString(path, local);
        YamlMapper.Result<LocalizedConfig> result = mapper.load(path);
        assertEquals(local, Files.readString(path));
        mapper.save(path, result.document(), new LocalizedConfig());
        String saved = Files.readString(path);
        assertTrue(saved.contains("# Local comment"), saved);
        assertFalse(saved.contains("中文前置"), saved);
        assertTrue(saved.contains("# 中文行内"), saved);
        assertTrue(saved.contains("# 中文后置"), saved);
    }

    @Test
    void should_UseYamlLanguageInUpgradeTemplate() throws Exception {
        SparrowYaml yaml = SparrowYaml.builder().locale(Locale.CHINA).build();
        YamlMapper<VersionedConfig> mapper = YamlMapperFactory.builder().sparrowYaml(yaml)
                .upgradePipeline(YamlUpgradePipeline.builder().updateComments(true).build())
                .build().create(VersionedConfig.class, VersionedConfig::new);
        Path path = this.tempDir.resolve("upgrade.yml");
        Files.writeString(path, "config-version: '1'\n# Old comment\nvalue: local\n");
        mapper.load(path);
        String saved = Files.readString(path);
        assertTrue(saved.contains("# 升级中文\nvalue: local"), saved);
        assertFalse(saved.contains("Old comment"), saved);
    }

    @Test
    void should_RejectDuplicateAndMalformedDeclarationsAtMapperCreation() {
        SparrowYaml yaml = SparrowYaml.create();
        AutoSerializerException duplicate = assertThrows(AutoSerializerException.class,
                () -> yaml.mapper(DuplicateConfig.class, DuplicateConfig::new));
        assertTrue(duplicate.getMessage().contains("Duplicate @Comment language 'zh-CN'"));
        assertTrue(duplicate.getMessage().contains("DuplicateConfig.value"));
        assertThrows(AutoSerializerException.class, () -> yaml.mapper(DuplicateFallback.class, DuplicateFallback::new));
        assertThrows(AutoSerializerException.class, () -> yaml.mapper(DuplicateInline.class, DuplicateInline::new));
        assertThrows(AutoSerializerException.class, () -> yaml.mapper(DuplicateAfter.class, DuplicateAfter::new));
        AutoSerializerException invalid = assertThrows(AutoSerializerException.class,
                () -> yaml.mapper(InvalidConfig.class, InvalidConfig::new));
        assertTrue(invalid.getMessage().contains("Invalid @Comment language 'zh-@'"));
    }

    @Test
    void should_KeepLegacyCustomMapperAndConstructorWorking() throws Exception {
        SparrowYaml yaml = SparrowYaml.builder().locale(Locale.JAPAN).build();
        ConfigDocumentMapper<String> custom = new ConfigDocumentMapper<>() {
            @Override
            public YamlDocument toDocument(String value, YamlDocument existing, SparrowYaml owner) {
                YamlDocument document = owner.createDocument();
                document.setSubNode("value", value);
                return document;
            }

            @Override
            public String fromDocument(YamlDocument document, SparrowYaml owner) {
                return document.getString(Route.from("value"));
            }
        };
        YamlMapper<String> mapper = new YamlMapper<>(String.class, yaml, custom, null, false,
                SparrowYaml.defaultBackupPathResolver(), () -> "kept");
        Path path = this.tempDir.resolve("custom.yml");
        mapper.save(path, "kept");
        assertEquals("kept", mapper.loadValue(path));
    }

    @Configuration
    public static class LocalizedConfig {
        @Comment("English fallback")
        @Comment(lang = "zh-CN", value = "中文前置\n第二行")
        @Comment(lang = "ja-JP", value = "日本語")
        @InlineComment("inline fallback")
        @InlineComment(lang = "zh-CN", value = "中文行内")
        @AfterComment("after fallback")
        @AfterComment(lang = "zh-CN", value = "中文后置")
        private String value = "kept";
    }

    @Configuration
    public static class ChineseConfig {
        @Comment("中文前置\n第二行")
        @InlineComment("中文行内")
        @AfterComment("中文后置")
        private String value = "kept";
    }

    @Configuration
    public static class RankedConfig {
        @Comment(lang = "zh", value = "language")
        @Comment("fallback")
        @Comment(lang = "zh-Hans", value = "script")
        @Comment(lang = "ZH_hans_cn", value = "exact")
        @Comment(lang = "zh-CN", value = "region")
        private String value = "kept";
    }

    @Configuration
    public static class RegionOnlyConfig {
        @Comment(lang = "en-US", value = "US only")
        private String value = "kept";
    }

    public static class ParentConfig {
        @Comment("parent fallback")
        @Comment(lang = "zh-CN", value = "父类")
        private String parent = "kept";
    }

    @Configuration
    public static class NestedConfig extends ParentConfig {
        private LocalizedConfig child = new LocalizedConfig();
        private LocalizedRecord record = new LocalizedRecord("kept");
    }

    @Configuration
    public record LocalizedRecord(
            @Comment("record fallback") @Comment(lang = "zh-CN", value = "记录前置")
            @InlineComment("record inline fallback") @InlineComment(lang = "zh-CN", value = "记录行内")
            @AfterComment("record after fallback") @AfterComment(lang = "zh-CN", value = "记录后置") String value
    ) {
    }

    @Configuration
    public static class EmptyConfig {
        private String anchor = "kept";
        @BlankLineBefore(2)
        @Comment("fallback") @Comment(lang = "zh-CN", value = {})
        @InlineComment("fallback") @InlineComment(lang = "zh-CN", value = {})
        @AfterComment("fallback") @AfterComment(lang = "zh-CN", value = {})
        private String value = "kept";
    }

    @Configuration
    public static class BlankConfig {
        private String anchor = "kept";
        @BlankLineBefore(2)
        private String value = "kept";
    }

    @Configuration
    public static class VersionedConfig {
        @YamlProperty("config-version")
        private String version = "2";
        @Comment("upgrade fallback") @Comment(lang = "zh-CN", value = "升级中文")
        private String value = "default";
    }

    @Configuration
    public static class DuplicateConfig {
        @Comment(lang = "zh-CN", value = {}) @Comment(lang = "ZH_cn", value = "duplicate")
        private String value = "kept";
    }

    @Configuration
    public static class DuplicateFallback {
        @Comment("first") @Comment("second")
        private String value = "kept";
    }

    @Configuration
    public static class DuplicateInline {
        @InlineComment("first") @InlineComment("second")
        private String value = "kept";
    }

    @Configuration
    public static class DuplicateAfter {
        @AfterComment("first") @AfterComment("second")
        private String value = "kept";
    }

    @Configuration
    public static class InvalidConfig {
        @Comment(lang = "zh-@", value = "invalid")
        private String value = "kept";
    }
}
