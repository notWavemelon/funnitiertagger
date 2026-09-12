package org.wavemelon.funnitiertagger.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.wavemelon.funnitiertagger.ModConfig;
import org.wavemelon.funnitiertagger.TierManager;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void changeDisplayName(CallbackInfoReturnable<Text> cir) {
        if (!ModConfig.getInstance().enabled) return;

        Text original = cir.getReturnValue();
        if (original == null) return;

        String raw = original.getString();
        // Prevent duplicate tagging if getDisplayName is wrapped or re-invoked
        if (raw.contains(" | ") && (raw.contains("HT") || raw.contains("LT") || raw.contains("R1") || raw.contains("R2") || raw.contains("R3") || raw.contains("R4") || raw.contains("R5") || raw.contains("pts"))) {
            return;
        }

        // 1. Cast 'this' to PlayerEntity to get access to player methods
        PlayerEntity player = (PlayerEntity) (Object) this;

        // 2. Get the formatted tag from TierManager
        Text tag = TierManager.getFormattedTag(player.getUuid());

        // 3. Only modify the name if a tag actually exists in the cache
        if (tag != null) {
            MutableText newName = Text.empty()
                    .append(tag)
                    .append(original);

            cir.setReturnValue(newName);
        }
    }
}
