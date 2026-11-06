# Changelog

## 1.3.11

### Fixed

- **Slot frame overshot the bottom edge by one pixel.** Left, right and top already lined up with
  the slot border, but the slot's own bottom border sits one pixel inside the cell boundary. The
  frame's height is shortened by that single pixel; the width is untouched.

## 1.3.10

### Fixed

- **Slot frame overhung the bottom of the slot.** The height was measured from the vertical spacing
  between rows, which is larger than a slot, so the frame grew two pixels too tall while the width
  was already correct. A slot is square, so the horizontally measured size is reused for the height;
  the vertical measurement is only used when the icon itself is not square.

## 1.3.9

### Fixed

- **Slot frame alignment.** The frame was centred on the icon, but the slot is anchored at the
  element origin: Neo ECO AE's own hover highlight draws
  `NETextures.AE2_SLOT_HIGHLIGHT` at `(0, 0, 16, 16)` and `ItemSlot` draws the item at `(0, 0)` too.
  Centring therefore pushed the frame two pixels left and up of the real slot border. The frame is
  now top-left anchored at the slot position with a size of `max(slot pitch, icon + 2)` — 18x18 for
  a normal grid — which lands exactly on the visible slot border.

## 1.3.8

### Fixed

- **Slot frame size, properly this time.** A `CellSlot` reports only its 16x16 icon area, and the
  gap between two slots equals the icon pitch, so both were smaller than the slot graphic drawn
  around it. The cell size now comes from the grid itself — `gridWidth / columns` and
  `gridHeight / rows` — with the position pitch and finally `icon + 4` as fallbacks. The frame is
  centred on the icon, so it covers the whole slot.

## 1.3.7

### Fixed

- **Only one controller slot was framed when several drives held the item.** `drawForGrid` stopped
  at the first match. It now collects every slot whose drive position is in the answer set and
  frames all of them, which matches the vanilla/ExtendedAE behaviour where the drive overlay
  highlights the whole set rather than one entry.

## 1.3.6

### Fixed

- **Controller slot frame was smaller than the slot.** A `CellSlot` reports a 16x16 icon area, but
  the grid cell it occupies is one pitch wide, so the frame hugged the icon instead of the slot. The
  size is now derived from the neighbouring slots (smallest positive gap between distinct
  coordinates) and the frame is centred on the icon, which makes it match the cell exactly. A
  single-cell grid falls back to the icon size plus the usual 1px border.

## 1.3.5

### Fixed

- **Controller slot frame position.** `getLocalToWorldPose()` returns an identity matrix outside
  the UI draw pass — the diagnostic showed `m30 = m31 = 0` — so every formula built on it was
  operating on nothing. The frame now uses `getPositionX/Y` (which already carries the absolute GUI
  coordinate) minus the `ModularUI` origin, because the `GuiGraphics` pose at this point is
  translated by exactly that origin.
- **Diagnostic log spam.** The probe compared each message against the previous one, but the frame
  and match messages alternate every frame, so both looked new and were written 60+ times a second.
  Messages are now deduplicated in a small set (cleared every 24 distinct entries), so an open GUI
  writes a handful of lines instead of megabytes.

## 1.3.4

### Fixed

- **Controller slot frame disappeared in 1.3.3.** That build summed every ancestor's
  `getPositionX/Y`, but those values already include their ancestors, so the sum multiplied the
  coordinate and pushed the frame off-screen. The frame is drawn from the element's
  `getLocalToWorldPose()` alone now: no origin term, no accumulation. A sanity fallback keeps a
  frame visible even if a coordinate comes back absurd.

## 1.3.3

### Fixed

- **Controller slot frame was offset by roughly six cells.** The rect combined the element's
  local-to-world pose with the `ModularUI` origin, but the pose already resolves to the screen's GUI
  space, so the origin was counted twice. The position now accumulates
  `getPositionX()/getPositionY()` up the parent chain, which is exactly how LDLib2 lays the tree out,
  and adds no origin term.
- **The frame is coloured like the drive outline.** It used a hard-coded white; it now uses
  `ClientConfig.slotColor()` with the same 0x33 fill plus a 1px border, so changing the slot colour
  in the config screen affects both places.

## 1.3.2

### Changed

- **Particles are gone.** Vanilla particles are drawn depth-tested, so they cannot pierce the
  multiblock casing — the whole point of the link. The straight beam is back, but as **three nested
  tubes**: a bright thin core plus two wider, fainter halos. The falloff removes the "plastic rod"
  look of the previous single tube.
- **The controller now matches a drive exactly**: same shell alpha (0.18) and the same two edge
  boxes (alpha 1.0 / 0.55, inflate 0.002 / 0.010). The edges are thin tubes (0.004 / 0.006 half
  width) instead of the previous thick bar, so its weight reads like the drive's line boxes.

### Fixed

- The controller grid is recognised **structurally** now: an element that offers `cell(int)` and has
  a `CellSlot` child counts as a grid, whatever the JSON layout wrapped it in. Entries are resolved
  through `cell(index)` — the same call the panel itself uses to paint the slot — instead of reaching
  into the sync object, which removes two assumptions.

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
