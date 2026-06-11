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
document.set(Route.from("database", "ports"), List.of(3306, 3307));
document.set(Route.from("database", "pool"), Map.of("size", 10));
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
- 当本地版本和目标版本不同，先对本地文档执行补丁，再把本地文档合并到默认文档结构中。

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

- 如果本地版本和目标版本相同，直接返回本地文档，不合并默认文档新增节点。
- 如果版本不同，找出所有 `VersionMatcher` 命中的 `VersionPatch`，按 `Patch#getOrder()` 排序执行。
- 合并和清理完成后，通过 `VersionExtractor#writeVersion(...)` 把本地文档版本写成目标版本。
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

当类型不能直接由内置规则转换时，通过 `NodeSerializers` 组合出 `NodeSerializer<T>`。
它的定位接近 DataFixerUpper 的 `Codec<T>`：调用方不实现底层读写接口，而是从基础 serializer 出发，用
`xmap`、`listOf`、`mapOf`、`fieldOf`、`element`、`group(...).apply(...)`、`forms(...)` 逐层拼装出目标类型。
公开创建入口集中在 `NodeSerializers`；mapping、sequence、forms 等 builder 类型位于 `serializer.builder` 包，
但推荐仍从 `NodeSerializers.mapping(...)`、`NodeSerializers.sequence(...)`、`NodeSerializers.forms(...)` 获取。
`NodeSerializer` 是不可继承的组合结果，不需要也不应该直接实现或调用底层读写工厂。

```java
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.NodeSerializers;

record BlockPos(int x, int y, int z) {}

NodeSerializer<BlockPos> blockPosSerializer = NodeSerializers.mapping(BlockPos.class)
        .group(
                NodeSerializers.INT.fieldOf("x").forGetter(BlockPos::x),
                NodeSerializers.INT.fieldOf("y").forGetter(BlockPos::y),
                NodeSerializers.INT.fieldOf("z").forGetter(BlockPos::z)
        )
        .apply(BlockPos::new);

yaml.serializers().register(BlockPos.class, blockPosSerializer);

BlockPos spawn = document.get(BlockPos.class, "spawn");
document.setAndGet(BlockPos.class, new BlockPos(0, 64, 0), "spawn");
```

`mapping(type)` 用于 YAML 对象：

- 每个 `fieldOf(name)` 声明一个字段。
- `forGetter(...)` 只负责编码时从对象取值。
- `apply(...)` 是解码时的构造函数或工厂方法。
- 必填字段缺失会抛出 `MissingNodeException`；字段存在但类型错误会抛出 `InvalidNodeException`。
- 构造函数抛普通异常或返回 `null` 时会抛出 `InvalidNodeException`。

如果希望把对象写成 YAML 序列，可以使用 `sequence(type)`：

```java
NodeSerializer<BlockPos> blockPosSerializer = NodeSerializers.sequence(BlockPos.class)
        .group(
                NodeSerializers.INT.element(0).forGetter(BlockPos::x),
                NodeSerializers.INT.element(1).forGetter(BlockPos::y),
                NodeSerializers.INT.element(2).forGetter(BlockPos::z)
        )
        .apply(BlockPos::new);
```

`sequence(type)` 与 `mapping(type)` 的结构相同，只是用 `element(index)` 按下标读写。编码时会按最大下标创建
list，因此 `element(2)` 会写入第三个元素。

如果一个类型可以通过已有类型转换，可以用 `xmap`：

```java
NodeSerializer<BlockPos> serializer = NodeSerializers.INT.listOf().xmap(
        list -> new BlockPos(list.get(0), list.get(1), list.get(2)),
        pos -> List.of(pos.x(), pos.y(), pos.z())
);
```

标量值对象可以用 `stringBacked`：

```java
record NamespacedKey(String value) {}

NodeSerializer<NamespacedKey> keySerializer = NodeSerializers.stringBacked(
        NamespacedKey::new,
        NamespacedKey::value
);
```

如果同一个 Java 类型需要兼容多种 YAML 根形态，可以用 `forms(type)`：

