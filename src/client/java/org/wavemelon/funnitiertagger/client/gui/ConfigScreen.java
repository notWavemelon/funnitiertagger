package org.wavemelon.funnitiertagger.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.wavemelon.funnitiertagger.ModConfig;
import org.wavemelon.funnitiertagger.TierManager;
import org.wavemelon.funnitiertagger.client.TierCommand;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConfigScreen extends Screen {
    private static final int DWAYNE_CLICK_THRESHOLD = 10;

    private final Screen parent;
    private final List<String> availableModes = new ArrayList<>();
    private int currentModeIndex = 0;
    private int consecutiveClearClicks = 0;

    public ConfigScreen(Screen parent) {
        super(Component.literal("funniTiers Configuration"));
        this.parent = parent;

        availableModes.add(null); // Automatic
        availableModes.addAll(Arrays.asList(TierCommand.MODES));

        String saved = ModConfig.getInstance().selectedMode;
        if (saved != null) {
            int idx = availableModes.indexOf(saved);
            if (idx >= 0) {
                currentModeIndex = idx;
            }
        }
    }

    @Override
    protected void init() {
        this.consecutiveClearClicks = 0;

        int centerX = this.width / 2;
        int colGap = 8;
        int buttonWidth = Math.min(165, (this.width - 24 - colGap) / 2);
        int leftX = centerX - buttonWidth - (colGap / 2);
        int rightX = centerX + (colGap / 2);
        int buttonHeight = 20;
        int rowGap = 24;
        int startY = Math.max(46, this.height / 2 - 64);

        // Row 0 Left: Enabled Toggle
        addRenderableWidget(Button.builder(getEnabledText(), button -> {
            this.consecutiveClearClicks = 0;
            ModConfig.getInstance().enabled = !ModConfig.getInstance().enabled;
            ModConfig.save();
            TierManager.clearCache();
            button.setMessage(getEnabledText());
        }).bounds(leftX, startY, buttonWidth, buttonHeight).build());

        // Row 0 Right: Mode Cycle
        addRenderableWidget(Button.builder(getModeText(), button -> {
            this.consecutiveClearClicks = 0;
            currentModeIndex = (currentModeIndex + 1) % availableModes.size();
            String newMode = availableModes.get(currentModeIndex);
            ModConfig.getInstance().selectedMode = newMode;
            TierManager.setOverrideMode(newMode);
            ModConfig.save();
            TierManager.clearCache();
            button.setMessage(getModeText());
        }).bounds(rightX, startY, buttonWidth, buttonHeight).build());

        // Row 1 Left: Display Format Cycle
        addRenderableWidget(Button.builder(getDisplayTypeText(), button -> {
            this.consecutiveClearClicks = 0;
            ModConfig.getInstance().displayType = ModConfig.getInstance().displayType.next();
            ModConfig.save();
            button.setMessage(getDisplayTypeText());
        }).bounds(leftX, startY + rowGap, buttonWidth, buttonHeight).build());

        // Row 1 Right: Tag Position (Left / Right)
        addRenderableWidget(Button.builder(getPositionText(), button -> {
            this.consecutiveClearClicks = 0;
            ModConfig.getInstance().tagPosition = ModConfig.getInstance().tagPosition.next();
            ModConfig.save();
            button.setMessage(getPositionText());
        }).bounds(rightX, startY + rowGap, buttonWidth, buttonHeight).build());

        // Row 2 Left: Icon Style (Custom Icons / Legacy Emojis)
        addRenderableWidget(Button.builder(getIconStyleText(), button -> {
            this.consecutiveClearClicks = 0;
            ModConfig.getInstance().iconStyle = ModConfig.getInstance().iconStyle.next();
            ModConfig.save();
            button.setMessage(getIconStyleText());
        }).bounds(leftX, startY + rowGap * 2, buttonWidth, buttonHeight).build());

        // Row 2 Right: Divider Style (Default / Dynamic / None)
        addRenderableWidget(Button.builder(getDividerStyleText(), button -> {
            this.consecutiveClearClicks = 0;
            ModConfig.getInstance().dividerStyle = ModConfig.getInstance().dividerStyle.next();
            ModConfig.save();
            button.setMessage(getDividerStyleText());
        }).bounds(rightX, startY + rowGap * 2, buttonWidth, buttonHeight).build());

        // Row 3 Left: Customize Rank Colors
        addRenderableWidget(Button.builder(Component.literal("Rank Colors...").withStyle(ChatFormatting.GOLD), button -> {
            this.consecutiveClearClicks = 0;
            if (this.minecraft != null) {
                this.minecraft.gui.setScreen(new ColorConfigScreen(this));
            }
        }).bounds(leftX, startY + rowGap * 3, buttonWidth, buttonHeight).build());

        // Row 3 Right: Clear Cache Button (+ secret easter egg after 10 consecutive clicks)
        addRenderableWidget(Button.builder(Component.literal("Clear Tier Cache").withStyle(ChatFormatting.YELLOW), button -> {
            TierManager.clearCache();
            this.consecutiveClearClicks++;
            if (this.consecutiveClearClicks >= DWAYNE_CLICK_THRESHOLD) {
                this.consecutiveClearClicks = 0;
                if (this.minecraft != null) {
                    this.minecraft.gui.setScreen(new DwayneScreen(this));
                    return;
                }
            }
            button.setMessage(Component.literal("Tier Cache Cleared!").withStyle(ChatFormatting.GREEN));
        }).bounds(rightX, startY + rowGap * 3, buttonWidth, buttonHeight).build());

        // Row 4 Centered: Done Button
        int doneWidth = Math.min(200, buttonWidth * 2 + colGap);
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(centerX - doneWidth / 2, startY + rowGap * 4 + 8, doneWidth, buttonHeight)
                .build());
    }

    private Component getEnabledText() {
        boolean enabled = ModConfig.getInstance().enabled;
        return Component.literal("Nametags: ")
                .append(enabled
                        ? Component.literal("ON").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                        : Component.literal("OFF").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    }

    private Component getModeText() {
        String mode = ModConfig.getInstance().selectedMode;
        if (mode == null) {
            return Component.literal("Mode: ").append(Component.literal("Auto (Peak)").withStyle(ChatFormatting.AQUA));
        }
        return Component.literal("Mode: ").append(Component.literal(TierCommand.formatModeName(mode)).withStyle(ChatFormatting.GOLD));
    }

    private Component getDisplayTypeText() {
        ModConfig.DisplayType type = ModConfig.getInstance().displayType;
        return Component.literal("Display: ").append(Component.literal(type.displayName).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private Component getPositionText() {
        ModConfig.TagPosition pos = ModConfig.getInstance().tagPosition;
        return Component.literal("Position: ").append(Component.literal(pos.displayName).withStyle(ChatFormatting.AQUA));
    }

    private Component getIconStyleText() {
        ModConfig.IconStyle style = ModConfig.getInstance().iconStyle;
        return Component.literal("Icons: ").append(Component.literal(style.displayName).withStyle(ChatFormatting.GREEN));
    }

    private Component getDividerStyleText() {
        ModConfig.DividerStyle style = ModConfig.getInstance().dividerStyle;
        return Component.literal("Divider: ").append(Component.literal(style.displayName).withStyle(ChatFormatting.YELLOW));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        int startY = Math.max(46, this.height / 2 - 64);
        context.centeredText(this.font, this.title, this.width / 2, startY - 32, 0xFFFFFFFF);

        // Live nametag preview reflecting current config
        String previewMode = ModConfig.getInstance().selectedMode != null ? ModConfig.getInstance().selectedMode : "wind_charge";
        Component preview = Component.literal("Preview: ").withStyle(ChatFormatting.GRAY)
                .append(TierManager.buildPreviewNametag(previewMode, "HT1", "Player"));
        context.centeredText(this.font, preview, this.width / 2, startY - 16, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }
}
