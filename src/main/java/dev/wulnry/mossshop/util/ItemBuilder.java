package dev.wulnry.mossshop.util;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Config bolumlerinden tam ozellikli {@link ItemStack} insa eder.
 * Custom item destegi: material, amount, display name, lore, enchantments,
 * item flags, custom model data, glow, attribute modifiers ve
 * PersistentDataContainer (NBT) verileri.
 *
 * <p>Reflection veya NMS kullanmaz; tamamen modern Bukkit/Paper API'sine
 * dayanir. Hatali alanlar loglanir ancak item olusturmayi kesmez.</p>
 */
public final class ItemBuilder {

    private ItemBuilder() {
    }

    public static ItemStack fromSection(Plugin plugin, ConfigurationSection section, Map<String, String> placeholders) {
        if (section == null) {
            return null;
        }

        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        if (material == null || material.isAir()) {
            plugin.getLogger().warning("Gecersiz material: " + section.getString("material") + " (yol: " + section.getCurrentPath() + ")");
            return null;
        }

        int amount = Math.max(1, section.getInt("amount", 1));
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        // Display name
        if (section.isString("name")) {
            String name = Text.apply(section.getString("name"), placeholders);
            meta.displayName(Text.color(name));
        }

        // Lore
        if (section.isList("lore")) {
            List<String> raw = Text.apply(section.getStringList("lore"), placeholders);
            List<Component> lore = Text.color(raw);
            meta.lore(lore);
        }

        // Enchantments
        ConfigurationSection enchants = section.getConfigurationSection("enchantments");
        if (enchants != null) {
            for (String key : enchants.getKeys(false)) {
                Enchantment enchantment = resolveEnchantment(key);
                if (enchantment == null) {
                    plugin.getLogger().warning("Gecersiz enchantment: " + key + " (yol: " + section.getCurrentPath() + ")");
                    continue;
                }
                meta.addEnchant(enchantment, Math.max(1, enchants.getInt(key, 1)), true);
            }
        }

        // Item flags
        for (String flagName : section.getStringList("flags")) {
            try {
                meta.addItemFlags(ItemFlag.valueOf(flagName.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Gecersiz item flag: " + flagName + " (yol: " + section.getCurrentPath() + ")");
            }
        }

        // Custom model data
        if (section.isInt("custom-model-data")) {
            meta.setCustomModelData(section.getInt("custom-model-data"));
        }

        // Glow (enchant glint override - flag/enchant gerektirmez)
        if (section.getBoolean("glow", false)) {
            meta.setEnchantmentGlintOverride(true);
        }

        // Attribute modifiers
        applyAttributes(plugin, section.getConfigurationSection("attributes"), meta);

        // Persistent data (NBT)
        applyPersistentData(plugin, section.getConfigurationSection("persistent-data"), meta);

        item.setItemMeta(meta);
        return item;
    }

    private static Enchantment resolveEnchantment(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        NamespacedKey namespacedKey = normalized.contains(":")
                ? NamespacedKey.fromString(normalized)
                : NamespacedKey.minecraft(normalized);
        if (namespacedKey == null) {
            return null;
        }
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(namespacedKey);
    }

    private static void applyAttributes(Plugin plugin, ConfigurationSection attributes, ItemMeta meta) {
        if (attributes == null) {
            return;
        }
        for (String attrName : attributes.getKeys(false)) {
            try {
                NamespacedKey attrKey = NamespacedKey.minecraft(attrName.toLowerCase(Locale.ROOT));
                Attribute attribute = RegistryAccess.registryAccess().getRegistry(RegistryKey.ATTRIBUTE).get(attrKey);
                if (attribute == null) {
                    plugin.getLogger().warning("Gecersiz attribute: " + attrName);
                    continue;
                }
                List<Map<?, ?>> modifiers = attributes.getMapList(attrName);
                int index = 0;
                for (Map<?, ?> modifier : modifiers) {
                    double value = toDouble(modifier.get("amount"), 0.0D);
                    AttributeModifier.Operation operation = parseOperation(String.valueOf(modifier.get("operation")));
                    EquipmentSlotGroup slotGroup = parseSlotGroup(String.valueOf(modifier.get("slot")));
                    NamespacedKey modifierKey = new NamespacedKey(plugin, "mossshop_" + attrName.toLowerCase(Locale.ROOT) + "_" + index++);
                    meta.addAttributeModifier(attribute, new AttributeModifier(modifierKey, value, operation, slotGroup));
                }
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Attribute uygulanamadi: " + attrName, ex);
            }
        }
    }

    private static void applyPersistentData(Plugin plugin, ConfigurationSection data, ItemMeta meta) {
        if (data == null) {
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        for (String keyString : data.getKeys(false)) {
            ConfigurationSection entry = data.getConfigurationSection(keyString);
            if (entry == null) {
                continue;
            }
            NamespacedKey key = NamespacedKey.fromString(keyString.toLowerCase(Locale.ROOT), plugin);
            if (key == null) {
                plugin.getLogger().warning("Gecersiz persistent-data anahtari: " + keyString);
                continue;
            }
            String type = entry.getString("type", "STRING").toUpperCase(Locale.ROOT);
            try {
                switch (type) {
                    case "STRING" -> container.set(key, PersistentDataType.STRING, entry.getString("value", ""));
                    case "INTEGER", "INT" -> container.set(key, PersistentDataType.INTEGER, entry.getInt("value"));
                    case "LONG" -> container.set(key, PersistentDataType.LONG, entry.getLong("value"));
                    case "DOUBLE" -> container.set(key, PersistentDataType.DOUBLE, entry.getDouble("value"));
                    case "FLOAT" -> container.set(key, PersistentDataType.FLOAT, (float) entry.getDouble("value"));
                    case "SHORT" -> container.set(key, PersistentDataType.SHORT, (short) entry.getInt("value"));
                    case "BYTE" -> container.set(key, PersistentDataType.BYTE, (byte) entry.getInt("value"));
                    case "BOOLEAN", "BOOL" -> container.set(key, PersistentDataType.BYTE, (byte) (entry.getBoolean("value") ? 1 : 0));
                    default -> plugin.getLogger().warning("Desteklenmeyen persistent-data tipi: " + type);
                }
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Persistent-data uygulanamadi: " + keyString, ex);
            }
        }
    }

    private static AttributeModifier.Operation parseOperation(String name) {
        if (name == null) {
            return AttributeModifier.Operation.ADD_NUMBER;
        }
        try {
            return AttributeModifier.Operation.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return AttributeModifier.Operation.ADD_NUMBER;
        }
    }

    private static EquipmentSlotGroup parseSlotGroup(String name) {
        if (name == null || name.isBlank()) {
            return EquipmentSlotGroup.ANY;
        }
        EquipmentSlotGroup group = EquipmentSlotGroup.getByName(name.toLowerCase(Locale.ROOT));
        return group != null ? group : EquipmentSlotGroup.ANY;
    }

    private static double toDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
