package org.wavemelon.funnitiertagger.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.wavemelon.funnitiertagger.TierManager;
import org.wavemelon.funnitiertagger.TierProfile;
import org.wavemelon.funnitiertagger.client.TierCommand;

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
    private static final String INFO_SYMBOL = "\uD83D\uDEC8"; // 🛈

    private final Screen parent;
    private final TierProfile profile;
    private final List<String> previousUsernames;
    private Identifier bustTexture = null;
    private boolean imageLoading = true;
    private boolean imageFailed = false;

    public PlayerProfileScreen(Screen parent, TierProfile profile) {
        super(Component.literal(profile != null && profile.username != null ? profile.username : "Player Profile"));
        this.parent = parent;
        this.profile = profile;
        this.previousUsernames = profile != null ? profile.getPreviousUsernames() : List.of();
    }

    public PlayerProfileScreen(TierProfile profile) {
        this(null, profile);
    }

    @Override
    protected void init() {
        int cardHeight = Math.min(225, this.height - 48);
        int cardY = Math.max(8, (this.height - cardHeight) / 2 - 14);
        int buttonY = Math.min(this.height - 26, cardY + cardHeight + 8);

        // Centered Done button
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(this.width / 2 - 75, buttonY, 150, 20)
                .build());

        // Trigger overall rank fetch
        if (profile != null && !profile.restricted) {
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
        String visageUrl = "https://visage.surgeplay.com/bust/" + uuidWithHyphens;
        String mcHeadsUrl = "https://mc-heads.net/avatar/" + cleanUuid;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(visageUrl))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                    .whenComplete((res, err) -> {
                        if (err == null && res != null && res.statusCode() == 200) {
                            tryDecodeAndRegister(res.body(), cleanUuid, () -> fetchFallbackImage(mcHeadsUrl, cleanUuid));
                        } else {
                            fetchFallbackImage(mcHeadsUrl, cleanUuid);
                        }
                    });
        } catch (Exception e) {
            fetchFallbackImage(mcHeadsUrl, cleanUuid);
        }
    }

    private void fetchFallbackImage(String fallbackUrl, String cleanUuid) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fallbackUrl))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                    .whenComplete((res, err) -> {
                        if (err == null && res != null && res.statusCode() == 200) {
                            tryDecodeAndRegister(res.body(), cleanUuid, () -> {
                                Minecraft.getInstance().execute(() -> {
                                    this.imageLoading = false;
                                    this.imageFailed = true;
                                });
                            });
                        } else {
                            Minecraft.getInstance().execute(() -> {
                                this.imageLoading = false;
                                this.imageFailed = true;
                            });
                        }
                    });
        } catch (Exception e) {
            Minecraft.getInstance().execute(() -> {
                this.imageLoading = false;
                this.imageFailed = true;
            });
        }
    }

    private void tryDecodeAndRegister(byte[] bytes, String cleanUuid, Runnable onFailure) {
        try {
            NativeImage image = NativeImage.read(bytes);
            if (image == null) {
                onFailure.run();
                return;
            }
            Minecraft.getInstance().execute(() -> {
                try {
                    DynamicTexture dynamicTexture = new DynamicTexture(() -> "bust_" + cleanUuid, image);
                    dynamicTexture.upload();
                    Identifier textureId = Identifier.fromNamespaceAndPath("funnitiers", "bust_" + cleanUuid);
                    Minecraft.getInstance().getTextureManager().register(textureId, dynamicTexture);
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
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        if (profile == null) {
            context.centeredText(this.font, Component.literal("No profile data").withStyle(ChatFormatting.RED), this.width / 2, this.height / 2, 0xFFFF5555);
            return;
        }

        boolean restricted = profile.restricted;

        // Dynamically compute card bounds based on screen size
        int cardWidth = Math.min(480, this.width - 20);
        int cardHeight = Math.min(225, this.height - 48);
        int cardX = (this.width - cardWidth) / 2;
        int cardY = Math.max(8, (this.height - cardHeight) / 2 - 14);

        // Card palette: red theme for restricted players, dark gray for normal players
        int cardBgColor = restricted ? 0xD82B0B0B : 0xD0101015;
        int borderColor = restricted ? 0x90FF4444 : 0x40FFFFFF;
        int dividerColor = restricted ? 0x45FF5555 : 0x25FFFFFF;
        int bustFrameBg = restricted ? 0x80200000 : 0x80000000;
        int bustFrameTop = restricted ? 0x55FF4444 : 0x30FFFFFF;
        int rowHoverColor = restricted ? 0x38FF5555 : 0x35FFFFFF;
        int rowAltColor = restricted ? 0x18FF4444 : 0x12FFFFFF;

        // Draw Main Background Card
        context.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, cardBgColor);
        context.fill(cardX - 1, cardY - 1, cardX + cardWidth + 1, cardY, borderColor); // Top border
        context.fill(cardX - 1, cardY + cardHeight, cardX + cardWidth + 1, cardY + cardHeight + 1, borderColor); // Bottom border
        context.fill(cardX - 1, cardY, cardX, cardY + cardHeight, borderColor); // Left border
        context.fill(cardX + cardWidth, cardY, cardX + cardWidth + 1, cardY + cardHeight, borderColor); // Right border

        // Layout: Left Panel = Player Hero Card, Right Panel = Gamemodes Grid
        int leftWidth = 165;
        int leftCenterX = cardX + (leftWidth / 2);

        // Divider between left and right panel
        context.fill(cardX + leftWidth, cardY + 10, cardX + leftWidth + 1, cardY + cardHeight - 10, dividerColor);

        // --- LEFT PANEL: PLAYER PROFILE ---
        // 1. Player Username (&7&m if restricted, &b&l otherwise) + optional 🛈 history badge
        String name = profile.username != null ? profile.username : "Unknown";
        MutableComponent namePart = restricted
                ? Component.literal(name).withStyle(ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH)
                : Component.literal(name).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);

        boolean hasHistory = !this.previousUsernames.isEmpty();
        boolean hoveringHistoryIcon = false;
        int nameY = cardY + 12;

        if (hasHistory) {
            Component infoSpacing = Component.literal(" ");
            Component infoIcon = Component.literal(INFO_SYMBOL).withStyle(s -> s
                    .withColor(0xAAAAAA)
                    .withBold(false)
                    .withStrikethrough(false));

            int nameWidth = this.font.width(namePart);
            int spaceWidth = this.font.width(infoSpacing);
            int iconWidth = this.font.width(infoIcon);
            int totalWidth = nameWidth + spaceWidth + iconWidth;
            int startX = leftCenterX - (totalWidth / 2);
            int iconX = startX + nameWidth + spaceWidth;

            hoveringHistoryIcon = mouseX >= iconX - 2 && mouseX <= iconX + iconWidth + 2
                    && mouseY >= nameY - 2 && mouseY <= nameY + this.font.lineHeight + 1;

            int iconColor = hoveringHistoryIcon ? 0xFFFFFF : 0xAAAAAA;
            MutableComponent fullName = Component.empty()
                    .append(namePart)
                    .append(infoSpacing)
                    .append(Component.literal(INFO_SYMBOL).withStyle(s -> s
                            .withColor(iconColor)
                            .withBold(false)
                            .withStrikethrough(false)));

            context.text(this.font, fullName, startX, nameY, 0xFFFFFFFF);
        } else {
            context.centeredText(this.font, namePart, leftCenterX, nameY, restricted ? 0xFFAAAAAA : 0xFF55FFFF);
        }

        // 2. 3D Bust (Size 76x76)
        int bustSize = 76;
        int bustX = leftCenterX - (bustSize / 2);
        int bustY = cardY + 28;

        // Bust card frame
        context.fill(bustX - 3, bustY - 3, bustX + bustSize + 3, bustY + bustSize + 3, bustFrameBg);
        context.fill(bustX - 3, bustY - 3, bustX + bustSize + 3, bustY - 2, bustFrameTop);

        if (this.bustTexture != null) {
            context.blit(RenderPipelines.GUI_TEXTURED, this.bustTexture, bustX, bustY, 0.0f, 0.0f, bustSize, bustSize, bustSize, bustSize);
        } else if (this.imageLoading) {
            context.centeredText(this.font, Component.literal("Loading...").withStyle(ChatFormatting.GRAY), leftCenterX, bustY + 34, 0xFFAAAAAA);
        } else {
            context.centeredText(this.font, Component.literal("No Bust").withStyle(ChatFormatting.DARK_GRAY), leftCenterX, bustY + 34, 0xFF888888);
        }

        // 3. Stats below bust
        int statsY = bustY + bustSize + 8;
        String region = profile.region != null ? profile.region : "Unknown";
        Component regionText = Component.literal("Region: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(region).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        context.centeredText(this.font, regionText, leftCenterX, statsY, 0xFFFFFFFF);

        // Overall placement rank (if available and not restricted) + Points (0 if restricted)
        Integer overallRank = null;
        if (!restricted) {
            if (profile.uuid != null) {
                overallRank = TierManager.getOverallRank(profile.uuid);
            }
            if (overallRank == null && profile.username != null) {
                overallRank = TierManager.getOverallRank(profile.username);
            }
        }

        int displayPoints = restricted ? 0 : profile.points;
        MutableComponent pointsText = Component.literal("Points: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(displayPoints + " pts").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (overallRank != null) {
            pointsText.append(Component.literal(" (#" + overallRank + ")").withStyle(ChatFormatting.YELLOW));
        }
        context.centeredText(this.font, pointsText, leftCenterX, statsY + 12, 0xFFFFFFFF);

        int nextY = statsY + 24;
        String peakMode = profile.getDisplayMode();
        if (peakMode != null) {
            Component bestText = Component.literal("Peak: ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(TierCommand.formatModeName(peakMode)).withStyle(ChatFormatting.YELLOW));
            context.centeredText(this.font, bestText, leftCenterX, nextY, 0xFFFFFFFF);
            nextY += 14;
        }

        if (restricted) {
            Component restrictedNotice = Component.literal("This player is restricted.")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD, ChatFormatting.UNDERLINE);
            context.centeredText(this.font, restrictedNotice, leftCenterX, nextY, 0xFFFF5555);
        }

        // --- RIGHT PANEL: GAMEMODES GRID ---
        int rightX = cardX + leftWidth + 12;
        int rightWidth = cardWidth - leftWidth - 24;

        // Header
        Component headerText = Component.literal("Gamemode Tiers").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
        context.text(this.font, headerText, rightX, cardY + 12, 0xFFFFAA00);

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
                    context.fill(colX - 2, rowY - 1, colX + colWidth - 2, rowY + lineHeight - 1, rowHoverColor);
                } else if ((inCol1 ? i : (i - half)) % 2 == 0) {
                    context.fill(colX - 2, rowY - 1, colX + colWidth - 2, rowY + lineHeight - 1, rowAltColor);
                }

                // Format: [Icon] Mode: Tier (Peak)
                MutableComponent icon = TierManager.getIcon(modeKey);
                String modeFormatted = TierCommand.formatModeName(modeKey);
                int tierColor = TierManager.getTierColor(data.tier, data.retired);
                String tierStr = (data.retired ? "R" : "") + data.tier;

                MutableComponent line = icon.append(Component.literal(" " + modeFormatted + ": ").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(tierStr).withStyle(s -> s.withColor(tierColor)));

                if (data.peakTier != null && !data.peakTier.equals(data.tier) && !data.peakTier.equals("LTnull")) {
                    line.append(Component.literal(" (" + data.peakTier + ")").withStyle(ChatFormatting.DARK_GRAY));
                }

                context.text(this.font, line, colX, rowY, 0xFFFFFFFF);
            }
        }

        // Draw Tooltip on Hover (Username History takes priority if hovered)
        if (hoveringHistoryIcon) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("Previous Usernames").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            for (String prev : this.previousUsernames) {
                tooltip.add(Component.literal("• ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.literal(prev).withStyle(ChatFormatting.GRAY)));
            }
            context.setComponentTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
        } else if (hoveredModeKey != null && hoveredData != null) {
            List<Component> tooltip = new ArrayList<>();
            String modeFormatted = TierCommand.formatModeName(hoveredModeKey);
            int tierColor = TierManager.getTierColor(hoveredData.tier, hoveredData.retired);
            String tierStr = (hoveredData.retired ? "R" : "") + hoveredData.tier;

            // 1. Title Line: [Icon] Gamemode - Tier (hyphen formatted as &7-)
            MutableComponent title = TierManager.getIcon(hoveredModeKey)
                    .append(Component.literal(" " + modeFormatted).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(Component.literal(" - ").withStyle(s -> s.withColor(ChatFormatting.GRAY).withBold(false)))
                    .append(Component.literal(tierStr).withStyle(s -> s.withColor(tierColor).withBold(true)));
            tooltip.add(title);

            // 2. Peak Tier
            if (hoveredData.peakTier != null && !hoveredData.peakTier.equals("LTnull")) {
                tooltip.add(Component.literal("Peak Tier: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(hoveredData.peakTier).withStyle(ChatFormatting.AQUA)));
            }

            // 3. Points Given
            tooltip.add(Component.literal("Points Awarded: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("+" + hoveredData.points + " pts").withStyle(ChatFormatting.YELLOW)));

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
                    tooltip.add(Component.literal("Attained: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(dateStr).withStyle(ChatFormatting.WHITE)));
                } catch (Exception ignored) {
                    tooltip.add(Component.literal("Attained: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal("Legacy / Active").withStyle(ChatFormatting.DARK_GRAY)));
                }
            } else {
                tooltip.add(Component.literal("Attained: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Legacy / Active").withStyle(ChatFormatting.DARK_GRAY)));
            }

            // 5. Retired status
            if (hoveredData.retired) {
                tooltip.add(Component.literal("Status: Retired").withStyle(ChatFormatting.LIGHT_PURPLE));
            }

            context.setComponentTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }
}
