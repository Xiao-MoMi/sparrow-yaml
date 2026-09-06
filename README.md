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

SparrowYaml yaml = SparrowYaml.create();

YamlDocument document = yaml.load("""
        server:
          host: "127.0.0.1"
          port: 25565
        """);

String host = document.getString(Route.from("server", "host"));
int port = document.getIntOrDefault(25565, Route.from("server", "port"));

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
YamlDocument fromOwnedResource = yaml.loadFromResource(AppConfig.class, "config.yml");
YamlDocument fromLoaderResource = yaml.loadFromResource(AppConfig.class.getClassLoader(), "config.yml");
YamlDocument empty = yaml.createDocument();
String yamlText = fromString.dumpToString();

// 保存可以写入 `File`、`Path`、`OutputStream` 或 `Writer`：
document.save(Path.of("config.yml"));
```

`SparrowYaml.create()` 使用默认设置并且每次返回新实例；serializer 注册表不会在不同实例之间共享。

如果你需要“本地文件不存在时写入默认配置，存在时执行升级并保存”，可以使用：

```java
YamlDocument defaults = yaml.loadFromResource("config.yml");
YamlDocument upgraded = yaml.upgradeFile(
        Path.of("config.yml"),
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
var node = document.getNodeOrNull(Route.from("database", "host"));
var section = document.getSectionOrNull(Route.from("database"));
var users = document.getSequenceOrNull(Route.from("users"));
var host = document.getScalarOrNull(Route.from("database", "host"));
```

节点 API 使用显式 `Route`。`Route.from(Object...)` 会把非负整数识别为列表索引，其它值转换为字符串 key：

```java
String host = document.getString(Route.dotted("database.host"));
String literal = document.getString(Route.from("database.host"));
String firstUser = document.get(String.class, Route.from("users", 0));
```

`Route.dotted` 只按 `.` 拆分 mapping key，不 trim，也不会把数字段推断成 index。空路径段会保留，因此 `Route.dotted("map.0")` 等于 `Route.from("map", "0")`；Sequence index 继续使用显式整数。空字符串不是有效点分路径，根路由使用 `Route.empty()`；单个包含 `.` 的 literal key 使用 `Route.from("database.host")`。

`Route.empty()` 表示文档根节点；`YamlDocument#route()` 和根节点的 `getKeyAsRoute()` 都返回空路由。

写入原始值时推荐显式使用 `Route`：

```java
document.set(Route.from("database", "host"), "localhost");
document.set(Route.from("database", "ports"), List.of(3306, 3307));
document.set(Route.from("database", "pool"), Map.of("size", 10));
```

写入 `Map` 会创建 `SectionNode`，写入 `List` 会创建 `SequenceNode`。写入列表越界索引时，中间空位会用 YAML null 补齐；这些 null 是真实位置，保存和重新加载后不会改变后续元素的 index。Mapping 中值为 null 的字段继续在保存时省略。

对已有非根节点调用 `node.set(Class/TypeRef/NodeSerializer, value)` 时，serializer 的 Map/List/scalar 输出会替换成对应节点形状，并保留当前节点的表层注释和集合 flow style。`YamlDocument` 根节点必须保持 mapping，因此根节点的 typed set 只接受 Map 序列化结果；其它形状会在修改文档前抛出 `IllegalArgumentException`。

`SectionNode#getValues()` 返回 `Map<String, Object>`，每一层 mapping key 都使用 `String.valueOf` 转成字符串；`SequenceNode#getValues()` 对嵌套 mapping 使用相同规则。集合继续递归为 Map/List，ScalarNode 直接返回内部原始值，YAML null 转成 Java null。需要保留实际 Object key 时使用 `representValue()`。

SnakeYAML Node 图保存结构、anchor、注释与展示样式，是文档的权威表示。`SectionNode#value()` 和 `SequenceNode#value()` 返回只读视图，结构写入必须使用 `setSubNode`、`removeSubNode`、`reorderKeys` 或 typed set。`internalValueNode()` 保留为高级 Interface，可用于读取和调整注释、flow style 等展示信息；调用方不得直接修改 Mapping/Sequence 子集合或标量内容，违反该约定的结果不由库兜底。

把现有 `YamlNode` 写入其它位置时会深复制完整节点图，源节点及其所属文档保持不变，复制图内部的 anchor/alias 关系会保留。`removeNode` 返回的 detached wrapper 仍可读取值和元数据，也可以作为后续 copy 的来源；依赖 root 的读取与写入不适用于 detached wrapper。升级管线在同一文档内 relocate 时移动原节点 identity，因此仍指向该节点的 alias 会继续共享后续修改。

删除节点：

```java
document.removeNode(Route.from("database", "old-key"));
document.removeNode(Route.from("users", 0));
```

## 注释与展示风格

读取时会解析注释，保存时会写回注释。节点提供了 SnakeYAML 的注释行访问和设置方法：

```java
import org.snakeyaml.engine.v2.comments.CommentLine;
import org.snakeyaml.engine.v2.comments.CommentType;

import java.util.List;
import java.util.Optional;

var host = document.getScalarOrNull(Route.from("database", "host"));
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

默认版本字段是 `config-version`，由 `FieldVersionExtractor` 读取。版本字段必须是非 null 标量；缺失、YAML null 或不是标量都会抛出 `InvalidConfigVersionException`。

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
                    if (local.getNodeOrNull(Route.from("database", "host")) == null) {
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
- `updateComments(true)` 会把默认文档中的非空注释同步到已有本地节点，默认值为 `false`；缺失节点仍会连同模板注释一起补入。
- `deleteRemovedNodes(true)` 会删除默认文档中已不存在的本地节点，默认值为 `true`。
- `MergeOptions.defaultOptions()` 与 `YamlUpgradePipeline.builder().build()` 使用相同默认值：保留已有本地注释，清理未被忽略的已移除节点。
- `addIgnoredRoute(...)` 标记的路径不会参与合并，也不会参与清理。
- `addIgnoredRoute(Route.empty())` 会关闭整份文档的自动合并和清理；显式 `Patch` 和目标版本字段更新仍会执行。
- `pipeline.upgrade(local, defaults, dynamicSectionRoutes)` 可以额外登记一组动态 Section 路由：路由本身仍参与补齐（本地缺失时写入默认内容），但其子节点视为用户数据，不会被默认文档合并，也不会被清理删除。
- `dynamicSectionRoutes` 包含 `Route.empty()` 时，根节点本身仍参与升级，但所有非根路径都视为动态用户数据。

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
    context.put("old-host", localDoc.get(String.class, Route.from("database", "host")));
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

枚举可以通过 `NodeSerializers.enumCodec(MyEnum.class)` 获取序列化器；自动序列化 record/class 字段时也会解析枚举字段。解码先按大写名称匹配，未命中再按 `enum.name()` 的真实名称精确匹配；编码写出真实名称。直接读取未知名称仍会抛出 `InvalidNodeException`。

### 自定义序列化器

当类型不能直接由内置规则转换时，通过 `NodeSerializers` 组合出 `NodeSerializer<T>`。
它的定位接近 DataFixerUpper 的 `Codec<T>`：调用方不实现底层读写接口，而是从基础 serializer 出发，用
`xmap`、`listOf`、`mapOf`、`required`、`optional`、`group(...).apply(...)`、`withAlternative(...)`
逐层拼装出目标类型。
公开创建入口集中在 `NodeSerializers`；mapping、sequence 等 builder 类型位于 `serializer.builder` 包，
但推荐仍从 `NodeSerializers.mapping(...)`、`NodeSerializers.sequence(...)` 获取。
`NodeSerializer` 是不可继承的组合结果，不需要也不应该直接实现或调用底层读写工厂。

```java
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.NodeSerializers;

record BlockPos(int x, int y, int z) {}

NodeSerializer<BlockPos> blockPosSerializer = NodeSerializers.mapping(BlockPos.class)
        .group(
                NodeSerializers.INT.required("x").forGetter(BlockPos::x),
                NodeSerializers.INT.optional("y", 64).forGetter(BlockPos::y),
                NodeSerializers.INT.required("z").forGetter(BlockPos::z)
        )
        .apply(BlockPos::new);

yaml.serializers().register(BlockPos.class, blockPosSerializer);

BlockPos spawn = document.get(BlockPos.class, Route.from("spawn"));
document.setAndGet(BlockPos.class, new BlockPos(0, 64, 0), Route.from("spawn"));
```

`mapping(type)` 用于 YAML 对象：

- `required(name)` 声明必填字段；字段缺失或 YAML null 会抛出 `MissingNodeException`。
- `optional(name, value)` 声明带默认值字段；字段缺失或 YAML null 会使用默认值。
- `optional(name)` 声明可选字段；字段缺失或 YAML null 会解成 `Optional.empty()`。
- `forGetter(...)` 只负责编码时从对象取值。
- `apply(...)` 是解码时的构造函数或工厂方法。
- 字段存在但类型错误会抛出 `InvalidNodeException`；构造函数抛普通异常或返回 `null` 时也会抛出 `InvalidNodeException`。

如果希望把对象写成 YAML 序列，可以使用 `sequence(type)`：

```java
NodeSerializer<BlockPos> blockPosSerializer = NodeSerializers.sequence(BlockPos.class)
        .group(
                NodeSerializers.INT.required(0).forGetter(BlockPos::x),
                NodeSerializers.INT.optional(1, 64).forGetter(BlockPos::y),
                NodeSerializers.INT.required(2).forGetter(BlockPos::z)
        )
        .apply(BlockPos::new);
```

`sequence(type)` 与 `mapping(type)` 的结构相同，只是用 `required(index)`、`optional(index, value)`、
`optional(index)` 按下标读写。sequence 编码按紧凑 tuple 处理，index 必须从 0 连续声明；
`required(index)` 或 `optional(index, value)` 编码出 `null` 会失败；
`optional(index)` 的 `Optional.empty()` 只能在尾部省略，如果位于中间会抛出 `InvalidNodeException`。

如果一个类型可以通过已有类型转换，可以用 `xmap`：

```java
NodeSerializer<BlockPos> serializer = NodeSerializers.INT.listOf().xmap(
        list -> new BlockPos(list.get(0), list.get(1), list.get(2)),
        pos -> List.of(pos.x(), pos.y(), pos.z())
);
```

标量值对象可以用 `scalar`：

```java
record NamespacedKey(String value) {}

NodeSerializer<NamespacedKey> keySerializer = NodeSerializers.scalar(
        NamespacedKey.class,
        NamespacedKey::new,
        NamespacedKey::value
);

yaml.serializers().register(NamespacedKey.class, keySerializer);
```

注册后，自动序列化器可以直接推断声明为 `Map<NamespacedKey, T>` 的字段。

如果同一个 Java 类型需要兼容多种 YAML 写法，可以从规范写法的 `NodeSerializer` 出发追加只读 alternative：

```java
record BlockPos(int x, int y, int z) {
    static BlockPos fromPackedLong(long value) {
        int x = (int) (value / 1_000_000L);
        int y = (int) ((value / 1_000L) % 1_000L);
        int z = (int) (value % 1_000L);
        return new BlockPos(x, y, z);
    }

    static BlockPos fromCsv(String value) {
        String[] parts = value.split(",", 3);
        return new BlockPos(
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim())
        );
    }

}

