package org.wavemelon.funnitiertagger.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.wavemelon.funnitiertagger.TierManager;
import org.wavemelon.funnitiertagger.TierProfile;
import org.wavemelon.funnitiertagger.client.TierCommand;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerProfileScreen extends Screen {
    private static final Map<String, Identifier> BUST_CACHE = new ConcurrentHashMap<>();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final Screen parent;
    private final TierProfile profile;
    private Identifier bustTexture = null;
    private boolean imageLoading = true;
    private boolean imageFailed = false;

    public PlayerProfileScreen(Screen parent, TierProfile profile) {
        super(Text.literal(profile != null && profile.username != null ? profile.username : "Player Profile"));
        this.parent = parent;
        this.profile = profile;
    }

    public PlayerProfileScreen(TierProfile profile) {
        this(null, profile);
    }

    @Override
    protected void init() {
        int cardHeight = Math.min(220, this.height - 50);
        int cardY = (this.height - cardHeight) / 2 - 10;
        int buttonY = Math.min(this.height - 26, cardY + cardHeight + 8);

        // Centered Done button
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close())
                .dimensions(this.width / 2 - 75, buttonY, 150, 20)
                .build());

        // Trigger overall rank fetch
        if (profile != null) {
            TierManager.getOverallRank(profile.uuid);
        }

        // Load 3D bust image if not already cached
        if (profile != null && profile.uuid != null) {
            String cleanUuid = profile.uuid.replace("-", "").toLowerCase();
            if (BUST_CACHE.containsKey(cleanUuid)) {
                this.bustTexture = BUST_CACHE.get(cleanUuid);
                this.imageLoading = false;
            } else {
                fetchBustImage(profile.uuid, cleanUuid);
            }
        } else {
            this.imageLoading = false;
            this.imageFailed = true;
        }
    }

    private void fetchBustImage(String uuidWithHyphens, String cleanUuid) {
        String craftyUrl = "https://render.crafty.gg/3d/bust/" + uuidWithHyphens;
        String visageUrl = "https://visage.surgeplay.com/bust/" + uuidWithHyphens;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(craftyUrl))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "funnitiers/2.0.0")
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                    .whenComplete((res, err) -> {
                        if (err == null && res != null && res.statusCode() == 200) {
                            tryDecodeAndRegister(res.body(), cleanUuid, () -> fetchFallbackImage(visageUrl, cleanUuid));
                        } else {
                            fetchFallbackImage(visageUrl, cleanUuid);
                        }
                    });
        } catch (Exception e) {
            fetchFallbackImage(visageUrl, cleanUuid);
        }
    }

    private void fetchFallbackImage(String fallbackUrl, String cleanUuid) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fallbackUrl))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "funnitiers/2.0.0")
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                    .whenComplete((res, err) -> {
                        if (err == null && res != null && res.statusCode() == 200) {
                            tryDecodeAndRegister(res.body(), cleanUuid, () -> {
                                MinecraftClient.getInstance().execute(() -> {
                                    this.imageLoading = false;
                                    this.imageFailed = true;
                                });
                            });
                        } else {
                            MinecraftClient.getInstance().execute(() -> {
                                this.imageLoading = false;
                                this.imageFailed = true;
                            });
                        }
                    });
        } catch (Exception e) {
            MinecraftClient.getInstance().execute(() -> {
                this.imageLoading = false;
                this.imageFailed = true;
            });
        }
    }

    private void tryDecodeAndRegister(byte[] bytes, String cleanUuid, Runnable onFailure) {
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                onFailure.run();
                return;
            }
            MinecraftClient.getInstance().execute(() -> {
                try {
                    NativeImageBackedTexture dynamicTexture = new NativeImageBackedTexture(() -> "bust_" + cleanUuid, image);
                    Identifier textureId = Identifier.of("funnitiers", "bust_" + cleanUuid);
                    MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, dynamicTexture);
                    BUST_CACHE.put(cleanUuid, textureId);
                    this.bustTexture = textureId;
                    this.imageLoading = false;
                    this.imageFailed = false;
                } catch (Exception e) {
                    this.imageLoading = false;
                    this.imageFailed = true;
                }
            });
        } catch (Throwable t) {
            onFailure.run();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        if (profile == null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No profile data").formatted(Formatting.RED), this.width / 2, this.height / 2, 0xFFFF5555);
            return;
        }

        // Dynamically compute card bounds based on screen size
        int cardWidth = Math.min(460, this.width - 20);
        int cardHeight = Math.min(215, this.height - 48);
        int cardX = (this.width - cardWidth) / 2;
        int cardY = Math.max(8, (this.height - cardHeight) / 2 - 14);

        // Draw Main Background Card
        context.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, 0xD0101015);
        context.fill(cardX - 1, cardY - 1, cardX + cardWidth + 1, cardY, 0x40FFFFFF); // Top border
        context.fill(cardX - 1, cardY + cardHeight, cardX + cardWidth + 1, cardY + cardHeight + 1, 0x40FFFFFF); // Bottom border
        context.fill(cardX - 1, cardY, cardX, cardY + cardHeight, 0x40FFFFFF); // Left border
        context.fill(cardX + cardWidth, cardY, cardX + cardWidth + 1, cardY + cardHeight, 0x40FFFFFF); // Right border

        // Layout: Left Panel = Player Hero Card (145px), Right Panel = Gamemodes Grid
        int leftWidth = 145;
        int leftCenterX = cardX + (leftWidth / 2);

        // Divider between left and right panel
        context.fill(cardX + leftWidth, cardY + 10, cardX + leftWidth + 1, cardY + cardHeight - 10, 0x25FFFFFF);

        // --- LEFT PANEL: PLAYER PROFILE ---
        // 1. Player Username (Bold Aqua)
        String name = profile.username != null ? profile.username : "Unknown";
        MutableText nameText = Text.literal(name).formatted(Formatting.AQUA, Formatting.BOLD);
        context.drawCenteredTextWithShadow(this.textRenderer, nameText, leftCenterX, cardY + 12, 0xFF55FFFF);

        // 2. 3D Bust (Size 76x76)
        int bustSize = 76;
        int bustX = leftCenterX - (bustSize / 2);
        int bustY = cardY + 28;

        // Bust card frame
        context.fill(bustX - 3, bustY - 3, bustX + bustSize + 3, bustY + bustSize + 3, 0x80000000);
        context.fill(bustX - 3, bustY - 3, bustX + bustSize + 3, bustY - 2, 0x30FFFFFF);

        if (this.bustTexture != null) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, this.bustTexture, bustX, bustY, 0.0f, 0.0f, bustSize, bustSize, bustSize, bustSize);
        } else if (this.imageLoading) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Loading...").formatted(Formatting.GRAY), leftCenterX, bustY + 34, 0xFFAAAAAA);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No Bust").formatted(Formatting.DARK_GRAY), leftCenterX, bustY + 34, 0xFF888888);
        }

        // 3. Stats below bust
        int statsY = bustY + bustSize + 8;
        String region = profile.region != null ? profile.region : "Unknown";
        Text regionText = Text.literal("Region: ").formatted(Formatting.GRAY)
                .append(Text.literal(region).formatted(Formatting.WHITE, Formatting.BOLD));
        context.drawCenteredTextWithShadow(this.textRenderer, regionText, leftCenterX, statsY, 0xFFFFFFFF);

        // Overall placement rank (if available) + Points
        Integer overallRank = null;
        if (profile.uuid != null) {
            overallRank = TierManager.getOverallRank(profile.uuid);
        }
        if (overallRank == null && profile.username != null) {
            overallRank = TierManager.getOverallRank(profile.username);
        }

        MutableText pointsText = Text.literal("Points: ").formatted(Formatting.GRAY)
                .append(Text.literal(profile.points + " pts").formatted(Formatting.GOLD, Formatting.BOLD));
        if (overallRank != null) {
            pointsText.append(Text.literal(" (#" + overallRank + ")").formatted(Formatting.YELLOW));
        }
        context.drawCenteredTextWithShadow(this.textRenderer, pointsText, leftCenterX, statsY + 12, 0xFFFFFFFF);

        String peakMode = profile.getDisplayMode();
        if (peakMode != null) {
            Text bestText = Text.literal("Peak: ").formatted(Formatting.DARK_GRAY)
                    .append(Text.literal(TierCommand.formatModeName(peakMode)).formatted(Formatting.YELLOW));
            context.drawCenteredTextWithShadow(this.textRenderer, bestText, leftCenterX, statsY + 24, 0xFFFFFFFF);
        }

        // --- RIGHT PANEL: GAMEMODES GRID ---
        int rightX = cardX + leftWidth + 12;
        int rightWidth = cardWidth - leftWidth - 24;

        // Header
        Text headerText = Text.literal("Gamemode Tiers").formatted(Formatting.YELLOW, Formatting.BOLD);
        context.drawTextWithShadow(this.textRenderer, headerText, rightX, cardY + 12, 0xFFFFAA00);

        int colWidth = (rightWidth - 10) / 2;
        int col1X = rightX;
        int col2X = rightX + colWidth + 10;
        int startY = cardY + 28;
        int lineHeight = 11;

        String hoveredModeKey = null;
        TierProfile.GameModeData hoveredData = null;

        if (profile.tiers != null && !profile.tiers.isEmpty()) {
            List<Map.Entry<String, TierProfile.GameModeData>> entries = new ArrayList<>(profile.tiers.entrySet());
            int total = entries.size();
            int half = (total + 1) / 2;

            for (int i = 0; i < total; i++) {
                Map.Entry<String, TierProfile.GameModeData> entry = entries.get(i);
                String modeKey = entry.getKey();
                TierProfile.GameModeData data = entry.getValue();
                if (data == null || data.tier == null) continue;

                boolean inCol1 = (i < half);
                int colX = inCol1 ? col1X : col2X;
                int rowY = startY + (inCol1 ? i : (i - half)) * lineHeight;

                if (rowY + lineHeight > cardY + cardHeight - 6) {
                    continue;
                }

                // Check mouse hover
                boolean isHovered = mouseX >= colX - 2 && mouseX <= colX + colWidth - 2 && mouseY >= rowY - 1 && mouseY <= rowY + lineHeight - 1;
                if (isHovered) {
                    hoveredModeKey = modeKey;
                    hoveredData = data;
                    context.fill(colX - 2, rowY - 1, colX + colWidth - 2, rowY + lineHeight - 1, 0x35FFFFFF);
                } else if ((inCol1 ? i : (i - half)) % 2 == 0) {
                    context.fill(colX - 2, rowY - 1, colX + colWidth - 2, rowY + lineHeight - 1, 0x12FFFFFF);
                }

                // Format: [Icon] Mode: Tier (Peak)
                MutableText icon = TierManager.getIcon(modeKey);
                String modeFormatted = TierCommand.formatModeName(modeKey);
                int tierColor = data.retired ? 0xFF880EFC : getTierColor(data.tier);
                String tierStr = (data.retired ? "R" : "") + data.tier;

                MutableText line = icon.append(Text.literal(" " + modeFormatted + ": ").formatted(Formatting.WHITE))
                        .append(Text.literal(tierStr).styled(s -> s.withColor(tierColor)));

                if (data.peakTier != null && !data.peakTier.equals(data.tier) && !data.peakTier.equals("LTnull")) {
                    line.append(Text.literal(" (" + data.peakTier + ")").formatted(Formatting.DARK_GRAY));
                }

                context.drawTextWithShadow(this.textRenderer, line, colX, rowY, 0xFFFFFFFF);
            }
        }

        // Draw Gamemode Tooltip on Hover
        if (hoveredModeKey != null && hoveredData != null) {
            List<Text> tooltip = new ArrayList<>();
            String modeFormatted = TierCommand.formatModeName(hoveredModeKey);
            int tierColor = hoveredData.retired ? 0xFF880EFC : getTierColor(hoveredData.tier);
            String tierStr = (hoveredData.retired ? "R" : "") + hoveredData.tier;

            // 1. Title Line: [Icon] Gamemode: Tier
            MutableText title = TierManager.getIcon(hoveredModeKey)
                    .append(Text.literal(" " + modeFormatted).formatted(Formatting.GOLD, Formatting.BOLD))
                    .append(Text.literal(" - " + tierStr).styled(s -> s.withColor(tierColor).withBold(true)));
            tooltip.add(title);

            // 2. Peak Tier
            if (hoveredData.peakTier != null && !hoveredData.peakTier.equals("LTnull")) {
                tooltip.add(Text.literal("Peak Tier: ").formatted(Formatting.GRAY)
                        .append(Text.literal(hoveredData.peakTier).formatted(Formatting.AQUA)));
            }

            // 3. Points Given
            tooltip.add(Text.literal("Points Awarded: ").formatted(Formatting.GRAY)
                    .append(Text.literal("+" + hoveredData.points + " pts").formatted(Formatting.YELLOW)));

            // 4. Attained Date
            Long attained = null;
            if (hoveredData.attained > 0) {
                attained = hoveredData.attained;
            } else if (profile.uuid != null) {
                attained = TierManager.getAttained(profile.uuid, hoveredModeKey);
            }

            if (attained != null && attained > 1000000) {
                try {
                    String dateStr = Instant.ofEpochSecond(attained).atZone(ZoneId.systemDefault()).format(DATE_FORMATTER);
                    tooltip.add(Text.literal("Attained: ").formatted(Formatting.GRAY)
                            .append(Text.literal(dateStr).formatted(Formatting.WHITE)));
                } catch (Exception ignored) {
                    tooltip.add(Text.literal("Attained: ").formatted(Formatting.GRAY)
                            .append(Text.literal("Legacy / Active").formatted(Formatting.DARK_GRAY)));
                }
            } else {
                tooltip.add(Text.literal("Attained: ").formatted(Formatting.GRAY)
                        .append(Text.literal("Legacy / Active").formatted(Formatting.DARK_GRAY)));
            }

            // 5. Retired status
            if (hoveredData.retired) {
                tooltip.add(Text.literal("Status: Retired").formatted(Formatting.LIGHT_PURPLE));
            }

            context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
        }
    }

    private static int getTierColor(String tier) {
        if (tier == null) return 0xFFFFFFFF;
        return switch (tier) {
            case "LT5" -> 0xFFD0D0D0;
            case "HT5" -> 0xFF7E7E7E;
            case "LT4" -> 0xFF8EEB8E;
            case "HT4" -> 0xFF00B000;
            case "LT3" -> 0xFFC67B42;
            case "HT3" -> 0xFFF89F5A;
            case "LT2" -> 0xFFA0A7B2;
            case "HT2" -> 0xFFC4D3E7;
            case "LT1" -> 0xFFD5B355;
            case "HT1" -> 0xFFFFAA00;
            default -> 0xFFFFFFFF;
        };
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
}
