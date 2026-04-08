package org.wavemelon.funnitiertagger.client;

import com.google.gson.Gson;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.wavemelon.funnitiertagger.TierProfile;

public class TierCommand {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M/dd/yyyy h:mm a")
            .withZone(ZoneId.systemDefault());

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var rootCommand = ClientCommandManager.literal("funnitiers")
                    .executes(ctx -> {
                        // Use the client player's name as default
                        String self = ctx.getSource().getClient().getSession().getUsername();
                        fetchAndSend(ctx.getSource(), self);
                        return 1;
                    })
                    .then(ClientCommandManager.argument("player", StringArgumentType.word())
                            .executes(ctx -> {
                                String name = StringArgumentType.getString(ctx, "player");
                                fetchAndSend(ctx.getSource(), name);
                                return 1;
                            })
                    );

            dispatcher.register(rootCommand);

            // Register the alias
            dispatcher.register(ClientCommandManager.literal("funnitiertagger")
                    .redirect(dispatcher.getRoot().getChild("funnitiers")));
        });
    }

    private static void fetchAndSend(FabricClientCommandSource source, String username) {
        String url = "https://funnitiers-api.onrender.com/player/" + username;

        client.sendAsync(HttpRequest.newBuilder().uri(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
                .thenAccept(res -> {
                    source.getClient().execute(() -> {
                        try {
                            TierProfile profile = gson.fromJson(res.body(), TierProfile.class);

                            if (profile == null || profile.tiers == null) {
                                source.sendFeedback(Text.literal("§cNo data found for " + username));
                                return;
                            }

                            // --- Header ---
                            MutableText playerName = Text.literal(profile.username).formatted(Formatting.AQUA);
                            playerName = playerName.styled(style -> style.withHoverEvent(
                                    new net.minecraft.text.HoverEvent.ShowText(
                                            Text.literal("§bUUID: §7" + profile.uuid + "\n§bRegion: §7" + profile.region)
                                    )
                            ));

                            MutableText header = Text.literal("\n=== ").formatted(Formatting.GRAY)
                                    .append(playerName)
                                    .append(Text.literal("'s funniTiers ").formatted(Formatting.AQUA))
                                    .append("===\n").formatted(Formatting.GRAY);

                            source.sendFeedback(header);

                            // --- Gamemodes List ---
                            for (Map.Entry<String, TierProfile.GameModeData> entry : profile.tiers.entrySet()) {
                                String modeKey = entry.getKey();
                                TierProfile.GameModeData data = entry.getValue();

                                MutableText line = Text.empty();

                                // Get the icon and extract its style for the text
                                MutableText icon = getIcon(modeKey);
                                String formattedName = formatModeName(modeKey);

                                // Apply icon color to the mode name
                                MutableText modeName = icon.append(Text.literal(" " + formattedName).setStyle(icon.getStyle()))
                                        .append(Text.literal(" §7-§r"));

                                String tierStr = (data.retired ? "R" : "") + data.tier;
                                int color = data.retired ? 0xA2D6FF : getTierColor(data.tier);
                                MutableText tierTag = Text.literal(tierStr).styled(s -> s.withColor(color));

                                // HOVER TEXT PRESERVED AS IS
                                String dateStr = data.attained == 0 ? "Unknown" : DATE_FORMAT.format(Instant.ofEpochSecond(data.attained));
                                MutableText hoverText = Text.literal("§bAttained: §7" + dateStr + "\n§bPeak Tier: §7" + data.peakTier);

                                line.append(modeName).append(" ").append(tierTag);
                                line = line.styled(style -> style.withHoverEvent(
                                        new net.minecraft.text.HoverEvent.ShowText(hoverText)
                                ));

                                source.sendFeedback(line);
                            }

                        } catch (Exception e) {
                            source.sendFeedback(Text.literal("§cError fetching tiers."));
                            e.printStackTrace();
                        }
                    });
                });
    }

    /**
     * Converts "wind_charge" to "Wind Charge"
     */
    private static String formatModeName(String str) {
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

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private static int getTierColor(String tier) {
        return switch (tier) {
            case "LT5" -> 0xD0D0D0;
            case "HT5" -> 0x7E7E7E;
            case "LT4" -> 0x8EEB8E;
            case "HT4" -> 0x006300;
            case "LT3" -> 0xC67B42;
            case "HT3" -> 0xF89F5A;
            case "LT2" -> 0xA0A7B2;
            case "HT2" -> 0xC4D3E7;
            case "LT1" -> 0xD5B355;
            case "HT1" -> 0xFFAA00;
            default -> 0xFFFFFF;
        };
    }

    private static MutableText getIcon(String mode) {
        return switch (mode) {
            case "wind_charge" -> Text.literal("💨").styled(s -> s.withColor(0xD6EEFF));
            case "slime_mace" -> Text.literal("🫟").styled(s -> s.withColor(0x45BC53));
            case "real_pot" -> Text.literal("🧪").styled(s -> s.withColor(0x912E48));
            case "pickaxe" -> Text.literal("⛏").styled(s -> s.withColor(0x4289BC));
            case "boxing" -> Text.literal("🥊").styled(s -> s.withColor(0xFF5555));
            case "jousting" -> Text.literal("🏇").styled(s -> s.withColor(0x7F3300));
            case "pressure_plate" -> Text.literal("🍽").styled(s -> s.withColor(0xAAAAAA));
            case "stone_age" -> Text.literal("🪨").styled(s -> s.withColor(0x555555));
            case "wooden_smp" -> Text.literal("🪵").styled(s -> s.withColor(0x914D1F));
            case "dogs_out" -> Text.literal("\uD83D\uDC63").styled(s -> s.withColor(0xFFBB8E));
            case "modern" -> Text.literal("☻").styled(s -> s.withColor(0x55FF55));
            case "carrot" -> Text.literal("🥕").styled(s -> s.withColor(0xFF6A00));
            case "one_shot" -> Text.literal("💥").styled(s -> s.withColor(0xFF5555));
            case "wavemelon" -> Text.literal("🍉").styled(s -> s.withColor(0x7FC9FF));
            case "crystal;" -> Text.literal("💎").styled(s -> s.withColor(0xFF55FF));
            case "wooden_spear" -> Text.literal("🩼").styled(s -> s.withColor(0x914D1F));
            case "bow_boost_mace" -> Text.literal("⚒").styled(s -> s.withColor(0x914D1F));
            default -> Text.literal("•");
        };
    }
}