NodeSerializer<BlockPos> blockPosSerializer = NodeSerializers.mapping(BlockPos.class)
        .group(
                NodeSerializers.INT.required("x").forGetter(BlockPos::x),
                NodeSerializers.INT.optional("y", 64).forGetter(BlockPos::y),
                NodeSerializers.INT.required("z").forGetter(BlockPos::z)
        )
        .apply(BlockPos::new)
        .withAlternative(NodeSerializers.sequence(BlockPos.class)
                .group(
                        NodeSerializers.INT.required(0).forGetter(BlockPos::x),
                        NodeSerializers.INT.optional(1, 64).forGetter(BlockPos::y),
                        NodeSerializers.INT.required(2).forGetter(BlockPos::z)
                )
                .apply(BlockPos::new))
        .withAlternative(NodeSerializers.LONG, BlockPos::fromPackedLong)
        .withAlternative(NodeSerializers.STRING, BlockPos::fromCsv);
```

上面的 serializer 可以读取这四种写法：

```yaml
map:
  x: 1
  y: 2
  z: 3
list: [1, 2, 3]
packed: 1002003
csv: "1,2,3"
```

primary alternative 是 `mapping`、`sequence`、`scalar`/`xmap` 之上的兼容层：

- primary serializer 是规范写法；编码时始终使用 primary，不保留输入文件原本形态。
- `withAlternative(...)` 追加只读候选；候选不做节点种类预过滤，而是按注册顺序逐个尝试。
- alternative 可以传入完整 `NodeSerializer<A>` 和 `A -> T` 转换函数，因此 legacy 写法不需要提供反向 writer。
- 同一种 YAML 写法可以追加多个候选；候选按追加顺序尝试，首个成功分支胜出。
- 单个候选失败时会抛出对应的 `NodeParsingException`；候选抛出的普通 `RuntimeException` 或非致命 `LinkageError` 会先包装为 `UnexpectedNodeParsingException`。多个候选全部失败时会抛出 `AlternativesNodeException`，并按尝试顺序保留所有候选的原始异常。

字段和元素的 presence 在入口处声明。YAML null 在字段/元素访问层等价于缺失：

```java
import java.util.Optional;

