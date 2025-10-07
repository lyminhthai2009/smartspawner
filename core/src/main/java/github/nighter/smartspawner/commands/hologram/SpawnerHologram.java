package github.nighter.smartspawner.commands.hologram;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.ColorUtil;
import github.nighter.smartspawner.language.LanguageManager;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class SpawnerHologram {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final AtomicReference<TextDisplay> textDisplay = new AtomicReference<>(null);
    private final Location spawnerLocation;
    private int stackSize;
    private EntityType entityType;
    private int currentExp;
    private int maxExp;
    private int currentItems;
    private int maxSlots;
    private static final String HOLOGRAM_IDENTIFIER = "SmartSpawner-Holo";
    private final String uniqueIdentifier;

    private static final Vector3f SCALE = new Vector3f(1.0f, 1.0f, 1.0f);
    private static final Vector3f TRANSLATION = new Vector3f(0.0f, 0.0f, 0.0f);
    private static final AxisAngle4f ROTATION = new AxisAngle4f(0, 0, 0, 0);

    public SpawnerHologram(Location location) {
        this.plugin = SmartSpawner.getInstance();
        this.spawnerLocation = location;
        this.languageManager = plugin.getLanguageManager();
        this.uniqueIdentifier = generateUniqueIdentifier(location);
    }

    private String generateUniqueIdentifier(Location location) {
        return HOLOGRAM_IDENTIFIER + "-" +
                location.getWorld().getName() + "-" +
                location.getBlockX() + "-" +
                location.getBlockY() + "-" +
                location.getBlockZ();
    }

    public void createHologram() {
        if (spawnerLocation == null || spawnerLocation.getWorld() == null) return;

        // Clean up any existing hologram for this spawner first
        cleanupExistingHologram();

        double offsetX = plugin.getConfig().getDouble("hologram.offset_x", 0.5);
        double offsetY = plugin.getConfig().getDouble("hologram.offset_y", 0.5);
        double offsetZ = plugin.getConfig().getDouble("hologram.offset_z", 0.5);

        Location holoLoc = spawnerLocation.clone().add(offsetX, offsetY, offsetZ);

        // Use the location scheduler to spawn the entity in the correct region
        // *** ĐOẠN CODE ĐÃ ĐƯỢC SỬA LỖI BẮT ĐẦU TỪ ĐÂY ***
        Scheduler.runLocationTask(holoLoc, () -> {
            try {
                // Bước 1: Tạo entity TextDisplay trước
                TextDisplay display = spawnerLocation.getWorld().spawn(holoLoc, TextDisplay.class);

                // Bước 2: Cấu hình các thuộc tính của entity sau khi đã tạo
                display.setBillboard(Display.Billboard.CENTER);
                
                String alignmentStr = plugin.getConfig().getString("hologram.alignment", "CENTER");
                TextDisplay.TextAlignment alignment;
                try {
                    alignment = TextDisplay.TextAlignment.valueOf(alignmentStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    alignment = TextDisplay.TextAlignment.CENTER;
                    plugin.getLogger().warning("Invalid hologram alignment in config: " + alignmentStr + ". Using CENTER as default.");
                }
                display.setAlignment(alignment);
                display.setViewRange(16.0f);
                display.setShadowed(plugin.getConfig().getBoolean("hologram.shadowed_text", true));
                display.setDefaultBackground(false);
                display.setTransformation(new Transformation(TRANSLATION, ROTATION, SCALE, ROTATION));
                display.setSeeThrough(plugin.getConfig().getBoolean("hologram.see_through", false));
                display.setCustomName(uniqueIdentifier);
                display.setCustomNameVisible(false);

                // Bước 3: Lưu lại và cập nhật text
                textDisplay.set(display);
                updateText();
            } catch (Exception e) {
                plugin.getLogger().severe("Error creating hologram: " + e.getMessage());
                e.printStackTrace();
            }
        });
        // *** KẾT THÚC ĐOẠN CODE SỬA LỖI ***
    }

    public void updateText() {
        TextDisplay display = textDisplay.get();
        if (display == null || entityType == null) return;

        // Don't check isValid() here as it needs to be on the entity thread

        // Prepare the text content outside of the entity thread
        String entityTypeName = languageManager.getFormattedMobName(entityType);

        // Create replacements map
        Map<String, String> replacements = new HashMap<>();
        replacements.put("%entity%", entityTypeName);
        replacements.put("%ᴇɴᴛɪᴛʏ%", languageManager.getSmallCaps(entityTypeName));
        replacements.put("%stack_size%", String.valueOf(stackSize));
        replacements.put("%current_exp%", languageManager.formatNumber(currentExp));
        replacements.put("%max_exp%", languageManager.formatNumber(maxExp));
        replacements.put("%used_slots%", languageManager.formatNumber(currentItems));
        replacements.put("%max_slots%", languageManager.formatNumber(maxSlots));

        String hologramText = languageManager.getHologramText();

        // Apply replacements
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            hologramText = hologramText.replace(entry.getKey(), entry.getValue());
        }

        // Apply color codes
        final String finalText = ColorUtil.translateHexColorCodes(hologramText);

        // Schedule the entity update on the entity's thread
        Scheduler.runEntityTask(display, () -> {
            if (display.isValid()) {
                display.setText(finalText);
            }
        });
    }

    public void updateData(int stackSize, EntityType entityType, int currentExp, int maxExp, int currentItems, int maxSlots) {
        // First, ensure we have a valid hologram
        TextDisplay display = textDisplay.get();
        if (display == null || !display.isValid()) {
            // If hologram doesn't exist or is invalid, recreate it
            createHologram();
        }

        // Update data values
        this.stackSize = stackSize;
        this.entityType = entityType;
        this.currentExp = currentExp;
        this.maxExp = maxExp;
        this.currentItems = currentItems;
        this.maxSlots = maxSlots;

        // Update the text display
        updateText();
    }

    public void remove() {
        TextDisplay display = textDisplay.get();
        if (display != null && display.isValid()) {
            // Run on the entity's thread
            Scheduler.runEntityTask(display, display::remove);
            textDisplay.set(null);
        }
    }

    public void cleanupExistingHologram() {
        if (spawnerLocation == null || spawnerLocation.getWorld() == null) return;

        // First, check if our tracked hologram is still valid
        TextDisplay display = textDisplay.get();
        if (display != null) {
            if (display.isValid()) {
                // If it's valid but we're cleaning up, remove it
                display.remove();
            }
            textDisplay.set(null);
        }

        Scheduler.runLocationTask(spawnerLocation, () -> {
            // Define a tighter search radius just to catch any potentially duplicated holograms
            // with the same identifier (which shouldn't happen but being safe)
            double searchRadius = 2.0;

            // Look for any entity with our specific unique identifier
            spawnerLocation.getWorld().getNearbyEntities(spawnerLocation, searchRadius, searchRadius, searchRadius)
                    .stream()
                    .filter(entity -> entity instanceof TextDisplay && entity.getCustomName() != null)
                    .filter(entity -> entity.getCustomName().equals(uniqueIdentifier))
                    .forEach(Entity::remove);
        });
    }
}