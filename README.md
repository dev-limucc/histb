# 👀 Haven't I Seen This Before? (HISTB?)

**Find any build in your Minecraft world — automatically.**

Give it a schematic, and it highlights every place that build appears around you. Like X-ray, but for
*structures* instead of ores.

> Minecraft **26.1.2** · **Fabric** · client-side only

---

## ⚡ TL;DR (30 seconds)

1. Install **HISTB?**, **Fabric API**, and **MaLiLib** (drop the `.jar`s in your `mods` folder).
2. In **Litematica**, save the build you want to find as a `.litematic`.
3. In game: open **Mod Menu → HISTB? → Schematics tab → click your file**.
4. Go to the **General tab → turn it ON**.
5. Walk around. Matching builds light up. ✨

That's it. No setup, no commands, no fiddling.

---

## 🎯 What it does

You point it at a build (a schematic). It constantly looks around you and draws a glowing box on every
copy of that build it finds — even if the copy is **rotated or mirrored**.

Great for:
- Finding all your **villager halls / iron farms / shops** across a big base
- Spotting **duplicate builds** on a server
- Locating a **specific structure** you keep losing

---

## 📦 Install (step by step)

**You need 3 mods.** Put all of them in your `.minecraft/mods` folder (or your modpack's mods folder):

| Mod | Where |
|-----|-------|
| **HISTB?** (this) | [Releases](https://github.com/dev-limucc/histb/releases) / Modrinth |
| **Fabric API** | [modrinth.com/mod/fabric-api](https://modrinth.com/mod/fabric-api) |
| **MaLiLib** | [modrinth.com/mod/malilib](https://modrinth.com/mod/malilib) |

👉 Most people also want **[Litematica](https://modrinth.com/mod/litematica)** — that's what you use to
*make* the schematics HISTB? searches for.

Make sure every mod matches Minecraft **26.1.2**.

---

## 🕹️ How to use it

### Step 1 — Make a schematic (in Litematica)
Select an area around a build → save it. You now have a `.litematic` file in your `schematics` folder.
*(Already have `.litematic` or `.nbt` files? Skip this step.)*

### Step 2 — Load it into HISTB?
Open **Mod Menu → HISTB?**. You'll see tabs across the top:

- **Schematics** → click a file to load it (it turns into a "pattern").
- **Patterns** → see your loaded patterns, tick/untick which ones to search for.
- **General** → the **ON / OFF** switch.

### Step 3 — Turn it on
General tab → **Enable**. Done. It scans automatically and highlights matches live as you move.

> Prefer a hotkey? **Options → Controls → Haven't I Seen This Before?** — bind *Toggle* and *Open menu*
> to whatever keys you like. (They're unbound by default so they never clash with your other mods.)

---

## ⚙️ Settings (all optional)

Everything lives in the **Mod Menu** tabs — no config files to edit.

- **Matching** — how strict a match must be, and whether to find rotated/mirrored copies.
- **Display** — box style (outline / filled / both), color, see-through-walls, chat coordinates.

---

## ❓ Quick troubleshooting

| Problem | Fix |
|---------|-----|
| **Nothing lights up** | Is it **ON** (General tab)? Is a pattern **ticked** (Patterns tab)? Is the build actually near you? |
| **Only finds nearby builds** | It can only see **loaded chunks** — increase your render distance to search further. |
| **Mod won't load** | Make sure **Fabric API** and **MaLiLib** are installed, all for **26.1.2**. |
| **Rotated copies missed** | Turn on rotations/mirror in the **Matching** tab. |

---

## 🧠 How it works (for the curious)

It reads the blocks in your loaded area and looks for spots where they match your schematic's exact
layout. To stay fast it skips chunk sections that can't possibly contain your build (the same trick
Litematica uses), and it scans on a background thread so your game never stutters.

> **Heads up:** HISTB? *discovers* builds by scanning the world, so it can only highlight things in
> **loaded chunks** (your render distance). Unlike Litematica's placed holograms, it can't show a match
> in a chunk that isn't loaded yet — there's simply no data there to read.

---

## 🙏 Credits
Made by **[Limucc-dev](https://github.com/dev-limucc)**.
Powered by **[MaLiLib](https://github.com/maruohon/malilib)** (by masa). Schematics compatible with
**[Litematica](https://github.com/maruohon/litematica)**.

MIT licensed — do what you like with it.
