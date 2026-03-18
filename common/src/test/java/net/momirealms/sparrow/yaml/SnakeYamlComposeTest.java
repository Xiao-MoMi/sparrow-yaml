package net.momirealms.sparrow.yaml;

import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.YamlOutputStreamWriter;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;
import org.snakeyaml.engine.v2.nodes.Node;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class SnakeYamlComposeTest {

    public static void main(String[] args) throws IOException {
        // 读取器
        Compose load = buildLoad();

        // 读取文件
        InputStream resource = SnakeYamlComposeTest.class.getClassLoader().getResourceAsStream("full-test.yml");
        Node rootNode = load.composeInputStream(resource).orElse(null);

        // 如果解析成功，则导出到 output.yml
        if (rootNode != null) {
            DumpSettings dumpSettings = buildDump();
            Dump dump = new Dump(dumpSettings);

            // 使用 try-with-resources 创建 BufferedWriter
            Path outputPath = Path.of("output.yml");
            OutputStream outputStream = Files.newOutputStream(outputPath);
            YamlOutputStreamWriter yamlOutputStreamWriter = new YamlOutputStreamWriter(outputStream, StandardCharsets.UTF_8);

            // 调用正确的接口直接导出 Node
            dump.dumpNode(rootNode, yamlOutputStreamWriter);
            System.out.println("文件已成功保存到: output.yml");
        }

    }

    // 创建读取器
    private static Compose buildLoad() {
        LoadSettings loadSettings = LoadSettings.builder()
                .setParseComments(true) // 读取注释
                .setAllowDuplicateKeys(false) // 遇到重复的 Key 报错
                .setAllowNonScalarKeys(true)
                .build();
        return new Compose(loadSettings);
    }

    // 创建导出器
    private static DumpSettings buildDump() {
        DumpSettings dumpSettings = DumpSettings.builder()
                .setIndicatorIndent(2)
                .setIndent(2)
                .setIndentWithIndicator(true)
                .setDumpComments(true) // 如果需要导出注释
                .build();
        return dumpSettings;
    }

}