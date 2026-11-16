package com.robohelper.release;

import com.robohelper.entity.RobotEntity;
import com.robohelper.entity.RobotMode;
import com.robohelper.screen.RobotScreen;
import com.robohelper.screen.RobotScreenHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import java.nio.file.Files;
import java.util.Locale;

/** Автоматизированные действия двух настоящих Fabric-клиентов через обычный протокол GUI. */
public final class ReleaseClient implements ClientModInitializer {
    private int stage;
    private int ticks;
    private int wait;
    private boolean denied;
    private boolean minerWarning;
    private RobotEntity own;
    private RobotEntity foreign;
    private int stoneSlot;

    @Override
    public void onInitializeClient() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (message.getString().contains("Шахтёр остановлен")) minerWarning = true;
            if (message.getString().contains("Это не ваш робот") || message.getString().contains("not your robot")) denied = true;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++ticks > 3600) { fail(client, new IllegalStateException("Client acceptance timed out at stage " + stage)); return; }
            if (stage >= 3 && stage <= 11) {
                client.getToastManager().clear();
                client.inGameHud.getChatHud().clear(false);
            }
            if (wait-- > 0 || client.getOverlay() != null) return;
            try { tick(client); } catch (Exception error) { fail(client, error); }
        });
    }

    private void tick(MinecraftClient client) throws Exception {
        if (stage == 0) {
            if (!(client.currentScreen instanceof TitleScreen)) return;
            client.options.getGuiScale().setValue(2);
            client.options.getMaxFps().setValue(30);
            client.options.getViewDistance().setValue(4);
            client.options.language = "ru_ru";
            client.getLanguageManager().setLanguage("ru_ru");
            client.reloadResources();
            stage = 1; wait = 20;
        } else if (stage == 1) {
            ConnectScreen.connect(new TitleScreen(), client, ServerAddress.parse("127.0.0.1:25575"),
                    new ServerInfo("RoboHelper release checks", "127.0.0.1:25575", false), false);
            stage = 2;
        } else if (stage == 2) {
            if (client.world == null || client.player == null) return;
            String username = client.getSession().getUsername();
            for (RobotEntity robot : client.world.getEntitiesByClass(RobotEntity.class,
                    client.player.getBoundingBox().expand(16), robot -> robot.hasCustomName())) {
                if (robot.getCustomName().getString().equals(username + "Bot")) own = robot;
                else foreign = robot;
            }
            if (own == null || foreign == null) return;
            client.player.getInventory().selectedSlot = 0;
            client.setScreen(null);
            client.interactionManager.interactEntity(client.player, foreign, Hand.MAIN_HAND);
            stage = 3; wait = 30;
        } else if (stage == 3) {
            require(denied && !(client.currentScreen instanceof RobotScreen), "Foreign robot opened or denial not received");
            report(client, "foreign-denied");
            client.interactionManager.interactEntity(client.player, own, Hand.MAIN_HAND);
            stage = 4; wait = 20;
        } else if (stage == 4) {
            require(client.currentScreen instanceof RobotScreen, "Own robot GUI missing");
            RobotScreenHandler handler = handler(client);
            require(handler.getEnergy() == 4801 && handler.getMode() == RobotMode.IDLE && handler.getSlot(18).getStack().isOf(Items.COAL), "GUI properties did not synchronize");
            require(handler.getSlot(19).getStack().getNbt().getInt("RoboHelper.Counter") == 500, "Module NBT did not synchronize");
            report(client, "gui-open");
            ScreenshotRecorder.saveScreenshot(client.runDirectory, "robot-gui-ru.png", client.getFramebuffer(), ignored -> {});
            stoneSlot = -1;
            for (int i = RobotScreenHandler.PLAYER_START; i < handler.slots.size(); i++) {
                if (handler.getSlot(i).getStack().isOf(Items.STONE)) stoneSlot = i;
            }
            require(stoneSlot >= 0, "Missing client stone fixture");
            client.interactionManager.clickSlot(handler.syncId, stoneSlot, 0, SlotActionType.PICKUP, client.player);
            stage = 5; wait = 10;
        } else if (stage == 5) {
            RobotScreenHandler handler = handler(client);
            require(handler.getCursorStack().isOf(Items.STONE), "Stone not picked up");
            client.interactionManager.clickSlot(handler.syncId, 18, 0, SlotActionType.PICKUP, client.player);
            stage = 6; wait = 10;
        } else if (stage == 6) {
            RobotScreenHandler handler = handler(client);
            require(handler.getSlot(18).getStack().isOf(Items.COAL) && handler.getCursorStack().isOf(Items.STONE), "Fuel slot accepted stone");
            client.interactionManager.clickSlot(handler.syncId, stoneSlot, 0, SlotActionType.PICKUP, client.player);
            report(client, "fuel-rejected");
            // Invoke the actual GUI button, which sends the vanilla button packet.
            ((ButtonWidget) client.currentScreen.children().stream().filter(child -> child instanceof ButtonWidget button
                    && button.getMessage().getString().startsWith("Режим:")).findFirst().orElseThrow()).onPress();
            stage = 7; wait = 10;
        } else if (stage == 7) {
            require(handler(client).getMode() == RobotMode.FOLLOW, "Mode button did not synchronize FOLLOW");
            client.interactionManager.clickButton(handler(client).syncId, RobotScreenHandler.MODE_BUTTON);
            stage = 8; wait = 10;
        } else if (stage == 8) {
            require(handler(client).getMode() == RobotMode.WORK, "Mode button did not synchronize WORK");
            client.interactionManager.clickButton(handler(client).syncId, RobotScreenHandler.MODE_BUTTON);
            stage = 9; wait = 10;
        } else if (stage == 9) {
            require(handler(client).getMode() == RobotMode.IDLE && handler(client).getCursorStack().isEmpty(), "Mode/cursor did not settle");
            if (client.getSession().getUsername().equals("ReleaseOwner")) {
                require(minerWarning, "Owner did not receive miner safety warning");
                report(client, "miner-warning");
            }
            report(client, "buttons");
            client.options.language = "en_us";
            client.getLanguageManager().setLanguage("en_us");
            client.reloadResources();
            stage = 10; wait = 20;
        } else if (stage == 10) {
            require(client.currentScreen instanceof RobotScreen, "GUI lost during language reload");
            ScreenshotRecorder.saveScreenshot(client.runDirectory, "robot-gui-en.png", client.getFramebuffer(), ignored -> {});
            client.player.closeHandledScreen();
            client.player.setYaw(180); client.player.setPitch(15);
            stage = 11; wait = 20;
        } else if (stage == 11) {
            ScreenshotRecorder.saveScreenshot(client.runDirectory, "robots-world.png", client.getFramebuffer(), ignored -> {});
            report(client, "done");
            Files.writeString(client.runDirectory.toPath().resolve("acceptance-passed.txt"), "foreign-denied,gui-open,fuel-rejected,buttons,ru/en-render\n");
            System.out.println("RELEASE CLIENT PASSED " + client.getSession().getUsername());
            stage = 12; wait = 20;
        } else if (stage == 12) {
            client.scheduleStop();
            stage = 13;
        }
    }

    private RobotScreenHandler handler(MinecraftClient client) {
        require(client.player.currentScreenHandler instanceof RobotScreenHandler, "Robot screen handler missing at stage " + stage);
        return (RobotScreenHandler) client.player.currentScreenHandler;
    }
    private void report(MinecraftClient client, String event) { client.player.networkHandler.sendChatCommand("robohelper_release report " + event); }
    private static void require(boolean value, String error) { if (!value) throw new IllegalStateException(error); }
    private void fail(MinecraftClient client, Exception error) {
        error.printStackTrace();
        try { Files.writeString(client.runDirectory.toPath().resolve("acceptance-failed.txt"), "stage=" + stage + " " + error); } catch (Exception ignored) { }
        stage = 13;
        client.scheduleStop();
    }
}
