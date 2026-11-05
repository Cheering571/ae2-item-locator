# Changelog

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
