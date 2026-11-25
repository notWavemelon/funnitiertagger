package org.wavemelon.funnitiertagger.client.mixin;

import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.wavemelon.funnitiertagger.ModConfig;
import org.wavemelon.funnitiertagger.TierManager;

@Mixin(value = AvatarRenderer.class, priority = 1500)
public abstract class AvatarRendererMixin<AvatarlikeEntity extends Avatar & ClientAvatarEntity> {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void ensureTagPresentOnNameTag(AvatarlikeEntity entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        if (!ModConfig.getInstance().enabled) return;
        if (state.nameTag == null || TierManager.hasFunniTiersTag(state.nameTag)) return;

        if (entity instanceof Player player) {
            state.nameTag = TierManager.applyTag(state.nameTag, player.getUUID());
        }
    }
}
