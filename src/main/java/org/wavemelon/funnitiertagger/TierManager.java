package org.wavemelon.funnitiertagger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TierManager {
    public static final Map<UUID, TierProfile> profileCache = new ConcurrentHashMap<>();
    private static final Set<UUID> pendingFetches = ConcurrentHashMap.newKeySet();
    private static final TierProfile EMPTY_PROFILE = new TierProfile();

    // Cache for overall placement (#1, #2, etc.) and attained timestamps
    public static final Map<String, Integer> overallRankCache = new ConcurrentHashMap<>();
    public static final Map<String, Long> attainedDataCache = new ConcurrentHashMap<>();
    private static long lastOverallFetchTime = 0;

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();

    private static String overrideMode = null;

    public static void setOverrideMode(String mode) {
        overrideMode = mode;
        ModConfig.getInstance().selectedMode = mode;
        ModConfig.save();
    }

    public static String getOverrideMode() {
        if (overrideMode != null) return overrideMode;
        return ModConfig.getInstance().selectedMode;
    }

    public static void clearCache() {
        profileCache.clear();
        pendingFetches.clear();
        overallRankCache.clear();
        attainedDataCache.clear();
        lastOverallFetchTime = 0;
        fetchOverallLeaderboard();
    }

    public static Integer getOverallRank(String uuidOrUsername) {
        if (uuidOrUsername == null) return null;
        checkFetchOverall();
        String clean = uuidOrUsername.replace("-", "").toLowerCase();
        Integer rank = overallRankCache.get(clean);
        if (rank == null) {
            rank = overallRankCache.get(uuidOrUsername.toLowerCase());
        }
        return rank;
    }

    public static Long getAttained(String uuid, String mode) {
        if (uuid == null || mode == null) return null;
        checkFetchOverall();
        String cleanUuid = uuid.replace("-", "").toLowerCase();
        return attainedDataCache.get(cleanUuid + ":" + mode.toLowerCase());
    }

    private static void checkFetchOverall() {
        long now = System.currentTimeMillis();
        if (now - lastOverallFetchTime > 60_000) { // Refresh every 60 seconds
            fetchOverallLeaderboard();
        }
    }

    public static void fetchOverallLeaderboard() {
        lastOverallFetchTime = System.currentTimeMillis();
        String url = "https://funnitiers-api.onrender.com/overall";
        client.sendAsync(HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", "funnitiers/1.0").build(), HttpResponse.BodyHandlers.ofString())
                .whenComplete((res, err) -> {
                    try {
                        if (err != null || res == null || res.statusCode() != 200) {
                            return;
                        }

                        JsonArray array = gson.fromJson(res.body(), JsonArray.class);
                        if (array == null) return;

                        for (int i = 0; i < array.size(); i++) {
                            JsonElement el = array.get(i);
                            if (!el.isJsonObject()) continue;
                            JsonObject playerObj = el.getAsJsonObject();

                            int rank = i + 1;

                            String uuidStr = playerObj.has("uuid") ? playerObj.get("uuid").getAsString() : null;
                            String username = playerObj.has("username") ? playerObj.get("username").getAsString() : null;

                            if (uuidStr != null && !uuidStr.isEmpty()) {
                                String cleanUuid = uuidStr.replace("-", "").toLowerCase();
                                overallRankCache.put(cleanUuid, rank);

                                // Parse gamemodes attained dates
                                if (playerObj.has("gamemodes") && playerObj.get("gamemodes").isJsonArray()) {
                                    JsonArray gmArray = playerObj.getAsJsonArray("gamemodes");
                                    for (JsonElement gmEl : gmArray) {
                                        if (!gmEl.isJsonObject()) continue;
                                        JsonObject gmObj = gmEl.getAsJsonObject();
                                        if (gmObj.has("mode") && gmObj.has("attained")) {
                                            String mode = gmObj.get("mode").getAsString().toLowerCase();
                                            long attained = gmObj.get("attained").getAsLong();
                                            attainedDataCache.put(cleanUuid + ":" + mode, attained);
                                        }
                                    }
                                }
                            }

                            if (username != null && !username.isEmpty()) {
                                overallRankCache.put(username.toLowerCase(), rank);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
    }

    public static Text getFormattedTag(UUID uuid) {
        if (uuid == null) return null;

        if (!ModConfig.getInstance().enabled) {
            return null;
        }

        TierProfile profile = profileCache.get(uuid);
        if (profile == null) {
            if (!profileCache.containsKey(uuid)) {
                if (pendingFetches.add(uuid)) {
                    fetch(uuid);
                }
            }
            return null;
        }

        if (profile == EMPTY_PROFILE || profile.tiers == null || profile.tiers.isEmpty()) {
            return null;
        }

        // 1. Overall Rank Display Mode
        if (ModConfig.getInstance().displayType == ModConfig.DisplayType.RANK) {
            Integer rank = getOverallRank(uuid.toString());
            if (rank != null) {
                MutableText tag = Text.literal("🏆 ").styled(s -> s.withColor(0xFFD700));
                tag.append(Text.literal("#" + rank).styled(s -> s.withColor(0xFFAA00)));
                tag.append(Text.literal(" | ").styled(s -> s.withColor(0xAAAAAA)));
                return tag;
            }
        }

        // 2. Points Display Mode
        if (ModConfig.getInstance().displayType == ModConfig.DisplayType.POINTS) {
            MutableText tag = Text.literal("⭐ ").styled(s -> s.withColor(0xFFD700));
            tag.append(Text.literal(profile.points + " pts").styled(s -> s.withColor(0xFFAA00)));
            tag.append(Text.literal(" | ").styled(s -> s.withColor(0xAAAAAA)));
            return tag;
        }

        // 3. Tiers Display Mode (Default)
        String effectiveMode = getOverrideMode();
        String modeKey;
        if (effectiveMode != null && profile.tiers.containsKey(effectiveMode)) {
            modeKey = effectiveMode;
        } else {
            modeKey = profile.getDisplayMode();
        }

        if (modeKey == null) return null;

        TierProfile.GameModeData data = profile.tiers.get(modeKey);
        if (data == null || data.tier == null) return null;

        try {
            // Build the Icon
            MutableText tag = getIcon(modeKey);

            // Build the Tier (e.g., "R1" or "HT1")
            String tierStr = (data.retired ? "R" : "") + data.tier;
            int tierColor = data.retired ? 0x880EFC : getTierColor(data.tier);
            tag.append(Text.literal(" " + tierStr).styled(s -> s.withColor(tierColor)));

            // Add Separator
            tag.append(Text.literal(" | ").styled(s -> s.withColor(0xAAAAAA)));

            return tag;
        } catch (Exception e) {
            return null;
        }
    }

    private static int getTierColor(String tier) {
        if (tier == null) return 0xFFFFFF;
        return switch (tier) {
            case "LT5" -> 0xD0D0D0;
            case "HT5" -> 0x7E7E7E;
            case "LT4" -> 0x8EEB8E;
            case "HT4" -> 0x00A000;
            case "LT3" -> 0xC67B42;
            case "HT3" -> 0xF89F5A;
            case "LT2" -> 0xA0A7B2;
            case "HT2" -> 0xC4D3E7;
            case "LT1" -> 0xD5B355;
            case "HT1" -> 0xFFAA00;
            default -> 0xFFFFFF;
        };
    }

    public static MutableText getIcon(String mode) {
        if (mode == null) return Text.literal("");
        return switch (mode) {
            case "wind_charge" -> Text.literal("\uE000");
            case "slime_mace" -> Text.literal("\uE001");
            case "real_pot" -> Text.literal("\uE002");
            case "pickaxe" -> Text.literal("\uE003");
            case "boxing" -> Text.literal("\uE004");
            case "jousting" -> Text.literal("\uE005");
            case "pressure_plate" -> Text.literal("\uE006");
            case "stone_age" -> Text.literal("\uE007");
            case "wooden_smp" -> Text.literal("\uE008");
            case "dogs_out" -> Text.literal("\uE009");
            case "modern" -> Text.literal("\uE00A");
            case "carrot" -> Text.literal("\uE00B");
            case "one_shot" -> Text.literal("\uE00C");
            case "wavemelon" -> Text.literal("\uE00D");
            case "crystal", "crystal;" -> Text.literal("\uE00E");
            case "wooden_spear" -> Text.literal("\uE00F");
            case "bow_boost_mace" -> Text.literal("\uE010");
            default -> Text.literal("");
        };
    }

    private static void fetch(UUID uuid) {
        String url = "https://funnitiers-api.onrender.com/player/" + uuid;
        client.sendAsync(HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", "funnitiers/1.0").build(), HttpResponse.BodyHandlers.ofString())
                .whenComplete((res, err) -> {
                    try {
                        if (err != null || res == null || res.statusCode() != 200) {
                            profileCache.put(uuid, EMPTY_PROFILE);
                            return;
                        }
                        TierProfile p = gson.fromJson(res.body(), TierProfile.class);
                        if (p != null && p.tiers != null) {
                            profileCache.put(uuid, p);
                        } else {
                            profileCache.put(uuid, EMPTY_PROFILE);
                        }
                    } catch (Exception e) {
                        profileCache.put(uuid, EMPTY_PROFILE);
                    } finally {
                        pendingFetches.remove(uuid);
                    }
                });
    }
}
