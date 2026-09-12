package org.wavemelon.funnitiertagger.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.wavemelon.funnitiertagger.ModConfig;
import org.wavemelon.funnitiertagger.TierManager;

import java.util.regex.Pattern;

@Mixin(DisplayEntity.TextDisplayEntity.class)
public abstract class TextDisplayMixin {

    @Inject(method = "getText", at = @At("RETURN"), cancellable = true)
    private void modifyText(CallbackInfoReturnable<Text> cir) {
        if (!ModConfig.getInstance().enabled) return;

        Text original = cir.getReturnValue();
        if (original == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        String raw = original.getString();
        if (raw.isEmpty()) return;

        // Prevent duplicate tag stacking
        if (raw.contains(" | ") && (raw.contains("HT") || raw.contains("LT") || raw.contains("R1") || raw.contains("R2") || raw.contains("R3") || raw.contains("R4") || raw.contains("R5") || raw.contains("pts"))) {
            return;
        }

        DisplayEntity.TextDisplayEntity entity = (DisplayEntity.TextDisplayEntity) (Object) this;

        // 1. If text display entity is mounted on / riding a player
        Entity vehicle = entity.getVehicle();
        if (vehicle instanceof PlayerEntity player) {
            Text tag = TierManager.getFormattedTag(player.getUuid());
            if (tag != null) {
                MutableText newText = tag.copy().append(original);
                cir.setReturnValue(newText);
                return;
            }
        }

        // 2. Nametag check (skip large non-nametag text displays like holographic leaderboards)
        if (raw.length() > 64) return;

        for (PlayerEntity player : client.world.getPlayers()) {
            String name = player.getName().getString();
            if (name.isEmpty()) continue;

            // Word-boundary match to avoid matching substrings of other words (e.g., "Dan" matching "Danger")
            if (Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(raw).find()) {
                Text tag = TierManager.getFormattedTag(player.getUuid());
                if (tag != null) {
                    MutableText newText = tag.copy().append(original);
                    cir.setReturnValue(newText);
                    return;
                }
            }
        }
    }
}