record OptionalSpawn(int x, int y, Optional<String> world) {}

NodeSerializer<OptionalSpawn> serializer = NodeSerializers.mapping(OptionalSpawn.class)
        .group(
                NodeSerializers.INT.required("x").forGetter(OptionalSpawn::x),
                NodeSerializers.INT.optional("y", 64).forGetter(OptionalSpawn::y),
                NodeSerializers.STRING.optional("world").forGetter(OptionalSpawn::world)
        )
        .apply(OptionalSpawn::new);
```

`optional(...)` 和 `optional(..., defaultValue)` 只处理缺失/null；如果 YAML 中存在该值但类型错误，仍然按失败处理。
需要兜底错误值时使用 `onFail(...)`：

```java
import net.momirealms.sparrow.yaml.exception.MissingNodeException;

NodeSerializer<BlockPos> serializer = NodeSerializers.mapping(BlockPos.class)
        .group(
                NodeSerializers.INT.required("x").onFail(failure -> 0).forGetter(BlockPos::x),
                NodeSerializers.INT.optional("y", 64).forGetter(BlockPos::y),
                NodeSerializers.INT.required("z").onFail(failure -> {
                    if (failure instanceof MissingNodeException) {
                        return 0;
                    }
                    return 1;
                }).forGetter(BlockPos::z)
        )
        .apply(BlockPos::new);
