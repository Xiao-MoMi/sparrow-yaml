package net.momirealms.sparrow.yaml;

import net.momirealms.sparrow.yaml.mapper.YamlMapper;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.serializer.auto.AutoSerializerMode;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.AfterComment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.InlineComment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationCommentLineBreakTest {

    @Test
    void should_SplitFieldCommentsAndPreserveEmptyLines(@TempDir Path tempDir) throws IOException {
        AutoSerializerMode[] modes = AutoSerializerMode.values();
        for (int i = 0; i < modes.length; i++) {
            SparrowYaml yaml = SparrowYaml.builder().setAutoSerializerMode(modes[i]).build();
            YamlMapper<MultilineConfig> mapper = yaml.mapper(MultilineConfig.class, MultilineConfig::new);
            Path path = tempDir.resolve(modes[i] + "-multiline.yml");
            Path expandedPath = tempDir.resolve(modes[i] + "-expanded.yml");
            mapper.save(path, new MultilineConfig());
            yaml.mapper(ExpandedConfig.class, ExpandedConfig::new).save(expandedPath, new ExpandedConfig());

            String saved = Files.readString(path);
            assertEquals(Files.readString(expandedPath), saved);
            assertTrue(saved.contains("# first\n# second\n# third\n# fourth\n# fifth\n#\n#\n# \\n literal"), saved);
            assertEquals("kept", yaml.load(saved).getString(Route.from("value")));
            MultilineConfig loaded = mapper.loadValue(path);
            assertEquals("kept", loaded.value);
            mapper.save(path, loaded);
            assertEquals(saved, Files.readString(path));
        }
    }

    @Test
    void should_SplitRecordComponentComments(@TempDir Path tempDir) throws IOException {
        AutoSerializerMode[] modes = AutoSerializerMode.values();
        for (int i = 0; i < modes.length; i++) {
            SparrowYaml yaml = SparrowYaml.builder().setAutoSerializerMode(modes[i]).build();
            MultilineRecord value = new MultilineRecord("kept");
            YamlMapper<MultilineRecord> mapper = yaml.mapper(MultilineRecord.class, () -> value);
            Path path = tempDir.resolve(modes[i] + "-record.yml");
            Path expandedPath = tempDir.resolve(modes[i] + "-expanded-record.yml");
            mapper.save(path, value);
            yaml.mapper(ExpandedRecord.class, () -> new ExpandedRecord("kept")).save(expandedPath, new ExpandedRecord("kept"));

            String saved = Files.readString(path);
            assertEquals(Files.readString(expandedPath), saved);
            assertEquals(value, mapper.loadValue(path));
            mapper.save(path, value);
            assertEquals(saved, Files.readString(path));
        }
    }

    @Configuration
    public static class MultilineConfig {
        @BlankLineBefore
        @Comment({"first\nsecond", " third\r\nfourth\rfifth\n", "", "\\n literal"})
        @InlineComment({"inline\n next", "\nend\n"})
        @AfterComment({"after\r\n tail", "last\r"})
        private String value = "kept";
    }

    @Configuration
    public static class ExpandedConfig {
        @BlankLineBefore
        @Comment({"first", "second", " third", "fourth", "fifth", "", "", "\\n literal"})
        @InlineComment({"inline", " next", "", "end", ""})
        @AfterComment({"after", " tail", "last", ""})
        private String value = "kept";
    }

    @Configuration
    public record MultilineRecord(
            @Comment("record before\nsecond")
            @InlineComment("record inline\nsecond")
            @AfterComment("record after\nsecond") String value
    ) {
    }

    @Configuration
    public record ExpandedRecord(
            @Comment({"record before", "second"})
            @InlineComment({"record inline", "second"})
            @AfterComment({"record after", "second"}) String value
    ) {
    }
}
