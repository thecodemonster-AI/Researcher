package com.ender.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ender.blockentity.ResearchTableBlockEntity;
import com.ender.network.ModNetworking;
import com.ender.network.RequestSyncPacket;
import com.ender.network.StartResearchPacket;
import com.ender.network.ClaimResearchPacket;
import com.ender.researcher.ResearchManager;
import com.ender.client.ClientResearchProgressManager;
import com.ender.config.ResearchEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.ender.config.ResearchEntry.Requirement;
import net.minecraft.ChatFormatting;

public class ResearchTableScreen extends Screen {

    // Categories are loaded from ResearchManager (config JSON)
    private final Map<String, String> CategoryText = new LinkedHashMap<>();
    private ScrollableTextWidget rightPanel;
    private ScrollableListWidget leftList; // left-side scrollable category list
    private final Map<String, ResearchState> categoryStates = new LinkedHashMap<>();
    private StatefulButton researchButton; // The button at the bottom of the right panel
    private int selectedIndex = 0;
    private int buttonStartY;
    private final ResearchTableBlockEntity blockEntity;
    private final Player player;

    // Base design sizes (used to compute scaled sizes)
    private static final int BASE_BUTTON_WIDTH = 180;
    private static final int BASE_BUTTON_HEIGHT = 20;
    private static final int BASE_RIGHT_PANEL_WIDTH = 250;
    private static final int BASE_RIGHT_PANEL_HEIGHT = 220;
    private static final int BASE_LEFT_OFFSET = 210;
    private static final int BASE_TOP_OFFSET = 110;

    // Scaled sizes computed in init()
    private int scaledButtonWidth;
    private int scaledButtonHeight;
    private int scaledRightPanelWidth;
    private int scaledRightPanelHeight;
    private float uiScaleFactor = 1.0f;

    // client-side estimated remaining ticks used for stable countdown UI
    private int estimatedRemainingTicks = -1;


    // Return the current category index (int)
    private int getCurrentCategory() {
        return selectedIndex; // simply use the selected index
    }

    // Return the current research state from the block entity (defensive)
    private ResearchState getCurrentCategoryState() {
        int categoryIndex = getCurrentCategory();
        if (blockEntity == null) return ResearchState.NOT_STARTED;
        if (categoryIndex < 0 || categoryIndex >= CategoryText.size()) return ResearchState.NOT_STARTED;
        // Use client-side manager (updated by ResearchStatePacket) to get player's state
        com.ender.client.ResearchState clientState = ClientResearchProgressManager.getState(player.getUUID(), categoryIndex);
        if (clientState == null) return ResearchState.NOT_STARTED;
        try {
            return ResearchState.valueOf(clientState.name());
        } catch (IllegalArgumentException ex) {
            return ResearchState.NOT_STARTED;
        }
    }