```

`onFail(...)` 接收 `NodeParsingException`，常见子类如下：

- 字段或元素不存在时是 `MissingNodeException`，异常会携带缺失 key、完整路径和目标 Java 类型。
- 字段或元素存在但基础 serializer 解码失败时是 `InvalidNodeException`，异常会携带当前路径、当前值类型和目标 Java 类型。
- 自定义 reader、`xmap(...)` 解码映射函数、builder 构造函数或底层依赖抛出普通 `RuntimeException` / 非致命 `LinkageError` 时是 `UnexpectedNodeParsingException`，异常会携带当前路径、当前值类型、目标 Java 类型和原始 cause。
- 基础类型 serializer 遇到解析错误时会直接抛出 `InvalidNodeException`，不再用 `null` 表达错误值。
- 自动反序列化普通 class 的直接枚举字段时，未知标量名称会保留构造器已经写入的非 null 字段初始值，其余字段继续加载；字段没有可用初始值，以及 record/构造器参数、Unsafe 分配、集合内枚举和自定义 serializer 仍按失败处理。
- 字段/元素节点存在但 scalar value 为 `null` 时，会先被访问层当作缺失处理；直接读取该 scalar 节点仍是 `InvalidNodeException`。
- `listOf()`、`setOf()`、`mapOf()` 遇到错误根节点形态时也会抛出 `InvalidNodeException`。
- `xmap(...)` 的解码映射函数返回 `null` 时会抛出 `InvalidNodeException`，抛普通异常时会抛出 `UnexpectedNodeParsingException`。
- `serialize(value)` 只有在显式传入 `null` 时允许返回 `null`；非 `null` 输入如果编码结果为 `null`，会抛出 `InvalidNodeException`。
- 如果某个组合 serializer 自己抛出 `NodeParsingException`，builder 会把这个异常直接交给 `onFail(...)`，不会再包一层。
- `onFail(...)` 自己抛异常或返回 `null` 时，整体解码会抛出 `InvalidNodeException`。

常用组合方法：

- `NodeSerializer#xmap(to, from)`：双向映射成新类型。
- `NodeSerializers.OBJECT`：严格递归读取 YAML 值，返回 Java 标量、`List<Object>` 或 `Map<Object, Object>`；任一递归位置出现 YAML null 都会抛出 `InvalidNodeException`。需要保留 null 时可用 `representValue()` 获取 Object-key 投影，或用 `getValues()` 获取 String-key 原始值。
- `NodeSerializers.SCALAR`：只读取 YAML scalar 节点。
- `listOf()`：处理 `List<T>`。
- `setOf()`：处理 `Set<T>`，解码时保持 YAML 序列顺序。
- `mapOf()`：处理 `Map<String, T>`；`mapOf(keySerializer)` 处理键具有标量 serializer 的 `Map<K, T>`。
- `required(name/index)` / `optional(name/index)` / `optional(name/index, value)`：声明 mapping/sequence 的字段或元素 presence。
- `NodeSerializers.mapping(type)` / `NodeSerializers.sequence(type)`：通过 `group(...).apply(...)` 组合对象。
- `NodeSerializers.scalar(read, write)`：处理字符串承载的值对象。
- `withAlternative(...)`：为同一个 Java 类型追加只读 legacy 写法，编码始终使用 primary serializer。
- `NodeSerializers.lazy(...)`：处理递归类型。

