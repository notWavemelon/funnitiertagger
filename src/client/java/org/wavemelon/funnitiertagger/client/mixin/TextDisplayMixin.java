package org.wavemelon.funnitiertagger.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.wavemelon.funnitiertagger.ModConfig;
import org.wavemelon.funnitiertagger.TierManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(Display.TextDisplay.class)
public abstract class TextDisplayMixin {

    @Inject(method = "getText", at = @At("RETURN"), cancellable = true)
    private void addTierToTextDisplay(CallbackInfoReturnable<Component> cir) {
        if (!ModConfig.getInstance().enabled) return;

        Component original = cir.getReturnValue();
        if (original == null) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        String raw = original.getString();
        if (raw.isEmpty()) return;

        // Prevent duplicate tagging if getText is wrapped or re-invoked
        if (raw.contains(" | ") && (raw.contains("HT") || raw.contains("LT") || raw.contains("R1") || raw.contains("R2") || raw.contains("R3") || raw.contains("R4") || raw.contains("R5") || raw.contains("pts") || raw.contains("#"))) {
            return;
        }

        Display.TextDisplay entity = (Display.TextDisplay) (Object) this;

        // 1. Fast-path: check if entity is riding a player
        if (entity.getVehicle() instanceof Player player) {
            Component tag = TierManager.getFormattedTag(player.getUUID());
            if (tag != null) {
                MutableComponent newText = Component.empty().append(tag).append(original);
                cir.setReturnValue(newText);
            }
            return;
        }

        // 2. Fallback: match by exact word boundary against nearby players
        for (AbstractClientPlayer player : client.level.players()) {
            String name = player.getName().getString();
            if (name == null || name.length() < 3) continue;

            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(name) + "\\b");
            Matcher matcher = pattern.matcher(raw);

            if (matcher.find()) {
                Component tag = TierManager.getFormattedTag(player.getUUID());
                if (tag != null) {
                    MutableComponent newText = Component.empty().append(tag).append(original);
                    cir.setReturnValue(newText);
                    return;
                }
            }
        }
    }
}
