package org.wavemelon.funnitiertagger.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.wavemelon.funnitiertagger.TierManager;

public class funnitiertaggerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TierCommand.register();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

            // 1. /funnitiers:clearcache (Universal Client Command)
            dispatcher.register(ClientCommandManager.literal("funnitiers:clearcache")
                    .executes(context -> {
                        TierManager.profileCache.clear();
                        context.getSource().sendFeedback(Text.literal("Tier cache cleared!").formatted(Formatting.GREEN));
                        return 1;
                    })
            );

            // 2. /funnimode <gamemode> (Universal Client Command)
            dispatcher.register(ClientCommandManager.literal("funnimode")
                    .then(ClientCommandManager.argument("gamemode", StringArgumentType.word())
                            .executes(context -> {
                                String mode = StringArgumentType.getString(context, "gamemode");

                                TierManager.setOverrideMode(mode);
                                TierManager.profileCache.clear(); // Clear cache to force refresh with new mode

                                context.getSource().sendFeedback(Text.literal("Mode set to: " + mode + " (Cache Cleared)").formatted(Formatting.AQUA));
                                return 1;
                            })
                    )
                    // Reset to automatic peak mode if no argument is provided
                    .executes(context -> {
                        TierManager.setOverrideMode(null);
                        TierManager.profileCache.clear();
                        context.getSource().sendFeedback(Text.literal("Mode reset to Automatic (Peak)").formatted(Formatting.YELLOW));
                        return 1;
                    })
            );
        });

    }
}