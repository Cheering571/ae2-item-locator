# AE2 Item Locator

[English](README.md) · **简体中文**

**不用再一张张拔磁盘了。**

AE2 Item Locator 回答了应用能源 2（AE2）本身无法回答的问题：**这个物品到底存在哪一块物理磁盘里？**
在任意 AE2 终端里悬停物品，物品信息栏就会告诉你它所在的驱动器坐标和槽位；按一个键，驱动器就会在
世界里亮起来——穿墙、跨基地，甚至跨维度。

- 不添加方块、物品和配方，只是给现成的终端加了一层能力。
- 兼容原版驱动器、ME 箱子，以及附属模组的驱动器（如 ExtendedAE 的 20 槽扩展驱动器）。
- 会沿 Storage Bus 桥接进入子网，主网里能看到的子网物品也能定位到子网里那张盘。
- 懂多方块：装在 Neo ECO AE 存储子系统主机里的驱动器会连一条线指向主机，并且在主机自己的界面里
  把对应格子框出来。
- 实时跟随：拔出磁盘高亮立即消失；插到另一个驱动器，高亮立刻跟过去。

## 功能

| | |
|---|---|
| **信息栏显示磁盘与槽位** | 每个网络物品都会多出一段：驱动器坐标、槽位号、磁盘类型。 |
| **世界内高亮** | 半透明外壳 + 边框描边 + 可选光柱。关掉终端依然保留。 |
| **槽位描边** | 打开驱动器界面，装该物品的那张盘所在槽位会被框出来，直接拔就对。 |
| **识别子网** | 沿 Storage Bus 桥接遍历，来自子网的结果会标注「（子网）」。 |
| **不挑驱动器** | 只要实现 AE2 的 `IChestOrDrive` 就能用，包括槽位布局自定义的附属驱动器；此外也支持 Neo ECO AE 的存储矩阵驱动器这类「单格存储方块」。 |
| **识别多方块** | 属于某个控制器的驱动器会画出连线，控制器方块本身同样高亮，对应格子会在控制器界面里被框出——在正确的页上，不在当前页时会提示页码。 |
| **颜色、彩虹与提示位置** | 颜色可统一或分开配（光柱 / 驱动器 / 槽位），每一项还能单独开启彩虹轮换；提示文字可拖动到任意位置。 |

## 截图

游戏内界面自带英文与中文，以下截图使用中文语言设置。

<p align="center">
  <img src="docs/screenshots/tooltip.png" width="82%" alt="物品信息栏列出所在驱动器">
</p>

<p align="center">
  <em>信息栏列出该物品的每一个来源驱动器、坐标和槽位；经由子网到达的物品会标注
  <code>（子网）</code>。</em>
</p>

<p align="center">
  <img src="docs/screenshots/world-highlight.jpg" width="100%" alt="世界中高亮的驱动器">
</p>

<p align="center">
  <em>按 <code>H</code> 锁定高亮：半透明外壳、边框描边和光柱，关掉终端也不会消失。</em>
</p>

<p align="center">
  <img src="docs/screenshots/rainbow.gif" width="78%" alt="彩虹轮换效果">
</p>

<p align="center">
  <em>开启彩虹轮换后，光柱、方块高亮、连线与槽位描边共用同一种颜色，一起沿色环转动——
  六秒一圈。</em>
</p>

<p align="center">
  <img src="docs/screenshots/drive-slots.png" width="49%" alt="驱动器槽位描边">
  <img src="docs/screenshots/config-unified.png" width="49%" alt="统一颜色的设置界面">
</p>

<p align="center">
  <em>左：驱动器界面里对应的槽位被框出。右：光柱、驱动器、槽位共用一种颜色，并带有独立的
  <code>RGB</code> 开关。</em>
</p>

<p align="center">
  <img src="docs/screenshots/config-separate.png" width="49%" alt="分别设置颜色的界面">
</p>

<p align="center">
  <em>关闭「统一配置高亮颜色」后，光柱 / 驱动器 / 槽位可以分别设置——RGB 滑条加十六进制输入，
  每一行还有 <code>RGB</code> 开关可让该颜色沿色环循环，全部即时生效。</em>
</p>

## 兼容性

