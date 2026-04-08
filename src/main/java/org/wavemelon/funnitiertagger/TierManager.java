package org.wavemelon.funnitiertagger;

import com.google.gson.Gson;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TierManager {
    public static final Map<UUID, TierProfile> profileCache = new HashMap<>();
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();



    public static Text getFormattedTag(UUID uuid) {
        if (!profileCache.containsKey(uuid)) {
            fetch(uuid);
            return null;
        }

        TierProfile profile = profileCache.get(uuid);
        if (profile == null || profile.tiers == null || profile.tiers.isEmpty()) return null;

        // --- LOGIC CHANGE HERE ---
        String modeKey;
        if (overrideMode != null && profile.tiers.containsKey(overrideMode)) {
            // Use the manual override if the player actually has a tier in that mode
            modeKey = overrideMode;
        } else {
            // Fallback to your "Peak" logic if no override is set or player doesn't have it
            modeKey = profile.getDisplayMode();
        }
        // -------------------------

        if (modeKey == null) return null;

        TierProfile.GameModeData data = profile.tiers.get(modeKey);
        if (data == null) return null;

        try {
            // 4. Build the Icon
            MutableText tag = getIcon(modeKey);

            // 5. Build the Tier (e.g., "R1" or "3")
            String tierStr = (data.retired ? "R" : "") + data.tier;
            int tierColor = data.retired ? 0x880EFC : getTierColor(data.tier);
            tag.append(Text.literal(" " + tierStr).styled(s -> s.withColor(tierColor)));

            // 6. Add Separator
            tag.append(Text.literal(" | ").styled(s -> s.withColor(0xAAAAAA)));

            return tag;

        } catch (Exception e) {
            // Final catch-all to prevent nameplate crashes if icons or colors fail
            return null;
        }
    }

    private static int getTierColor(String tier) {
        return switch (tier) {
            case "LT5" -> 0xD0D0D0;
            case "HT5" -> 0x7E7E7E;
            case "LT4" -> 0x8EEB8E;
            case "HT4" -> 0x006300;
            case "LT3" -> 0xC67B42;
            case "HT3" -> 0xF89F5A;
            case "LT2" -> 0xA0A7B2;
            case "HT2" -> 0xC4D3E7;
            case "LT1" -> 0xD5B355;
            case "HT1" -> 0xFFAA00;
            default -> 0xFFFFFF;
        };
    }

    private static MutableText getIcon(String mode) {
        return switch (mode) {
            case "wind_charge" -> Text.literal("💨").styled(s -> s.withColor(0xD6EEFF));
            case "slime_mace" -> Text.literal("🫟").styled(s -> s.withColor(0x45BC53));
            case "real_pot" -> Text.literal("🧪").styled(s -> s.withColor(0x912E48));
            case "pickaxe" -> Text.literal("⛏").styled(s -> s.withColor(0x4289BC));
            case "boxing" -> Text.literal("🥊").styled(s -> s.withColor(0xFF5555));
            case "jousting" -> Text.literal("🏇").styled(s -> s.withColor(0x7F3300));
            case "pressure_plate" -> Text.literal("🍽").styled(s -> s.withColor(0xAAAAAA));
            case "stone_age" -> Text.literal("🪨").styled(s -> s.withColor(0x555555));
            case "wooden_smp" -> Text.literal("🪵").styled(s -> s.withColor(0x914D1F));
            case "dogs_out" -> Text.literal("\uD83D\uDC63").styled(s -> s.withColor(0xFFBB8E));
            case "modern" -> Text.literal("☻").styled(s -> s.withColor(0x55FF55));
            case "carrot" -> Text.literal("🥕").styled(s -> s.withColor(0xFF6A00));
            case "one_shot" -> Text.literal("💥").styled(s -> s.withColor(0xFF5555));
            case "wavemelon" -> Text.literal("🍉").styled(s -> s.withColor(0x7FC9FF));
            case "crystal;" -> Text.literal("💎").styled(s -> s.withColor(0xFF55FF));
            case "wooden_spear" -> Text.literal("🩼").styled(s -> s.withColor(0x914D1F));
            case "bow_boost_mace" -> Text.literal("⚒").styled(s -> s.withColor(0x914D1F));
            default -> Text.literal("").styled(s -> s.withColor(0xFFFFFF));
        };
    }

    private static void fetch(UUID uuid) {
        String url = "https://funnitiers-api.onrender.com/player/" + uuid;
        client.sendAsync(HttpRequest.newBuilder().uri(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
                .thenAccept(res -> {
                    try {
                        TierProfile p = gson.fromJson(res.body(), TierProfile.class);
                        profileCache.put(uuid, p);
                    } catch (Exception ignored) {}
                });
    }

    // Inside TierManager.java
    private static String overrideMode = null;

    public static void setOverrideMode(String mode) {
        overrideMode = mode;
    }

}