package net.momirealms.sparrow.yaml.consumer;

import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.serializer.NodeSerializers;
import net.momirealms.sparrow.yaml.serializer.TypeRef;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicApiConsumerTest {

    @Test
    void should_CompileAndUseRouteAndPathElementOverloads() throws Exception {
        SparrowYaml yaml = SparrowYaml.builder().build();
        YamlDocument document = yaml.load("");
        TypeRef<Integer> integerType = new TypeRef<>() {};
        Supplier<Integer> defaultValueSupplier = () -> 7;

        document.setSubNode("server", Map.of("ports", List.of("25565", "25566")));

        assertEquals(25565, document.getInt("server", "ports", 0));
        assertEquals(25565, document.get(Integer.class, "server", "ports", 0));
        assertEquals(25565, document.get(NodeSerializers.INT, "server", "ports", 0));
        assertEquals(25565, document.get(integerType, "server", "ports", 0));
        assertEquals(List.of(25565, 25566), document.getList(Integer.class, "server", "ports"));
        assertEquals(List.of("25565", "25566"), document.getStringList("server", "ports"));
        assertTrue(document.contains("server", "ports", 1));
        assertEquals(7, document.getIntOrDefault(7, "missing"));
        assertEquals(7, document.getOrDefault(defaultValueSupplier, Integer.class, "missing"));
        StringWriter writer = new StringWriter();
        document.save(writer);
        assertEquals(25566, yaml.load(writer.toString()).getInt("server", "ports", 1));
    }
}
