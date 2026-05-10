# Sparrow YAML

Sparrow YAML 是一个面向 Java 17+ 的 YAML 配置工具库。它基于 SnakeYAML Engine 的 fork 版本，并在库内封装了更适合配置文件场景的节点模型、版本升级管线和对象序列化能力。

这个项目的目标不是替代所有 YAML 处理场景，而是让 Java 应用，尤其是插件、服务端工具和需要长期维护配置文件的项目，可以更稳地完成这些工作：

- 从文件、字符串、字节流或 classpath 资源加载 YAML。
- 通过路径读取、写入、删除 `YamlNode`，并保留注释和集合展示风格。
- 将用户旧配置合并到新的默认配置结构中，迁移字段、转换值、补默认值并清理已移除节点。
- 在 `YamlNode` 与 Java 对象之间做双向转换，支持内置类型、自定义序列化器、record/class 自动序列化和配置类文件映射。

## 环境与安装

项目源码和发布包目标版本为 Java 17。

```kotlin
repositories {
    maven("https://repo.catnies.top/releases")
}

dependencies {
    implementation("net.momirealms:sparrow-yaml:1.0.6")
}
```

项目使用 Shadow 插件构建 jar；根项目的 shadow jar 配置会重定位 SnakeYAML Engine 包名，避免和宿主环境里的 SnakeYAML 冲突。

## 快速开始

```java
import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.route.Route;

import java.nio.file.Path;

SparrowYaml yaml = SparrowYaml.builder().build();

YamlDocument document = yaml.load("""
        server:
          host: "127.0.0.1"
          port: 25565
        """);

String host = document.get(String.class, "server", "host");
int port = document.getOrDefault(Integer.class, 25565, "server", "port");

document.set(Route.from("server", "host"), "0.0.0.0");
document.set(Route.from("server", "motd"), "Welcome");
document.save(Path.of("config.yml"));
```

`YamlDocument` 本身是根 `SectionNode`。根节点必须是 YAML mapping；空字符串会得到空文档；多文档 YAML 暂不支持。

## 构建 `SparrowYaml`

```java
SparrowYaml yaml = SparrowYaml.builder()
        .setAllowDuplicateKeys(false) // 默认拒绝重复 key，读取到重复 key 时由 SnakeYAML 抛出异常
        .build();
```

## 读取与保存

`SparrowYaml` 提供了几种加载入口：

```java
YamlDocument fromFile = yaml.load(Path.of("config.yml"));
YamlDocument fromString = yaml.load("key: value");
YamlDocument fromBytes = yaml.load("key: value".getBytes());
YamlDocument fromResource = yaml.loadFromResource("config.yml");

// 保存可以写入 `File`、`Path`、`OutputStream` 或 `Writer`：
document.save(Path.of("config.yml"));
```

如果你需要“本地文件不存在时写入默认配置，存在时执行升级并保存”，可以使用：

```java
YamlDocument defaults = yaml.loadFromResource("config.yml");
YamlDocument upgraded = yaml.upgradeFile(
        Path.of("config.yml").toFile(),
        defaults,
        pipeline,
        true
);
```

最后一个参数为 `true` 时，会在升级前创建 `config.yml.bak.<timestamp>` 备份。

## 节点模型

Sparrow YAML 把 YAML 文档包装成三种节点：

- `SectionNode`：mapping/object 节点，内部保留 key 顺序。
- `SequenceNode`：sequence/list 节点，支持按索引访问和写入。
- `ScalarNode`：标量节点，保存字符串、数字、布尔值等基础值。

常用读取方法：

```java
var node = document.getNodeOrNull("database", "host");
var section = document.getSectionOrNull("database");
var users = document.getSequenceOrNull("users");
var host = document.getScalarOrNull("database", "host");
```

`Object... route` 会被转换为 `Route`。非负整数会被识别为列表索引，其它值会被转成字符串 key：

```java
String firstUser = document.get(String.class, "users", 0);
```

写入原始值时推荐显式使用 `Route`：

```java
document.set(Route.from("database", "host"), "localhost");
document.set(Route.from("database", "ports"), java.util.List.of(3306, 3307));
document.set(Route.from("database", "pool"), java.util.Map.of("size", 10));
```

写入 `Map` 会创建 `SectionNode`，写入 `List` 会创建 `SequenceNode`。写入列表越界索引时，中间空位会用空标量补齐。

删除节点：

```java
document.removeNode("database", "old-key");
document.removeNode(Route.from("users", 0));
```

## 注释与展示风格