| Minecraft | 加载器 | NeoForge | AE2 | 状态 |
|---|---|---|---|---|
| 1.21.1 | NeoForge | 21.1.169+ | 19.2.18 – 19.x | 支持 |

可选联动：**Neo ECO AE Extension** 21.2.x —— 它的存储矩阵驱动器（`eco_drive`）虽然没有实现
`IChestOrDrive`，同样能被定位；它的存储子系统主机界面也会显示槽位描边。

需要 **应用能源 2** 及其前置 **GuideME**。本模组必须在**客户端和服务端同时安装**（单人游戏自动满足）。
只装一侧时功能会静默关闭，不会报错。

## 适配的模组

识别是通用的：凡是实现了 AE2 `IChestOrDrive` 的方块实体，都会逐个槽位读取，槽位里的存储单元不管来自
哪个模组都会被检查。下表列出的是**已在实际模组上验证过**的组合。

| 模组 | 适配内容 | 识别方式 |
|---|---|---|
| **应用能源 2**（必需） | ME 驱动器（10 槽）、ME 箱子 | 原生 `IChestOrDrive` / `IMEChest` |
| **ExtendedAE** | 扩展驱动器 Ex Drive —— 20 槽，**左右两列都支持**，槽位描边可用 | `TileExDrive extends DriveBlockEntity` |
| **MEGA Cells** | Cell Dock 部件（装在 cable 上的单元座） | `CellDockPart implements IChestOrDrive` |
| **Neo ECO AE Extension** | `eco_drive` 及其存储子系统主机：连线、主机高亮、控制器界面内槽位描边、页码提示 | 未实现 `IChestOrDrive`，通过 `getCellStack()` / `getCellInventory()` 契约探测 |
| **AE2 Things**、**MEGA Cells** 大宗单元、以及任何其他存储单元 | 定位到装它的那个驱动器 | 直接向驱动器查询每个槽位的内容，与单元来自哪个模组无关 |
| **其他任何**提供驱动器的附属模组 | 自动识别 | 只要实现 `IChestOrDrive` |

未覆盖的情况：

- **Storage Bus 指向非 AE 容器**（抽屉、木桶、箱子）—— 它们不是驱动器，物品没有物理磁盘可指。
  物品在终端里依然可见。
- **既没有实现 `IChestOrDrive`、也不符合 `getCellStack()` / `getCellInventory()` 契约的存储方块** ——
  比如只暴露聚合存储视图的某些多方块。遇到这类可以带模组名提 issue。

## 安装