```java
record BlockPos(int x, int y, int z) {
    static BlockPos fromPackedLong(long value) {
        int x = (int) (value / 1_000_000L);
        int y = (int) ((value / 1_000L) % 1_000L);
        int z = (int) (value % 1_000L);
        return new BlockPos(x, y, z);
    }

    long asPackedLong() {
        return x * 1_000_000L + y * 1_000L + z;
    }
}

NodeSerializer<BlockPos> blockPosSerializer = NodeSerializers.forms(BlockPos.class)
        .mapping("map", mapping -> mapping.group(
                NodeSerializers.INT.fieldOf("x").forGetter(BlockPos::x),
                NodeSerializers.INT.fieldOf("y").forGetter(BlockPos::y),
                NodeSerializers.INT.fieldOf("z").forGetter(BlockPos::z)
        ).apply(BlockPos::new))
        .sequence("list", sequence -> sequence.group(
                NodeSerializers.INT.element(0).forGetter(BlockPos::x),
                NodeSerializers.INT.element(1).forGetter(BlockPos::y),
                NodeSerializers.INT.element(2).forGetter(BlockPos::z)
        ).apply(BlockPos::new))
        .scalar("packed", NodeSerializers.LONG.xmap(BlockPos::fromPackedLong, BlockPos::asPackedLong))
        .serializeAs("map")
        .build();
```

上面的 serializer 可以读取这三种写法：

```yaml
map:
  x: 1
  y: 2
  z: 3
list: [1, 2, 3]
packed: 1002003
```

`forms` 是 `mapping`、`sequence`、`stringBacked`/`xmap` 之上的兼容层：

- `mapping(id, ...)` 注册 YAML object 根形态。
- `sequence(id, ...)` 注册 YAML list 根形态。
- `scalar(id, serializer)` 注册 YAML scalar 根形态，常用于 `stringBacked(...)` 或 `xmap(...)` 组合出的标量表示。
- 每种根形态最多注册一个分支；分支 id 必须唯一。
- `serializeAs(id)` 是必填项，且必须指向已注册分支；编码时始终使用该规范形态，不保留输入文件原本形态。
- 解码时只按 YAML 根节点形态选择唯一分支；选中分支内部抛出的 `MissingNodeException` 或 `InvalidNodeException` 会原样抛出，不尝试其他形态。
- 如果当前 YAML 根形态没有注册分支，会抛出 `InvalidNodeException`。

字段和元素默认是必填。可以用 `defaulted(value)` 处理缺失值，也可以用 `optional()` 让缺失值解成 `null`：

```java
NodeSerializer<BlockPos> serializer = NodeSerializers.mapping(BlockPos.class)
        .group(
                NodeSerializers.INT.fieldOf("x").forGetter(BlockPos::x),
                NodeSerializers.INT.fieldOf("y").defaulted(64).forGetter(BlockPos::y),
                NodeSerializers.INT.fieldOf("z").forGetter(BlockPos::z)
        )
        .apply(BlockPos::new);
```

`defaulted(value)` 和 `optional()` 只处理缺失字段；如果 YAML 中存在该值但类型错误，仍然按失败处理。
需要兜底错误值时使用 `onFail(...)`：

```java
import net.momirealms.sparrow.yaml.exception.MissingNodeException;

NodeSerializer<BlockPos> serializer = NodeSerializers.mapping(BlockPos.class)
        .group(
                NodeSerializers.INT.fieldOf("x").onFail(failure -> 0).forGetter(BlockPos::x),
                NodeSerializers.INT.fieldOf("y").defaulted(64).forGetter(BlockPos::y),
                NodeSerializers.INT.fieldOf("z").onFail(failure -> {
                    if (failure instanceof MissingNodeException) {
                        return 0;
                    }
                    return 1;
                }).forGetter(BlockPos::z)
        )
        .apply(BlockPos::new);
```

`onFail(...)` 接收 `MissingNodeException` 或 `InvalidNodeException`：

- 字段或元素不存在时是 `MissingNodeException`，异常会携带缺失 key、完整路径和目标 Java 类型。
- 字段或元素存在但基础 serializer 解码失败时是 `InvalidNodeException`，异常会携带当前路径、当前值类型和目标 Java 类型。
- 基础类型 serializer 遇到解析错误时会直接抛出 `InvalidNodeException`，不再用 `null` 表达错误值。
- 节点存在但 scalar value 为 `null` 时也会抛出 `InvalidNodeException`。
- `listOf()`、`setOf()`、`mapOf()` 遇到错误根节点形态时也会抛出 `InvalidNodeException`。
- `xmap(...)` 的解码映射函数抛普通异常或返回 `null` 时会抛出 `InvalidNodeException`。
- `serialize(value)` 只有在显式传入 `null` 时允许返回 `null`；非 `null` 输入如果编码结果为 `null`，会抛出 `InvalidNodeException`。
- 如果某个组合 serializer 自己抛出 `MissingNodeException` 或 `InvalidNodeException`，builder 会把这个异常直接交给 `onFail(...)`，不会再包一层。
- `onFail(...)` 自己抛异常或返回 `null` 时，整体解码会抛出 `InvalidNodeException`。

常用组合方法：

