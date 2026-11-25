package org.wavemelon.funnitiertagger.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.InputStream;

public class DwayneScreen extends Screen {
    private static Identifier cachedTexture = null;
    private static int cachedWidth = 0;
    private static int cachedHeight = 0;
    private static boolean loadAttempted = false;

    private final Screen parent;

    public DwayneScreen(Screen parent) {
        super(Component.literal("🤨"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ensureTextureLoaded();

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(this.width / 2 - 60, this.height - 28, 120, 20)
                .build());
    }

    private static void ensureTextureLoaded() {
        if (loadAttempted) return;
        loadAttempted = true;

        try (InputStream stream = DwayneScreen.class.getResourceAsStream("/dwayne.png")) {
            if (stream == null) return;
            NativeImage image = NativeImage.read(stream);
            if (image == null) return;

            cachedWidth = image.getWidth();
            cachedHeight = image.getHeight();
            DynamicTexture dynamicTexture = new DynamicTexture(() -> "secret_dwayne", image);
            dynamicTexture.upload();
            Identifier id = Identifier.fromNamespaceAndPath("funnitiers", "secret_dwayne");
            Minecraft.getInstance().getTextureManager().register(id, dynamicTexture);
            cachedTexture = id;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        if (cachedTexture != null && cachedWidth > 0 && cachedHeight > 0) {
            int maxW = Math.max(64, this.width - 40);
            int maxH = Math.max(64, this.height - 56);
            double scale = Math.min((double) maxW / cachedWidth, (double) maxH / cachedHeight);
            int drawW = Math.max(1, (int) Math.round(cachedWidth * scale));
            int drawH = Math.max(1, (int) Math.round(cachedHeight * scale));
            int drawX = (this.width - drawW) / 2;
            int drawY = (this.height - 34 - drawH) / 2 + 4;

            // Subtle frame around image
            context.fill(drawX - 2, drawY - 2, drawX + drawW + 2, drawY + drawH + 2, 0xA0000000);
            context.blit(RenderPipelines.GUI_TEXTURED, cachedTexture, drawX, drawY, 0.0f, 0.0f, drawW, drawH, drawW, drawH);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }
}
