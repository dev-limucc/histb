package dev.limucc.histb.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.limucc.histb.client.HistbClient;
import dev.limucc.histb.client.compat.ScreenNav;
import dev.limucc.histb.client.config.ConfigManager;
import dev.limucc.histb.client.config.ModConfig;
import dev.limucc.histb.client.gui.HistbConfigScreen;
import dev.limucc.histb.client.render.HighlightStore;
import dev.limucc.histb.client.scan.Match;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.spongepowered.asm.mixin.MixinEnvironment;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Runtime check of every HISTB? feature. A chiral 4x2x4 "L" build (7 distinct blocks on the ground layer + 2 on top)
 * is saved as BOTH a .nbt structure and a bit-packed .litematic, loaded through the real GUI, and then searched for in
 * a world containing: the original, a 90-degree rotation, a 180-degree rotation, a mirror image, a decoy with one wrong
 * block, and a copy with an extra block in an air cell (matches only when air is a wildcard).
 *
 * <p>Run with {@code gradlew runClientGameTest "-Ptarget=<mc>"}.
 */
public class HistbGameTest implements FabricClientGameTest {

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("HISTB-GAMETEST FAIL: " + what);
        System.out.println("[HISTB-GAMETEST] PASS: " + what);
    }

    private static void info(String what) {
        System.out.println("[HISTB-GAMETEST] INFO: " + what);
    }

    // ---- the test build --------------------------------------------------------------------------------
    private static final int N = 4;           // footprint N x N, height 2
    private record Cell(int x, int y, int z, String block) {}

    private static final List<Cell> BASE = List.of(
            new Cell(0, 0, 0, "minecraft:red_concrete"),
            new Cell(1, 0, 0, "minecraft:blue_concrete"),
            new Cell(2, 0, 0, "minecraft:gold_block"),
            new Cell(3, 0, 0, "minecraft:emerald_block"),
            new Cell(0, 0, 1, "minecraft:lapis_block"),
            new Cell(0, 0, 2, "minecraft:white_concrete"),
            new Cell(0, 0, 3, "minecraft:black_concrete"),
            new Cell(3, 1, 0, "minecraft:gold_block"),
            new Cell(0, 1, 3, "minecraft:red_concrete"));

    private static final String[] PALETTE = {
            "minecraft:air", "minecraft:red_concrete", "minecraft:blue_concrete", "minecraft:gold_block",
            "minecraft:emerald_block", "minecraft:lapis_block", "minecraft:white_concrete", "minecraft:black_concrete"};

    private static final int OY = -60;        // build layer above the flat-world grass (y=-61)
    private static final int OZ = 10;
    private static final int[] OX = {10, 20, 30, 40, 50, 60};   // A, B(rot90), C(rot180), D(mirror), E(decoy), F(extra block)

    private static int[] xf(Cell c, String kind) {
        return switch (kind) {
            case "rot90" -> new int[]{N - 1 - c.z, c.y, c.x};
            case "rot180" -> new int[]{N - 1 - c.x, c.y, N - 1 - c.z};
            case "mirror" -> new int[]{N - 1 - c.x, c.y, c.z};
            default -> new int[]{c.x, c.y, c.z};
        };
    }

    private static void place(TestSingleplayerContext w, int ox, String kind, String replaceBlueWith) {
        for (Cell c : BASE) {
            int[] p = xf(c, kind);
            String b = (replaceBlueWith != null && c.block.equals("minecraft:blue_concrete")) ? replaceBlueWith : c.block;
            w.getServer().runCommand("setblock " + (ox + p[0]) + " " + (OY + p[1]) + " " + (OZ + p[2]) + " " + b);
        }
    }

    // ---- schematic writers ---------------------------------------------------------------------------------
    private static ListTag ints(int... v) {
        ListTag l = new ListTag();
        for (int i : v) l.add(IntTag.valueOf(i));
        return l;
    }

    private static CompoundTag named(String name) {
        CompoundTag t = new CompoundTag();
        t.putString("Name", name);
        return t;
    }

    private static void writeNbt(Path file) throws Exception {
        CompoundTag root = new CompoundTag();
        root.put("size", ints(N, 2, N));
        ListTag palette = new ListTag();
        for (int i = 1; i < PALETTE.length; i++) palette.add(named(PALETTE[i]));   // no air entry: absent cells = air
        root.put("palette", palette);
        ListTag blocks = new ListTag();
        for (Cell c : BASE) {
            CompoundTag b = new CompoundTag();
            b.put("pos", ints(c.x, c.y, c.z));
            int idx = -1;
            for (int i = 1; i < PALETTE.length; i++) if (PALETTE[i].equals(c.block)) idx = i - 1;
            b.putInt("state", idx);
            blocks.add(b);
        }
        root.put("blocks", blocks);
        root.putInt("DataVersion", 4000);
        NbtIo.writeCompressed(root, file);
    }

    private static void writeLitematic(Path file) throws Exception {
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(PALETTE.length - 1));   // 8 entries -> 3 bits
        int volume = N * 2 * N;
        long[] data = new long[(int) (((long) volume * bits + 63) / 64)];
        for (Cell c : BASE) {
            int idx = -1;
            for (int i = 0; i < PALETTE.length; i++) if (PALETTE[i].equals(c.block)) idx = i;
            long litIndex = (long) (c.y * N + c.z) * N + c.x;        // Litematica order: y, z, x
            long start = litIndex * bits;
            int sl = (int) (start >> 6), off = (int) (start & 63);
            data[sl] |= ((long) idx) << off;
            int el = (int) (((litIndex + 1) * bits - 1) >> 6);
            if (el != sl) data[el] |= ((long) idx) >>> (64 - off);   // straddles two longs
        }
        CompoundTag region = new CompoundTag();
        CompoundTag size = new CompoundTag();
        size.putInt("x", N); size.putInt("y", 2); size.putInt("z", N);
        region.put("Size", size);
        ListTag pal = new ListTag();
        for (String p : PALETTE) pal.add(named(p));
        region.put("BlockStatePalette", pal);
        region.putLongArray("BlockStates", data);
        CompoundTag regions = new CompoundTag();
        regions.put("main", region);
        CompoundTag root = new CompoundTag();
        root.put("Regions", regions);
        root.putInt("Version", 6);
        NbtIo.writeCompressed(root, file);
    }

    // ---- observation helpers -----------------------------------------------------------------------------------
    private static Set<BlockPos> matchMins() {
        Set<BlockPos> s = new HashSet<>();
        for (Match m : HighlightStore.get()) s.add(m.min().immutable());
        return s;
    }

    private static BlockPos mn(int ox) { return new BlockPos(ox, OY, OZ); }

    /** Waits (bounded) until the live scanner has published exactly the expected set of match origins. */
    private static boolean waitMatches(ClientGameTestContext ctx, Set<BlockPos> expect, int timeoutTicks) {
        for (int t = 0; t < timeoutTicks; t += 10) {
            ctx.waitTicks(10);
            if (matchMins().equals(expect)) return true;
        }
        return false;
    }

    private static void cfg(ClientGameTestContext ctx, java.util.function.Consumer<ModConfig> edit) {
        ctx.runOnClient(mc -> { edit.accept(ConfigManager.get()); ConfigManager.save(); });
    }

    private static boolean isCyan(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return r < 90 && g > 170 && b > 190;     // default highlight colour 0x00E0FF
    }

    private static int count(BufferedImage img, IntPredicate p) {
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) if (p.test(img.getRGB(x, y))) n++;
        return n;
    }

    private static BufferedImage shot(ClientGameTestContext ctx, String name) throws Exception {
        Path p = ctx.takeScreenshot(name);
        return ImageIO.read(p.toFile());
    }

    private static void click(ClientGameTestContext ctx, double gx, double gy) {
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(gx * scale, gy * scale);
        ctx.waitTick();
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(4);
    }

    private static void clickButton(ClientGameTestContext ctx, String text) {
        double[] c = ctx.computeOnClient(mc -> {
            for (AbstractWidget w : Screens.getWidgets(ScreenNav.current(mc))) {
                if (w.getMessage().getString().contains(text)) return new double[]{w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0};
            }
            return null;
        });
        if (c == null) throw new AssertionError("HISTB-GAMETEST FAIL: no button containing '" + text + "'");
        click(ctx, c[0], c[1]);
    }

    private static KeyMapping bind(ClientGameTestContext ctx, KeyMapping m, int key) {
        ctx.runOnClient(mc -> {
            m.setKey(keyboard().getOrCreate(key));
            KeyMapping.resetMapping();
        });
        return m;
    }

    /** InputConstants.Type.KEYSYM was renamed KEYBOARD in 26.3. */
    private static InputConstants.Type keyboard() {
        try {
            return InputConstants.Type.valueOf("KEYSYM");
        } catch (IllegalArgumentException e) {
            return InputConstants.Type.valueOf("KEYBOARD");
        }
    }

    // ---- entry point ---------------------------------------------------------------------------------------------
    @Override
    public void runTest(ClientGameTestContext ctx) throws RuntimeException {
        try {
            run(ctx);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void run(ClientGameTestContext ctx) throws Exception {
        info("MC loaded; malilib=" + FabricLoader.getInstance().getModContainer("malilib").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?"));
        ctx.runOnClient(mc -> MixinEnvironment.getCurrentEnvironment().audit());
        check(true, "MixinEnvironment.audit() passed (HISTB ships no mixins; environment clean)");
        check(FabricLoader.getInstance().isModLoaded("malilib"), "MaLiLib is loaded next to HISTB");

        Path schem = FabricLoader.getInstance().getGameDir().resolve("schematics");
        Files.createDirectories(schem);
        for (String old : new String[]{"histb_test_L.nbt", "histb_test_L.litematic"}) Files.deleteIfExists(schem.resolve(old));
        writeLitematic(schem.resolve("histb_test_L.litematic"));
        writeNbt(schem.resolve("histb_test_L.nbt"));
        cfg(ctx, c -> {
            c.patterns.clear();
            c.enabled = false;
            c.rotateY = true; c.mirror = true; c.rotateX = false; c.rotateZ = false;
            c.matchMode = ModConfig.MatchMode.IGNORE_AIR;
            c.highlightStyle = ModConfig.HighlightStyle.BOTH;
            c.boxColor = 0x00E0FF; c.showBoxes = true; c.throughWalls = true; c.chatCoords = false;
        });
        ctx.getInput().resizeWindow(1280, 720);
        ctx.waitTicks(5);
        KeyMapping toggleKey = bind(ctx, HistbClient.KEY_TOGGLE, InputConstants.KEY_H);
        KeyMapping openKey = bind(ctx, HistbClient.KEY_OPEN, InputConstants.KEY_J);

        try (TestSingleplayerContext w = ctx.worldBuilder().create()) {
            ctx.waitTicks(40);
            for (String c : new String[]{"gamemode creative @p", "difficulty peaceful", "time set noon", "weather clear",
                    "gamerule advance_time false"}) w.getServer().runCommand(c);
            place(w, OX[0], "id", null);
            place(w, OX[1], "rot90", null);
            place(w, OX[2], "rot180", null);
            place(w, OX[3], "mirror", null);
            place(w, OX[4], "id", "minecraft:orange_concrete");                 // decoy: one wrong block
            place(w, OX[5], "id", null);
            w.getServer().runCommand("setblock " + (OX[5] + 1) + " " + (OY + 1) + " " + (OZ + 1) + " minecraft:stone");   // extra block in an air cell
            w.getServer().runCommand("tp @p 25 -58 -12 0 12");
            ctx.waitTicks(80);
            check(true, "test world built: original, rot90, rot180, mirror, decoy, extra-block copy");

            // ---- 1. Config GUI: Schematics tab loads both formats ------------------------------------------------
            ctx.setScreen(() -> new HistbConfigScreen(null));
            ctx.waitTicks(5);
            ctx.takeScreenshot("histb-01-gui-general");
            clickButton(ctx, "Schematics");
            ctx.waitTicks(3);
            ctx.takeScreenshot("histb-02-gui-schematics");
            int cx = ctx.computeOnClient(mc -> ScreenNav.current(mc).width) / 2;
            int rowY0 = 30 + 8 + 26 + 8 + 14 + 10;           // panelT + 8 + 26 + 8 + 14 + half row
            click(ctx, cx, rowY0);                           // first file (.litematic, sorted by name)
            click(ctx, cx, rowY0 + 20);                      // second file (.nbt)
            ctx.takeScreenshot("histb-03-gui-schematics-loaded");
            int np = ctx.computeOnClient(mc -> ConfigManager.get().patterns.size());
            check(np == 2, "1a clicking the two schematic files in the GUI loads 2 patterns (.litematic + .nbt): " + np);
            List<String> c0 = ctx.computeOnClient(mc -> new ArrayList<>(ConfigManager.get().patterns.get(0).cells));
            List<String> c1 = ctx.computeOnClient(mc -> new ArrayList<>(ConfigManager.get().patterns.get(1).cells));
            check(c0.equals(c1) && c0.size() == N * 2 * N, "1b .litematic (bit-packed, crossing longs) and .nbt decode to the identical " + c0.size() + "-cell pattern");
            long solid = c0.stream().filter(s -> !s.isEmpty()).count();
            check(solid == BASE.size(), "1c air cells are wildcards: " + solid + " solid cells == " + BASE.size());
            clickButton(ctx, "Patterns");
            ctx.waitTicks(3);
            ctx.takeScreenshot("histb-04-gui-patterns");
            // delete the second pattern through its [delete] hotspot, keep the first
            int panelR = (ctx.computeOnClient(mc -> ScreenNav.current(mc).width) + Math.min(420, ctx.computeOnClient(mc -> ScreenNav.current(mc).width) - 40)) / 2;
            click(ctx, panelR - 20, 30 + 8 + 26 + 8 + 10 + 20);     // row 1, delete zone
            check(ctx.computeOnClient(mc -> ConfigManager.get().patterns.size()) == 1, "1d [delete] on a pattern row removes it");
            clickButton(ctx, "General");
            clickButton(ctx, "DISABLED");
            check(ctx.computeOnClient(mc -> ConfigManager.get().enabled), "1e General tab: 'Mod: DISABLED' button enables scanning");
            ctx.takeScreenshot("histb-05-gui-enabled");
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(5);
            check(ctx.computeOnClient(mc -> ScreenNav.current(mc) == null), "1f Escape closes the config screen");

            // ---- 2. live scan finds every orientation ----------------------------------------------------------------
            Set<BlockPos> all5 = Set.of(mn(OX[0]), mn(OX[1]), mn(OX[2]), mn(OX[3]), mn(OX[5]));
            boolean ok = waitMatches(ctx, all5, 600);
            check(ok, "2a scan found original + rot90 + rot180 + mirror + extra-block copy (5), NOT the decoy: " + matchMins());
            List<String> orient = new ArrayList<>();
            for (Match m : HighlightStore.get()) orient.add(m.orientation());
            info("orientations reported: " + orient);
            check(HighlightStore.get().stream().allMatch(m -> m.max().getX() - m.min().getX() <= N - 1 && m.max().getZ() - m.min().getZ() <= N - 1),
                    "2b every match box is within the " + N + "x" + N + " footprint");
            BufferedImage img = shot(ctx, "histb-06-outlines-all");
            int cyan = count(img, HistbGameTest::isCyan);
            check(cyan > 1500, "2c highlight outlines are really drawn (cyan pixels: " + cyan + ")");

            // ---- 3. orientation options --------------------------------------------------------------------------------
            cfg(ctx, c -> { c.rotateY = false; c.mirror = true; });
            check(waitMatches(ctx, Set.of(mn(OX[0]), mn(OX[3]), mn(OX[5])), 600), "3a rotations OFF + mirror ON -> original, mirror, extra only: " + matchMins());
            cfg(ctx, c -> { c.rotateY = true; c.mirror = false; });
            check(waitMatches(ctx, Set.of(mn(OX[0]), mn(OX[1]), mn(OX[2]), mn(OX[5])), 600), "3b rotations ON + mirror OFF -> no mirror image: " + matchMins());
            cfg(ctx, c -> { c.rotateY = false; c.mirror = false; });
            check(waitMatches(ctx, Set.of(mn(OX[0]), mn(OX[5])), 600), "3c both OFF -> only the unrotated copies: " + matchMins());
            cfg(ctx, c -> { c.rotateY = true; c.mirror = true; });
            check(waitMatches(ctx, all5, 600), "3d both ON again -> all 5");

            // ---- 4. strictness --------------------------------------------------------------------------------------------
            cfg(ctx, c -> c.matchMode = ModConfig.MatchMode.EXACT);
            check(waitMatches(ctx, Set.of(mn(OX[0]), mn(OX[1]), mn(OX[2]), mn(OX[3])), 600), "4a EXACT: the copy with an extra block in an air cell no longer matches: " + matchMins());
            cfg(ctx, c -> c.matchMode = ModConfig.MatchMode.BLOCK_ONLY);
            check(waitMatches(ctx, Set.of(mn(OX[0]), mn(OX[1]), mn(OX[2]), mn(OX[3])), 600), "4b BLOCK_ONLY: air cells must still be air (only IGNORE_AIR treats them as wildcards) -> extra-block copy excluded: " + matchMins());
            cfg(ctx, c -> c.matchMode = ModConfig.MatchMode.IGNORE_AIR);
            check(waitMatches(ctx, all5, 600), "4c IGNORE_AIR: matches");

            // ---- 5. display options ---------------------------------------------------------------------------------------
            cfg(ctx, c -> { c.highlightStyle = ModConfig.HighlightStyle.LINES; c.boxColor = 0xFF3030; });
            ctx.waitTicks(10);
            BufferedImage lines = shot(ctx, "histb-07-style-lines-red");
            int red = count(lines, rgb -> ((rgb >> 16) & 255) > 220 && ((rgb >> 8) & 255) < 110 && (rgb & 255) < 110);
            check(red > 300, "5a LINES style + red colour draws red outlines (red px " + red + ")");
            cfg(ctx, c -> { c.highlightStyle = ModConfig.HighlightStyle.FILLED; c.boxColor = 0x30FF30; });
            ctx.waitTicks(10);
            ctx.takeScreenshot("histb-08-style-filled-green");
            cfg(ctx, c -> { c.highlightStyle = ModConfig.HighlightStyle.BOTH; c.boxColor = 0x00E0FF; c.showBoxes = false; });
            ctx.waitTicks(10);
            int none = count(shot(ctx, "histb-09-boxes-hidden"), HistbGameTest::isCyan);
            check(none < 100, "5b showBoxes=false hides the outlines (cyan px " + none + ")");
            cfg(ctx, c -> c.showBoxes = true);
            ctx.waitTicks(10);

            // ---- 6. pattern list controls -------------------------------------------------------------------------------------
            cfg(ctx, c -> c.patterns.get(0).active = false);
            ctx.waitTicks(60);
            check(HighlightStore.get().isEmpty(), "6a deactivating the only pattern clears the highlights");
            cfg(ctx, c -> c.patterns.get(0).active = true);
            check(waitMatches(ctx, all5, 600), "6b re-activating brings the highlights back");

            // ---- 7. keybinds ---------------------------------------------------------------------------------------------------
            ctx.waitTicks(10);
            ctx.getInput().pressKey(toggleKey);
            ctx.waitTicks(10);
            check(!ctx.computeOnClient(mc -> ConfigManager.get().enabled), "7a toggle keybind switches scanning OFF");
            ctx.waitTicks(20);
            check(HighlightStore.get().isEmpty(), "7b highlights cleared while OFF");
            ctx.getInput().pressKey(toggleKey);
            ctx.waitTicks(5);
            check(ctx.computeOnClient(mc -> ConfigManager.get().enabled), "7c toggle keybind switches it back ON");
            check(waitMatches(ctx, all5, 600), "7d scanning resumes after toggling back ON");
            ctx.getInput().pressKey(openKey);
            ctx.waitFor(mc -> ScreenNav.current(mc) instanceof HistbConfigScreen, 40);
            check(true, "7e open keybind opens the HISTB screen");
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(5);
        }
        ctx.waitTicks(20);
        check(HighlightStore.get().isEmpty(), "8 leaving the world clears the highlights (scanner stops)");
        System.out.println("[HISTB-GAMETEST] PASS: ALL HISTB checks complete");
    }
}
