package org.wavemelon.funnitiertagger.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.wavemelon.funnitiertagger.TierManager;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void changeDisplayName(CallbackInfoReturnable<Text> cir) {
        // 1. Cast 'this' to PlayerEntity to get access to player methods
        PlayerEntity player = (PlayerEntity) (Object) this;

        // 2. Get the formatted tag from your TierManager
        // This includes the icon, tier, and the " | " separator
        Text tag = TierManager.getFormattedTag(player.getUuid());

        // 3. Only modify the name if a tag actually exists in the cache
        if (tag != null) {
            MutableText newName = Text.empty()
                    .append(tag)
                    .append(cir.getReturnValue());

            cir.setReturnValue(newName);
        }
    }
}