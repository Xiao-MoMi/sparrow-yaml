package net.momirealms.sparrow.yaml.consumer;

import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.mapper.YamlMapper;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.serializer.NodeSerializers;
import net.momirealms.sparrow.yaml.serializer.TypeRef;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import net.momirealms.sparrow.yaml.upgrade.YamlUpgradePipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicApiConsumerTest {

    @Configuration
    record ConsumerConfig(String value) {
    }

    @Test
    void should_CompileAndUseRouteOverloads() throws Exception {
        SparrowYaml yaml = SparrowYaml.create();
        YamlDocument document = yaml.createDocument();
        TypeRef<Integer> integerType = new TypeRef<>() {};
        Supplier<Integer> defaultValueSupplier = () -> 7;

        document.setSubNode("server", Map.of("ports", List.of("25565", "25566")));

        assertEquals(25565, document.getInt(Route.from("server", "ports", 0)));
        assertEquals(25565, document.get(Integer.class, Route.from("server", "ports", 0)));
        assertEquals(25565, document.get(NodeSerializers.INT, Route.from("server", "ports", 0)));
        assertEquals(25565, document.get(integerType, Route.from("server", "ports", 0)));
        assertEquals(List.of(25565, 25566), document.getList(Integer.class, Route.from("server", "ports")));
        assertEquals(List.of("25565", "25566"), document.getStringList(Route.from("server", "ports")));
        assertTrue(document.contains(Route.from("server", "ports", 1)));
        assertEquals(7, document.getIntOrDefault(7, Route.from("missing")));
        assertEquals(7, document.getOrDefault(defaultValueSupplier, Integer.class, Route.from("missing")));
        assertEquals(25566, yaml.load(document.dumpToString()).getInt(Route.from("server", "ports", 1)));
    }

    @Test
    void should_CompileAndUseFileLifecycleConveniences(@TempDir Path tempDir) throws Exception {
        SparrowYaml yaml = SparrowYaml.create();
        YamlMapper<ConsumerConfig> mapper = yaml.mapper(ConsumerConfig.class, () -> new ConsumerConfig("default"));
        Path mapperPath = tempDir.resolve("mapper.yml");

        ConsumerConfig config = mapper.loadValue(mapperPath);
        YamlMapper.Result<ConsumerConfig> result = mapper.load(mapperPath);

        assertEquals("default", config.value());
        assertEquals("default", result.value().value());

        YamlDocument defaults = yaml.load("config-version: 2\nvalue: default\n");
        YamlUpgradePipeline pipeline = YamlUpgradePipeline.builder().build();
        Path upgradePath = tempDir.resolve("upgrade.yml");
        yaml.upgradeFile(upgradePath, defaults, pipeline, false);
        yaml.upgradeFile(upgradePath, defaults, pipeline, (Path) null);
        yaml.upgradeFile(upgradePath, defaults, pipeline, path -> path.resolveSibling("upgrade.yml.bak"));
        assertTrue(Files.exists(upgradePath));

        ClassLoader classLoader = new ClassLoader() {
            @Override
            public InputStream getResourceAsStream(String name) {
                if (name.equals("consumer.yml")) {
                    return new ByteArrayInputStream("source: class-loader\n".getBytes());
                }
                return super.getResourceAsStream(name);
            }
        };
        assertEquals("class-loader", yaml.loadFromResource(classLoader, "consumer.yml").getString(Route.from("source")));
        assertTrue(yaml.loadFromResource(PublicApiConsumerTest.class, "test.yml").contains(Route.from("first-key", "test")));
    }
}
