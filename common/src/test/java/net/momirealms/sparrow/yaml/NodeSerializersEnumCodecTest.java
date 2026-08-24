package net.momirealms.sparrow.yaml;

import net.momirealms.sparrow.yaml.exception.InvalidNodeException;
import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.NodeSerializers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NodeSerializersEnumCodecTest {
    private static final SparrowYaml YAML = SparrowYaml.builder().build();

    @Test
    void should_ReuseCodecPerEnumClass() {
        NodeSerializer<Normal> codec = NodeSerializers.enumCodec(Normal.class);

        assertSame(codec, NodeSerializers.enumCodec(Normal.class));
        assertNotSame(codec, NodeSerializers.enumCodec(Obfuscated.class));
    }

    @Test
    void should_MatchUppercaseNamesBeforeExactNames() {
        NodeSerializer<Normal> normalCodec = NodeSerializers.enumCodec(Normal.class);
        NodeSerializer<Collision> collisionCodec = NodeSerializers.enumCodec(Collision.class);

        assertEquals(Normal.FIRST_VALUE, ScalarNode.create(YAML, "FIRST_VALUE").get(normalCodec));
        assertEquals(Normal.FIRST_VALUE, ScalarNode.create(YAML, "first_value").get(normalCodec));
        assertEquals(Normal.FIRST_VALUE, ScalarNode.create(YAML, "FiRsT_VaLuE").get(normalCodec));
        assertEquals(Collision.FOO, ScalarNode.create(YAML, "foo").get(collisionCodec));
    }

    @Test
    void should_FallbackToExactNameForObfuscatedEnums() {
        NodeSerializer<Obfuscated> codec = NodeSerializers.enumCodec(Obfuscated.class);

        assertEquals(Obfuscated.a, ScalarNode.create(YAML, "a").get(codec));
        assertEquals(Obfuscated.someValue, ScalarNode.create(YAML, "someValue").get(codec));
    }

    @Test
    void should_KeepInvalidValuesAsInvalidNodeFailures() {
        NodeSerializer<Obfuscated> codec = NodeSerializers.enumCodec(Obfuscated.class);

        InvalidNodeException wrongCase = assertThrows(InvalidNodeException.class, () -> ScalarNode.create(YAML, "A").get(codec));
        InvalidNodeException inexactName = assertThrows(InvalidNodeException.class, () -> ScalarNode.create(YAML, "somevalue").get(codec));

        assertEquals(Obfuscated.class, wrongCase.targetType());
        assertEquals(Obfuscated.class, inexactName.targetType());
    }

    @Test
    void should_EncodeTheRealEnumName() {
        NodeSerializer<Obfuscated> codec = NodeSerializers.enumCodec(Obfuscated.class);

        assertEquals("a", codec.serialize(Obfuscated.a));
        assertEquals("someValue", codec.serialize(Obfuscated.someValue));
    }

    private enum Normal {
        FIRST_VALUE,
        SECOND_VALUE
    }

    private enum Obfuscated {
        a,
        b,
        someValue
    }

    private enum Collision {
        FOO,
        foo
    }
}
