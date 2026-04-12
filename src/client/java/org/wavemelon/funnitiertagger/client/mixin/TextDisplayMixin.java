package org.wavemelon.funnitiertagger.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.wavemelon.funnitiertagger.TierManager;

@Mixin(DisplayEntity.TextDisplayEntity.class)
public abstract class TextDisplayMixin {

    @Inject(method = "getText", at = @At("RETURN"), cancellable = true)
    private void modifyText(CallbackInfoReturnable<Text> cir) {
        Text original = cir.getReturnValue();
        if (original == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        String raw = original.getString();

        for (PlayerEntity player : client.world.getPlayers()) {
            String name = player.getName().getString();

            if (raw.contains(name)) {
                Text tag = TierManager.getFormattedTag(player.getUuid());

                if (tag != null) {
                    // THIS is the fix:
                    MutableText newText = tag.copy()
                            .append(Text.literal(""))
                            .append(original);

                    cir.setReturnValue(newText);
                    return;
                }
            }
        }
    }
}