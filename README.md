# Schemfinder

A **MaLiLib addon** for **Minecraft 26.1.2** (Fabric) that scans the loaded world for blocks and
structures matching your saved patterns — and shows you where they are.

> ⚠️ Work in progress. The single-block finder works today; multi-block structure capture,
> rotation/mirror matching, in-world highlight boxes, and the management GUI are in active development.

## Current features (v0.1)
- **Single-block finder**: look at a block, set it as the target, scan loaded chunks within a
  configurable radius. Matches are printed to chat with coordinates and distance.
- Fast section-palette culling so scanning stays performant.
- Asynchronous scan (runs off the main thread — no freeze).

## Controls
- **T** — set the target to the block you're looking at
- **G** — scan now
- Rebindable in Options → Controls → Gameplay.

## Planned
- Capture multi-block structures by selecting a region (two corners)
- Match with X/Y/Z rotations + mirrors (each toggleable)
- Strictness modes: exact blockstate / block-type only / ignore-air wildcard
- In-world highlight boxes (MaLiLib rendering), on-screen HUD list, optional beacon beams
- Continuous (auto) scan mode + manage-patterns GUI

## Requirements
- Minecraft 26.1.2, Fabric Loader 0.19.2+
- Fabric API
- [MaLiLib](https://modrinth.com/mod/malilib) 0.28.6+

## Credits
Built by [Limucc-dev](https://github.com/dev-limucc). Uses [MaLiLib](https://github.com/maruohon/malilib) by masa.
