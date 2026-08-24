package net.momirealms.sparrow.yaml;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlDocumentLifecycleTest {

    @Test
    void should_ClearExistingState_When_ReloadingEmptyInput() throws IOException {
        SparrowYaml yaml = SparrowYaml.create();
        YamlDocument document = yaml.load("server:\n  host: old\n");

        document.reload(input(""));

        assertTrue(document.isEmptyDocument());
        assertTrue(document.value().isEmpty());
        assertNull(document.getNodeOrNull("server"));
        assertFalse(document.dumpToString().contains("server"));
    }

    @Test
    void should_PreserveExistingState_When_ReloadValidationFails() throws IOException {
        SparrowYaml yaml = SparrowYaml.create();
        YamlDocument document = yaml.load("server:\n  host: old\n");

        assertThrows(RuntimeException.class, () -> document.reload(input("server: [broken")));
        assertEquals("old", document.getString("server", "host"));

        assertThrows(NullPointerException.class, () -> document.reload(input("null: value\n")));
        assertEquals("old", document.getString("server", "host"));
        assertTrue(document.dumpToString().contains("host: old"));
    }

    @Test
    void should_RebindAliases_When_AdoptingReloadedRoot() throws IOException {
        SparrowYaml yaml = SparrowYaml.create();
        YamlDocument document = yaml.load("old: value\n");

        document.reload(input("base: &base\n  value: old\nalias: *base\n"));
        document.getScalarOrNull("base", "value").setValue("new");
        document.getSectionOrNull("base").setSubNode("added", true);

        assertSame(document.getNodeOrNull("base").internalValueNode(), document.getNodeOrNull("alias").internalValueNode());
        assertEquals("new", document.getString("alias", "value"));
        assertTrue(document.getBoolean("alias", "added"));
        assertTrue(document.dumpToString().contains("*"));
    }

    @Test
    void should_ExposeOriginalIOException_When_WriterWriteFails() throws IOException {
        YamlDocument document = SparrowYaml.create().load("key: value\n");
        IOException failure = new IOException("write failed");

        IOException thrown = assertThrows(IOException.class, () -> document.save(new FailingWriter(failure, false)));

        assertSame(failure, thrown);
        assertTrue(document.dumpToString().contains("key: value"));
    }

    @Test
    void should_ExposeOriginalIOException_When_WriterFlushFails() throws IOException {
        YamlDocument document = SparrowYaml.create().load("key: value\n");
        IOException failure = new IOException("flush failed");

        IOException thrown = assertThrows(IOException.class, () -> document.save(new FailingWriter(failure, true)));

        assertSame(failure, thrown);
        assertTrue(document.dumpToString().contains("key: value"));
    }

    @Test
    void should_CreateIndependentDefaultsAndDumpWithoutCheckedIOException() throws IOException {
        SparrowYaml first = SparrowYaml.create();
        SparrowYaml second = SparrowYaml.create();
        YamlDocument document = first.createDocument();

        document.setSubNode("name", "sparrow");
        String dumped = document.dumpToString();
        StringWriter writer = new StringWriter();
        document.save(writer);

        assertNotSame(first, second);
        assertNotSame(first.serializers(), second.serializers());
        assertEquals(writer.toString(), dumped);
        assertEquals("sparrow", first.load(dumped).getString("name"));
    }

    private static ByteArrayInputStream input(String yaml) {
        return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    }

    private static final class FailingWriter extends Writer {
        private final IOException failure;
        private final boolean failOnFlush;

        private FailingWriter(IOException failure, boolean failOnFlush) {
            this.failure = failure;
            this.failOnFlush = failOnFlush;
        }

        @Override
        public void write(char[] buffer, int offset, int length) throws IOException {
            if (!this.failOnFlush) {
                throw this.failure;
            }
        }

        @Override
        public void flush() throws IOException {
            if (this.failOnFlush) {
                throw this.failure;
            }
        }

        @Override
        public void close() {
        }
    }
}
