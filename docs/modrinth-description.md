<!--
  Ready-to-paste Modrinth copy for HISTB?. Written to satisfy Modrinth's content rules:
  - reads as a clear, honest utility; no cheat/exploit framing or multiplayer-advantage claims
  - clear & honest about what it does, reads fine as plain text, headers used as headers
  Metadata to set on the project: Client side = Required, Server side = Unsupported,
  Categories e.g. Utility / Management, License MIT.
-->

# Suggested summary (short tagline — keep under ~256 chars, do not repeat the title)

Outlines every copy of your Litematica schematics in the area loaded around you — rotated and mirrored
matches included. A lightweight, client-side visual companion for MaLiLib.

---

# Project description (paste into the Modrinth body)

**Haven't I Seen This Before?** is a client-side companion for **MaLiLib** / **Litematica** that finds
where your own saved builds already exist in the world around you and outlines them.

## What it does

Load a `.litematic` (or `.nbt`) schematic you made in Litematica. HISTB? reads the chunks already loaded
around you and draws an outline on every copy of that build it finds — including **rotated** and
**mirrored** copies.

It is a **visual overlay only**: it reads the same client-side world data Litematica and MiniHUD use, it
never contacts or reads the server, and it never changes a single block.

## Good for

- Locating your own builds across a large base (farms, halls, shops)
- Counting how many copies of a modular build you have placed
- Keeping a big creative or survival project organized
- Re-finding a structure you built long ago and lost track of

## How to use

1. Install **HISTB?**, **Fabric API**, and **MaLiLib** for Minecraft 26.1.2.
2. Save a build as a `.litematic` in **Litematica**.
3. Open **Mod Menu → HISTB? → Schematics** and click the file to load it.
4. Open the **General** tab and enable it. Matching builds outline live as you move.

Optional hotkeys live under **Options → Controls** (unbound by default).

## Requirements

- Minecraft **26.1.2**, Fabric Loader **0.19.2+**
- **Fabric API**
- **MaLiLib**
- **Litematica** recommended (used to create the schematics)
- **Mod Menu** optional (in-game settings UI)

## Multiplayer

HISTB? is a personal, client-side overlay built on the same rendering Litematica uses. It only outlines
copies of the schematics you load, from chunks already loaded on your client, and sends nothing to the
server. Please follow the rules of any server you play on.

## Credits

By **Limucc-dev**. Built on **MaLiLib** by masa; schematics compatible with **Litematica**.
