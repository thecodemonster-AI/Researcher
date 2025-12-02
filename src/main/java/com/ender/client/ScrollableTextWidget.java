package com.ender.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

public class ScrollableTextWidget extends AbstractWidget {

    private List<String> lines;
    private double scrollOffset = 0;
    private static final int LINE_HEIGHT = 10;

    public ScrollableTextWidget(int x, int y, int width, int height, String text) {
        super(x, y, width, height, Component.empty());
        this.lines = Arrays.stream(text.split("\n")).toList();
    }
    
    public void setText(String newText) {
        this.lines = Arrays.asList(newText.split("\n"));
        this.scrollOffset = 0;
    }


    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {

        // Outer panel
        g.fill(getX() - 2, getY() - 2, getX() + getWidth() + 2, getY() + getHeight() + 2, 0xFF222222);
        // Inner background
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF151515);

        // Optional header bar
        int headerH = 14;
        g.fill(getX(), getY(), getX() + getWidth(), getY() + headerH, 0xFF1F2B2A);

        // Scale header text
        float s = UIConfig.getScale();
        g.pose().pushPose();
        g.pose().translate(getX() + 6, getY() + 3, 0);
        g.pose().scale(s, s, 1f);
        g.drawString(Minecraft.getInstance().font, "Details:", 0, 0, 0xFFCAD3CF);
        g.pose().popPose();

        int visibleLines = Math.max(0, (getHeight() - headerH) / LINE_HEIGHT);
        int contentHeight = Math.max(0, lines.size() * LINE_HEIGHT - (getHeight() - headerH));
        scrollOffset = Math.max(0, Math.min(scrollOffset, contentHeight));
        int startIndex = (int) (scrollOffset / LINE_HEIGHT);
        int yPos = getY() + headerH - (int) (scrollOffset % LINE_HEIGHT);

        double scaleFactor = Minecraft.getInstance().getWindow().getGuiScale();
        int scissorX = (int) ((getX() + 2) * scaleFactor);
        int scissorY = (int) ((Minecraft.getInstance().getWindow().getGuiScaledHeight() - (getY() + getHeight() - 2)) * scaleFactor);
        int scissorW = (int) ((getWidth() - 12) * scaleFactor);
        int scissorH = (int) ((getHeight() - headerH - 2) * scaleFactor);
        RenderSystem.enableScissor(scissorX, scissorY, Math.max(0, scissorW), Math.max(0, scissorH));

        for (int i = 0; i < visibleLines + 2; i++) {
            int actualIndex = startIndex + i;
            if (actualIndex >= lines.size()) break;

            int lineY = yPos + (i * LINE_HEIGHT);
            if (lineY + LINE_HEIGHT <= getY() + headerH || lineY >= getY() + getHeight()) continue;

            g.pose().pushPose();
            g.pose().translate(getX() + 6, lineY, 0);
            g.pose().scale(s, s, 1f);
            g.drawString(
                    Minecraft.getInstance().font,
                    lines.get(actualIndex),
                    0, 0,
                    0xDDDDDD
            );
            g.pose().popPose();
        }
        RenderSystem.disableScissor();

        // Slim scrollbar with darker track
        int sbX = getX() + getWidth() - 8;
        g.fill(sbX, getY() + headerH, sbX + 6, getY() + getHeight(), 0xFF2C2C2C);
        g.fill(sbX + 1, getY() + headerH + 2, sbX + 5, getY() + headerH + 8, 0xFF5A6B68);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollOffset = Math.max(0, Math.min(scrollOffset - delta * 10,
                Math.max(0, lines.size() * LINE_HEIGHT - (getHeight() - 14))
        ));
        return true;
    }

    public boolean isHovered(double mx, double my) {
        return mx >= getX() && mx <= getX() + getWidth() &&
               my >= getY() && my <= getY() + getHeight();
    }

    @Override
    protected void renderWidget(GuiGraphics p_282139_, int p_268034_, int p_268009_, float p_268085_) {
        // handled in render
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput p_259858_) {
        // no-op
    }
}