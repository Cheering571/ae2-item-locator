# AE2 Item Locator

[English](README.md) · **简体中文**

**别再一张一张拔磁盘了。**

在 AE2 里找一个物品存放在哪块磁盘上，目前只能手动操作：终端能显示物品，却不显示它的位置，唯一确认的
办法就是一张张拔出来看。本模组把这件事简化成一次悬停——信息栏会给出驱动器坐标与槽位，按一个键即可在
世界中高亮该驱动器。

- 不添加方块、物品或配方，仅扩展现有终端的能力。
- 支持原版驱动器、ME 箱子以及附属模组的驱动器，包括 ExtendedAE 的 20 槽扩展驱动器。
- 会沿 Storage Bus 桥接进入子网，从子网进入主网的物品同样能追溯到子网中的那张磁盘。
- 支持多方块结构：安装在 Neo ECO AE 存储子系统主机中的驱动器会绘制一条指向主机的连线，并在主机界面
  内框出其对应格子。

## 功能

| | |
|---|---|
| **信息栏显示驱动器与槽位** | 每个网络物品的信息栏会新增一段内容：驱动器坐标、槽位号与磁盘类型。 |
| **世界内高亮** | 半透明外壳、边框描边以及可选光柱标记驱动器；关闭终端后高亮仍然保留。 |
| **槽位描边** | 打开驱动器界面时，存放该磁盘的槽位会被框出。 |
| **识别子网** | 会沿 Storage Bus 桥接遍历，经由子网到达的结果会被标注。 |
| **不限于特定驱动器** | 支持任何实现 AE2 `IChestOrDrive` 的驱动器，包括槽位布局自定义的附属驱动器；同时支持 Neo ECO AE 存储矩阵驱动器这类单格存储方块。 |
| **识别多方块** | 归属于控制器的驱动器会绘制指向控制器的连线，控制器方块本身同样高亮，对应格子会在控制器界面内被框出——位于正确页面，不在当前页时给出页码提示。 |
| **外观可配置** | 颜色可统一配置，也可分别设置光柱 / 驱动器 / 槽位；每项均可单独开启彩虹轮换；屏幕提示文字位置可拖动。 |

## 截图

游戏内界面提供中英双语，以下截图使用中文环境。

<p align="center">
  <img src="docs/screenshots/tooltip.png" width="82%" alt="物品信息栏列出所在驱动器">
</p>

<p align="center">
  <em>信息栏列出该物品所在的每一个驱动器、坐标与槽位；经由子网到达的物品会标注
  <code>（子网）</code>。</em>
</p>

<p align="center">
  <img src="docs/screenshots/world-highlight.jpg" width="100%" alt="世界中高亮的驱动器">
</p>

<p align="center">
  <em>按 <code>H</code> 锁定高亮：外壳、描边与光柱，关闭终端后依然保留。</em>
</p>

<p align="center">
  <img src="docs/screenshots/rainbow.gif" width="78%" alt="彩虹轮换效果">
</p>

<p align="center">
  <em>开启彩虹轮换后，光柱、方块高亮、连线与槽位描边共用同一种颜色，一同沿色环转动，六秒一圈。</em>
</p>

<p align="center">
  <img src="docs/screenshots/drive-slots.png" width="49%" alt="驱动器槽位描边">
  <img src="docs/screenshots/config-unified.png" width="49%" alt="统一颜色的设置界面">
</p>

<p align="center">
  <em>左：驱动器界面内对应槽位被框出。右：全部共用一个颜色，并带有独立的
  <code>RGB</code> 开关。</em>
</p>

<p align="center">
  <img src="docs/screenshots/config-separate.png" width="49%" alt="分别设置颜色的界面">
</p>

<p align="center">
  <em>关闭「统一配置高亮颜色」后，光柱、驱动器与槽位可分别设置——RGB 滑条加十六进制输入，
  每一行另有 <code>RGB</code> 开关用于循环该颜色。修改即时生效。</em>
</p>

## 兼容性

| Minecraft | 加载器 | NeoForge | AE2 | 状态 |
|---|---|---|---|---|
| 1.21.1 | NeoForge | 21.1.169+ | 19.2.18 – 19.x | 支持 |

