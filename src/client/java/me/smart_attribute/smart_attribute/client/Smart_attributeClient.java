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

/**
 * Main client class for the Smart Attribute mod.
 * This mod automatically switches to a specified hotbar slot when the player attacks,
 * based on configurable trigger conditions.
 */
public class Smart_attributeClient implements ClientModInitializer {

    /**
     * Holds the current configuration data for the mod.
     */
    public static ConfigData config = new ConfigData();
    /**
     * The configuration file where settings are stored.
     */
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("smart_attribute.json").toFile();
    /**
     * Gson instance for serializing and deserializing the configuration file.
     */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * A list of possible attribute targets for the slot switching.
     */
    public static final List<String> ATTRIBUTE_OPTIONS = Arrays.asList("Sword", "Axe", "Spear", "Trident", "Mace", "Empty Hand", "Specific Slot");
    /**
     * A list of modes that determine which items trigger the slot switch.
     */
    public static final List<String> TRIGGER_MODES = Arrays.asList(
            "All Items", "Current Item", "Weapons Only",
            "Only Sword", "Only Axe", "Only Spear", "Only Mace", "Only Trident"
    );

    /**
     * Data class for storing the mod's configuration settings.
     */
    public static class ConfigData {
        /**
         * If true, the mod's features are enabled.
         */
        public boolean enabled = false;
        /**
         * If true, the mod will switch back to the original slot after an attack.
         */
        public boolean switchBack = true;
        /**
         * The index of the selected attribute from {@link #ATTRIBUTE_OPTIONS}. Default is 2 ("Spear").
         */
        public int attributeIndex = 2;
        /**
         * The index of the selected trigger mode from {@link #TRIGGER_MODES}. Default is 2 ("Weapons Only").
         */
        public int triggerModeIndex = 2;
        /**
         * The target hotbar slot (1-9) to switch to when "Specific Slot" is chosen.
         */
        public int targetSlot = 1;
        /**
         * The item ID used as a trigger when "Current Item" mode is active.
         */
        public String customTriggerId = "minecraft:air";
    }

    /**
     * Stores the player's hotbar slot before the automatic switch. Used for switching back.
     */
    private int preSwapSlot = -1;
    /**
     * A short delay in client ticks before switching back to the original slot.
     */
    private int tickDelay = -1;
    /**
     * A lock to prevent the mod from re-triggering while the attack key is held down.
     */
    private boolean isLocked = false;

