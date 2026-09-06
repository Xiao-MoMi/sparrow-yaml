package net.momirealms.sparrow.yaml;

import net.momirealms.sparrow.yaml.node.ScalarNode;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.route.Route;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.snakeyaml.engine.v2.comments.CommentLine;
import org.snakeyaml.engine.v2.comments.CommentType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeCommentPreservationTest {

    @Test
    void should_PreserveCommentsAcrossCopyReplacementAndSaveOverloads(@TempDir Path tempDir) throws IOException {
        YamlDocument document = SparrowYaml.create().load("first: old\nnested:\n  first: nested\ntarget: target\n");
        ScalarNode first = document.getScalarOrNull(Route.from("first"));
        ScalarNode nestedFirst = document.getScalarOrNull(Route.from("nested", "first"));
        ScalarNode target = document.getScalarOrNull(Route.from("target"));

        first.setBeforeKeyComments(List.of(new CommentLine(Optional.empty(), Optional.empty(), " root-first", CommentType.BLOCK)));
        first.setInlineValueComments(List.of(new CommentLine(Optional.empty(), Optional.empty(), " root-inline", CommentType.IN_LINE)));
        first.setAfterValueComments(List.of(new CommentLine(Optional.empty(), Optional.empty(), " root-after", CommentType.BLOCK)));
        nestedFirst.setBeforeKeyComments(List.of(new CommentLine(Optional.empty(), Optional.empty(), " nested-first", CommentType.BLOCK)));
        first.copyCommentsTo(target);
        first.setValue("new");

        assertCommentsEqual(first, target);
        String expected = document.dumpToString();
        assertTrue(expected.contains("# root-first"));
        assertTrue(expected.contains("# nested-first"));
        assertEquals(expected, document.dumpToString());
        assertComment(first.internalKeyNode().getBlockComments(), 0, CommentType.BLOCK, " root-first");
        assertComment(nestedFirst.internalKeyNode().getBlockComments(), 0, CommentType.BLOCK, " nested-first");

        StringWriter writer = new StringWriter();
        document.save(writer);
        assertEquals(expected, writer.toString());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        document.save(outputStream);
        assertEquals(expected, outputStream.toString(StandardCharsets.UTF_8));

        Path path = tempDir.resolve("path.yml");
        document.save(path);
        assertEquals(expected, Files.readString(path));

        Path filePath = tempDir.resolve("file.yml");
        document.save(filePath.toFile());
        assertEquals(expected, Files.readString(filePath));
    }

    @Test
    void should_RestoreFirstKeyCommentsAfterWriterFailure() throws IOException {
        YamlDocument document = SparrowYaml.create().load("first: value\n");
        ScalarNode first = document.getScalarOrNull(Route.from("first"));
        first.setBeforeKeyComments(List.of(new CommentLine(Optional.empty(), Optional.empty(), " first", CommentType.BLOCK)));
        Writer failingWriter = new Writer() {
            @Override
            public void write(char[] chars, int offset, int length) throws IOException {
                throw new IOException("expected");
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };

        assertThrows(IOException.class, () -> document.save(failingWriter));
        assertNotNull(first.internalKeyNode().getBlockComments());
        assertEquals("# first\nfirst: value\n", document.dumpToString());
    }

    private static void assertComment(List<CommentLine> comments, int index, CommentType type, String value) {
        CommentLine comment = comments.get(index);
        assertEquals(type, comment.getCommentType());
        assertEquals(value, comment.getValue());
    }

    private static void assertCommentsEqual(YamlNode<?> expected, YamlNode<?> actual) {
        assertCommentListsEqual(expected.beforeKeyComments(), actual.beforeKeyComments());
        assertCommentListsEqual(expected.inlineKeyComments(), actual.inlineKeyComments());
        assertCommentListsEqual(expected.afterKeyComments(), actual.afterKeyComments());
        assertCommentListsEqual(expected.beforeValueComments(), actual.beforeValueComments());
        assertCommentListsEqual(expected.inlineValueComments(), actual.inlineValueComments());
        assertCommentListsEqual(expected.afterValueComments(), actual.afterValueComments());
    }

    private static void assertCommentListsEqual(List<CommentLine> expected, List<CommentLine> actual) {
        if (expected == null || actual == null) {
            assertEquals(expected, actual);
            return;
        }
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertComment(actual, i, expected.get(i).getCommentType(), expected.get(i).getValue());
        }
    }
}
