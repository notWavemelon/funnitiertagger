package org.wavemelon.funnitiertagger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class TierManager {
    public static final String TAG_MARKER = "funnitiers_tag";

    public static final Map<UUID, TierProfile> profileCache = new ConcurrentHashMap<>();
    private static final Set<UUID> pendingFetches = ConcurrentHashMap.newKeySet();
    private static final TierProfile EMPTY_PROFILE = new TierProfile();

    public static final Map<String, Integer> overallRankCache = new ConcurrentHashMap<>();
    public static final Map<String, Long> attainedDataCache = new ConcurrentHashMap<>();
    private static long lastOverallFetchTime = 0;

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();
    private static final Gson gson = new Gson();
    private static final Pattern LEGACY_COLOR_PATTERN = Pattern.compile("§.");

    private static String overrideMode = null;

    private record TagParts(MutableComponent icon, MutableComponent label, int accentColor) {
        MutableComponent formatBadge(ModConfig.TagPosition position) {
            boolean hasIcon = icon != null && !icon.getString().isEmpty();
            if (position == ModConfig.TagPosition.RIGHT) {
                // Right side: [rank] [icon]
                MutableComponent badge = Component.empty().append(label);
                if (hasIcon) {
                    badge.append(Component.literal(" ")).append(icon);
                }
                return badge;
            } else {
                // Left side: [icon] [rank]
                MutableComponent badge = Component.empty();
                if (hasIcon) {
                    badge.append(icon).append(Component.literal(" "));
                }
                badge.append(label);
                return badge;
            }
        }
    }

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
        if (now - lastOverallFetchTime > 60_000) {
            fetchOverallLeaderboard();
        }
    }

    public static void fetchOverallLeaderboard() {
        lastOverallFetchTime = System.currentTimeMillis();
        String url = "https://funnitiers-api.onrender.com/overall";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "funnitiers/2.0.1")
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks whether the component tree already contains a tag added by FunniTiers.
     * Uses an invisible style insertion marker so tags from other tier taggers are never mistaken for ours.
     */
    public static boolean hasFunniTiersTag(Component component) {
        if (component == null) return false;
        if (TAG_MARKER.equals(component.getStyle().getInsertion())) {
            return true;
        }
        for (Component sibling : component.getSiblings()) {
            if (hasFunniTiersTag(sibling)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether text contains the player's username as a standalone identifier
     * (prevents tagging health displays or unrelated text lines).
     */
    public static boolean containsPlayerName(String text, String playerName) {
        if (text == null || text.isEmpty() || playerName == null || playerName.length() < 2) {
            return false;
        }
        String clean = LEGACY_COLOR_PATTERN.matcher(text).replaceAll("");
        Pattern pattern = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(playerName) + "(?![A-Za-z0-9_])");
        return pattern.matcher(clean).find();
    }

    /**
     * Attaches the FunniTiers tag to a component (on the left or right per config),
     * preserving any tags already added by other tier taggers.
     */
    public static Component applyTag(Component original, UUID uuid) {
        if (original == null || uuid == null || hasFunniTiersTag(original)) {
            return original;
        }
        TagParts parts = buildTagParts(uuid);
        if (parts == null) {
            return original;
        }
        return attachTagParts(original, parts);
    }

    /**
     * Attaches the FunniTiers tag ONLY to the line containing the player's username.
     * For single-line text displays, behaves identically to applyTag after verifying the username is present.
     * For multi-line text displays (e.g. username + health below), only the username line is tagged.
     */
    public static Component applyTagToNameLine(Component original, String playerName, UUID uuid) {
        if (original == null || playerName == null || uuid == null || hasFunniTiersTag(original)) {
            return original;
        }

        String raw = original.getString();
        if (!containsPlayerName(raw, playerName)) {
            return original;
        }

        if (!raw.contains("\n")) {
            return applyTag(original, uuid);
        }

        TagParts parts = buildTagParts(uuid);
        if (parts == null) {
            return original;
        }

        List<MutableComponent> lines = new ArrayList<>();
        lines.add(Component.empty());

        original.visit((style, text) -> {
            String[] segments = text.split("\n", -1);
            for (int i = 0; i < segments.length; i++) {
                if (i > 0) {
                    lines.add(Component.empty());
                }
                if (!segments[i].isEmpty()) {
                    lines.get(lines.size() - 1).append(Component.literal(segments[i]).withStyle(style));
                }
            }
            return Optional.empty();
        }, Style.EMPTY);

        boolean tagged = false;
        MutableComponent rebuilt = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                rebuilt.append(Component.literal("\n"));
            }
            MutableComponent line = lines.get(i);
            if (!tagged && containsPlayerName(line.getString(), playerName)) {
                rebuilt.append(attachTagParts(line, parts));
                tagged = true;
            } else {
                rebuilt.append(line);
            }
        }

        return tagged ? rebuilt : original;
    }

    private static MutableComponent attachTagParts(Component target, TagParts parts) {
        ModConfig.TagPosition position = ModConfig.getInstance().tagPosition;
        MutableComponent divider = buildDivider(parts.accentColor());
        MutableComponent badge = parts.formatBadge(position);
        MutableComponent markedTag = Component.empty().withStyle(s -> s.withInsertion(TAG_MARKER));

        if (position == ModConfig.TagPosition.RIGHT) {
            // [name] [divider] [rank] [icon]
            markedTag.append(divider).append(badge);
            return Component.empty().append(target).append(markedTag);
        } else {
            // [icon] [rank] [divider] [name]
            markedTag.append(badge).append(divider);
            return Component.empty().append(markedTag).append(target);
        }
    }

    private static MutableComponent buildDivider(int accentColor) {
        return switch (ModConfig.getInstance().dividerStyle) {
            case DYNAMIC -> Component.literal(" | ").withStyle(s -> s.withColor(accentColor));
            case NONE -> Component.literal(" ").withStyle(Style.EMPTY);
            case DEFAULT -> Component.literal(" | ").withStyle(s -> s.withColor(0xAAAAAA));
        };
    }

    private static TagParts buildTagParts(UUID uuid) {
        if (uuid == null || !ModConfig.getInstance().enabled) {
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

        if (profile == EMPTY_PROFILE || profile.restricted || profile.tiers == null || profile.tiers.isEmpty()) {
            return null;
        }

        ModConfig config = ModConfig.getInstance();

        if (config.displayType == ModConfig.DisplayType.RANK) {
            Integer rank = getOverallRank(uuid.toString());
            if (rank != null) {
                MutableComponent icon = Component.literal("🏆").withStyle(s -> s.withColor(0xFFD700));
                MutableComponent label = Component.literal("#" + rank).withStyle(s -> s.withColor(0xFFAA00));
                return new TagParts(icon, label, 0xFFAA00);
            }
        }

        if (config.displayType == ModConfig.DisplayType.POINTS) {
            MutableComponent icon = Component.literal("⭐").withStyle(s -> s.withColor(0xFFD700));
            MutableComponent label = Component.literal(profile.points + " pts").withStyle(s -> s.withColor(0xFFAA00));
            return new TagParts(icon, label, 0xFFAA00);
        }

        String effectiveMode = getOverrideMode();
        String modeKey;
        if (effectiveMode != null && profile.tiers.containsKey(effectiveMode)) {
            modeKey = effectiveMode;
        } else if ("crystal".equals(effectiveMode) && profile.tiers.containsKey("crystal;")) {
            modeKey = "crystal;";
        } else {
            modeKey = profile.getDisplayMode();
        }

        if (modeKey == null) return null;

        TierProfile.GameModeData data = profile.tiers.get(modeKey);
        if (data == null || data.tier == null) return null;

        try {
            MutableComponent icon = getIcon(modeKey);
            String tierStr = (data.retired ? "R" : "") + data.tier;
            int tierColor = getTierColor(data.tier, data.retired);
            MutableComponent label = Component.literal(tierStr).withStyle(s -> s.withColor(tierColor));
            return new TagParts(icon, label, tierColor);
        } catch (Exception e) {
            return null;
        }
    }

    public static Component getFormattedTag(UUID uuid) {
        TagParts parts = buildTagParts(uuid);
        if (parts == null) return null;
        ModConfig.TagPosition position = ModConfig.getInstance().tagPosition;
        MutableComponent divider = buildDivider(parts.accentColor());
        MutableComponent badge = parts.formatBadge(position);
        MutableComponent tag = Component.empty().withStyle(s -> s.withInsertion(TAG_MARKER));
        if (position == ModConfig.TagPosition.RIGHT) {
            tag.append(divider).append(badge);
        } else {
            tag.append(badge).append(divider);
        }
        return tag;
    }

    public static MutableComponent buildPreviewNametag(String modeKey, String rankKey, String samplePlayer) {
        boolean retired = "RETIRED".equalsIgnoreCase(rankKey);
        String tierStr = retired ? "RHT1" : rankKey;
        int color = ModConfig.getInstance().getColorByKey(rankKey);
        MutableComponent icon = getIcon(modeKey);
        MutableComponent label = Component.literal(tierStr).withStyle(s -> s.withColor(color));
        return attachTagParts(Component.literal(samplePlayer).withStyle(s -> s.withColor(0xFFFFFF)), new TagParts(icon, label, color));
    }

    public static int getTierColor(String tier, boolean retired) {
        return ModConfig.getInstance().getTierColor(tier, retired);
    }

    public static MutableComponent getIcon(String mode) {
        if (mode == null) return Component.literal("");
        if (ModConfig.getInstance().iconStyle == ModConfig.IconStyle.LEGACY) {
            return getLegacyIcon(mode);
        }
        return getCustomIcon(mode);
    }

    private static MutableComponent getCustomIcon(String mode) {
        String glyph = switch (mode) {
            case "wind_charge" -> "\uF6C0";
            case "slime_mace" -> "\uF6C1";
            case "real_pot" -> "\uF6C2";
            case "pickaxe" -> "\uF6C3";
            case "boxing" -> "\uF6C4";
            case "jousting" -> "\uF6C5";
            case "pressure_plate" -> "\uF6C6";
            case "stone_age" -> "\uF6C7";
            case "wooden_smp" -> "\uF6C8";
            case "dogs_out" -> "\uF6C9";
            case "modern" -> "\uF6CA";
            case "carrot" -> "\uF6CB";
            case "one_shot" -> "\uF6CC";
            case "wavemelon" -> "\uF6CD";
            case "crystal", "crystal;" -> "\uF6CE";
            case "wooden_spear" -> "\uF6CF";
            case "bow_boost_mace" -> "\uF6D0";
            default -> "";
        };
        return Component.literal(glyph).withStyle(s -> s.withColor(0xFFFFFF));
    }

    private static MutableComponent getLegacyIcon(String mode) {
        return switch (mode) {
            case "wind_charge" -> Component.literal("💨").withStyle(s -> s.withColor(0xD6EEFF));
            case "slime_mace" -> Component.literal("🫟").withStyle(s -> s.withColor(0x45BC53));
            case "real_pot" -> Component.literal("🧪").withStyle(s -> s.withColor(0x912E48));
            case "pickaxe" -> Component.literal("⛏").withStyle(s -> s.withColor(0x4289BC));
            case "boxing" -> Component.literal("🥊").withStyle(s -> s.withColor(0xFF5555));
            case "jousting" -> Component.literal("🏇").withStyle(s -> s.withColor(0x7F3300));
            case "pressure_plate" -> Component.literal("🍽").withStyle(s -> s.withColor(0xAAAAAA));
            case "stone_age" -> Component.literal("🪨").withStyle(s -> s.withColor(0x555555));
            case "wooden_smp" -> Component.literal("🪵").withStyle(s -> s.withColor(0x914D1F));
            case "dogs_out" -> Component.literal("\uD83D\uDC63").withStyle(s -> s.withColor(0xFFBB8E));
            case "modern" -> Component.literal("☻").withStyle(s -> s.withColor(0x55FF55));
            case "carrot" -> Component.literal("🥕").withStyle(s -> s.withColor(0xFF6A00));
            case "one_shot" -> Component.literal("💥").withStyle(s -> s.withColor(0xFF5555));
            case "wavemelon" -> Component.literal("🍉").withStyle(s -> s.withColor(0x7FC9FF));
            case "crystal", "crystal;" -> Component.literal("💎").withStyle(s -> s.withColor(0xFF55FF));
            case "wooden_spear" -> Component.literal("🩼").withStyle(s -> s.withColor(0x914D1F));
            case "bow_boost_mace" -> Component.literal("⚒").withStyle(s -> s.withColor(0x914D1F));
            default -> Component.literal("").withStyle(s -> s.withColor(0xFFFFFF));
        };
    }

    private static void fetch(UUID uuid) {
        String url = "https://funnitiers-api.onrender.com/player/" + uuid;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "funnitiers/2.0.1")
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .whenComplete((res, err) -> {
                        try {
                            if (err != null || res == null || res.statusCode() != 200) {
                                profileCache.put(uuid, EMPTY_PROFILE);
                                return;
                            }
                            TierProfile p = gson.fromJson(res.body(), TierProfile.class);
                            if (p != null && (p.tiers != null || p.restricted)) {
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
        } catch (Exception e) {
            profileCache.put(uuid, EMPTY_PROFILE);
            pendingFetches.remove(uuid);
        }
    }
}