    /**
     * Initializes the client-side of the mod.
     * This method loads the configuration, registers the command for opening the settings GUI,
     * and sets up the client tick event listener for the core logic.
     */
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

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null || !config.enabled) return;

            boolean attackKeyPressed = client.options.attackKey.isPressed();

            if (attackKeyPressed && !isLocked) {
                if (isTriggerItem(client.player.getMainHandStack())) {
                    int attrSlot = getTargetSlot(client);

                    if (attrSlot != -1 && client.player.getInventory().getSelectedSlot() != attrSlot) {
                        preSwapSlot = client.player.getInventory().getSelectedSlot();
                        client.player.getInventory().setSelectedSlot(attrSlot);
                        if (config.switchBack) tickDelay = 1;
                    }
                }
                isLocked = true;
            }

            if (!attackKeyPressed) isLocked = false;

            if (tickDelay == 0 && preSwapSlot != -1) {
                client.player.getInventory().setSelectedSlot(preSwapSlot);
                preSwapSlot = -1;
                tickDelay = -1;
            } else if (tickDelay > 0) {
                tickDelay--;
            }
        });
    }

    /**
     * Determines the target hotbar slot based on the current configuration.
     *
     * @param client The MinecraftClient instance.
     * @return The 0-indexed hotbar slot to switch to, or -1 if no suitable slot is found.
     */
    private int getTargetSlot(MinecraftClient client) {
        String mode = ATTRIBUTE_OPTIONS.get(config.attributeIndex);
        if (mode.equals("Specific Slot")) return config.targetSlot - 1; // Convert 1-9 to 0-8
        if (mode.equals("Empty Hand")) return findEmptySlot(client);
        return findSlotByName(client, mode);
    }

    /**
     * Checks if the item in the player's main hand should trigger the slot switch.
     *
     * @param stack The ItemStack in the player's main hand.
     * @return True if the item is a valid trigger, false otherwise.
     */
    private boolean isTriggerItem(ItemStack stack) {
        String mode = TRIGGER_MODES.get(config.triggerModeIndex);
        if (mode.equals("All Items")) return true;
        String name = stack.getItem().toString().toLowerCase();
        return switch (mode) {
            case "Current Item" -> name.equals(config.customTriggerId);
            case "Weapons Only" -> name.contains("sword") || name.contains("axe") || name.contains("mace") || name.contains("spear") || name.contains("trident");
            case "Only Sword" -> name.contains("sword");
            case "Only Axe" -> name.contains("axe");
            case "Only Spear" -> name.contains("spear");
            case "Only Mace" -> name.contains("mace");
            case "Only Trident" -> name.contains("trident");
            default -> false;
        };
    }

    /**
     * Finds the first hotbar slot containing an item whose name partially matches the given name.
     *
     * @param client The MinecraftClient instance.
     * @param name   The name to search for (e.g., "Sword").
     * @return The 0-indexed slot number, or -1 if not found.
     */
    private int findSlotByName(MinecraftClient client, String name) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = client.player.getInventory().getStack(i);
            if (!s.isEmpty() && s.getItem().toString().toLowerCase().contains(name.toLowerCase())) return i;
        }
        return -1;
    }

    /**
     * Finds the first empty hotbar slot.
     *
     * @param client The MinecraftClient instance.
     * @return The 0-indexed slot number, or -1 if no empty slot is found.
     */
    private int findEmptySlot(MinecraftClient client) {
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    /**
     * Saves the current configuration to the JSON file.
     */
    public static void saveConfig() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) { GSON.toJson(config, writer); }
        catch (IOException e) { e.printStackTrace(); }
    }

    /**
     * Loads the configuration from the JSON file, if it exists.
     */
    private void loadConfig() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) { config = GSON.fromJson(reader, ConfigData.class); }
            catch (IOException e) { e.printStackTrace(); }
        }
    }

    /**
     * The main configuration screen for the mod.
     */
    public static class MainConfigScreen extends Screen {
        public MainConfigScreen() { super(Text.literal("Smart Attribute Settings")); }

        @Override
        protected void init() {
            int x = this.width / 2 - 100;
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Mod: " + (config.enabled ? "§aON" : "§cOFF")), b -> { config.enabled = !config.enabled; b.setMessage(Text.literal("Mod: " + (config.enabled ? "§aON" : "§cOFF"))); saveConfig(); }).dimensions(x, 35, 200, 20).build());

            String attrDisplay = ATTRIBUTE_OPTIONS.get(config.attributeIndex);
            if (attrDisplay.equals("Specific Slot")) attrDisplay = "Slot: " + config.targetSlot;

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Trigger on: §e" + TRIGGER_MODES.get(config.triggerModeIndex)), b -> this.client.setScreen(new TriggerSelectionScreen(this))).dimensions(x, 60, 200, 20).build());
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Switch Back: " + (config.switchBack ? "§aON" : "§cOFF")), b -> { config.switchBack = !config.switchBack; b.setMessage(Text.literal("Switch Back: " + (config.switchBack ? "§aON" : "§cOFF"))); saveConfig(); }).dimensions(x, 85, 200, 20).build());
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Attribute: §b" + attrDisplay), b -> this.client.setScreen(new AttributeSelectionScreen(this))).dimensions(x, 110, 200, 20).build());
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> this.client.setScreen(null)).dimensions(x, 145, 200, 20).build());
        }

        @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) { this.renderInGameBackground(context); context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFF); super.render(context, mouseX, mouseY, delta); }
    }

    /**
     * The sub-menu for selecting the target attribute or slot.
     */
    public static class AttributeSelectionScreen extends Screen {
        private final Screen parent;
        public AttributeSelectionScreen(Screen parent) { super(Text.literal("Select Attribute")); this.parent = parent; }

        @Override protected void init() {
            int bw = 100, bh = 20, sp = 4, cols = 3;
            int sx = (this.width - ((cols * bw) + ((cols - 1) * sp))) / 2, sy = 60;
            for (int i = 0; i < ATTRIBUTE_OPTIONS.size(); i++) {
                int index = i;
                String label = ATTRIBUTE_OPTIONS.get(i);
                if (label.equals("Specific Slot")) label = "Slot: " + config.targetSlot;

                this.addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
                    if (ATTRIBUTE_OPTIONS.get(index).equals("Specific Slot")) {
                        // Cycle through slots 1 -> 9 -> 1
                        config.targetSlot = (config.targetSlot % 9) + 1;
                        config.attributeIndex = index; // Select directly
                        b.setMessage(Text.literal("Slot: " + config.targetSlot));
                    } else {
                        config.attributeIndex = index;
                        client.setScreen(parent);
                    }
                    saveConfig();
                }).dimensions(sx + (i % cols) * (bw + sp), sy + (i / cols) * (bh + sp), bw, bh).build());
            }
        }

        @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) { this.renderInGameBackground(context); super.render(context, mouseX, mouseY, delta); }
    }

    /**
     * The sub-menu for selecting the trigger mode.
     */
    public static class TriggerSelectionScreen extends Screen {
        private final Screen parent;
        public TriggerSelectionScreen(Screen parent) { super(Text.literal("Select Trigger Mode")); this.parent = parent; }

        @Override protected void init() {
            int bw = 100, bh = 20, sp = 4, cols = 2;
            int sx = (this.width - ((cols * bw) + ((cols - 1) * sp))) / 2, sy = 60;
            for (int i = 0; i < TRIGGER_MODES.size(); i++) {
                int index = i;
                this.addDrawableChild(ButtonWidget.builder(Text.literal(TRIGGER_MODES.get(i)), b -> {
                    config.triggerModeIndex = index;
                    if (TRIGGER_MODES.get(index).equals("Current Item")) {
                        config.customTriggerId = client.player.getMainHandStack().getItem().toString().toLowerCase();
                    }
                    saveConfig();
                    client.setScreen(parent);
                }).dimensions(sx + (i % cols) * (bw + sp), sy + (i / cols) * (bh + sp), bw, bh).build());
            }
        }

        @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) { this.renderInGameBackground(context); super.render(context, mouseX, mouseY, delta); }
    }
}