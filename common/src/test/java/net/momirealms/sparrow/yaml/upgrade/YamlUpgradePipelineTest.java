package net.momirealms.sparrow.yaml.upgrade;

import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.exception.InvalidConfigVersionException;
import net.momirealms.sparrow.yaml.exception.PatchValidationException;
import net.momirealms.sparrow.yaml.exception.YamlUpgradeException;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.serializer.NodeSerializers;
import net.momirealms.sparrow.yaml.upgrade.patch.ConverterRule;
import net.momirealms.sparrow.yaml.upgrade.patch.DefaultValueRule;
import net.momirealms.sparrow.yaml.upgrade.patch.Patch;
import net.momirealms.sparrow.yaml.upgrade.patch.PatchContext;
import net.momirealms.sparrow.yaml.upgrade.patch.RelocateRule;
import net.momirealms.sparrow.yaml.upgrade.patch.ValidationRule;
import net.momirealms.sparrow.yaml.upgrade.patch.VersionPatch;
import net.momirealms.sparrow.yaml.upgrade.version.FieldVersionExtractor;
import net.momirealms.sparrow.yaml.upgrade.version.NumberVersionMatcher;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 针对 YamlUpgradePipeline 升级模块的综合测试.
 * 测试用例按功能划分为: 版本提取器测试、基础升级规则测试、忽略路由与合并清理测试、复杂流水线执行测试.
 */
class YamlUpgradePipelineTest {

