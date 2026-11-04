package com.robohelper.screen;

import com.robohelper.RoboHelper;
import com.robohelper.config.RobotConfig;
import com.robohelper.entity.RobotInventory;
import com.robohelper.item.RobotModuleItem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Locale;

/** Экран робота: контейнер, состояние и кнопки стандартного протокола Minecraft. */
public final class RobotScreen extends HandledScreen<RobotScreenHandler> {
    private static final Identifier TEXTURE = new Identifier(RoboHelper.MOD_ID, "textures/gui/robot.png");
    private static final int BAR_X = 249;
    private static final int BAR_Y = 27;
    private static final int BAR_WIDTH = 12;
    private static final int BAR_HEIGHT = 33;
    private ButtonWidget modeButton;
    private ButtonWidget directionButton;

    public RobotScreen(RobotScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 276;
        backgroundHeight = 230;
        playerInventoryTitleX = 57;
        playerInventoryTitleY = 134;
    }

    @Override
    protected void init() {
        super.init();
        modeButton = addDrawableChild(ButtonWidget.builder(modeLabel(),
                button -> sendButton(RobotScreenHandler.MODE_BUTTON)).dimensions(x + 8, y + 110, 114, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.robohelper.unload"),
                button -> sendButton(RobotScreenHandler.UNLOAD_BUTTON)).dimensions(x + 126, y + 110, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.robohelper.zone_here"),
                button -> sendButton(RobotScreenHandler.ZONE_BUTTON)).dimensions(x + 200, y + 110, 68, 20).build());
        directionButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.robohelper.direction"),
                button -> sendButton(RobotScreenHandler.DIRECTION_BUTTON)).dimensions(x + 90, y + 29, 112, 20).build());
        updateButtons();
    }

    private void sendButton(int id) {
        if (client != null && client.interactionManager != null) {
            client.interactionManager.clickButton(handler.syncId, id);
        }
    }

    private Text modeLabel() {
        return Text.translatable("gui.robohelper.mode", Text.translatable(
                "mode.robohelper." + handler.getMode().name().toLowerCase(Locale.ROOT)));
    }

    private void updateButtons() {
        modeButton.setMessage(modeLabel());
        directionButton.active = handler.hasMiner();
        directionButton.setTooltip(Tooltip.of(handler.hasMiner()
                ? Text.translatable("gui.robohelper.direction_hint", Text.translatable(
                        "direction.robohelper." + handler.getMinerDirection().getName()))
                : Text.translatable("gui.robohelper.miner_required")));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateButtons();
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
        if (mouseX >= x + BAR_X && mouseX < x + BAR_X + BAR_WIDTH
                && mouseY >= y + BAR_Y && mouseY < y + BAR_Y + BAR_HEIGHT) {
            context.drawTooltip(textRenderer, Text.translatable("gui.robohelper.energy",
                    handler.getEnergy(), RobotConfig.ENERGY_CAPACITY), mouseX, mouseY);
        }
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight, 512, 256);
        int energy = handler.getEnergy();
        int filled = energy * BAR_HEIGHT / RobotConfig.ENERGY_CAPACITY;
        int color = energy > RobotConfig.ENERGY_CAPACITY / 4 ? 0xFF58BD77
                : energy > RobotConfig.LOW_ENERGY_THRESHOLD ? 0xFFE3BD4D : 0xFFE15E55;
        context.fill(x + BAR_X, y + BAR_Y + BAR_HEIGHT - filled,
                x + BAR_X + BAR_WIDTH, y + BAR_Y + BAR_HEIGHT, color);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title, titleX, titleY, 0x273442, false);
        context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, 0x273442, false);
        context.drawText(textRenderer, Text.translatable("gui.robohelper.modules"), 8, 18, 0x3C4D5A, false);
        context.drawText(textRenderer, Text.translatable("gui.robohelper.fuel"), 205, 18, 0x3C4D5A, false);
        for (int module = 0; module < RobotConfig.MODULE_SLOTS; module++) {
            ItemStack stack = handler.getSlot(RobotInventory.FIRST_MODULE_SLOT + module).getStack();
            Text label = stack.isEmpty() ? Text.literal("—")
                    : Text.translatable("gui.robohelper.module_level", RobotModuleItem.getLevel(stack));
            context.drawCenteredTextWithShadow(textRenderer, label, 20 + module * 24, 51, 0xFFE3EBEF);
        }
        Text status = Text.translatable("status.robohelper." + handler.getStatus().name().toLowerCase(Locale.ROOT));
        context.drawText(textRenderer, textRenderer.trimToWidth(status.getString(), 146), 90, 54, 0x3C4D5A, false);
    }
}
