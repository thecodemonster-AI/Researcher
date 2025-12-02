package com.ender.client;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class StatefulButton {

    private final PillButton button;
    private ResearchState state;
    private String extraText = null; // optional extra text appended to the button (e.g., countdown)
    private final Runnable onStartResearch;

    // Transient message fields (shown for a short duration, e.g., red error text)
    private String transientText = null;
    private ChatFormatting transientColor = ChatFormatting.RED;
    private int transientTicks = 0; // remaining ticks to show transient text

    public StatefulButton(int x, int y, int width, int height, ResearchState initialState, Runnable onStartResearch) {
        this.state = initialState;
        this.onStartResearch = onStartResearch;

        // Create the pill-shaped button
        button = new PillButton(x, y, width, height, Component.literal(""), () -> onPress());
        // default colors
        button.setBgColor(0xFF6AA84F);
        button.setTextColor(0xFFFFFFFF);

        updateText();
    }

    public PillButton getButton() {
        return button;
    }

    public void setState(ResearchState state) {
        this.state = state;
        updateText();
    }

    // Set optional extra text (e.g., countdown). Pass null to clear.
    public void setExtraText(String txt) {
        this.extraText = txt;
        updateText();
    }

    public ResearchState getState() {
        return state;
    }

    // Show a transient message in a given color for the specified number of ticks
    public void showTransientMessage(String text, ChatFormatting color, int ticks) {
        this.transientText = text;
        this.transientColor = color != null ? color : ChatFormatting.RED;
        this.transientTicks = Math.max(0, ticks);
        updateText();
    }

    // Must be called every screen tick to decrement transient message timer
    public void tick() {
        if (transientTicks > 0) {
            transientTicks--;
            if (transientTicks == 0) {
                transientText = null;
                updateText();
            }
        }
    }

    private void updateText() {
        // If a transient text is active, show it with the specified color
        if (transientText != null && transientTicks > 0) {
            button.setMessage(Component.literal(transientText));
            // color the pill to a subtle red background
            button.setBgColor(0xFF8B1E1E);
            return;
        }

        // default pill appearance depending on state
        switch (state) {
            case NOT_STARTED -> {
                button.setMessage(Component.literal("Start Research"));
                button.setBgColor(0xFF6AA84F); // green
            }
            case IN_PROGRESS -> {
                String base = "Research in Progress";
                if (extraText != null && !extraText.isEmpty()) {
                    button.setMessage(Component.literal(base + " (" + extraText + ")"));
                } else {
                    button.setMessage(Component.literal(base));
                }
                button.setBgColor(0xFF4A6B8B); // blue-grey
            }
            case COMPLETE_UNCLAIMED -> {
                String base = "Research Complete";
                if (extraText != null && !extraText.isEmpty()) {
                    button.setMessage(Component.literal(base + " (" + extraText + ")"));
                } else {
                    button.setMessage(Component.literal(base));
                }
                button.setBgColor(0xFFDAA520); // gold
            }
            case COMPLETE_CLAIMED -> {
                button.setMessage(Component.literal("Already Researched"));
                button.setBgColor(0xFF777777); // gray
            }
        }
    }

    private void onPress() {
        // Always delegate action to the provided Runnable; the caller (screen) decides what to do for each state.
        if (onStartResearch != null) {
            onStartResearch.run();
        }
    }
    
}