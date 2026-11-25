package org.wavemelon.funnitiertagger.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.wavemelon.funnitiertagger.ModConfig;
import org.wavemelon.funnitiertagger.TierManager;

import java.util.ArrayList;
import java.util.List;

public class ColorConfigScreen extends Screen {
    private static final int VALID_TEXT_COLOR = EditBox.DEFAULT_TEXT_COLOR; // 0xFFE0E0E0 (ARGB)
    private static final int INVALID_TEXT_COLOR = 0xFFFF5555; // ARGB red

    private final Screen parent;
    private final List<ColorEntry> entries = new ArrayList<>();
    private Button templateButton;
    private String previewRankKey = "HT1";
    private boolean updatingBoxes = false;

    private record ColorEntry(String key, String label, int x, int y, EditBox box) {}

    public ColorConfigScreen(Screen parent) {
        super(Component.literal("Rank Colors"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.entries.clear();

        ModConfig config = ModConfig.getInstance();
        int centerX = this.width / 2;
        int colGap = 16;
        int colWidth = 156;
        int leftColX = centerX - colWidth - (colGap / 2);
        int rightColX = centerX + (colGap / 2);

        int rowHeight = 22;
        int startY = Math.max(46, this.height / 2 - 74);

        // Left column: HT1..HT5 + RETIRED (6 rows)
        // Right column: LT1..LT5 (5 rows) + Template selector (row 5)
        String[] leftKeys = {"HT1", "HT2", "HT3", "HT4", "HT5", "RETIRED"};
        String[] rightKeys = {"LT1", "LT2", "LT3", "LT4", "LT5"};

        for (int i = 0; i < leftKeys.length; i++) {
            addColorRow(config, leftKeys[i], leftColX, startY + i * rowHeight);
        }
        for (int i = 0; i < rightKeys.length; i++) {
            addColorRow(config, rightKeys[i], rightColX, startY + i * rowHeight);
        }

        // Row 5 Right: Template Preset Cycle Button
        this.templateButton = addRenderableWidget(Button.builder(getTemplateText(config), button -> {
            ModConfig.ColorTemplate current = config.getMatchingTemplate();
            ModConfig.ColorTemplate next = (current != null) ? current.next() : ModConfig.ColorTemplate.FUNNITIERS;
            config.applyColorTemplate(next);
            ModConfig.save();
            refreshBoxesFromConfig(config);
            button.setMessage(getTemplateText(config));
        }).bounds(rightColX, startY + 5 * rowHeight, 150, 18).build());

        int bottomY = Math.min(this.height - 28, startY + leftKeys.length * rowHeight + 10);

        addRenderableWidget(Button.builder(Component.literal("Reset All to Default").withStyle(ChatFormatting.RED), button -> {
            config.resetTierColors();
            ModConfig.save();
            refreshBoxesFromConfig(config);
            updateTemplateButtonLabel(config);
        }).bounds(centerX - 154, bottomY, 150, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(centerX + 4, bottomY, 150, 20)
                .build());
    }

    private void refreshBoxesFromConfig(ModConfig config) {
        this.updatingBoxes = true;
        try {
            for (ColorEntry entry : this.entries) {
                entry.box().setValue(config.getTierColorHex(entry.key()));
                entry.box().setTextColor(VALID_TEXT_COLOR);
            }
        } finally {
            this.updatingBoxes = false;
        }
    }

    private void updateTemplateButtonLabel(ModConfig config) {
        if (this.templateButton != null) {
            this.templateButton.setMessage(getTemplateText(config));
        }
    }

    private static Component getTemplateText(ModConfig config) {
        ModConfig.ColorTemplate matched = config.getMatchingTemplate();
        if (matched == null) {
            return Component.literal("Template: ").append(Component.literal("Custom").withStyle(ChatFormatting.GRAY));
        }
        ChatFormatting color = switch (matched) {
            case FUNNITIERS -> ChatFormatting.GOLD;
            case MCTIERS -> ChatFormatting.AQUA;
            case PVPTIERS -> ChatFormatting.RED;
        };
        return Component.literal("Template: ").append(Component.literal(matched.displayName).withStyle(color));
    }

    private void addColorRow(ModConfig config, String key, int x, int y) {
        String label = "RETIRED".equals(key) ? "Ret." : key;
        int boxX = x + 52;
        int boxWidth = 74;
        int resetX = boxX + boxWidth + 4;

        EditBox box = new EditBox(this.font, boxX, y, boxWidth, 18, Component.literal(key + " Hex Color"));
        box.setMaxLength(7);
        box.setTextColor(VALID_TEXT_COLOR);
        box.setValue(config.getTierColorHex(key));
        box.setResponder(value -> {
            if (this.updatingBoxes) return;
            this.previewRankKey = key;
            Integer parsed = ModConfig.parseHexColor(value);
            if (parsed != null) {
                config.setTierColorHex(key, value);
                ModConfig.save();
                box.setTextColor(VALID_TEXT_COLOR);
                updateTemplateButtonLabel(config);
            } else {
                box.setTextColor(INVALID_TEXT_COLOR);
            }
        });

        addRenderableWidget(box);

        addRenderableWidget(Button.builder(Component.literal("↺"), button -> {
            this.previewRankKey = key;
            config.resetTierColor(key);
            ModConfig.save();
            this.updatingBoxes = true;
            try {
                box.setValue(config.getTierColorHex(key));
                box.setTextColor(VALID_TEXT_COLOR);
            } finally {
                this.updatingBoxes = false;
            }
            updateTemplateButtonLabel(config);
        }).bounds(resetX, y, 20, 18).build());

        this.entries.add(new ColorEntry(key, label, x, y, box));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        int startY = Math.max(46, this.height / 2 - 74);
        context.centeredText(this.font, this.title, this.width / 2, startY - 34, 0xFFFFFFFF);

        // Live nametag preview using the currently edited rank color
        String previewMode = ModConfig.getInstance().selectedMode != null ? ModConfig.getInstance().selectedMode : "wind_charge";
        Component preview = Component.literal("Preview: ").withStyle(ChatFormatting.GRAY)
                .append(TierManager.buildPreviewNametag(previewMode, this.previewRankKey, "Player"));
        context.centeredText(this.font, preview, this.width / 2, startY - 18, 0xFFFFFFFF);

        ModConfig config = ModConfig.getInstance();
        for (ColorEntry entry : this.entries) {
            int rgb = config.getColorByKey(entry.key());
            int argb = 0xFF000000 | rgb;

            // Color swatch square
            int swatchX = entry.x();
            int swatchY = entry.y() + 2;
            int swatchSize = 14;
            context.fill(swatchX - 1, swatchY - 1, swatchX + swatchSize + 1, swatchY + swatchSize + 1, 0xFFAAAAAA);
            context.fill(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize, argb);

            // Rank label rendered in its configured color
            context.text(this.font, Component.literal(entry.label()).withStyle(s -> s.withColor(rgb).withBold(true)),
                    swatchX + swatchSize + 5, entry.y() + 5, argb);
        }
    }

    @Override
    public void onClose() {
        ModConfig.save();
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }
}
