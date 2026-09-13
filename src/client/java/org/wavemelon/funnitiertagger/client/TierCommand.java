package org.wavemelon.funnitiertagger.client;

import com.google.gson.Gson;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import org.wavemelon.funnitiertagger.TierProfile;
import org.wavemelon.funnitiertagger.client.gui.ConfigScreen;
import org.wavemelon.funnitiertagger.client.gui.PlayerProfileScreen;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class TierCommand {

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();
    private static final Gson gson = new Gson();

    public static final String[] MODES = new String[] {
            "wind_charge",
            "slime_mace",
            "real_pot",
            "pickaxe",
            "boxing",
            "jousting",
            "pressure_plate",
            "stone_age",
            "wooden_smp",
            "dogs_out",
            "modern",
            "carrot",
            "one_shot",
            "wavemelon",
            "crystal;",
            "wooden_spear",
            "bow_boost_mace"
    };

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var rootCommand = ClientCommands.literal("funnitiers")
                    .executes(ctx -> {
                        // Open Config GUI when run without arguments
                        ctx.getSource().getClient().execute(() -> {
                            ctx.getSource().getClient().gui.setScreen(new ConfigScreen(null));
                        });
                        return 1;
                    })
                    .then(ClientCommands.argument("player", StringArgumentType.word())
                            .executes(ctx -> {
                                String name = StringArgumentType.getString(ctx, "player");
                                fetchAndOpen(ctx.getSource(), name);
                                return 1;
                            })
                    );

            dispatcher.register(rootCommand);

            // Register the alias
            dispatcher.register(ClientCommands.literal("funnitiertagger")
                    .redirect(dispatcher.getRoot().getChild("funnitiers")));
        });
    }

    private static void fetchAndOpen(FabricClientCommandSource source, String username) {
        try {
            String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
            String url = "https://funnitiers-api.onrender.com/player/" + encoded;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "funnitiers/2.0.0")
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .whenComplete((res, err) -> {
                        source.getClient().execute(() -> {
                            try {
                                if (err != null || res == null || res.statusCode() != 200) {
                                    source.sendFeedback(Component.literal("§cNo data found for " + username));
                                    return;
                                }

                                TierProfile profile = gson.fromJson(res.body(), TierProfile.class);

                                if (profile == null || profile.tiers == null || profile.tiers.isEmpty()) {
                                    source.sendFeedback(Component.literal("§cNo data found for " + username));
                                    return;
                                }

                                // Open the 3D bust & profile GUI
                                source.getClient().gui.setScreen(new PlayerProfileScreen(profile));

                            } catch (Exception e) {
                                source.sendFeedback(Component.literal("§cError fetching profile for " + username));
                                e.printStackTrace();
                            }
                        });
                    });
        } catch (Exception e) {
            source.sendFeedback(Component.literal("§cInvalid player name: " + username));
        }
    }

    /**
     * Converts "wind_charge" to "Wind Charge" and "crystal;" to "Crystal;"
     */
    public static String formatModeName(String str) {
        if (str == null || str.isEmpty()) return str;

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : str.toCharArray()) {
            if (c == '_') {
                result.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
