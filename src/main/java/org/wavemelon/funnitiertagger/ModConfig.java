package org.wavemelon.funnitiertagger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("funnitiers.json").toFile();

    private static ModConfig INSTANCE;

    public boolean enabled = true;
    public String selectedMode = null; // null for Automatic (Peak)
    public DisplayType displayType = DisplayType.TIERS;

    public enum DisplayType {
        TIERS("Tiers (e.g. HT1)"),
        POINTS("Points (e.g. 687 pts)");

        public final String displayName;

        DisplayType(String displayName) {
            this.displayName = displayName;
        }

        public DisplayType next() {
            return this == TIERS ? POINTS : TIERS;
        }
    }

    public static ModConfig getInstance() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, ModConfig.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (INSTANCE == null) {
            INSTANCE = new ModConfig();
        }
    }

    public static void save() {
        try {
            if (CONFIG_FILE.getParentFile() != null) {
                CONFIG_FILE.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