读取时会解析注释，保存时会写回注释。节点提供了 SnakeYAML 的注释行访问和设置方法：

```java
import org.snakeyaml.engine.v2.comments.CommentLine;
import org.snakeyaml.engine.v2.comments.CommentType;

import java.util.List;
import java.util.Optional;

var host = document.getScalarOrNull("database", "host");
if (host != null) {
    host.setBeforeKeyComments(List.of(
            new CommentLine(Optional.empty(), Optional.empty(), " Database host", CommentType.BLOCK)
    ));
    host.setInlineValueComments(List.of(
            new CommentLine(Optional.empty(), Optional.empty(), " public address", CommentType.IN_LINE)
    ));
}
```

节点工具方法：

- `copyCommentsTo(target)`：复制当前节点的表层注释。
- `copyNonEmptyCommentsTo(target)`：只复制非空注释。
- `deepCopyCommentsTo(target)`：递归复制当前节点和子节点注释。
- `preserveFlowStyleTo(target)`：把集合节点的 flow style 同步到目标节点，例如保留 `[1, 2, 3]` 这种单行列表风格。

## 文档升级管线

升级管线面向“用户本地旧配置 + 程序内置默认配置”的场景：

- 本地文档保存用户已经改过的值。
- 默认文档代表当前程序版本需要的最新结构。
- 当两份文档版本不同，先对本地文档执行补丁，再把本地文档合并到默认文档结构中。

默认版本字段是 `config-version`，由 `FieldVersionExtractor` 读取。版本字段必须是标量；缺失或不是标量会抛出 `InvalidConfigVersionException`。

```java
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.upgrade.YamlUpgradePipeline;
import net.momirealms.sparrow.yaml.upgrade.patch.PatchValidationException;
import net.momirealms.sparrow.yaml.upgrade.version.FieldVersionExtractor;
import net.momirealms.sparrow.yaml.upgrade.version.NumberVersionMatcher;

import java.util.List;

YamlUpgradePipeline pipeline = YamlUpgradePipeline.builder()
        .versionExtractor(new FieldVersionExtractor("config-version"))
        .updateComments(true)
        .deleteRemovedNodes(true)
        .addIgnoredRoute("runtime")
        .addPatch(NumberVersionMatcher.of(1), patch -> patch
                .relocate(Route.from("database", "old_user"), Route.from("database", "username"))
                .convert(Route.from("admins"), node -> List.of(String.valueOf(node.value()).split(",")))
                .defaultValue(Route.from("database", "pool-size"), 10)
                .validate(local -> {
                    if (local.getNodeOrNull("database", "host") == null) {
                        throw new PatchValidationException("database.host is required");
                    }
                }))
        .build();

YamlDocument local = yaml.load(Path.of("config.yml"));
YamlDocument defaults = yaml.loadFromResource("config.yml");
YamlDocument upgraded = pipeline.upgrade(local, defaults);
upgraded.save(Path.of("config.yml"));
```

升级行为：

- 如果本地版本和默认版本相同，直接返回本地文档，不合并默认文档新增节点。
- 如果版本不同，找出所有 `VersionMatcher` 命中的 `VersionPatch`，按 `Patch#getOrder()` 排序执行。
- 补丁执行完成后，把本地文档版本写成默认文档版本。
- 合并阶段保留本地已有值，只补齐默认文档中存在但本地缺失的节点。
- 合并后会按默认文档 key 顺序重排；本地独有 key 会保留在最后。
- `updateComments(true)` 会把默认文档中的非空注释同步到已有本地节点。
- `deleteRemovedNodes(true)` 会删除默认文档中已不存在的本地节点，默认值为 `true`。
- `addIgnoredRoute(...)` 标记的路径不会参与合并，也不会参与清理。

常用补丁规则：

| 规则 | 执行顺序 | 作用 |
| --- | ---: | --- |
| 自定义 `Patch` | `0` 默认 | 自己实现任意升级逻辑 |
| `relocate(from, to)` | `20` | 把旧路径节点迁移到新路径，保留注释和 flow style |
| `convert(route, converter)` | `30` | 转换指定路径的节点值 |
| `defaultValue(route, value/provider)` | `40` | 目标节点缺失时写入固定值或动态值 |
| `validate(...)` | `50` | 校验升级结果，失败时抛出 `PatchValidationException` |

`PatchContext` 会在一次升级流程内被所有补丁共享，适合在前一个补丁里保存临时值，再由后续补丁读取：

```java
patch.patch((defDoc, localDoc, context) -> {
    context.put("old-host", localDoc.get(String.class, "database", "host"));
    return localDoc;
});
```

