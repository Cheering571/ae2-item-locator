# Changelog

## 1.2.0

### Added

- **Multiblock link line.** When a located drive belongs to a multiblock controller (Neo ECO AE's
  `eco_drive` + storage subsystem host), a thin beam is drawn from the drive to the controller, in
  the same colour as the drive highlight.
- **Slot outline inside the controller GUI.** The ECO storage host lists its drives in a paged
  grid; the slot holding the locked item is now outlined there, which is the only place those cells
  can actually be taken out.
- **Page hint.** When the target drive sits on another page of that grid, the GUI says which page
  it is on instead of highlighting the wrong slot.

### Notes

- Both features are purely reflective, so nothing extra is needed at build time and the mod keeps
  working when the addon is absent.
- The controller GUI is an LDLib2 `ModularUI`, not a vanilla container screen, so it needed its own
  render hook rather than the existing slot outline.

## 1.1.0

### Added

- **Addon storage blocks are now located.** Drives that keep their contents in a cell item but do
  not implement AE2's `IChestOrDrive` were invisible to the scan; they are now found through the
  `getCellStack()` / `getCellInventory()` contract that such blocks share.
  Reference case: **Neo ECO AE Extension**'s `eco_drive` with its ECO storage matrix cells, which
  previously reported "not stored in any drive".
  The tooltip shows the drive's coordinates and the installed cell's name instead of a slot number.

### Notes

- The probe is reflective so the mod keeps building without addon jars on the classpath.
- The drive-GUI slot outline only applies to drives whose menu is an AE2 drive menu; single-cell
  storage blocks have no slot to outline.

## 1.0.1

### Changed

- The NeoForge mod-list config screen is now localised: every entry and category carries an
  explicit translation key, so names and their tooltips follow the game language (English and
  Simplified Chinese) instead of always showing the English comments. The English comments remain
  as the fallback for other languages.

No behaviour changes; existing config files keep working unchanged.

## 1.0.0 — first public release

Minecraft 1.21.1 / NeoForge 21.1.169+ / Applied Energistics 2 19.2.18+

### Features

- Item tooltip in every AE2 terminal gains a "which drive holds this" section with drive
  coordinates, slot number and cell type.
- Lockable in-world highlight (**H**): translucent shell, double wireframe and an optional beacon
  beam. The lock survives closing the terminal.
- Drive GUI slot outline, so the exact disk to pull is marked.
- Works with vanilla drives, ME chests and addon drives, including ExtendedAE's 20-slot drive
  whose right-hand column uses a custom slot semantic.
- Subnetwork support: storage-bus bridges are followed and results are labelled `(subnet)`.
- Live tracking: pulling a disk removes the highlight, moving it to another drive moves the
  highlight, all within a fraction of a second.
- In-game settings screen (**K**): unified or per-part colours with RGB sliders and hex input, plus
  a draggable position for the on-screen status text.

### Technical notes

- The network catalogue is built on the server, sliced across ticks and cached per network; the
  client only ever receives answers for the key it asked about.
- Invalidated by a cheap "distinct key count" probe instead of scanning every tick.
- While a highlight is locked the server answers from a fresh targeted scan (250 ms cadence), which
  is independent of the cached catalogue.
- Subnetwork discovery combines storage-bus traversal, an adjacency walk and a bounded positional
  fallback over the loaded chunks around the network.