需要 **应用能源 2** 与 **GuideME**。本模组必须在客户端与服务端同时安装，单人游戏自动满足；仅安装
一侧时功能会保持静默，不会报错。

## 适配的模组

识别方式是通用的：凡是实现 AE2 `IChestOrDrive` 的方块实体，都会逐个槽位读取，槽位中的存储单元无论
来自哪个模组都会被检查。下表列出的是已在实际模组上验证过的组合。

| 模组 | 支持内容 | 识别方式 |
|---|---|---|
| **应用能源 2**（必需） | ME 驱动器（10 槽）、ME 箱子 | 原生 `IChestOrDrive` / `IMEChest` |
| **ExtendedAE** | 扩展驱动器 Ex Drive —— 20 槽，**左右两列均支持**，包含槽位描边 | `TileExDrive extends DriveBlockEntity` |
| **MEGA Cells** | Cell Dock，即安装在 cable 上的单元座 | `CellDockPart implements IChestOrDrive` |
| **Neo ECO AE Extension** | `eco_drive` 及其存储子系统主机：连线、主机高亮、控制器界面内的槽位描边、页码提示 | 未实现 `IChestOrDrive`，通过 `getCellStack()` / `getCellInventory()` 契约识别 |
| **AE2 Things**、**MEGA Cells** 大宗单元，以及其他任何存储单元 | 定位到存放它的驱动器 | 直接查询驱动器各槽位内容，与单元来源模组无关 |
| **其他任何**提供驱动器的附属模组 | 无需额外适配 | 实现 `IChestOrDrive` 即可 |

未覆盖的情况：

- **Storage Bus 指向非 AE 容器**（抽屉、木桶、普通箱子）。这类方块不是驱动器，物品没有对应的物理
  磁盘可供指向，但在终端中依然可见。
- **既未实现 `IChestOrDrive`、也不符合 `getCellStack()` / `getCellInventory()` 契约的存储方块**，
  例如仅对外提供聚合存储视图的多方块结构。如遇到此类情况，欢迎附上模组名提交 issue。

## 安装