`NumberVersionMatcher.of(2)` 会匹配能解析成数字且 `localVersion <= 2` 的本地版本。非数字版本不会命中 `NumberVersionMatcher`，可以自行实现 `VersionMatcher`。

## 序列化器

每个 `SparrowYaml` 实例都有独立的 `SerializerRegistry`：

```java
var registry = yaml.serializers();
```

内置注册的类型包括：

- `Object`
- `String`
- `int` / `Integer`
- `float` / `Float`
- `double` / `Double`
- `long` / `Long`
- `boolean` / `Boolean`
- `UUID`
- `Locale`
- `Date`
- `Calendar`
- `LocalDate`
- `LocalTime`
- `LocalDateTime`
- `ZonedDateTime`
- `Instant`
- `Duration`
- `Period`

枚举可以通过 `NodeSerializers.enumCodec(MyEnum.class)` 手动创建序列化器；自动序列化 record/class 字段时也会解析枚举字段。

### 自定义序列化器

当类型不能直接由内置规则转换时，实现 `NodeSerializer<T>`：

```java
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.NodeSerializers;

import java.util.List;

record BlockPos(int x, int y, int z) {}

NodeSerializer<BlockPos> blockPosSerializer = new NodeSerializer<>() {
    @Override
    public BlockPos deserialize(YamlNode<?> node) {
        List<Integer> values = NodeSerializers.INT.listOf().deserialize(node);
        if (values == null || values.size() != 3) {
            return null;
        }
        return new BlockPos(values.get(0), values.get(1), values.get(2));
    }

    @Override
    public Object serialize(BlockPos value) {
        if (value == null) {
            return null;
        }
        return List.of(value.x(), value.y(), value.z());
    }
};

yaml.serializers().register(BlockPos.class, blockPosSerializer);

BlockPos spawn = document.get(BlockPos.class, "spawn");
document.setAndGet(BlockPos.class, new BlockPos(0, 64, 0), "spawn");
```

如果一个类型可以通过已有类型转换，可以用 `xmap` 简化：

```java
NodeSerializer<BlockPos> serializer = NodeSerializers.INT.listOf().xmap(
        list -> new BlockPos(list.get(0), list.get(1), list.get(2)),
        pos -> List.of(pos.x(), pos.y(), pos.z())
);
```

常用组合方法：

- `NodeDecoder#map(to)`：只处理解码方向。
- `NodeEncoder#contraMap(from)`：只处理编码方向。
- `NodeSerializer#xmap(to, from)`：双向映射成新类型。
- `listOf()`：处理 `List<T>`。
- `mapOf()`：处理 `Map<String, T>`。
- `NodeSerializer.lazy(...)`：处理递归类型。

### 自动创建序列化器
项目支持自动注册序列化器，原理大致为解析类文件，并尝试使用最小的序列化器进行超级拼装。例如一个 BlockPos 类包含3个int字段，那么SparrowYaml将会自动识别到并自动生成序列化器。

record 可以直接注册：

```java
record DatabaseConfig(String host, int port) {}

yaml.serializers().register(DatabaseConfig.class);

DatabaseConfig config = document.get(DatabaseConfig.class, "database");
document.setAndGet(DatabaseConfig.class, config, "database");
```

普通 class 支持这些实例化方式：

- 无参构造器 + 字段注入。
- 使用 `@YamlConstructor` 指定构造器。
- 使用外部绑定指定构造器参数。
- 多个构造器且无法明确选择时，会尝试使用 Unsafe 分配实例再注入字段。

```java
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlConstructor;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlProperty;

class User {
    private final String name;
    private final int age;

    @YamlConstructor
    User(
            @YamlProperty("name") String name,
            @YamlProperty("age") int age
    ) {
        this.name = name;
        this.age = age;
    }
}

yaml.serializers().register(User.class);
```

不能或不想在类上加注解时，可以使用外部绑定：

```java
yaml.serializers().registerAuto(User.class, binding -> binding
        .constructor(String.class, int.class)
        .param("name")
        .param("age")
);
```

泛型根类型需要用 `TypeRef` 保留泛型信息：

```java
import net.momirealms.sparrow.yaml.serializer.TypeRef;

import java.util.List;

class Box<T> {
    T value;
    List<T> values;
}

TypeRef<Box<String>> boxType = new TypeRef<>() {};
yaml.serializers().register(boxType);

Box<String> box = document.get(boxType, "box");
```

自动序列化支持嵌套 record/class、递归类型、`List<T>`、`Set<T>`、`Map<String, T>` 和枚举字段。