1. 为 Minecraft 1.21.1 安装 NeoForge 21.1.169 或更高版本。
2. 把 [应用能源 2](https://modrinth.com/mod/ae2) 和 GuideME 放进 `mods/`。
3. 把 `ae2itemlocator-1.4.1.jar` 放进 `mods/`。

## 使用

1. 打开任意 ME 终端，悬停物品。信息栏会多出类似这样的一段：
   ```
   该物品存在于 2 个磁盘槽位
     挖矿维度 (-7, 254, 27) 槽位 0（子网）
     主世界 (-14, 255, 30) 槽位 0
   ```
2. 按 **H**（可改键）锁定高亮。锁定与界面无关：关掉终端走去驱动器即可。
   - 拔出磁盘 → 高亮消失。
   - 插到另一个驱动器 → 高亮跟过去。
   - 再按一次 **H** 取消。
3. 打开驱动器界面，对应槽位会被框出。装在多方块控制器里的驱动器则在控制器界面里被框出，
   即使目标在别的页也会提示页码。
4. 按 **K**（可改键）打开设置：颜色可统一或分开配，每一项都有 `RGB` 开关可让该颜色沿色环循环，
   还能调整屏幕提示文字的位置。

## 配置

`config/ae2itemlocator-server.toml`

| 键 | 默认 | 说明 |
|---|---|---|
| `allowQuery` | `true` | 关闭后对所有人停用本功能。 |
| `cellsPerTick` | `12` | 建立索引时每 tick 扫描的磁盘槽位数。调低更顺滑，首次查询更慢。 |
| `cacheSeconds` | `10` | 索引最长存活时间；内容变化本身会被自动检测。 |

`config/ae2itemlocator-client.toml`

| 键 | 默认 | 说明 |
|---|---|---|
| `maxSources` | `3` | 信息栏里最多列几条驱动器。 |
| `beam` | `true` | 是否绘制光柱。 |
| `highlightSeconds` | `0` | 高亮自动取消时间（秒），`0` 表示一直保留到再按一次键。 |
| `colors.unified` | `true` | 是否统一颜色。 |
| `colors.unifiedValue` | `#66D9FF` | 统一时使用的颜色。 |
| `colors.beam` / `drive` / `slot` | `#66D9FF` | 不统一时各部分的颜色。 |
| `colors.rainbowUnified` | `false` | 统一颜色是否沿彩虹循环。 |
| `colors.rainbowBeam` / `rainbowDrive` / `rainbowSlot` | `false` | 不统一时各部分是否沿彩虹循环。 |
| `hud.x` / `hud.y` | `8` / `8` | 提示文字位置，单位为屏幕尺寸的千分比。 |

## 实现原理

AE2 的网络存储（`IStorageService#getInventory()`）是一个聚合视图，**它刻意不记录物品来自哪里**。
唯一知道来源的地方是驱动器本身，所以本模组在服务端重建了一份「物品 → (驱动器, 槽位)」目录：

```
终端菜单 -> grid -> IChestOrDrive 宿主 -> 各槽位 MEStorage -> getAvailableStacks()
```

对于那些把内容存在存储单元物品里、却不实现 `IChestOrDrive` 的附属驱动器（Neo ECO AE 的存储矩阵
驱动器），则通过它们共有的 `getCellStack()` / `getCellInventory()` 契约识别，同样纳入目录。

建立这份目录并不便宜，所以：

- 分散到多个服务端 tick 里完成（每 tick 扫几个槽位），并按网络缓存；
- 网络内「不同物品数量」变化时自动失效；
- 锁定高亮期间改用**单键定向扫描**回答（250 ms 一次），这也是高亮能跟随磁盘移动的原因；
- 额外沿 Storage Bus 进入子网，并用一个**有界的按位置扫描**兜底（扫描网络周围已加载区块里的驱动器），
  覆盖图遍历看不到的桥接方式。

客户端只会收到「这个物品在哪些驱动器里」，永远拿不到整份索引。

控制器界面是 LDLib2 的 `ModularUI`，不是原版容器菜单，所以槽位描边由一层专门的覆盖绘制完成：
面板的格子网格把每个 `CellSlot` 的序号映射到 `CellEntry`，再用条目的坐标与结果集比对。LDLib2 的
`getPositionX/Y` 本身已经是屏幕绝对坐标，覆盖层只需要这一个数。

## 已知限制

- **Storage Bus 指向非 AE 容器**（抽屉、木桶、箱子）时它们不算驱动器，因此不会被报出来。物品在终端里
  依然可见，只是没有磁盘位置。
- **未加载的区块**不会被索引；没人加载过的区块里的驱动器找不到。
- 高亮使用正常深度测试，被墙挡住时外框会被遮挡——用来导航的是升到地表以上的光柱。跨维度结果只显示文字。
  控制器本身的高亮是例外：它是穿透绘制的，所以在多方块内部也能看见。
- 按位置兜底可能会列出**另一张网络**里的驱动器（前提是它正好建在你基地旁边，且恰好也存了同样的物品），
  这类结果会被标记为子网。
- **单格存储方块**（Neo ECO AE 存储矩阵驱动器）没有槽位概念，信息栏会显示方块坐标和存储单元名称而不是
  槽位号，驱动器界面里的槽位描边对它也不适用——描边会出现在控制器界面里。
- 控制器界面的覆盖层是反射读取附属模组面板类的；如果那边把 UI 结构大改，描边会退化成什么都不画，
  而不是报错。

## 从源码构建

```bash
# 1. 把 AE2 的 jar 放进 libs/（开发运行还需要 GuideME）
#    libs/appliedenergistics2-19.2.18.jar
#    libs/guideme-21.1.22.jar
# 2. 构建
gradle build            # 或 ./gradlew build
```

产物为 `build/libs/ae2itemlocator-<版本>.jar`，不会打包任何 AE2 或 GuideME 的类。

## 许可

MIT，见 [LICENSE](LICENSE)。本项目与 Team AppliedEnergistics 无隶属关系。
