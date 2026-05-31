package dev.limucc.histb.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.limucc.histb.client.HistbClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve("histb.json");

    private static ModConfig instance = new ModConfig();

    public static ModConfig get() { return instance; }

    public static void load() {
        if (!Files.exists(PATH)) { save(); return; }
        try (Reader r = Files.newBufferedReader(PATH)) {
            ModConfig loaded = GSON.fromJson(r, ModConfig.class);
            if (loaded != null) instance = loaded;
        } catch (Exception e) {
            HistbClient.LOGGER.error("Failed to load config, using defaults.", e);
            instance = new ModConfig();
        }
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(PATH)) {
            GSON.toJson(instance, w);
        } catch (Exception e) {
            HistbClient.LOGGER.error("Failed to save config.", e);
        }
    }
}