    private YamlDocument loadDoc(String yaml) throws IOException {
        SparrowYaml sparrowYaml = SparrowYaml.builder().build();
        return new YamlDocument(sparrowYaml, new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    private String dumpDoc(YamlDocument doc) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        doc.save(baos);
        return baos.toString(StandardCharsets.UTF_8);
    }

    @Nested
    class VersionExtractorTests {

        @Test
        void should_ExtractVersion_When_UsingCustomPathAndStringValue() throws IOException {
            // 1. 准备阶段 (Arrange)
            YamlDocument doc = loadDoc("""
                    meta:
                      version: "7"
                    """);
            FieldVersionExtractor extractor = new FieldVersionExtractor(Route.from("meta", "version"));

            // 2. 执行阶段 (Act)
            String version = extractor.extractVersion(doc);

            // 3. 断言阶段 (Assert)
            assertEquals("7", version, "自定义路由的版本提取器应正确获取字符串版本号");
        }

        @Test
        void should_ThrowException_When_VersionNodeIsMissing() throws IOException {
            // 1. 准备阶段 (Arrange)
            YamlDocument doc = loadDoc("name: test");
            FieldVersionExtractor extractor = new FieldVersionExtractor(); // 默认 config-version

            // 2 & 3. 执行并断言 (Act & Assert)
            assertThrows(InvalidConfigVersionException.class, () -> extractor.extractVersion(doc), "缺失版本节点时应报错");
        }

        @Test
        void should_AllowNonNumericString_When_VersionIsString() throws IOException {
            // 1. 准备阶段 (Arrange)
            YamlDocument doc = loadDoc("config-version: abc");
            FieldVersionExtractor extractor = new FieldVersionExtractor();

            // 2. 执行阶段 (Act)
            String version = extractor.extractVersion(doc);

            // 3. 断言阶段 (Assert)
            assertEquals("abc", version, "提取器应允许解析非数字版本的字符串");
        }

        @Test
        void should_ThrowException_When_VersionNodeIsNotScalar() throws IOException {
            // 1. 准备阶段 (Arrange)
            YamlDocument doc = loadDoc("""
                    config-version:
                      nested: 1
                    """);
            FieldVersionExtractor extractor = new FieldVersionExtractor();

            // 2 & 3. 执行并断言 (Act & Assert)
            assertThrows(InvalidConfigVersionException.class, () -> extractor.extractVersion(doc), "版本号不能为 Section 嵌套节点");
        }

        @Test
        void should_WriteVersion_When_UsingFieldVersionExtractor() throws IOException {
            YamlDocument doc = loadDoc("name: test");
            FieldVersionExtractor extractor = new FieldVersionExtractor();

            extractor.writeVersion(doc, "8");

            assertEquals("8", doc.getNodeOrNull("config-version").value());
        }

        @Test
        void should_ReportWhetherUpgradeIsNeeded_When_ComparingVersions() throws IOException {
            YamlDocument localDoc = loadDoc("config-version: 1\nvalue: local");
            YamlDocument sameVersionDoc = loadDoc("config-version: 1\nvalue: default");
            YamlDocument newerVersionDoc = loadDoc("config-version: 2\nvalue: default");
            YamlUpgradePipeline pipeline = YamlUpgradePipeline.builder().build();

            assertFalse(pipeline.needsUpgrade(localDoc, sameVersionDoc));
            assertTrue(pipeline.needsUpgrade(localDoc, newerVersionDoc));
        }
    }

    @Nested
    class UpgradeRuleTests {

        @Test
        void should_CreateDeepParentsAndCopyValue_When_UsingRelocateRule() throws IOException {
            // 1. 准备阶段 (Arrange)
            YamlDocument localDoc = loadDoc("config-version: 1\nflat: moved");
            YamlDocument jarDoc = loadDoc("config-version: 2\ndeep:\n  nested:\n    target: default");

            YamlUpgradePipeline pipeline = YamlUpgradePipeline.builder()
                    .addPatch(new VersionPatch(new NumberVersionMatcher(2), List.of(
                            new RelocateRule(Route.from("flat"), Route.from("deep", "nested", "target"))
                    )))
                    .build();

            // 2. 执行阶段 (Act)
            YamlDocument upgraded = pipeline.upgrade(localDoc, jarDoc);

            // 3. 断言阶段 (Assert)
            assertNull(upgraded.getNodeOrNull("flat"), "源节点应被移除");
            assertEquals("moved", upgraded.getNodeOrNull("deep", "nested", "target").value(), "值应被移动到目标深层路由中");
        }

        @Test
        void should_OverwriteExistingValue_When_UsingConverterRule() throws IOException {
            // 1. 准备阶段 (Arrange)
            YamlDocument localDoc = loadDoc("config-version: 1\nsettings:\n  threshold: 5");
            YamlDocument jarDoc = loadDoc("config-version: 2\nsettings:\n  threshold: 1");

            YamlUpgradePipeline pipeline = YamlUpgradePipeline.builder()
                    .addPatch(new VersionPatch(new NumberVersionMatcher(2), List.of(
                            new ConverterRule(Route.from("settings", "threshold"), node -> 9)
                    )))
                    .build();

            // 2. 执行阶段 (Act)
            YamlDocument upgraded = pipeline.upgrade(localDoc, jarDoc);

            // 3. 断言阶段 (Assert)
            assertEquals(9, upgraded.getNodeOrNull("settings", "threshold").value(), "转换器应成功应用并覆盖旧值");
        }

        @Test
        void should_ProvideValueOnlyWhenMissing_When_UsingDefaultValueRule() throws IOException {
            // 1. 准备阶段 (Arrange)
            YamlDocument localDoc = loadDoc("config-version: 1\nsource: prefix\nexisting: keep");
            YamlDocument jarDoc = loadDoc("config-version: 2\ncomputed: default\nexisting: template");

            YamlUpgradePipeline pipeline = YamlUpgradePipeline.builder()
                    .addPatch(new VersionPatch(new NumberVersionMatcher(2), List.of(
                            new DefaultValueRule(Route.from("computed"), doc -> doc.getNodeOrNull("source").value() + "-value"),
                            new DefaultValueRule(Route.from("existing"), "new-value")
                    )))
                    .build();

            // 2. 执行阶段 (Act)
            YamlDocument upgraded = pipeline.upgrade(localDoc, jarDoc);

            // 3. 断言阶段 (Assert)
            assertEquals("prefix-value", upgraded.getNodeOrNull("computed").value(), "当节点不存在时，动态提供默认值");
            assertEquals("keep", upgraded.getNodeOrNull("existing").value(), "当节点已存在时，不应覆盖旧值");
        }

        @Test
        void should_WrapException_When_ValidationRuleFails() throws IOException {
            // 1. 准备阶段 (Arrange)
            YamlDocument localDoc = loadDoc("config-version: 1\nval: 10");
            YamlDocument jarDoc = loadDoc("config-version: 2\nval: 10");

            ValidationRule validationRule = new ValidationRule((jar, local) -> {
                int val = Integer.parseInt(local.getNodeOrNull(Route.from("val")).value().toString());
                if (val < 20) {
                    throw new PatchValidationException("val must be >= 20");
                }
            });

            YamlUpgradePipeline pipeline = YamlUpgradePipeline.builder()
                    .addPatch(new VersionPatch(new NumberVersionMatcher(2), List.of(validationRule)))
                    .build();

            // 2 & 3. 执行并断言 (Act & Assert)
            YamlUpgradeException ex = assertThrows(YamlUpgradeException.class, () -> pipeline.upgrade(localDoc, jarDoc));
            assertEquals("1", ex.getFromVersion());
            assertEquals("2", ex.getToVersion());
            assertSame(validationRule, ex.getFailedRule());
            assertInstanceOf(PatchValidationException.class, ex.getCause(), "应当包装 ValidationException 抛出");
        }

        @Test
        void should_SharePatchContext_When_MultiplePatchesRun() throws IOException {
            // 1. 准备阶段 (Arrange)
            YamlDocument localDoc = loadDoc("config-version: 1\nsource: copied");
            YamlDocument jarDoc = loadDoc("config-version: 2\ntarget: default");

            Patch storePatch = new Patch() {
                @Override
                public YamlDocument apply(YamlDocument defDoc, YamlDocument localDoc, PatchContext context) {
                    context.put("source-value", localDoc.getNodeOrNull("source").value());
                    return localDoc;
                }

                @Override
                public int getOrder() {
                    return 10;
                }
            };

            Patch readPatch = new Patch() {
                @Override
                public YamlDocument apply(YamlDocument defDoc, YamlDocument localDoc, PatchContext context) {
                    String sourceValue = context.get("source-value", String.class);
                    localDoc.set(Route.from("target"), sourceValue + "-from-context");
                    return localDoc;
                }

                @Override
                public int getOrder() {
                    return 11;
                }
            };

            YamlUpgradePipeline pipeline = YamlUpgradePipeline.builder()
                    .addPatch(new VersionPatch(new NumberVersionMatcher(2), List.of(readPatch, storePatch)))
                    .build();

            // 2. 执行阶段 (Act)
            YamlDocument upgraded = pipeline.upgrade(localDoc, jarDoc);

            // 3. 断言阶段 (Assert)
            assertEquals("copied-from-context", upgraded.getNodeOrNull("target").value(), "后续 Patch 应能读取前序 Patch 存入的上下文数据");
        }
    }

    @Nested
    class MergeAndCleanTests {

        @Test
        void should_PreventMergeAndClean_When_IgnoredRouteIsConfigured() throws IOException {
            // 1. 准备阶段 (Arrange)
            YamlDocument localDoc = loadDoc("""
                    config-version: 1
                    ignored:
                      value: local
                      obsolete: true
                    """);
            YamlDocument jarDoc = loadDoc("""
                    config-version: 2
                    ignored:
                      value: jar
                      added: true
                    """);

            YamlUpgradePipeline pipeline = YamlUpgradePipeline.builder()
                    .deleteRemovedNodes(true)
                    .addIgnoredRoute(Route.from("ignored"))
                    .build();

            // 2. 执行阶段 (Act)
            YamlDocument upgraded = pipeline.upgrade(localDoc, jarDoc);

            // 3. 断言阶段 (Assert)
            assertEquals("local", upgraded.getNodeOrNull("ignored", "value").value(), "不应使用 Jar 覆盖");
            assertEquals(true, upgraded.getNodeOrNull("ignored", "obsolete").value(), "由于被忽略，旧的 obsolete 节点不应被清理");
            assertNull(upgraded.getNodeOrNull("ignored", "added"), "由于被忽略，不应合并 Jar 中的新节点");
        }

        @Test
        void should_NotDeleteOldNodes_When_DeleteRemovedNodesIsFalse() throws IOException {
            // 1. 准备阶段 (Arrange)
            YamlDocument localDoc = loadDoc("config-version: 1\nlegacy: true");
            YamlDocument jarDoc = loadDoc("config-version: 2");

            YamlUpgradePipeline pipeline = YamlUpgradePipeline.builder()
                    .deleteRemovedNodes(false)
                    .build();

            // 2. 执行阶段 (Act)
            YamlDocument upgraded = pipeline.upgrade(localDoc, jarDoc);

            // 3. 断言阶段 (Assert)
            assertEquals(true, upgraded.getNodeOrNull("legacy").value(), "关闭自动清理后，旧节点应被保留");
        }
    }

    @Nested
    class ComplexPipelineTests {

        @Test
        void should_ReturnOriginalInstance_When_VersionsAreEqual() throws IOException {
            // 1. 准备阶段 (Arrange)
            YamlDocument localDoc = loadDoc("config-version: 3\nuntouched: true");
            YamlDocument jarDoc = loadDoc("config-version: 3\nuntouched: false\nadded: yes");

            YamlUpgradePipeline pipeline = YamlUpgradePipeline.builder().build();

            // 2. 执行阶段 (Act)
            YamlDocument upgraded = pipeline.upgrade(localDoc, jarDoc);

            // 3. 断言阶段 (Assert)
            assertSame(localDoc, upgraded, "版本相同时直接返回传入的原文档对象");
            assertEquals(true, upgraded.getNodeOrNull("untouched").value());
            assertNull(upgraded.getNodeOrNull("added"));
        }

        @Test
        void should_ExecuteMultipleRulesInOrder_When_UpgradingFullConfig() throws IOException {
            // 1. 准备阶段 (Arrange)
            YamlDocument localDoc = loadDoc("""
                    config-version: 1
                    
                    # This is database setting
                    database:
                      host: "127.0.0.1"
                      port: 3306
                      old_user: "root"
                    
                    # Test converter (string to list)
                    admins: "Catnies,Momirealms"
                    
                    old_settings_str: "A,B,C"
                    
                    flat_node: "deep_value"
                    """);

            YamlDocument jarDoc = loadDoc("""
                    config-version: 2
                    
                    database:
                      host: "localhost"
                      port: 3306
                      new_username: "admin"
                      password: "password"
                    
                    admins:
                      - "Admin1"
                      
                    new_settings_list:
                      - "A"
                      
                    deep:
                      nested:
                        target_node: "default_deep"
                    """);

            YamlUpgradePipeline build = YamlUpgradePipeline.builder()
                    .updateComments(false)
                    .addPatch(new NumberVersionMatcher(1), patch -> patch
                            .relocate(Route.from("database", "old_user"), Route.from("database", "new_username"))
                            .relocate(Route.from("old_settings_str"), Route.from("new_settings_list"))
                            .relocate(Route.from("flat_node"), Route.from("deep", "nested", "target_node"))
                            .convert(Route.from("admins"), node -> {
                                String value = String.valueOf(node.value());
                                return List.of(value.split(","));
                            })
                            .convert(Route.from("new_settings_list"), node -> {
                                if (node.value() instanceof String str) {
                                    return List.of(str.split(","));
                                }
                                return node.value();
                            }))
                    .build();

            // 2. 执行阶段 (Act)
            YamlDocument upgrade = build.upgrade(localDoc, jarDoc);

            // 3. 断言阶段 (Assert)
            assertEquals("root", upgrade.get(NodeSerializers.STRING, "database", "new_username"), "RelocateRule 应生效");
            assertEquals(List.of("Catnies", "Momirealms"), upgrade.get(NodeSerializers.STRING.listOf(), "admins"), "ConverterRule 应生效");
            assertEquals(List.of("A", "B", "C"), upgrade.get(NodeSerializers.STRING.listOf(), "new_settings_list"), "RelocateRule 结合 ConverterRule 链式调用应生效");
            assertEquals("deep_value", upgrade.get(NodeSerializers.STRING, "deep", "nested", "target_node"), "深度 Relocate 应生效");
        }
    }
}
