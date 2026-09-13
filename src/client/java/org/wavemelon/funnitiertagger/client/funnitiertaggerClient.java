package org.wavemelon.funnitiertagger.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.wavemelon.funnitiertagger.ModConfig;
import org.wavemelon.funnitiertagger.TierManager;

public class funnitiertaggerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModConfig.load();
        TierCommand.register();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

            // 1. /funnitiers:clearcache (Universal Client Command)
            dispatcher.register(ClientCommands.literal("funnitiers:clearcache")
                    .executes(context -> {
                        TierManager.clearCache();
                        context.getSource().sendFeedback(Component.literal("Tier cache cleared!").withStyle(ChatFormatting.GREEN));
                        return 1;
                    })
            );

            // 2. /funnimode <gamemode> (Universal Client Command)
            dispatcher.register(ClientCommands.literal("funnimode")
                    .then(ClientCommands.argument("gamemode", StringArgumentType.word())
                            .executes(context -> {
                                String mode = StringArgumentType.getString(context, "gamemode");

                                TierManager.setOverrideMode(mode);
                                TierManager.clearCache();

                                context.getSource().sendFeedback(Component.literal("Mode set to: " + mode + " (Cache Cleared)").withStyle(ChatFormatting.AQUA));
                                return 1;
                            })
                    )
                    // Reset to automatic peak mode if no argument is provided
                    .executes(context -> {
                        TierManager.setOverrideMode(null);
                        TierManager.clearCache();
                        context.getSource().sendFeedback(Component.literal("Mode reset to Automatic (Peak)").withStyle(ChatFormatting.YELLOW));
                        return 1;
                    })
            );
        });
    }
}
