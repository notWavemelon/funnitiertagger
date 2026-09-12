package org.wavemelon.funnitiertagger.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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
        super(Text.literal("funniTiers Configuration"));
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
        addDrawableChild(ButtonWidget.builder(getEnabledText(), button -> {
            ModConfig.getInstance().enabled = !ModConfig.getInstance().enabled;
            ModConfig.save();
            TierManager.clearCache();
            button.setMessage(getEnabledText());
        }).dimensions(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight).build());

        // 2. Mode Cycle
        addDrawableChild(ButtonWidget.builder(getModeText(), button -> {
            currentModeIndex = (currentModeIndex + 1) % availableModes.size();
            String newMode = availableModes.get(currentModeIndex);
            ModConfig.getInstance().selectedMode = newMode;
            TierManager.setOverrideMode(newMode);
            ModConfig.save();
            TierManager.clearCache();
            button.setMessage(getModeText());
        }).dimensions(centerX - buttonWidth / 2, startY + gap, buttonWidth, buttonHeight).build());

        // 3. Display Format Cycle
        addDrawableChild(ButtonWidget.builder(getDisplayTypeText(), button -> {
            ModConfig.getInstance().displayType = ModConfig.getInstance().displayType.next();
            ModConfig.save();
            TierManager.clearCache();
            button.setMessage(getDisplayTypeText());
        }).dimensions(centerX - buttonWidth / 2, startY + gap * 2, buttonWidth, buttonHeight).build());

        // 4. Clear Cache Button
        addDrawableChild(ButtonWidget.builder(Text.literal("Clear Tier Cache").formatted(Formatting.YELLOW), button -> {
            TierManager.clearCache();
            button.setMessage(Text.literal("Tier Cache Cleared!").formatted(Formatting.GREEN));
        }).dimensions(centerX - buttonWidth / 2, startY + gap * 3, buttonWidth, buttonHeight).build());

        // 5. Done Button
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> {
            close();
        }).dimensions(centerX - buttonWidth / 2, startY + gap * 4 + 10, buttonWidth, buttonHeight).build());
    }

    private Text getEnabledText() {
        boolean enabled = ModConfig.getInstance().enabled;
        return Text.literal("Nametags & Displays: ")
                .append(enabled ? Text.literal("ON").formatted(Formatting.GREEN, Formatting.BOLD) : Text.literal("OFF").formatted(Formatting.RED, Formatting.BOLD));
    }

    private Text getModeText() {
        String mode = ModConfig.getInstance().selectedMode;
        if (mode == null) {
            return Text.literal("Gamemode: ").append(Text.literal("Automatic (Peak)").formatted(Formatting.AQUA));
        }
        return Text.literal("Gamemode: ").append(Text.literal(TierCommand.formatModeName(mode)).formatted(Formatting.GOLD));
    }

    private Text getDisplayTypeText() {
        ModConfig.DisplayType type = ModConfig.getInstance().displayType;
        return Text.literal("Display: ").append(Text.literal(type.displayName).formatted(Formatting.LIGHT_PURPLE));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 4 - 28, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
}
