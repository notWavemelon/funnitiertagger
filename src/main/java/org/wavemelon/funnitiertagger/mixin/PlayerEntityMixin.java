package org.wavemelon.funnitiertagger.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.wavemelon.funnitiertagger.ModConfig;
import org.wavemelon.funnitiertagger.TierManager;

@Mixin(value = Player.class, priority = 1500)
public abstract class PlayerEntityMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void changeDisplayName(CallbackInfoReturnable<Component> cir) {
        if (!ModConfig.getInstance().enabled) return;

        Component original = cir.getReturnValue();
        if (original == null || TierManager.hasFunniTiersTag(original)) return;

        Player player = (Player) (Object) this;
        Component updated = TierManager.applyTag(original, player.getUUID());
        if (updated != original) {
            cir.setReturnValue(updated);
        }
    }
}
