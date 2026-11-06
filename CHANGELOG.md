# Changelog

## 1.3.1

### Changed

- **The link is drawn with vanilla dust particles** in the highlight colour instead of own geometry.
  They read as a connection rather than as part of the drive, need no custom render type, and are
  visible through the casing.
- **The controller now looks exactly like a drive**: translucent shell plus a bright edge outline.
  The edges are built from thin tubes instead of GL lines so their thickness does not depend on the
  driver.

### Fixed

- **Slot outline was drawn too early.** `ScreenEvent.Render.Post` fires before the LDLib2 panel is
  painted for container screens, so anything drawn there ended up underneath the GUI. The overlay
  now hooks `ContainerScreenEvent.Render.Foreground`, which fires at the very end of
  `AbstractContainerScreen#render`; `ScreenEvent.Render.Post` is kept only for non-container
  screens.
- The drawn rectangle is logged (`frame x= y= w= h=`), so a wrong offset is a one-line fix instead
  of another guessing round.

## 1.3.0

### Changed

- **Link is a straight beam that ignores the depth buffer.** Routing it over the structure was a
  workaround for occlusion; the geometry now uses a dedicated render type built on
  `RenderStateShard.NO_DEPTH_TEST` + `COLOR_WRITE`, so the line pierces the multiblock casing
  instead of detouring around it. It is also thicker and near-opaque.
- **The controller block is highlighted too**, with the same through-wall shell, so both ends of
  the link are marked.

### Fixed

- **Controller GUI slot outline, third attempt.** `getAllElements()` only returns elements the
  `ModularUI` was told about, and this panel is built from a JSON layout, so the cell grid was never
  in that list. The overlay now walks the element tree from `UI.rootElement` in addition to the
  registry, and deduplicates by identity. When no grid is found it logs the element classes it did
  see.

## 1.2.2

### Fixed

- **Slot outline in the controller GUI.** The storage host opens
  `ModularUIContainerScreen`, which has no `getModularUI()` at all — the `ModularUI` hangs off its
  menu (`ModularUIContainerMenu.modularUI`). The accessor is now discovered per screen class:
  method, then field, then the container menu. This was the reason the overlay logged
  `-> not a modular UI, skipped` and drew nothing.
- **Link line was invisible.** The drive and its controller sit inside the same structure, so a
  straight segment between them was hidden by the casing. The link is now routed over the top of
  the structure (up, across, down) and is thicker and more opaque.

### Diagnostics

- Every scan reports `link=` and `hostSlot=`, the renderer reports `refs=` / `withLink=`, and the
  controller overlay reports the screen class, element count, grid count, page size and match result.

## 1.2.1

### Fixed

- **Link line could stay invisible.** `getStorageController()` only returns something once the
  addon has rebuilt its cluster reference for that drive, which is not guaranteed at scan time.
  The controller is now also located by searching the surrounding chunks for a controller whose
  cell list contains the drive, so the line appears even when the direct accessor is empty.
- **Slot outline never appeared in the controller GUI.** The overlay only accepted the exact
  `ModularUIScreen` class; it now accepts any LDLib2 screen that exposes `getModularUI()`, which
  covers the container-backed variant the storage host actually uses.

### Diagnostics

- Every scan line now reports `link=` and `hostSlot=`, the renderer logs `refs=/withLink=`, and the
  controller overlay logs one line per distinct state. All are change-triggered, so they stay quiet
  during normal play.

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
