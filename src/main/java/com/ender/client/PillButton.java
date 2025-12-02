package com.ender.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class PillButton extends AbstractWidget {
    private final Runnable onClick;
    private int bgColor = 0xFF6AA84F; // default green
    private int textColor = 0xFFFFFFFF;

    public PillButton(int x, int y, int width, int height, Component message, Runnable onClick) {
        super(x, y, width, height, message);
        this.onClick = onClick;
    }

    public void setBgColor(int color) { this.bgColor = color; }
    public void setTextColor(int color) { this.textColor = color; }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput p_259858_) {
        // no-op
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        boolean hovered = this.isHoveredOrFocused();
        int color = hovered ? darken(bgColor, 0.9f) : bgColor;

        // draw pill background (simulate rounded by drawing outer and inner rects)
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), color);
        g.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, darken(color, 0.95f));

        // draw centered text scaled by UIConfig
        // Get base UI scale
        float uiScale = UIConfig.getScale();
        String text = getMessage().getString();
        int textWidth = Minecraft.getInstance().font.width(text);

        // Determine maximum allowed pixel width for text (leave small padding)
        int padding = 8; // pixels of horizontal padding inside the button
        float maxTextWidthPx = Math.max(1, getWidth() - padding);

        // Compute fit scale so textWidth * (uiScale * fitScale) <= maxTextWidthPx
        float fitScale;
        if (textWidth <= 0) {
            fitScale = 1.0f;
        } else {
            fitScale = maxTextWidthPx / (float)textWidth;
            // clamp so we don't upscale beyond 1 and don't shrink too small
            fitScale = Math.min(1.0f, fitScale);
            fitScale = Math.max(0.5f, fitScale); // don't go below 50% of UI scale
        }

        float s = uiScale * fitScale;

        g.pose().pushPose();
        // compute scaled center position
        float scaledTextWidth = textWidth * s;
        float cx = getX() + (getWidth() / 2f) - (scaledTextWidth / 2f);
        float cy = getY() + (getHeight() / 2f) - (4f * s);

        g.pose().translate(cx, cy, 0);
        g.pose().scale(s, s, 1f);
        g.drawString(Minecraft.getInstance().font, text, 0, 0, textColor);
        g.pose().popPose();
    }

    private int darken(int col, float factor) {
        int a = (col >> 24) & 0xFF;
        int r = (col >> 16) & 0xFF;
        int g = (col >> 8) & 0xFF;
        int b = col & 0xFF;
        r = Math.max(0, (int)(r * factor));
        g = Math.max(0, (int)(g * factor));
        b = Math.max(0, (int)(b * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public void onClick(double pMouseX, double pMouseY) {
        if (onClick != null) onClick.run();
    }
}