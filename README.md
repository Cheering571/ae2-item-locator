# AE2 Item Locator

**English** · [简体中文](README.zh-CN.md)

**Stop pulling disks one by one.**

You know the moment. The terminal says you have 12,000 end stone, and somewhere in a wall of
drives, one specific disk is holding it. So you start pulling them out, checking the tooltip,
putting them back.

This mod kills that loop. Hover an item in any AE2 terminal and the tooltip tells you the drive's
coordinates and which slot it sits in. Press one key and that drive lights up in the world — through
walls, across the base, even in another dimension. Pull the disk and the highlight vanishes; drop it
in another drive and the highlight follows it there.

- Nothing new to craft. No blocks, no items, no recipes — it just makes the terminals you already
  have a bit smarter.
- Vanilla drives, ME chests and addon drives all work, including ExtendedAE's 20-slot drive.
- Storage-bus bridges are followed, so an item that reaches your main network from a subnet gets
  traced to the subnet's own disk.
- Multiblocks too: a drive inside Neo ECO AE's storage subsystem host gets a link line to the host,
  and its cell gets framed inside the host's own GUI.

## Features

| | |
|---|---|
| **Where it lives** | Hover any network item and the tooltip grows a section: drive coordinates, slot number, cell type. |
| **Seeing it in the world** | A soft shell, a bright edge and an optional beacon beam mark the drive. Closing the terminal doesn't clear it. |
| **Which disk to pull** | Open the drive and the right slot is outlined. No more guessing. |
| **Subnets included** | Storage-bus bridges are walked, and results that came through a subnet say so. |
| **Any drive, really** | Anything implementing AE2's `IChestOrDrive` works, custom slot layouts included — plus single-cell storage blocks like Neo ECO AE's storage matrix drives. |
| **Multiblocks included** | Drives owned by a controller get a link line, the controller block lights up too, and the matching cell is framed inside the controller's GUI — on the right page, or with a note saying which page it's on. |
| **Make it yours** | One colour for everything or separate beam / drive / slot colours, each with an optional rainbow cycle, plus a status text you can drag anywhere. |

## Screenshots

The in-game UI ships in English and Chinese; these were taken with Chinese selected.

<p align="center">
  <img src="docs/screenshots/tooltip.png" width="82%" alt="Item tooltip listing the drives that hold the item">
</p>

<p align="center">
  <em>Every drive holding the item, with coordinates and slot. Anything that came in through a
  subnet is marked <code>(subnet)</code>.</em>
</p>

<p align="center">
  <img src="docs/screenshots/world-highlight.jpg" width="100%" alt="Locked drive highlighted in the world">
</p>

<p align="center">
  <em>Press <code>H</code> to lock it: shell, edge and beam, still there after you close the
  terminal.</em>
</p>

<p align="center">
  <img src="docs/screenshots/rainbow.gif" width="78%" alt="Rainbow colour cycle across beam, blocks and link lines">
</p>

<p align="center">
  <em>Turn on the rainbow cycle and the beam, the blocks, the link lines and the slot outline all
  move through the hue wheel together — one full turn every six seconds.</em>
</p>

<p align="center">
  <img src="docs/screenshots/drive-slots.png" width="49%" alt="Drive slot outline">
  <img src="docs/screenshots/config-unified.png" width="49%" alt="Settings with one colour for everything">
</p>

<p align="center">
  <em>Left: the matching slot outlined in the drive. Right: one colour for everything, with its own
  <code>RGB</code> toggle.</em>
</p>

<p align="center">
  <img src="docs/screenshots/config-separate.png" width="49%" alt="Settings with separate colours">
</p>

<p align="center">
  <em>Untick "one colour" and the beam, the drive highlight and the slot outline each get their own
  colour — RGB sliders plus a hex box, and an <code>RGB</code> switch per row to cycle that one
  through the rainbow. Everything applies as you drag.</em>
</p>

## Compatibility

| Minecraft | Loader | NeoForge | AE2 | Status |
|---|---|---|---|---|
| 1.21.1 | NeoForge | 21.1.169+ | 19.2.18 – 19.x | Works |

You'll need **Applied Energistics 2** and **GuideME**. Install the mod on **both** sides — in
singleplayer that just happens. With only one side installed it goes quiet instead of throwing
errors at you.

## Supported mods

Detection is deliberately dumb and generic: anything that implements AE2's `IChestOrDrive` gets read
slot by slot, and every cell inside it gets inspected, no matter which mod that cell came from.
Here's what has actually been tested against real mods:

| Mod | What works | How it's found |
|---|---|---|
| **Applied Energistics 2** *(required)* | ME Drive (10 slots), ME Chest | native `IChestOrDrive` / `IMEChest` |
| **ExtendedAE** | Ex Drive — 20 slots, **both columns**, slot outline included | `TileExDrive extends DriveBlockEntity` |
| **MEGA Cells** | Cell Dock (the cable-mounted cell holder) | `CellDockPart implements IChestOrDrive` |
| **Neo ECO AE Extension** | `eco_drive` and its storage subsystem host: link line, host highlight, slot outline in the controller GUI, page hint | not `IChestOrDrive`; probed via `getCellStack()` / `getCellInventory()` |
| **AE2 Things**, **MEGA Cells** bulk cells, any other cell | found wherever it's plugged in | the drive is asked per slot, so the cell's origin doesn't matter |
| **Any other addon** adding a drive | should just work | implements `IChestOrDrive` |

What it can't do:

- **Storage buses pointing at non-AE inventories** (drawers, barrels, plain chests) — those aren't
  drives, so there's no disk to point at. The item still shows up in your terminal, it just has no
  home to highlight.
