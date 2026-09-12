package org.wavemelon.funnitiertagger.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(DisplayEntity.TextDisplayEntity.class)
public abstract class TextDisplayMixin {

    @Inject(method = "getText", at = @At("RETURN"), cancellable = true)
    private void addTierToTextDisplay(CallbackInfoReturnable<Text> cir) {
        if (!ModConfig.getInstance().enabled) return;

        Text original = cir.getReturnValue();
        if (original == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        String raw = original.getString();
        if (raw.isEmpty()) return;

        // Prevent duplicate tagging if getText is wrapped or re-invoked
        if (raw.contains(" | ") && (raw.contains("HT") || raw.contains("LT") || raw.contains("R1") || raw.contains("R2") || raw.contains("R3") || raw.contains("R4") || raw.contains("R5") || raw.contains("pts") || raw.contains("#"))) {
            return;
        }

        DisplayEntity.TextDisplayEntity entity = (DisplayEntity.TextDisplayEntity) (Object) this;

        // 1. Fast-path: check if entity is riding a player
        if (entity.getVehicle() instanceof PlayerEntity player) {
            Text tag = TierManager.getFormattedTag(player.getUuid());
            if (tag != null) {
                MutableText newText = Text.empty().append(tag).append(original);
                cir.setReturnValue(newText);
            }
            return;
        }

        // 2. Fallback: match by exact word boundary against nearby players
        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            String name = player.getName().getString();
            if (name == null || name.length() < 3) continue;

            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(name) + "\\b");
            Matcher matcher = pattern.matcher(raw);

            if (matcher.find()) {
                Text tag = TierManager.getFormattedTag(player.getUuid());
                if (tag != null) {
                    MutableText newText = Text.empty().append(tag).append(original);
                    cir.setReturnValue(newText);
                    return;
                }
            }
        }
    }
}