1. 为 Minecraft 1.21.1 安装 NeoForge 21.1.169 或更高版本。
2. 将 [应用能源 2](https://modrinth.com/mod/ae2) 与 GuideME 放入 `mods/`。
3. 将 `ae2itemlocator-1.4.1.jar` 放入 `mods/`。

## 使用

1. 打开任意 ME 终端并悬停物品，信息栏会新增如下内容：
   ```
   该物品存在于 2 个磁盘槽位
     挖矿维度 (-7, 254, 27) 槽位 0（子网）
     主世界 (-14, 255, 30) 槽位 0
   ```
2. 按 **H**（可改键）锁定高亮。锁定与界面无关，可关闭终端后前往目标位置。
   - 取出磁盘后高亮消失。
   - 插入其他驱动器后高亮随之移动。
   - 再次按 **H** 取消。
3. 打开驱动器界面即可看到对应槽位被框出。若该驱动器属于某个多方块控制器，描边将出现在控制器界面内；
   当目标不在当前页时会给出页码提示。
4. 按 **K**（可改键）打开设置：颜色可统一或分项配置，每项均可开启 `RGB` 轮换，并调整屏幕提示文字的
   位置。

## 配置

`config/ae2itemlocator-server.toml`

| 键 | 默认 | 说明 |
|---|---|---|
| `allowQuery` | `true` | 关闭后对所有人停用本功能。 |
| `cellsPerTick` | `12` | 建立索引时每个 tick 扫描的槽位数量。数值越低越平滑，但首次查询更慢。 |
| `cacheSeconds` | `10` | 索引的最长存活时间。内容变化本身会被自动检测，此项仅作兜底。 |

`config/ae2itemlocator-client.toml`

| 键 | 默认 | 说明 |
|---|---|---|
| `maxSources` | `3` | 信息栏中列出的驱动器数量。 |
| `beam` | `true` | 是否绘制竖直光柱。 |
| `highlightSeconds` | `0` | 经过指定秒数后自动取消锁定；`0` 表示保留至再次按键。 |
| `colors.unified` | `true` | 是否统一使用一种颜色。 |
| `colors.unifiedValue` | `#66D9FF` | 统一配置时使用的颜色。 |
| `colors.beam` / `drive` / `slot` | `#66D9FF` | 未统一时各部分的颜色。 |
| `colors.rainbowUnified` | `false` | 统一颜色是否沿彩虹循环。 |
| `colors.rainbowBeam` / `rainbowDrive` / `rainbowSlot` | `false` | 未统一时各部分是否沿彩虹循环。 |
| `hud.x` / `hud.y` | `8` / `8` | 提示文字位置，单位为屏幕尺寸的千分比。 |

## 实现原理

AE2 的网络存储（`IStorageService#getInventory()`）在设计上就是聚合视图，不记录物品来源。唯一掌握该
信息的是驱动器本身，因此本模组在服务端重建了一份「物品 →（驱动器, 槽位）」目录：

```
终端菜单 -> grid -> IChestOrDrive 宿主 -> 各槽位 MEStorage -> getAvailableStacks()
```

对于将内容存放在存储单元物品中、但未实现 `IChestOrDrive` 的附属驱动器（例如 Neo ECO AE 的存储矩阵
驱动器），则通过其共有的 `getCellStack()` / `getCellInventory()` 契约识别，同样纳入该目录。

建立目录有开销，因此：

- 分散到多个服务端 tick 中完成（每 tick 扫描若干槽位），并按网络缓存；
- 网络内「不同物品数量」发生变化时失效；
- 锁定高亮期间改为按物品进行定向扫描（250 ms 一次），这也是高亮能够跟随磁盘移动的原因；
- 额外沿 Storage Bus 进入子网，并辅以有界的按位置扫描（网络周围已加载区块内的驱动器），覆盖图遍历
  无法到达的桥接方式。

客户端始终只会收到「该物品位于哪些驱动器」，不会获得完整索引。

控制器界面是 LDLib2 的 `ModularUI`，而非原版容器菜单，因此槽位描边由一层独立的覆盖绘制完成：面板的
格子网格将每个 `CellSlot` 的序号映射到 `CellEntry`，再用条目坐标与结果集比对。LDLib2 的
`getPositionX/Y` 本身即为屏幕绝对坐标，覆盖层只需该值。

## 已知限制

- **Storage Bus 指向非 AE 容器**（抽屉、木桶、箱子）时不算驱动器，不会被报告。物品在终端中依然可见，
  但没有可指向的磁盘位置。
- **未加载的区块**不会进入索引；无人加载过的区块中的驱动器无法被找到。
- 高亮使用正常深度测试，被方块遮挡时会被挡住，导航主要依靠升至地表以上的光柱。跨维度结果仅以文字
  呈现。控制器是例外：其高亮采用穿透绘制，因此在多方块内部依然可见。
- 按位置扫描可能会列出**另一张网络**中的驱动器（前提是它紧邻你的基地，且恰好存有同一物品），这类结果
  会被标注为子网。
- **单格存储方块**（Neo ECO AE 存储矩阵驱动器）没有槽位概念，信息栏显示方块坐标与存储单元名称而非
  槽位号；驱动器界面的槽位描边对其不适用，描边会出现在控制器界面内。
- 控制器界面的覆盖层通过反射读取附属模组的面板类。若该界面结构发生较大改动，描边会静默地不绘制，
  而不会导致崩溃。

## 从源码构建

```bash
# 1. 将 AE2 的 jar 放入 libs/（如需运行开发环境，再加入 GuideME）
#    libs/appliedenergistics2-19.2.18.jar
#    libs/guideme-21.1.22.jar
# 2. 构建
gradle build            # 或 ./gradlew build
```

产物为 `build/libs/ae2itemlocator-<版本>.jar`，不会打包任何 AE2 或 GuideME 的类。

## 许可

MIT，见 [LICENSE](LICENSE)。本项目与 Team AppliedEnergistics 无隶属关系。