    // Determine first missing requirement message for a research entry on the client, or null if all satisfied
    private String getMissingRequirementMessage(int categoryIndex) {
        ResearchEntry entry = ResearchManager.getByIndex(categoryIndex);
        if (entry == null || entry.requirements == null) return null;
        for (Requirement req : entry.requirements) {
            if (req == null) continue;
            if ("item".equalsIgnoreCase(req.type)) {
                int have = 0;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack != null && !stack.isEmpty()) {
                        Item item = stack.getItem();
                        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
                        if (key != null && key.toString().equals(req.item)) {
                            have += stack.getCount();
                        }
                    }
                }
                int remaining = Math.max(0, req.count - have);
                if (remaining > 0) {
                    // try to get a friendly item name
                    String name = req.item;
                    try {
                        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(req.item));
                        if (it != null) name = it.getDefaultInstance().getHoverName().getString();
                    } catch (Exception ignored) {}
                    return remaining + "x " + name + " needed";
                }
            } else if ("research".equalsIgnoreCase(req.type)) {
                int otherIndex = ResearchManager.getIndexForId(req.research_id);
                if (otherIndex < 0) continue; // can't validate
                // check client-side research state
                com.ender.client.ResearchState st = ClientResearchProgressManager.getState(player.getUUID(), otherIndex);
                if (st != com.ender.client.ResearchState.COMPLETE_UNCLAIMED && st != com.ender.client.ResearchState.COMPLETE_CLAIMED) {
                    // show the required research title if available
                    ResearchEntry other = ResearchManager.getByIndex(otherIndex);
                    String rtitle = other != null && other.title != null ? other.title : req.research_id;
                    return "(" + rtitle + ") research required";
                }
            }
        }
        return null;
    }

    // Start research for the current category (send packet to server) but validate requirements first on client
    private void startResearchForCurrentCategory() {
        int categoryIndex = getCurrentCategory();
        if (blockEntity == null) return;
        if (categoryIndex < 0 || categoryIndex >= CategoryText.size()) return;

        String missing = getMissingRequirementMessage(categoryIndex);
        if (missing != null) {
            // show transient red message for 3 seconds (60 ticks)
            if (researchButton != null) researchButton.showTransientMessage(missing, ChatFormatting.RED, 60);
            return;
        }

        // send request to server to start research
        if (ModNetworking.CHANNEL != null) {
            ModNetworking.CHANNEL.sendToServer(new StartResearchPacket(blockEntity.getBlockPos(), categoryIndex));
        }
        // optimistic UI update
        if (researchButton != null) researchButton.setState(ResearchState.IN_PROGRESS);
    }


    public ResearchTableScreen(ResearchTableBlockEntity be, Player player) {
        super(Component.literal("Research Table"));
        this.blockEntity = be;
        this.player = player;

        // Populate categories from ResearchManager
        var entries = ResearchManager.getAll();
        if (entries == null || entries.isEmpty()) {
            // fallback: keep a default placeholder category
            CategoryText.put("No Research Configured", "No research entries were found. Check config/research.json or the mod assets.");
        } else {
            for (ResearchEntry e : entries) {
                String title = e.title != null ? e.title : e.id;
                String desc = e.description != null ? e.description : "";
                CategoryText.put(title, desc);
            }
        }
        for (String category : CategoryText.keySet()) {
            categoryStates.put(category, ResearchState.NOT_STARTED);
        }
    }

    @Override
    protected void init() {
        // Compute a UI scale factor based on current screen size against a base design (800x600)
        float sx = Math.max(0.6f, Math.min(1.5f, (float)this.width / 800.0f));
        float sy = Math.max(0.6f, Math.min(1.5f, (float)this.height / 600.0f));
        uiScaleFactor = Math.min(sx, sy);

        // Publish the scale so other widgets can render text accordingly
        UIConfig.setScale(uiScaleFactor);

        // Compute scaled widget sizes (enforce minimums)
        scaledButtonWidth = Math.max(80, (int)(BASE_BUTTON_WIDTH * uiScaleFactor));
        scaledButtonHeight = Math.max(14, (int)(BASE_BUTTON_HEIGHT * uiScaleFactor));
        scaledRightPanelWidth = Math.max(120, (int)(BASE_RIGHT_PANEL_WIDTH * uiScaleFactor));
        scaledRightPanelHeight = Math.max(80, (int)(BASE_RIGHT_PANEL_HEIGHT * uiScaleFactor));

        int leftX = this.width / 2 - Math.max(1, (int)(BASE_LEFT_OFFSET * uiScaleFactor));
        int topY = this.height / 2 - Math.max(1, (int)(BASE_TOP_OFFSET * uiScaleFactor));

        // Right panel (use safe text lookup)
        rightPanel = new ScrollableTextWidget(
                this.width / 2 + 20,
                topY,
                scaledRightPanelWidth,
                scaledRightPanelHeight,
                getTextAtIndex(0)
        );
        addRenderableWidget(rightPanel);

        int buttonX = rightPanel.getX();
        int buttonY = rightPanel.getY() + rightPanel.getHeight() + Math.max(4, (int)(4 * uiScaleFactor));
        int buttonWidth = scaledButtonWidth;
        int buttonHeight = scaledButtonHeight;

        // Read progress from the block entity (defensive) and convert server enum to client enum
        ResearchState state = ResearchState.NOT_STARTED;
        if (blockEntity != null) {
            int idx = selectedIndex;
            if (idx >= 0 && idx < CategoryText.size()) {
                com.ender.client.ResearchState cs = ClientResearchProgressManager.getState(player.getUUID(), idx);
                if (cs != null) {
                    try {
                        state = ResearchState.valueOf(cs.name());
                    } catch (IllegalArgumentException ex) {
                        state = ResearchState.NOT_STARTED;
                    }
                }
            }
        }
        // create the StatefulButton before calling methods on it
        researchButton = new StatefulButton(
                buttonX,
                buttonY,
                buttonWidth,
                buttonHeight,
                state,
                () -> {
                    ResearchState cur = getCurrentCategoryState();
                    int categoryIndex = getCurrentCategory();
                    if (cur == ResearchState.NOT_STARTED) {
                        startResearchForCurrentCategory();
                    } else if (cur == ResearchState.COMPLETE_UNCLAIMED) {
                        if (ModNetworking.CHANNEL != null) ModNetworking.CHANNEL.sendToServer(new ClaimResearchPacket(blockEntity.getBlockPos(), categoryIndex));
                        researchButton.setState(ResearchState.COMPLETE_CLAIMED);
                    }
                }
        );
        // ensure visual state matches
        researchButton.setState(state);
        addRenderableWidget(researchButton.getButton());

        // Left menu: use ScrollableListWidget so long lists can scroll
        var titles = new java.util.ArrayList<String>(CategoryText.keySet());
        int leftWidth = Math.max(120, (int)(BASE_BUTTON_WIDTH * uiScaleFactor));
        int leftHeight = scaledRightPanelHeight + buttonHeight + Math.max(8, (int)(8 * uiScaleFactor));
        this.leftList = new ScrollableListWidget(leftX, topY, leftWidth, leftHeight, titles, idx -> selectCategory(idx));
        this.leftList.setSelectedIndex(selectedIndex);
        addRenderableWidget(this.leftList);

        // After UI built, request sync from server for current BE state and per-category states
        if (ModNetworking.CHANNEL != null && blockEntity != null) {
            ModNetworking.CHANNEL.sendToServer(new RequestSyncPacket(blockEntity.getBlockPos()));
        }
    }

    @Override
    public void tick() {
        super.tick();
        // Poll the client-side block entity for updates that arrived from server
        if (blockEntity != null && researchButton != null) {
            // ensure the transient message timer is updated
            researchButton.tick();

            ResearchState state = getCurrentCategoryState();
            researchButton.setState(state);
            // If the block entity is currently researching and this category is the active one, show condensed countdown
            if (state == ResearchState.IN_PROGRESS) {
                int serverProgress = blockEntity.getProgress();
                int serverMax = blockEntity.getMaxProgress();
                int serverRemaining = Math.max(0, serverMax - serverProgress);

                if (estimatedRemainingTicks < 0) {
                    // initialize from server snapshot
                    estimatedRemainingTicks = serverRemaining;
                } else {
                    // if server drift is large, resync; tolerance 20 ticks (1s)
                    if (Math.abs(estimatedRemainingTicks - serverRemaining) > 20) {
                        estimatedRemainingTicks = serverRemaining;
                    } else {
                        // otherwise, decrement locally
                        estimatedRemainingTicks = Math.max(0, estimatedRemainingTicks - 1);
                    }
                }

                int remainingTicks = Math.max(0, estimatedRemainingTicks);
                int remainingSeconds = remainingTicks / 20; // 20 ticks = 1 second
                int mins = remainingSeconds / 60;
                int secs = remainingSeconds % 60;
                String mmss = String.format("%d:%02d", mins, secs);
                researchButton.setExtraText(mmss);
            } else {
                estimatedRemainingTicks = -1;
                researchButton.setExtraText(null);
            }
        }
    }

    private void selectCategory(int index) {
        if (index < 0 || index >= CategoryText.size()) return;
        selectedIndex = index;
        if (rightPanel != null) rightPanel.setText(getTextAtIndex(index));
        if (researchButton != null) researchButton.setState(getCurrentCategoryState());
        if (leftList != null) leftList.setSelectedIndex(selectedIndex);
    }

    private String getTextAtIndex(int index) {
        if (index < 0 || index >= CategoryText.size()) return "";
        return CategoryText.values().toArray(new String[0])[index];
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        // Keep the world running while this GUI is open (do not pause in singleplayer)
        return false;
    }

    // Called by StartFailedPacket to display authoritative failure reasons on the open screen
    public void showStartFailedMessage(String reason) {
        if (reason == null || reason.isEmpty()) reason = "Start failed";
        if (this.researchButton != null) {
            this.researchButton.showTransientMessage(reason, net.minecraft.ChatFormatting.RED, 80);
        }
    }

    public ResearchTableBlockEntity getBlockEntity() {
        return blockEntity;
    }
}