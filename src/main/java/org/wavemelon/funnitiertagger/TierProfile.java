package org.wavemelon.funnitiertagger;

import java.util.Map;

public class TierProfile {
    public String uuid;
    public String username;
    public String region;
    public Map<String, GameModeData> tiers;

    public static class GameModeData {
        public String tier;
        public String peakTier;
        public int tierInt;
        public long attained; // Unix timestamp
        public boolean retired;
    }

    // Logic to pick which gamemode to display (e.g., first one found or a specific one)
    public String getDisplayMode() {
        if (this.tiers == null || this.tiers.isEmpty()) return null;

        String bestMode = null;
        int bestTier = Integer.MAX_VALUE;
        boolean bestIsRetired = true;

        for (Map.Entry<String, GameModeData> entry : tiers.entrySet()) {
            String mode = entry.getKey();
            GameModeData data = entry.getValue();
            String tierNum = data.tier.replaceAll("\\D","");
            try {
                int tierInt = Integer.parseInt(tierNum);
            } catch (NumberFormatException e) {
                throw new RuntimeException(e);
            }
            int currentTier = data.tierInt;
            boolean currentIsRetired = data.retired;

            // Priority Logic:
            // 1. Prefer Active over Retired
            // 2. If both are Active (or both are Retired), prefer the lower number (Tier 1 > Tier 2)
            if (bestMode == null || (bestIsRetired && !currentIsRetired)) {
                bestMode = mode;
                bestTier = currentTier;
                bestIsRetired = currentIsRetired;
            } else if (bestIsRetired == currentIsRetired) {
                if (currentTier < bestTier) {
                    bestMode = mode;
                    bestTier = currentTier;
                }
            }
        }

        return bestMode;
    }
}