- `NodeSerializer#xmap(to, from)`：双向映射成新类型。
- `NodeSerializers.OBJECT`：递归读取任意 YAML 值，返回 Java 标量、`List<Object>` 或 `Map<Object, Object>`。
- `NodeSerializers.SCALAR`：只读取 YAML scalar 节点。
- `listOf()`：处理 `List<T>`。
- `setOf()`：处理 `Set<T>`，解码时保持 YAML 序列顺序。
- `mapOf()`：处理 `Map<String, T>`。
- `fieldOf(name)` / `element(index)`：声明 mapping/sequence 的字段或元素，可接 `defaulted(value)`、`optional()` 或 `onFail(...)`。
- `NodeSerializers.mapping(type)` / `NodeSerializers.sequence(type)`：通过 `group(...).apply(...)` 组合对象。
- `NodeSerializers.stringBacked(read, write)`：处理字符串承载的值对象。
- `NodeSerializers.forms(type)`：为同一个 Java 类型注册 mapping、sequence、scalar 多种 YAML 根形态，并指定规范编码形态。
- `NodeSerializers.lazy(...)`：处理递归类型。

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
yaml.serializers().register(User.class, binding -> binding
        .constructor(String.class, int.class)
        .param("name")
        .param("age")
);
```

泛型根类型需要用 `TypeRef` 保留泛型信息：

自动序列化器默认使用 AutoSerializerMode.ADAPTIVE 模式。
Sparrow YAML 在运行时，如果发现存在 ASM 桥接实现，就会尝试使用它，否则会回退到反射机制。
ASM 桥接对于由其他类加载器加载的目标类是安全的，因为生成的序列化器会保留在 Sparrow YAML 的类加载器中，并通过缓存的桥接来访问目标字段。

```java
import net.momirealms.sparrow.yaml.serializer.auto.AutoSerializerMode;

SparrowYaml yaml = SparrowYaml.builder()
        .setAutoSerializerMode(AutoSerializerMode.ASM) // ADAPTIVE, ASM, or REFLECTION
        .build();
```

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
import net.momirealms.sparrow.yaml.serializer.auto.annotation.AfterComment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.InlineComment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlProperty;

import java.nio.file.Path;
import java.util.List;

@Configuration
class AppConfig {
    @YamlProperty("config-version")
    @Comment("Configuration version")
    private int configVersion = 2;

    @Comment("Server host")
    private String host = "127.0.0.1";

    @YamlProperty("server-port")
    @Comment({"Server port", "Must be > 1024"})
    private int port = 8080;

    @InlineComment("Allowed users")
    @AfterComment("End of user allowlist")
    private List<String> users = List.of("admin", "guest");

    @BlankLineBefore
    @Comment("Debug options")
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
- 配置了 `YamlUpgradePipeline` 时，会把 `defaultInstanceSupplier` 返回的默认实例序列化成默认文档，再用升级管线比较本地版本和目标版本；版本不同则升级、保存、再反序列化。
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

运行期 mapper 会把根配置类字段上的 `@Comment` 和 `@BlankLineBefore` 应用到顶层 YAML key。嵌套对象自身字段上的注释目前不会被递归写入。

可用注解：

- `@Configuration`：配置类标记，作为 mapper 使用约定。
- `@YamlProperty("yaml-key")`：指定字段、record 组件或构造器参数对应的 YAML key。
- `@YamlIgnore`：忽略字段。
- `@YamlConstructor`：指定普通 class 反序列化使用的构造器。
- `@Comment(...)`：为字段生成前置块注释。
- `@InlineComment(...)`：为字段生成行内注释。
- `@AfterComment(...)`：为字段生成后置块注释。
- `@BlankLineBefore`：在字段前插入空行。

## 注意事项

- YAML 根节点必须是 mapping。
- 暂不支持多文档 YAML。
- `Route.from(...)` 不能创建空路由；需要空路由时使用 `Route.empty()`。
- `get(...)` 在目标路径缺失时会抛出 `MissingNodeException`；节点存在但序列化器解析失败时会抛出对应的 `InvalidNodeException` 或组合 serializer 自己抛出的异常。
- `getOrDefault(...)` 只在目标路径缺失时返回默认值；节点存在但解析失败时不会吞掉异常。
- 内置的字符串承载类型，例如 `UUID`、`Locale`、时间类型，不再把空字符串读取为 `null`；空字符串会按无效值抛出 `InvalidNodeException`。
- `serialize(null)` 会生成 null 值节点；保存文档时 null 值节点会被跳过，不写入最终 YAML。
- `ElementComment` / `InlineElementComment` / `AfterElementComment` 当前只是注解定义，运行期 mapper 尚未把它们应用到集合元素。

## License

Sparrow YAML 使用 GPL-3.0 license。详见 `LICENSE`。
