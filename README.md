# Haven't I Seen This Before?  (HISTB?)

A **MaLiLib addon** for **Minecraft 26.1.2** (Fabric) that scans the loaded world for blocks and
structures matching your saved patterns — *wait, haven't I seen this before?* — and shows you exactly
where they are with in-world highlight boxes, chat coordinates, and a HUD.

## Features
- **Capture any structure** — select two corners and save the box between them as a named pattern.
- **Single-block finder** too — look at a block and scan for every copy of it.
- **Rotation & mirror matching** — find structures rotated around X / Y / Z and mirrored (each toggleable).
- **Match strictness** — *Exact* (block + state), *Block type only*, or *Ignore air* (air cells act as
  wildcards, great for finding buildings).
- **In-world highlight boxes** — **LINES** (wireframe), **FILLED** (translucent sides), or **BOTH** —
  with customizable color, opacity, line width, through-walls, and lifetime.
- **Chat coordinates** — clickable-style coords + distance + the matched orientation.
- **Patterns manager GUI** — checkbox to activate/deactivate each saved pattern, delete, and adjust
  match + highlight settings.
- **Fast & async** — section-palette culling + anchor-based matching, run off the main thread so the
  game never freezes. Bounded by a configurable radius.

## Controls (rebindable in Options → Controls → Gameplay)
| Key | Action |
|-----|--------|
| `[` | Set region **corner 1** (look at a block) |
| `]` | Set region **corner 2** (look at a block) |
| `K` | **Capture** the selected region as a pattern |
| `G` | **Scan** for active patterns (or the single-block target) |
| `O` | Open the **Patterns manager** |
| `T` | Set a single-block **target** (fallback finder) |

## How to use
1. Build or find a structure. Look at one corner and press `[`, the opposite corner and press `]`.
2. Press `K` to capture it — it's saved and marked active.
3. Press `G` to scan. Matches get highlight boxes + chat coordinates.
4. Press `O` to toggle which patterns are active, change highlight **Style**, strictness, rotations, etc.

## Requirements
- Minecraft 26.1.2, Fabric Loader 0.19.2+
- Fabric API
- [MaLiLib](https://modrinth.com/mod/malilib) 0.28.6+

## Credits
Built by [Limucc-dev](https://github.com/dev-limucc). Uses [MaLiLib](https://github.com/maruohon/malilib) by masa.
