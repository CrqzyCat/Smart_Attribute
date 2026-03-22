package me.smart_attribute.smart_attribute.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class Smart_attributeClient implements ClientModInitializer {

    // --- CONFIG & SAVING ---
    public static ConfigData config = new ConfigData();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("smart_attribute.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final List<String> OPTIONS = Arrays.asList(
            "Sword", "Axe", "Spear", "Trident", "Mace", "Empty Hand"
    );

    public static class ConfigData {
        public boolean enabled = false;
        public int attributeIndex = 2; // Default: Spear
    }

    @Override
    public void onInitializeClient() {
        loadConfig();

        // Befehl: /smartattribute
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("smartattribute").executes(context -> {
                MinecraftClient client = MinecraftClient.getInstance();
                client.execute(() -> client.setScreen(new MainConfigScreen()));
                return 1;
            }));
        });

        // Die Swap-Logik (Attribute Swapping)
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null || !config.enabled) return;

            // Wenn die Angriffstaste gedrückt wird
            if (client.options.attackKey.isPressed()) {
                String target = OPTIONS.get(config.attributeIndex);

                if (target.equals("Empty Hand")) {
                    int empty = findEmptySlot(client);
                    if (empty != -1) client.player.getInventory().setSelectedSlot(empty);
                } else {
                    int attrSlot = findSlotByName(client, target);
                    if (attrSlot != -1) {
                        client.player.getInventory().setSelectedSlot(attrSlot);
                    }
                }
            }
        });
    }

    private int findSlotByName(MinecraftClient client, String name) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = client.player.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            if (s.getItem().toString().toLowerCase().contains(name.toLowerCase())) return i;
        }
        return -1;
    }

    private int findEmptySlot(MinecraftClient client) {
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    // --- SPEICHER FUNKTIONEN ---
    public static void saveConfig() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadConfig() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                config = GSON.fromJson(reader, ConfigData.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // --- GUI KLASSEN ---

    public static class MainConfigScreen extends Screen {
        public MainConfigScreen() { super(Text.literal("Smart Attribute Settings")); }

        @Override
        protected void init() {
            int x = this.width / 2 - 100;

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Swap: " + (config.enabled ? "§aON" : "§cOFF")), b -> {
                config.enabled = !config.enabled;
                b.setMessage(Text.literal("Swap: " + (config.enabled ? "§aON" : "§cOFF")));
                saveConfig();
            }).dimensions(x, 40, 200, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Attribute Item: §b" + OPTIONS.get(config.attributeIndex)), b -> {
                this.client.setScreen(new SelectionScreen(this));
            }).dimensions(x, 70, 200, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> this.client.setScreen(null))
                    .dimensions(x, 110, 200, 20).build());
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

        public SelectionScreen(Screen parent) {
            super(Text.literal("Select Attribute Item"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int bw = 100, bh = 20, sp = 4, cols = 3;
            int tw = (cols * bw) + ((cols - 1) * sp);
            int sx = (this.width - tw) / 2, sy = 40;

            for (int i = 0; i < OPTIONS.size(); i++) {
                int index = i;
                int r = i / cols, c = i % cols;
                this.addDrawableChild(ButtonWidget.builder(Text.literal(OPTIONS.get(i)), b -> {
                    config.attributeIndex = index;
                    saveConfig();
                    client.setScreen(parent);
                }).dimensions(sx + c * (bw + sp), sy + r * (bh + sp), bw, bh).build());
            }

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> client.setScreen(parent))
                    .dimensions(this.width / 2 - 50, this.height - 30, 100, 20).build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderInGameBackground(context);
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);
            super.render(context, mouseX, mouseY, delta);
        }
    }
}