# Haven't I Seen This Before?  (HISTB?)

A **MaLiLib addon** for **Minecraft 26.1.2** (Fabric) that continuously scans your loaded world for
structures matching your schematics and highlights every match in real time — like an X-ray for builds.
*Wait… haven't I seen this before?*

## How it works
1. Build & save a structure in **Litematica** (or any tool that exports `.litematic` / `.nbt`).
2. In HISTB?, open **Load Schematic** and pick the file — it becomes an active pattern.
3. Turn the mod **on**. It scans your loaded area automatically and keeps live highlight boxes on
   every place that structure appears — rotated and mirrored too.

No area selection, no manual scan, no timers, no limits. It just works while you move, like X-ray.

## Features
- **File-based patterns** — load Litematica `.litematic` and vanilla `.nbt` structure files. Create
  and save them in Litematica; HISTB? loads them and finds them in your world.
- **Whole-structure matching** — matches the exact relative arrangement of every block (dozens to
  thousands), not just single blocks.
- **Rotations & mirrors** — finds the structure rotated around X / Y / Z and mirrored (toggleable).
- **Match strictness** — Exact (block + state), Block-type-only, or Ignore-air (air = wildcard).
- **Litematica/MiniHUD-style highlights** — **LINES**, **FILLED**, or **BOTH**, with custom color,
  opacity, line width, and through-walls.
- **Live & lag-free** — throttled background scanning with section-palette culling; never freezes.
- **Everything in ModMenu** — open the menu and reach all settings. Keybinds are **unbound by
  default** (bind a toggle + menu key in Options → Controls if you like).

## Requirements
- Minecraft 26.1.2, Fabric Loader 0.19.2+
- Fabric API
- [MaLiLib](https://modrinth.com/mod/malilib) 0.28.6+

## Credits
Built by [Limucc-dev](https://github.com/dev-limucc). Uses [MaLiLib](https://github.com/maruohon/malilib) by masa.
Schematic format compatible with [Litematica](https://github.com/maruohon/litematica).
