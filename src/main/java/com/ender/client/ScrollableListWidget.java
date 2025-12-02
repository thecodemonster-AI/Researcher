package com.ender.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;

public class ScrollableListWidget extends AbstractWidget {

    private final List<String> entries;
    private double scrollOffset = 0;
    private static final int ENTRY_HEIGHT = 16;
    private final IntConsumer onClick; // receives clicked index
    private int selectedIndex = -1;

    public ScrollableListWidget(int x, int y, int width, int height, List<String> entries, IntConsumer onClick) {
        super(x, y, width, height, Component.empty());
        this.entries = entries;
        this.onClick = onClick;
    }

    public void setSelectedIndex(int idx) { this.selectedIndex = idx; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {

        // Background
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF202020);

        int visibleLines = getHeight() / ENTRY_HEIGHT;

        int startIndex = (int) (scrollOffset / ENTRY_HEIGHT);
        int yPos = getY() - (int) (scrollOffset % ENTRY_HEIGHT);

        float s = UIConfig.getScale();

        for (int i = 0; i < visibleLines + 2; i++) {
            int actualIndex = startIndex + i;
            if (actualIndex >= entries.size()) break;

            String text = entries.get(actualIndex);

            // highlight selected
            if (actualIndex == selectedIndex) {
                g.fill(getX()+1, yPos + (i * ENTRY_HEIGHT), getX() + getWidth()-1, yPos + (i * ENTRY_HEIGHT) + ENTRY_HEIGHT, 0xFF2E542E);
            }

            g.pose().pushPose();
            g.pose().translate(getX() + 6, yPos + (i * ENTRY_HEIGHT) + 3, 0);
            g.pose().scale(s, s, 1f);
            g.drawString(Minecraft.getInstance().font, text, 0, 0, 0xFFFFFFFF);
            g.pose().popPose();
        }

        // Scrollbar
        g.fill(
                getX() + getWidth() - 6,
                getY(),
                getX() + getWidth() - 2,
                getY() + getHeight(),
                0xFF404040
        );
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollOffset = Math.max(0, Math.min(scrollOffset - delta * 10,
                Math.max(0, entries.size() * ENTRY_HEIGHT - getHeight())
        ));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX < getX() || mouseX > getX() + getWidth() || mouseY < getY() || mouseY > getY() + getHeight()) return false;
        int startIndex = (int) (scrollOffset / ENTRY_HEIGHT);
        int yPos = getY() - (int) (scrollOffset % ENTRY_HEIGHT);
        int relY = (int)mouseY - yPos;
        int clicked = startIndex + (relY / ENTRY_HEIGHT);
        if (clicked >= 0 && clicked < entries.size()) {
            if (onClick != null) onClick.accept(clicked);
            return true;
        }
        return false;
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