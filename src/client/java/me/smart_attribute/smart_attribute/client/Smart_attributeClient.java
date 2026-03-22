package me.smart_attribute.smart_attribute.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class Smart_attributeClient implements ClientModInitializer {

    public static ConfigData config = new ConfigData();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("smart_attribute.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final List<String> OPTIONS = Arrays.asList(
            "Sword", "Axe", "Spear", "Trident", "Mace", "Empty Hand"
    );

    public static class ConfigData {
        public boolean enabled = false;
        public boolean hudEnabled = true;
        public boolean switchBack = true;
        public int attributeIndex = 2;
    }

    private int preSwapSlot = -1; // Speichert den Slot VOR dem Swap
    private int tickDelay = -1;

    @Override
    public void onInitializeClient() {
        loadConfig();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("smartattribute").executes(context -> {
                MinecraftClient client = MinecraftClient.getInstance();
                client.execute(() -> client.setScreen(new MainConfigScreen()));
                return 1;
            }));
        });

        // HUD RENDERER (Unten Links)
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (!config.enabled || !config.hudEnabled) return;
            MinecraftClient client = MinecraftClient.getInstance();
            String status = "§6Smart Attribute: §aON §7(" + OPTIONS.get(config.attributeIndex) + ")";
            int y = context.getScaledWindowHeight() - 20;
            context.drawTextWithShadow(client.textRenderer, Text.literal(status), 10, y, 0xFFFFFF);
        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null || !config.enabled) return;

            // Logik: Swap bei Angriff oder Spear Lunge
            // Hinweis: Items.field_52011 ist oft der Mace/Spear in 1.21 Mappings,
            // falls das nicht geht, nutze: activeItem.getItem().toString().contains("spear")
            boolean isAttacking = client.options.attackKey.isPressed();

            if (isAttacking) {
                String target = OPTIONS.get(config.attributeIndex);
                int attrSlot = (target.equals("Empty Hand")) ? findEmptySlot(client) : findSlotByName(client, target);

                // NUR swappen, wenn wir nicht schon auf dem Ziel-Slot sind
                if (attrSlot != -1 && client.player.getInventory().getSelectedSlot() != attrSlot) {
                    // WICHTIG: Wir merken uns den AKTUELLEN Slot des Spielers
                    preSwapSlot = client.player.getInventory().getSelectedSlot();

                    // Wechsel zum Attribut
                    client.player.getInventory().setSelectedSlot(attrSlot);

                    if (config.switchBack) {
                        tickDelay = 1; // 1 Tick warten, damit der Hit registriert wird
                    }
                }
            }

            // Exakter Rückwechsel zum Ursprungsslot
            if (tickDelay == 0 && preSwapSlot != -1) {
                client.player.getInventory().setSelectedSlot(preSwapSlot);
                preSwapSlot = -1; // Zurücksetzen
                tickDelay = -1;
            } else if (tickDelay > 0) {
                tickDelay--;
            }
        });
    }

    private int findSlotByName(MinecraftClient client, String name) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = client.player.getInventory().getStack(i);
            if (!s.isEmpty() && s.getItem().toString().toLowerCase().contains(name.toLowerCase())) return i;
        }
        return -1;
    }

    private int findEmptySlot(MinecraftClient client) {
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    public static void saveConfig() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) { GSON.toJson(config, writer); }
        catch (IOException e) { e.printStackTrace(); }
    }

    private void loadConfig() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) { config = GSON.fromJson(reader, ConfigData.class); }
            catch (IOException e) { e.printStackTrace(); }
        }
    }

    // --- GUI ---
    public static class MainConfigScreen extends Screen {
        public MainConfigScreen() { super(Text.literal("Smart Attribute Settings")); }

        @Override
        protected void init() {
            int x = this.width / 2 - 100;
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Mod: " + (config.enabled ? "§aON" : "§cOFF")), b -> {
                config.enabled = !config.enabled;
                b.setMessage(Text.literal("Mod: " + (config.enabled ? "§aON" : "§cOFF")));
                saveConfig();
            }).dimensions(x, 35, 200, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("HUD Display: " + (config.hudEnabled ? "§aON" : "§cOFF")), b -> {
                config.hudEnabled = !config.hudEnabled;
                b.setMessage(Text.literal("HUD Display: " + (config.hudEnabled ? "§aON" : "§cOFF")));
                saveConfig();
            }).dimensions(x, 60, 200, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Switch Back: " + (config.switchBack ? "§aON" : "§cOFF")), b -> {
                config.switchBack = !config.switchBack;
                b.setMessage(Text.literal("Switch Back: " + (config.switchBack ? "§aON" : "§cOFF")));
                saveConfig();
            }).dimensions(x, 85, 200, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Attribute Item: §b" + OPTIONS.get(config.attributeIndex)), b -> {
                this.client.setScreen(new SelectionScreen(this));
            }).dimensions(x, 110, 200, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> this.client.setScreen(null)).dimensions(x, 145, 200, 20).build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderInGameBackground(context);
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);
            super.render(context, mouseX, mouseY, delta);
        }
    }

    public static class SelectionScreen extends Screen {
        private final Screen parent;
        public SelectionScreen(Screen parent) { super(Text.literal("Select Attribute Item")); this.parent = parent; }
        @Override
        protected void init() {
            int bw = 100, bh = 20, sp = 4, cols = 3;
            int sx = (this.width - ((cols * bw) + ((cols - 1) * sp))) / 2, sy = 50;
            for (int i = 0; i < OPTIONS.size(); i++) {
                int index = i;
                this.addDrawableChild(ButtonWidget.builder(Text.literal(OPTIONS.get(i)), b -> {
                    config.attributeIndex = index;
                    saveConfig();
                    client.setScreen(parent);
                }).dimensions(sx + (i % cols) * (bw + sp), sy + (i / cols) * (bh + sp), bw, bh).build());
            }
        }
        @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) { this.renderInGameBackground(context); super.render(context, mouseX, mouseY, delta); }
    }
}