package dev.limucc.histb.client.schematic;

import dev.limucc.histb.client.HistbClient;
import dev.limucc.histb.client.config.ModConfig;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads external schematic files into HISTB SavedPatterns, and exports captured
 * patterns to .litematic.
 *
 * Supported import:
 *   - .litematic  (Litematica: GZIP-NBT, Regions → palette + bit-packed BlockStates)
 *   - .nbt        (vanilla structure block: palette + blocks list)
 *
 * Cells are flattened x*(sy*sz)+y*sz+z to match Pattern.at; air → "" wildcard.
 */
public final class SchematicIO {

    private SchematicIO() {}

    private static HolderLookup<net.minecraft.world.level.block.Block> blockLookup() {
        return Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.BLOCK);
    }

    private static String serialize(BlockState s) { return BlockStateParser.serialize(s); }

    private static BlockState parse(String s) {
        try { return BlockStateParser.parseForBlock(blockLookup(), s, false).blockState(); }
        catch (Exception e) { return null; }
    }

    // ── Load dispatch ─────────────────────────────────────────────────────────

    /** Load a schematic file → SavedPattern (active). Throws with a friendly message on failure. */
    public static ModConfig.SavedPattern load(Path file) throws Exception {
        String name = file.getFileName().toString().toLowerCase();
        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        if (name.endsWith(".litematic")) return loadLitematic(root, baseName(file));
        if (name.endsWith(".nbt"))       return loadStructureNbt(root, baseName(file));
        throw new Exception("Unsupported file type (use .litematic or .nbt)");
    }

    private static String baseName(Path p) {
        String n = p.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    // ── .litematic ────────────────────────────────────────────────────────────

    private static ModConfig.SavedPattern loadLitematic(CompoundTag root, String name) throws Exception {
        CompoundTag regions = root.getCompound("Regions").orElseThrow(() -> new Exception("No Regions in .litematic"));
        // Use the first region.
        String regionName = regions.keySet().stream().findFirst().orElseThrow(() -> new Exception("Empty Regions"));
        CompoundTag region = regions.getCompoundOrEmpty(regionName);

        CompoundTag size = region.getCompoundOrEmpty("Size");
        int sx = Math.abs(size.getInt("x").orElse(0));
        int sy = Math.abs(size.getInt("y").orElse(0));
        int sz = Math.abs(size.getInt("z").orElse(0));
        if (sx == 0 || sy == 0 || sz == 0) throw new Exception("Bad region size");
        long volume = (long) sx * sy * sz;
        if (volume > 200000) throw new Exception("Too large (" + volume + " blocks)");

        // palette: list of blockstate compounds (Name + Properties)
        ListTag palette = region.getListOrEmpty("BlockStatePalette");
        String[] paletteStr = new String[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            paletteStr[i] = blockStateString(palette.getCompoundOrEmpty(i));
        }

        long[] data = region.getLongArray("BlockStates").orElseThrow(() -> new Exception("No BlockStates"));
        int bits = Math.max(2, ceilLog2(palette.size()));
        long mask = (1L << bits) - 1;

        // Litematica order is y, z, x (index = (y*sz + z)*sx + x). We re-flatten to our x,y,z order.
        List<String> cells = new ArrayList<>((int) volume);
        for (int i = 0; i < volume; i++) cells.add(""); // pre-size

        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    long litIndex = (long) (y * sz + z) * sx + x;
                    int palIdx = (int) getPackedValue(data, litIndex, bits, mask);
                    String bs = (palIdx >= 0 && palIdx < paletteStr.length) ? paletteStr[palIdx] : "";
                    String cell = isAir(bs) ? "" : bs;
                    cells.set(x * (sy * sz) + y * sz + z, cell);
                }
            }
        }
        return makePattern(name, sx, sy, sz, cells);
    }

    private static String blockStateString(CompoundTag entry) {
        String blockName = entry.getStringOr("Name", "minecraft:air");
        CompoundTag props = entry.getCompoundOrEmpty("Properties");
        if (props.isEmpty()) return blockName;
        StringBuilder sb = new StringBuilder(blockName).append('[');
        boolean first = true;
        for (String key : props.keySet()) {
            if (!first) sb.append(',');
            sb.append(key).append('=').append(props.getStringOr(key, ""));
            first = false;
        }
        return sb.append(']').toString();
    }

    /** Read a value spanning bit positions (Litematica packs without crossing… actually it can cross longs). */
    private static long getPackedValue(long[] arr, long index, int bits, long mask) {
        long startBit = index * bits;
        int startLong = (int) (startBit >> 6);
        int endLong = (int) (((index + 1) * bits - 1) >> 6);
        int startOffset = (int) (startBit & 63);
        if (startLong >= arr.length) return 0;
        if (startLong == endLong) {
            return (arr[startLong] >>> startOffset) & mask;
        } else {
            int bitsInFirst = 64 - startOffset;
            long low = arr[startLong] >>> startOffset;
            long high = (endLong < arr.length) ? (arr[endLong] << bitsInFirst) : 0;
            return (low | high) & mask;
        }
    }

    // ── .nbt (vanilla structure) ──────────────────────────────────────────────

    private static ModConfig.SavedPattern loadStructureNbt(CompoundTag root, String name) throws Exception {
        ListTag sizeList = root.getListOrEmpty("size");
        if (sizeList.size() < 3) throw new Exception("No size in .nbt");
        int sx = sizeList.getIntOr(0, 0), sy = sizeList.getIntOr(1, 0), sz = sizeList.getIntOr(2, 0);
        if (sx == 0 || sy == 0 || sz == 0) throw new Exception("Bad .nbt size");

        ListTag palette = root.getListOrEmpty("palette");
        String[] paletteStr = new String[palette.size()];
        for (int i = 0; i < palette.size(); i++) paletteStr[i] = blockStateString(palette.getCompoundOrEmpty(i));

        List<String> cells = new ArrayList<>(sx * sy * sz);
        for (int i = 0; i < sx * sy * sz; i++) cells.add("");

        ListTag blocks = root.getListOrEmpty("blocks");
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompoundOrEmpty(i);
            ListTag pos = b.getListOrEmpty("pos");
            int x = pos.getIntOr(0, 0), y = pos.getIntOr(1, 0), z = pos.getIntOr(2, 0);
            int state = b.getInt("state").orElse(0);
            String bs = (state >= 0 && state < paletteStr.length) ? paletteStr[state] : "";
            if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) continue;
            cells.set(x * (sy * sz) + y * sz + z, isAir(bs) ? "" : bs);
        }
        return makePattern(name, sx, sy, sz, cells);
    }

    // ── Export to .litematic ──────────────────────────────────────────────────

    public static void exportLitematic(ModConfig.SavedPattern sp, Path file) throws Exception {
        // Build palette (air first) + packed BlockStates in Litematica's y,z,x order.
        List<String> palette = new ArrayList<>();
        palette.add("minecraft:air");
        java.util.Map<String,Integer> palIdx = new java.util.HashMap<>();
        palIdx.put("minecraft:air", 0);

        int sx = sp.sizeX, sy = sp.sizeY, sz = sp.sizeZ;
        int[] indices = new int[sx * sy * sz];
        for (int y = 0; y < sy; y++)
            for (int z = 0; z < sz; z++)
                for (int x = 0; x < sx; x++) {
                    String cell = sp.cells.get(x * (sy * sz) + y * sz + z);
                    if (cell == null || cell.isEmpty()) cell = "minecraft:air";
                    int idx = palIdx.computeIfAbsent(cell, k -> { palette.add(k); return palette.size() - 1; });
                    indices[(y * sz + z) * sx + x] = idx;
                }

        int bits = Math.max(2, ceilLog2(palette.size()));
        long[] data = packLongs(indices, bits);

        CompoundTag region = new CompoundTag();
        CompoundTag pos = new CompoundTag(); pos.putInt("x",0); pos.putInt("y",0); pos.putInt("z",0);
        region.put("Position", pos);
        CompoundTag size = new CompoundTag(); size.putInt("x",sx); size.putInt("y",sy); size.putInt("z",sz);
        region.put("Size", size);
        ListTag palTag = new ListTag();
        for (String s : palette) palTag.addAndUnwrap(toStateCompound(s));
        region.put("BlockStatePalette", palTag);
        region.putLongArray("BlockStates", data);
        region.put("TileEntities", new ListTag());
        region.put("Entities", new ListTag());

        CompoundTag regions = new CompoundTag();
        regions.put(sp.name, region);

        CompoundTag root = new CompoundTag();
        root.putInt("Version", 6);
        root.putInt("MinecraftDataVersion", 4000);
        root.put("Regions", regions);
        CompoundTag meta = new CompoundTag();
        meta.putString("Name", sp.name);
        meta.putString("Author", "HISTB?");
        meta.putInt("RegionCount", 1);
        meta.putInt("TotalVolume", sx * sy * sz);
        root.put("Metadata", meta);

        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(root, file);
        HistbClient.LOGGER.info("Exported {} to {}", sp.name, file);
    }

    private static CompoundTag toStateCompound(String stateStr) {
        CompoundTag t = new CompoundTag();
        int br = stateStr.indexOf('[');
        if (br < 0) { t.putString("Name", stateStr); return t; }
        t.putString("Name", stateStr.substring(0, br));
        CompoundTag props = new CompoundTag();
        String inner = stateStr.substring(br + 1, stateStr.length() - 1);
        for (String kv : inner.split(",")) {
            int eq = kv.indexOf('=');
            if (eq > 0) props.putString(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
        }
        t.put("Properties", props);
        return t;
    }

    private static long[] packLongs(int[] indices, int bits) {
        long totalBits = (long) indices.length * bits;
        long[] out = new long[(int) ((totalBits + 63) >> 6)];
        for (int i = 0; i < indices.length; i++) {
            long startBit = (long) i * bits;
            int startLong = (int) (startBit >> 6);
            int startOffset = (int) (startBit & 63);
            long val = indices[i] & ((1L << bits) - 1);
            out[startLong] |= (val << startOffset);
            int endLong = (int) (((long) (i + 1) * bits - 1) >> 6);
            if (endLong != startLong) {
                out[endLong] |= (val >>> (64 - startOffset));
            }
        }
        return out;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static ModConfig.SavedPattern makePattern(String name, int sx, int sy, int sz, List<String> cells) {
        ModConfig.SavedPattern sp = new ModConfig.SavedPattern();
        sp.name = name; sp.active = true;
        sp.sizeX = sx; sp.sizeY = sy; sp.sizeZ = sz; sp.cells = cells;
        return sp;
    }

    private static boolean isAir(String bs) {
        return bs == null || bs.isEmpty() || bs.equals("minecraft:air")
            || bs.startsWith("minecraft:air[") || bs.equals("minecraft:cave_air") || bs.equals("minecraft:void_air");
    }

    private static int ceilLog2(int n) {
        int b = 0; int v = 1;
        while (v < n) { v <<= 1; b++; }
        return Math.max(1, b);
    }
}
