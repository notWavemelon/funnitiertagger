package org.wavemelon.funnitiertagger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TierProfile {
    public String uuid;
    public String username;
    public Map<String, String> usernameHistory;
    public String region;
    public int points;
    public boolean restricted;
    public Map<String, GameModeData> tiers;

    public static class GameModeData {
        public String tier;
        public String peakTier;
        public int tierInt;
        public int points;
        public long attained; // Unix timestamp
        public boolean retired;
    }

    /**
     * Returns unique past usernames in ascending history order,
     * excluding duplicates and entries matching the current username.
     */
    public List<String> getPreviousUsernames() {
        if (this.usernameHistory == null || this.usernameHistory.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map.Entry<String, String>> entries = new ArrayList<>(this.usernameHistory.entrySet());
        entries.sort((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a.getKey()), Integer.parseInt(b.getKey()));
            } catch (NumberFormatException e) {
                return a.getKey().compareTo(b.getKey());
            }
        });

        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String currentLower = this.username != null ? this.username.toLowerCase(Locale.ROOT) : null;

        for (Map.Entry<String, String> entry : entries) {
            String pastName = entry.getValue();
            if (pastName == null || pastName.isBlank()) continue;

            String lower = pastName.toLowerCase(Locale.ROOT);
            if (lower.equals(currentLower)) continue;
            if (seen.add(lower)) {
                result.add(pastName);
            }
        }

        return result;
    }

    private static int getTierScore(GameModeData data) {
        if (data == null) return 999;
        int num = data.tierInt;
        if (num <= 0 && data.tier != null) {
            try {
                String digits = data.tier.replaceAll("\\D", "");
                if (!digits.isEmpty()) {
                    num = Integer.parseInt(digits);
                }
            } catch (Exception ignored) {
                num = 99;
            }
        }
        if (num <= 0) num = 99;

        // HT (High Tier) is better than LT (Low Tier)
        int subRank = (data.tier != null && data.tier.toUpperCase().startsWith("LT")) ? 1 : 0;
        return num * 2 + subRank;
    }

    // Logic to pick which gamemode to display (e.g., highest active tier or peak)
    public String getDisplayMode() {
        if (this.tiers == null || this.tiers.isEmpty()) return null;

        String bestMode = null;
        int bestScore = Integer.MAX_VALUE;
        boolean bestIsRetired = true;

        for (Map.Entry<String, GameModeData> entry : tiers.entrySet()) {
            String mode = entry.getKey();
            GameModeData data = entry.getValue();
            if (data == null) continue;

            int currentScore = getTierScore(data);
            boolean currentIsRetired = data.retired;

            // Priority Logic:
            // 1. Prefer Active over Retired
            // 2. If both are Active (or both are Retired), prefer the lower score (HT1 > LT1 > HT2 > LT2)
            if (bestMode == null || (bestIsRetired && !currentIsRetired)) {
                bestMode = mode;
                bestScore = currentScore;
                bestIsRetired = currentIsRetired;
            } else if (bestIsRetired == currentIsRetired) {
                if (currentScore < bestScore) {
                    bestMode = mode;
                    bestScore = currentScore;
                }
            }
        }

        return bestMode;
    }
}
