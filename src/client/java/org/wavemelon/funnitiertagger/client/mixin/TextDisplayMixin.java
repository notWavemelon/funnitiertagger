package org.wavemelon.funnitiertagger.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.wavemelon.funnitiertagger.ModConfig;
import org.wavemelon.funnitiertagger.TierManager;

@Mixin(value = Display.TextDisplay.class, priority = 1500)
public abstract class TextDisplayMixin {

    @Inject(method = "getText", at = @At("RETURN"), cancellable = true)
    private void addTierToTextDisplay(CallbackInfoReturnable<Component> cir) {
        if (!ModConfig.getInstance().enabled) return;

        Component original = cir.getReturnValue();
        if (original == null || TierManager.hasFunniTiersTag(original)) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        String raw = original.getString();
        if (raw.isEmpty()) return;

        Display.TextDisplay entity = (Display.TextDisplay) (Object) this;

        // 1. Fast-path: walk vehicle chain to check if this TextDisplay is riding a player
        Player ridingPlayer = findRidingPlayer(entity);
        if (ridingPlayer != null) {
            // Only tag if this display actually contains the player's username
            // (prevents tagging health bars / sub-lines riding the player)
            String playerName = ridingPlayer.getName().getString();
            Component updated = TierManager.applyTagToNameLine(original, playerName, ridingPlayer.getUUID());
            if (updated != original) {
                cir.setReturnValue(updated);
            }
            return;
        }

        // 2. Fallback: match by exact username boundary against nearby players
        for (AbstractClientPlayer player : client.level.players()) {
            if (entity.distanceToSqr(player) > 25.0) continue;

            String name = player.getName().getString();
            if (name == null || name.length() < 2) continue;

            if (TierManager.containsPlayerName(raw, name)) {
                Component updated = TierManager.applyTagToNameLine(original, name, player.getUUID());
                if (updated != original) {
                    cir.setReturnValue(updated);
                }
                return;
            }
        }
    }

    private static Player findRidingPlayer(Entity entity) {
        Entity current = entity.getVehicle();
        int depth = 0;
        while (current != null && depth < 4) {
            if (current instanceof Player player) {
                return player;
            }
            current = current.getVehicle();
            depth++;
        }
        return null;
    }
}