### 自动创建序列化器
项目支持自动注册序列化器，原理大致为解析类文件，并尝试使用最小的序列化器进行超级拼装。例如一个 BlockPos 类包含3个int字段，那么SparrowYaml将会自动识别到并自动生成序列化器。

record 可以直接注册：

```java
record DatabaseConfig(String host, int port) {}

yaml.serializers().register(DatabaseConfig.class);

DatabaseConfig config = document.get(DatabaseConfig.class, Route.from("database"));
document.setAndGet(DatabaseConfig.class, config, Route.from("database"));
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

Box<String> box = document.get(boxType, Route.from("box"));
```

自动序列化支持嵌套 record/class、递归类型、`List<T>`、`Set<T>`、`Map<K, T>` 和枚举字段。Map 键会使用 `K` 对应的 serializer，并要求它编码为标量值。

自动序列化的限制：

- 数组、接口、抽象类需要先手动注册序列化器。
- `Map` 的键 serializer 必须编码为标量值，结构化的 Map/List 不能作为键。
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

AppConfig config = mapper.loadValue(Path.of("config.yml"));
mapper.save(Path.of("config.yml"), config);
```

不需要升级管线或自定义备份时，可以直接复用当前 `SparrowYaml` 创建 Mapper：

```java
YamlMapper<AppConfig> mapper = yaml.mapper(AppConfig.class, AppConfig::new);
AppConfig config = mapper.loadValue(Path.of("config.yml"));
```

需要同时访问 YAML 文档时，使用 `YamlMapper.Result<AppConfig> result = mapper.load(path)`，再分别读取 `result.document()` 和 `result.value()`。

`YamlMapper#load(path)` 与 `loadValue(path)` 的行为：

