package org.wavemelon.funnitiertagger;

import java.util.Map;

public class TierProfile {
    public String uuid;
    public String username;
    public String region;
    public int points;
    public Map<String, GameModeData> tiers;

    public static class GameModeData {
        public String tier;
        public String peakTier;
        public int tierInt;
        public int points;
        public long attained; // Unix timestamp
        public boolean retired;
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