- **Cell holders that are neither `IChestOrDrive` nor follow the `getCellStack()` /
  `getCellInventory()` contract** — a multiblock that only publishes an aggregate storage view, for
  instance. If you run into one, open an issue with the mod name.

## Installation

1. NeoForge 21.1.169 or newer, for Minecraft 1.21.1.
2. Drop [Applied Energistics 2](https://modrinth.com/mod/ae2) and GuideME into `mods/`.
3. Drop `ae2itemlocator-1.4.1.jar` in there too.

## Usage

1. Open any ME terminal and hover something. The tooltip picks up a section like this:
   ```
   Stored in 2 drive slots
     Mining Dimension (-7, 254, 27) slot 0 (subnet)
     Overworld (-14, 255, 30) slot 0
   ```
2. Hit **H** (rebindable) to lock the highlight. The lock doesn't care about the GUI — close the
   terminal and go for a walk.
   - Pull the disk out → the highlight disappears.
   - Put it in another drive → the highlight moves with it.
   - Hit **H** again to clear it.
3. Open the drive and the matching slot is outlined. If the drive lives inside a multiblock
   controller, look in the controller's GUI instead — it'll be framed there, and it tells you which
   page when it isn't on the one you're looking at.
4. Hit **K** (rebindable) for settings: one colour or separate ones, an `RGB` switch per colour to
   cycle it, and where the on-screen status text sits.

## Configuration

`config/ae2itemlocator-server.toml`

| Key | Default | What it does |
|---|---|---|
| `allowQuery` | `true` | Turn the whole thing off for everyone. |
| `cellsPerTick` | `12` | Cells scanned per tick while an index is being built. Lower is smoother, at the cost of a slower first lookup. |
| `cacheSeconds` | `10` | How long an index may live before a rebuild. Changes are picked up automatically anyway, so this is just a safety net. |

`config/ae2itemlocator-client.toml`

| Key | Default | What it does |
|---|---|---|
| `maxSources` | `3` | How many drives get listed in a tooltip. |
| `beam` | `true` | The vertical light column. |
| `highlightSeconds` | `0` | Auto-clear the lock after N seconds; `0` keeps it until you press the key again. |
| `colors.unified` | `true` | One colour for everything. |
| `colors.unifiedValue` | `#66D9FF` | The colour used while unified. |
| `colors.beam` / `drive` / `slot` | `#66D9FF` | Per-part colours when not unified. |
| `colors.rainbowUnified` | `false` | Cycle the unified colour through the rainbow. |
| `colors.rainbowBeam` / `rainbowDrive` / `rainbowSlot` | `false` | Cycle each part's colour when not unified. |
| `hud.x` / `hud.y` | `8` / `8` | Where the status text sits, in permille of the screen size. |

## How it works

AE2's network storage (`IStorageService#getInventory()`) is an aggregated view on purpose — it
throws away where a stack came from. The drive itself is the only thing that still knows, so the mod
rebuilds a "key → (drive, slot)" catalogue on the server:

```
terminal menu -> grid -> IChestOrDrive hosts -> per-slot MEStorage -> getAvailableStacks()
```

Addon drives that keep their contents in a cell item without implementing `IChestOrDrive` (Neo ECO
AE's storage matrix drives) are picked up through the `getCellStack()` / `getCellInventory()`
contract they share, so they end up in the same catalogue.

Building that catalogue isn't free, so it is

- sliced across server ticks (a few cells per tick) and cached per network,
- invalidated when the network's distinct key count changes,
- answered from a fresh targeted scan while a highlight is locked (250 ms), which is what lets the
  highlight chase a disk around,
- extended with subnetworks reachable through a storage bus, plus a bounded positional fallback
  (drives in the loaded chunks around the network) for bridges the graph walk can't see.

The client only ever receives "which drives hold *this* key" — never the whole index.

The controller GUI is an LDLib2 `ModularUI`, not a vanilla container menu, so its slot outline comes
from a dedicated overlay: the panel's cell grid maps each `CellSlot` index to a `CellEntry`, and the
entry's position is compared with the answer set. `getPositionX/Y` from LDLib2 is already the
absolute GUI coordinate, which is all the overlay needs.

## Known limitations

- **Storage buses onto non-AE inventories** (drawers, barrels, chests) aren't drives, so they're not
  reported. The item still shows in the terminal — it just has nowhere to point.
- **Unloaded chunks** aren't indexed. A drive in a chunk nobody has loaded can't be found.
- Highlights use normal depth testing, so a box is hidden behind walls — the beacon beam above the
  terrain is what you navigate by. Cross-dimension results are text only. The controller is the
  exception: its highlight is drawn through walls so it stays visible inside the multiblock.
- The positional fallback can list a drive from a *different* network that happens to sit right next
  to yours and happens to hold the same item. It's labelled as a subnet result.
- **Single-cell storage blocks** (Neo ECO AE storage matrix drives) have no slot, so the tooltip
  shows the block coordinates and the cell's name instead of a slot number, and the drive-GUI outline
  doesn't apply — it shows up in the controller's GUI instead.
- The controller GUI overlay reads the addon's panel classes reflectively. If that UI gets a big
  rewrite, the outline will quietly draw nothing rather than crash.

## Building from source

```bash
# 1. drop the AE2 jar into libs/  (plus GuideME if you want to run dev builds)
#    libs/appliedenergistics2-19.2.18.jar
#    libs/guideme-21.1.22.jar
# 2. build
gradle build            # or ./gradlew build
```

Output is `build/libs/ae2itemlocator-<version>.jar`. No AE2 or GuideME classes are bundled.

## License

MIT — see [LICENSE](LICENSE). Not affiliated with Team AppliedEnergistics.
