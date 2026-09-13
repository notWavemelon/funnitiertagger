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
    private final Screen parent;
    private final List<String> availableModes = new ArrayList<>();
    private int currentModeIndex = 0;

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
        int centerX = this.width / 2;
        int startY = this.height / 4;
        int buttonWidth = 240;
        int buttonHeight = 20;
        int gap = 24;

        // 1. Enabled Toggle
        addRenderableWidget(Button.builder(getEnabledText(), button -> {
            ModConfig.getInstance().enabled = !ModConfig.getInstance().enabled;
            ModConfig.save();
            TierManager.clearCache();
            button.setMessage(getEnabledText());
        }).bounds(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight).build());

        // 2. Mode Cycle
        addRenderableWidget(Button.builder(getModeText(), button -> {
            currentModeIndex = (currentModeIndex + 1) % availableModes.size();
            String newMode = availableModes.get(currentModeIndex);
            ModConfig.getInstance().selectedMode = newMode;
            TierManager.setOverrideMode(newMode);
            ModConfig.save();
            TierManager.clearCache();
            button.setMessage(getModeText());
        }).bounds(centerX - buttonWidth / 2, startY + gap, buttonWidth, buttonHeight).build());

        // 3. Display Format Cycle
        addRenderableWidget(Button.builder(getDisplayTypeText(), button -> {
            ModConfig.getInstance().displayType = ModConfig.getInstance().displayType.next();
            ModConfig.save();
            TierManager.clearCache();
            button.setMessage(getDisplayTypeText());
        }).bounds(centerX - buttonWidth / 2, startY + gap * 2, buttonWidth, buttonHeight).build());

        // 4. Clear Cache Button
        addRenderableWidget(Button.builder(Component.literal("Clear Tier Cache").withStyle(ChatFormatting.YELLOW), button -> {
            TierManager.clearCache();
            button.setMessage(Component.literal("Tier Cache Cleared!").withStyle(ChatFormatting.GREEN));
        }).bounds(centerX - buttonWidth / 2, startY + gap * 3, buttonWidth, buttonHeight).build());

        // 5. Done Button
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> {
            onClose();
        }).bounds(centerX - buttonWidth / 2, startY + gap * 4 + 10, buttonWidth, buttonHeight).build());
    }

    private Component getEnabledText() {
        boolean enabled = ModConfig.getInstance().enabled;
        return Component.literal("Nametags & Displays: ")
                .append(enabled ? Component.literal("ON").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD) : Component.literal("OFF").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    }

    private Component getModeText() {
        String mode = ModConfig.getInstance().selectedMode;
        if (mode == null) {
            return Component.literal("Gamemode: ").append(Component.literal("Automatic (Peak)").withStyle(ChatFormatting.AQUA));
        }
        return Component.literal("Gamemode: ").append(Component.literal(TierCommand.formatModeName(mode)).withStyle(ChatFormatting.GOLD));
    }

    private Component getDisplayTypeText() {
        ModConfig.DisplayType type = ModConfig.getInstance().displayType;
        return Component.literal("Display: ").append(Component.literal(type.displayName).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, this.title, this.width / 2, this.height / 4 - 28, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }
}
