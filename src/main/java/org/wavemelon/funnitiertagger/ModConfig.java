package org.wavemelon.funnitiertagger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("funnitiers.json").toFile();

    public static final List<String> TIER_KEYS = List.of(
            "HT1", "LT1",
            "HT2", "LT2",
            "HT3", "LT3",
            "HT4", "LT4",
            "HT5", "LT5",
            "RETIRED"
    );

    public static final Map<String, Integer> DEFAULT_TIER_COLORS;
    static {
        Map<String, Integer> defaults = new LinkedHashMap<>();
        defaults.put("HT1", 0xFFAA00);
        defaults.put("LT1", 0xD5B355);
        defaults.put("HT2", 0xC4D3E7);
        defaults.put("LT2", 0xA0A7B2);
        defaults.put("HT3", 0xF89F5A);
        defaults.put("LT3", 0xC67B42);
        defaults.put("HT4", 0x00A000);
        defaults.put("LT4", 0x8EEB8E);
        defaults.put("HT5", 0x7E7E7E);
        defaults.put("LT5", 0xD0D0D0);
        defaults.put("RETIRED", 0x880EFC);
        DEFAULT_TIER_COLORS = Collections.unmodifiableMap(defaults);
    }

    public enum ColorTemplate {
        FUNNITIERS("funniTiers", mapOf(
                "#FFAA00", "#D5B355",
                "#C4D3E7", "#A0A7B2",
                "#F89F5A", "#C67B42",
                "#00A000", "#8EEB8E",
                "#7E7E7E", "#D0D0D0",
                "#880EFC"
        )),
        MCTIERS("MCTIERS", mapOf(
                "#E8BA3A", "#D5B355",
                "#C4D3E7", "#A0A7B2",
                "#F89F5A", "#C67B42",
                "#81749A", "#655B79",
                "#8F82A8", "#655B79",
                "#A2D6FF"
        )),
        PVPTIERS("PvPTiers", mapOf(
                "#F5CE4D", "#F5CE4D",
                "#BFCDD6", "#BFCDD6",
                "#B06453", "#B06453",
                "#A0323D", "#A0323D",
                "#BCBBC1", "#BCBBC1",
                "#161616"
        ));

        public final String displayName;
        public final Map<String, String> colors;

        ColorTemplate(String displayName, Map<String, String> colors) {
            this.displayName = displayName;
            this.colors = Collections.unmodifiableMap(colors);
        }

        public ColorTemplate next() {
            ColorTemplate[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }

        private static Map<String, String> mapOf(
                String ht1, String lt1,
                String ht2, String lt2,
                String ht3, String lt3,
                String ht4, String lt4,
                String ht5, String lt5,
                String retired
        ) {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("HT1", ht1);
            map.put("LT1", lt1);
            map.put("HT2", ht2);
            map.put("LT2", lt2);
            map.put("HT3", ht3);
            map.put("LT3", lt3);
            map.put("HT4", ht4);
            map.put("LT4", lt4);
            map.put("HT5", ht5);
            map.put("LT5", lt5);
            map.put("RETIRED", retired);
            return map;
        }
    }

    private static ModConfig INSTANCE;

    public boolean enabled = true;
    public String selectedMode = null; // null for Automatic (Peak)
    public DisplayType displayType = DisplayType.TIERS;
    public TagPosition tagPosition = TagPosition.LEFT;
    public IconStyle iconStyle = IconStyle.CUSTOM;
    public DividerStyle dividerStyle = DividerStyle.DEFAULT;
    public Map<String, String> tierColors = createDefaultColorMap();

    public enum DisplayType {
        TIERS("Tiers (HT1)"),
        RANK("Rank (#1)"),
        POINTS("Points (687 pts)");

        public final String displayName;

        DisplayType(String displayName) {
            this.displayName = displayName;
        }

        public DisplayType next() {
            DisplayType[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public enum TagPosition {
        LEFT("Left"),
        RIGHT("Right");

        public final String displayName;

        TagPosition(String displayName) {
            this.displayName = displayName;
        }

        public TagPosition next() {
            TagPosition[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public enum IconStyle {
        CUSTOM("Custom Icons"),
        LEGACY("Legacy Emojis");

        public final String displayName;

        IconStyle(String displayName) {
            this.displayName = displayName;
        }

        public IconStyle next() {
            IconStyle[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public enum DividerStyle {
        DEFAULT("Default (|)"),
        DYNAMIC("Dynamic (|)"),
        NONE("None");

        public final String displayName;

        DividerStyle(String displayName) {
            this.displayName = displayName;
        }

        public DividerStyle next() {
            DividerStyle[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public static Map<String, String> createDefaultColorMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : DEFAULT_TIER_COLORS.entrySet()) {
            map.put(entry.getKey(), formatHex(entry.getValue()));
        }
        return map;
    }

    public static String formatHex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    public static Integer parseHexColor(String input) {
        if (input == null) return null;
        String cleaned = input.trim();
        if (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1);
        } else if (cleaned.startsWith("0x") || cleaned.startsWith("0X")) {
            cleaned = cleaned.substring(2);
        }
        if (cleaned.length() != 6) return null;
        try {
            return Integer.parseInt(cleaned, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public int getTierColor(String tier, boolean retired) {
        String key = retired ? "RETIRED" : (tier != null ? tier.toUpperCase(Locale.ROOT) : "");
        return getColorByKey(key);
    }

    public int getColorByKey(String key) {
        if (key == null) return 0xFFFFFF;
        String upper = key.toUpperCase(Locale.ROOT);
        if (this.tierColors != null && this.tierColors.containsKey(upper)) {
            Integer parsed = parseHexColor(this.tierColors.get(upper));
            if (parsed != null) return parsed;
        }
        return DEFAULT_TIER_COLORS.getOrDefault(upper, 0xFFFFFF);
    }

    public String getTierColorHex(String key) {
        return formatHex(getColorByKey(key));
    }

    public boolean setTierColorHex(String key, String hexInput) {
        if (key == null) return false;
        Integer parsed = parseHexColor(hexInput);
        if (parsed == null) return false;
        if (this.tierColors == null) {
            this.tierColors = createDefaultColorMap();
        }
        this.tierColors.put(key.toUpperCase(Locale.ROOT), formatHex(parsed));
        return true;
    }

    public void resetTierColor(String key) {
        if (key == null) return;
        String upper = key.toUpperCase(Locale.ROOT);
        Integer def = DEFAULT_TIER_COLORS.get(upper);
        if (def != null) {
            if (this.tierColors == null) {
                this.tierColors = createDefaultColorMap();
            }
            this.tierColors.put(upper, formatHex(def));
        }
    }

    public void resetTierColors() {
        this.tierColors = createDefaultColorMap();
    }

    /**
     * Applies a built-in color template preset.
     */
    public void applyColorTemplate(ColorTemplate template) {
        if (template == null) return;
        applyColorTemplate(template.colors);
    }

    /**
     * Applies a color template map (e.g., from another tier tagger preset).
     */
    public void applyColorTemplate(Map<String, String> template) {
        if (template == null) return;
        if (this.tierColors == null) {
            this.tierColors = createDefaultColorMap();
        }
        for (Map.Entry<String, String> entry : template.entrySet()) {
            setTierColorHex(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Returns the matching built-in template if all rank colors match it, or null if customized.
     */
    public ColorTemplate getMatchingTemplate() {
        for (ColorTemplate template : ColorTemplate.values()) {
            boolean matches = true;
            for (String key : TIER_KEYS) {
                String expected = template.colors.get(key);
                String actual = getTierColorHex(key);
                if (expected == null || !expected.equalsIgnoreCase(actual)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return template;
            }
        }
        return null;
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
        if (INSTANCE.displayType == null) {
            INSTANCE.displayType = DisplayType.TIERS;
        }
        if (INSTANCE.tagPosition == null) {
            INSTANCE.tagPosition = TagPosition.LEFT;
        }
        if (INSTANCE.iconStyle == null) {
            INSTANCE.iconStyle = IconStyle.CUSTOM;
        }
        if (INSTANCE.dividerStyle == null) {
            INSTANCE.dividerStyle = DividerStyle.DEFAULT;
        }
        if (INSTANCE.tierColors == null) {
            INSTANCE.tierColors = createDefaultColorMap();
        } else {
            for (Map.Entry<String, Integer> entry : DEFAULT_TIER_COLORS.entrySet()) {
                INSTANCE.tierColors.putIfAbsent(entry.getKey(), formatHex(entry.getValue()));
            }
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
