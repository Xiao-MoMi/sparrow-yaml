package net.momirealms.sparrow.yaml.consumer;

import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.serializer.NodeSerializers;
import net.momirealms.sparrow.yaml.serializer.TypeRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicApiConsumerTest {

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
}