- 文件不存在时，调用 `defaultInstanceSupplier` 创建默认配置实例，将它序列化成 YAML 文件并返回该实例。
- 文件存在时，读取文件并反序列化成配置对象。
- 普通 class 的直接枚举字段遇到未知标量名称时，保留实际构造对象中的非 null 字段初始化值并继续读取其他字段。字段当前为 null 时仍抛出 `InvalidNodeException`，不会猜测首个枚举常量。
- 配置了 `YamlUpgradePipeline` 时，会把 `defaultInstanceSupplier` 返回的默认实例序列化成默认文档，再用升级管线比较本地版本和目标版本；版本不同则升级、保存、再反序列化。
- 升级时配置类中的 `Map<K, V>` 字段（含嵌套配置类中的 Map 字段）会被自动登记为动态 Section：字段本身缺失时仍会补齐默认条目，但字段内的键值属于用户数据，不会被默认实例的内容合并或清理。
- `backupOnUpgrade(true)` 只会在实际发生升级且写回文件前备份原文件；`backupPathResolver(...)` 可以自定义备份路径，默认路径为 `原文件名.bak.<timestamp>`。
- 每次调用都会重新读取文件并创建新的配置实例, Mapper 不缓存文件内容或配置对象。

`factory.create(clazz, defaultInstanceSupplier)` 和 `yaml.mapper(clazz, defaultInstanceSupplier)` 的第二个参数是当前版本配置的默认实例来源。建议每次返回一个新的配置对象，例如 `AppConfig::new`；没有无参构造器或不可变配置类可以使用 lambda：

```java
YamlMapper<ImmutableConfig> mapper = factory.create(
        ImmutableConfig.class,
        () -> new ImmutableConfig("default-server", 32)
);
```

运行期 mapper 会把配置类字段上的 `@Comment` 和 `@BlankLineBefore` 应用到对应 YAML key，嵌套配置对象中的字段注释也会递归写入。

可用注解：

- `@Configuration`：配置类标记，作为 mapper 使用约定。
- `@YamlProperty("yaml-key")`：指定字段、record 组件或构造器参数对应的 YAML key。
- `@YamlIgnore`：忽略字段。
- `@YamlConstructor`：指定普通 class 反序列化使用的构造器。
- `@Comment(...)`：为字段生成前置块注释。
- `@InlineComment(...)`：为字段生成行内注释。
- `@AfterComment(...)`：为字段生成后置块注释。
- `@BlankLineBefore`：在字段前插入空行。

`@Comment`、`@InlineComment` 和 `@AfterComment` 的每个字符串都支持换行：

```java
@Comment("Server port\nMust be > 1024")
private int port = 25565;
```

这与 `@Comment({"Server port", "Must be > 1024"})` 等价。支持 LF (`\n`)、CRLF (`\r\n`) 和 CR (`\r`)，每行分别生成带 `#` 的注释；行内注释的后续行按原位置对齐。连续或首尾换行产生的空段保留为裸 `#`，真正的空白行使用 `@BlankLineBefore`。Java 字符串中的 `"\\n"` 是字面文本，会原样保留。

## 注意事项

- YAML 根节点必须是 mapping。
- 暂不支持多文档 YAML。
- `Route.from(...)` 不能创建空路由；需要空路由时使用 `Route.empty()`。
- `get(...)` 在目标路径缺失时会抛出 `MissingNodeException`；节点存在但序列化器解析失败时会抛出对应的 `NodeParsingException` 子类，例如 `InvalidNodeException` 或 `UnexpectedNodeParsingException`。
- `getOrDefault(...)` 只在目标路径缺失时返回默认值；节点存在但解析失败时不会吞掉异常。
- 内置的字符串承载类型，例如 `UUID`、`Locale`、时间类型，不再把空字符串读取为 `null`；空字符串会按无效值抛出 `InvalidNodeException`。
- `serialize(null)` 会生成 null 值节点；保存文档时 null 值节点会被跳过，不写入最终 YAML。
- `ElementComment` / `InlineElementComment` / `AfterElementComment` 当前只是注解定义，运行期 mapper 尚未把它们应用到集合元素。

## License

Sparrow YAML 使用 GPL-3.0 license。详见 `LICENSE`。