自动序列化的限制：

- 数组、接口、抽象类需要先手动注册序列化器。
- `Map` 只支持 `Map<String, T>`。
- raw type 和 wildcard 泛型不支持。
- `java.lang.Object` 字段不能自动推断具体类型。
- 单个带参构造器如果没有 `@YamlProperty` 且编译时未保留参数名，会因为无法确定 YAML key 而失败。

## 配置类文件映射

`YamlMapper` 是序列化器能力之上的文件映射工具，适合直接把配置对象读写到文件。

```java
import net.momirealms.sparrow.yaml.mapper.YamlMapper;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlProperty;

import java.nio.file.Path;
import java.util.List;

@Configuration
class AppConfig {
    @YamlProperty("config-version")
    @Comment(before = "Configuration version")
    private int configVersion = 2;

    @Comment(before = "Server host")
    private String host = "127.0.0.1";

    @YamlProperty("server-port")
    @Comment(before = {"Server port", "Must be > 1024"})
    private int port = 8080;

    @Comment(inline = "Allowed users")
    private List<String> users = List.of("admin", "guest");

    @BlankLineBefore
    @Comment(before = "Debug options")
    private DebugConfig debug = new DebugConfig();
}

class DebugConfig {
    private boolean enabled = false;
}

YamlMapperFactory factory = YamlMapperFactory.builder()
        .sparrowYaml(yaml)
        .upgradePipeline(pipeline)
        .backupPathResolver(path -> path.resolveSibling(path.getFileName() + ".bak"))
        .backupOnUpgrade(true)
        .build();

YamlMapper<AppConfig> mapper = factory.create(AppConfig.class, AppConfig::new);

AppConfig config = mapper.load(Path.of("config.yml"));
mapper.save(Path.of("config.yml"), config);
```

`YamlMapper#load(path)` 的行为：

- 文件不存在时，调用 `defaultInstanceSupplier` 创建默认配置实例，将它序列化成 YAML 文件并返回该实例。
- 文件存在时，读取文件并反序列化成配置对象。
- 配置了 `YamlUpgradePipeline` 时，会把 `defaultInstanceSupplier` 返回的默认实例序列化成默认文档，再和本地文档比较版本；版本不同则升级、保存、再反序列化。
- `backupOnUpgrade(true)` 只会在实际发生升级且写回文件前备份原文件；`backupPathResolver(...)` 可以自定义备份路径，默认路径为 `原文件名.bak.<timestamp>`。
- 同一个 mapper 会缓存最近一次加载的实例；当路径、最后修改时间和文件大小都没变时，`load` 返回缓存实例。
- `loadForce(path)` 会忽略缓存，强制重新读取。

`factory.create(clazz, defaultInstanceSupplier)` 的第二个参数是当前版本配置的默认实例来源。建议每次返回一个新的配置对象，例如 `AppConfig::new`；没有无参构造器或不可变配置类可以使用 lambda：

```java
YamlMapper<ImmutableConfig> mapper = factory.create(
        ImmutableConfig.class,
        () -> new ImmutableConfig("default-server", 32)
);
```

运行期 mapper 会把根配置类字段或 record 组件上的 `@Comment` 和 `@BlankLineBefore` 应用到顶层 YAML key。嵌套对象自身字段上的注释目前不会被递归写入。

可用注解：

- `@Configuration`：配置类标记，作为 mapper 使用约定。
- `@YamlProperty("yaml-key")`：指定字段、record 组件或构造器参数对应的 YAML key。
- `@YamlIgnore`：忽略字段或 record 组件。
- `@YamlConstructor`：指定普通 class 反序列化使用的构造器。
- `@Comment(before = ..., inline = ..., after = ...)`：为字段或 record 组件生成注释。
- `@BlankLineBefore`：在字段或 record 组件前插入空行。

## 注意事项

- YAML 根节点必须是 mapping。
- 暂不支持多文档 YAML。
- `Route.from(...)` 不能创建空路由；需要空路由时使用 `Route.empty()`。
- `getOrDefault(...)` 只在目标节点缺失时返回默认值；节点存在但序列化器解析失败时会返回解析结果，也就是通常为 `null`。
- 内置的字符串承载类型，例如 `UUID`、`Locale`、时间类型，会把 `null` 序列化为空字符串，并在读取空字符串时返回 `null`。
- `ElementComment` / `ElementComments` 当前只是注解定义，运行期 mapper 尚未把它们应用到集合元素。

## License

Sparrow YAML 使用 GPL-3.0 license。详见 `LICENSE`。
