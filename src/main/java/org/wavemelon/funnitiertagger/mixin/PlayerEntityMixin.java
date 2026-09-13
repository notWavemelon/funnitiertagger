package org.wavemelon.funnitiertagger.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.wavemelon.funnitiertagger.ModConfig;
import org.wavemelon.funnitiertagger.TierManager;

@Mixin(Player.class)
public abstract class PlayerEntityMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void changeDisplayName(CallbackInfoReturnable<Component> cir) {
        if (!ModConfig.getInstance().enabled) return;

        Component original = cir.getReturnValue();
        if (original == null) return;

        String raw = original.getString();
        // Prevent duplicate tagging if getDisplayName is wrapped or re-invoked
        if (raw.contains(" | ") && (raw.contains("HT") || raw.contains("LT") || raw.contains("R1") || raw.contains("R2") || raw.contains("R3") || raw.contains("R4") || raw.contains("R5") || raw.contains("pts") || raw.contains("#"))) {
            return;
        }

        // 1. Cast 'this' to Player to get access to player methods
        Player player = (Player) (Object) this;

        // 2. Get the formatted tag from TierManager
        Component tag = TierManager.getFormattedTag(player.getUUID());

        // 3. Only modify the name if a tag actually exists in the cache
        if (tag != null) {
            MutableComponent newName = Component.empty()
                    .append(tag)
                    .append(original);

            cir.setReturnValue(newName);
        }
    }
}
