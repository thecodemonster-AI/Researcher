package com.ender.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class CategoryButton extends AbstractWidget {

    private final Runnable onClick;

    public CategoryButton(int x, int y, int width, int height,
                          String text, Runnable onClick) {

        super(x, y, width, height, Component.literal(text));
        this.onClick = onClick;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {

        boolean hovered = this.isHoveredOrFocused();

        // Panel background (slightly lighter than surrounding UI)
        int bgColor = hovered ? 0xFF333333 : 0xFF2B2B2B;
        int inner = 0xFF1E1E1E;

        // Draw rounded-like background by drawing an outer and inner rect with padding
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);
        g.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, inner);

        // Accent bar on the left when hovered / focused (or you could show when selected)
        if (hovered) {
            g.fill(getX(), getY(), getX() + 4, getY() + getHeight(), 0xFF6AA84F); // soft green accent
        }

        // Text: scale according to UIConfig
        float s = UIConfig.getScale();
        g.pose().pushPose();
        // Translate to text origin, scale, then draw at 0,0 offset
        int textX = getX() + 10;
        int textY = getY() + (getHeight() / 2) - 5;
        g.pose().translate(textX, textY, 0);
        g.pose().scale(s, s, 1f);
        g.drawString(
                Minecraft.getInstance().font,
                getMessage().getString(),
                0,
                0,
                0xFFEFEFEF
        );
        g.pose().popPose();
    }

    @Override
    public void onClick(double pMouseX, double pMouseY) {
        onClick.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput p_259858_) {
        // no-op for now
    }
}