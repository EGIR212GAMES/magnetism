package ru.magnetism.magnet;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import ru.magnetism.config.ModConfig;
import ru.magnetism.util.ModLogger;

import java.util.concurrent.ConcurrentHashMap;

public final class MagneticWeightResolver {
    private MagneticWeightResolver() {
    }

    // Debug logging cooldown: track last log tick per entity/player
    private static final ConcurrentHashMap<String, Long> LAST_ITEM_LOG_TICK = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> LAST_PLAYER_LOG_TICK = new ConcurrentHashMap<>();
    private static final int DEBUG_LOG_COOLDOWN_TICKS = 20;

    public static double playerWeight(Player player) {
        double weight = 0.0D;
        double headWeight = armorPieceWeight(player.getItemBySlot(EquipmentSlot.HEAD));
        double chestWeight = armorPieceWeight(player.getItemBySlot(EquipmentSlot.CHEST));
        double legsWeight = armorPieceWeight(player.getItemBySlot(EquipmentSlot.LEGS));
        double feetWeight = armorPieceWeight(player.getItemBySlot(EquipmentSlot.FEET));
        weight = headWeight + chestWeight + legsWeight + feetWeight;

        maybeLogPlayerArmor(player, headWeight, chestWeight, legsWeight, feetWeight, weight);
        return weight;
    }

    public static double armorPieceWeight(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0D;
        }

        ModConfig.MagnetConfig config = ModConfig.get().magnet();

        if (stack.is(MagnetTags.NETHERITE_ARMOR)) {
            return config.netheriteArmorWeight();
        }
        if (stack.is(MagnetTags.DIAMOND_ARMOR)) {
            return config.diamondArmorWeight();
        }
        if (stack.is(MagnetTags.IRON_ARMOR)) {
            return config.ironArmorWeight();
        }
        if (stack.is(MagnetTags.CHAINMAIL_ARMOR)) {
            return config.chainmailArmorWeight();
        }

        return 0.0D;
    }

    public static double itemEntityWeight(ItemStack stack) {
        if (stack.isEmpty() || !isMagneticItem(stack)) {
            return 0.0D;
        }

        ModConfig.MagnetConfig config = ModConfig.get().magnet();
        double countScale = Math.sqrt(Math.max(1, stack.getCount()));
        double weight = config.itemBaseWeight() * countScale;

        maybeLogItemEntity(stack, weight);
        return weight;
    }

    /**
     * Keep dedicated material/spear tags authoritative even if the aggregate
     * magnetism:magnetic_items tag is not populated as expected after a data
     * reload. This also keeps the magnetic classification fully data-driven.
     */
    private static boolean isMagneticItem(ItemStack stack) {
        return stack.is(MagnetTags.MAGNETIC_ITEMS)
                || stack.is(MagnetTags.MAGNETIC_IRON)
                || stack.is(MagnetTags.MAGNETIC_DIAMOND)
                || stack.is(MagnetTags.MAGNETIC_NETHERITE)
                || stack.is(MagnetTags.MAGNETIC_ANCIENT_DEBRIS)
                || stack.is(MagnetTags.MAGNETIC_SPEARS);
    }

    private static void maybeLogItemEntity(ItemStack stack, double weight) {
        ModConfig.MagnetConfig config = ModConfig.get().magnet();
        if (!config.debugLogging()) {
            return;
        }
        long currentTick = ru.magnetism.server.ServerTickManager.getTickCount();
        String key = stack.getItem().toString() + "_" + stack.getCount();
        Long lastTick = LAST_ITEM_LOG_TICK.get(key);
        if (lastTick == null || currentTick - lastTick >= DEBUG_LOG_COOLDOWN_TICKS) {
            LAST_ITEM_LOG_TICK.put(key, currentTick);
            ModLogger.debug("[MAGNET] Magnetic item detected: item={} count={} weight={}",
                    stack.getItem().toString(), stack.getCount(), weight);
        }
    }

    private static void maybeLogPlayerArmor(Player player,
                                             double headWeight, double chestWeight,
                                             double legsWeight, double feetWeight,
                                             double totalWeight) {
        ModConfig.MagnetConfig config = ModConfig.get().magnet();
        if (!config.debugLogging()) {
            return;
        }
        long currentTick = ru.magnetism.server.ServerTickManager.getTickCount();
        String key = player.getUUID().toString();
        Long lastTick = LAST_PLAYER_LOG_TICK.get(key);
        if (lastTick == null || currentTick - lastTick >= DEBUG_LOG_COOLDOWN_TICKS) {
            LAST_PLAYER_LOG_TICK.put(key, currentTick);

            // Determine armor types for debug output
            int chainmail = 0, iron = 0, diamond = 0, netherite = 0;
            for (ItemStack stack : new ItemStack[]{
                    player.getItemBySlot(EquipmentSlot.HEAD),
                    player.getItemBySlot(EquipmentSlot.CHEST),
                    player.getItemBySlot(EquipmentSlot.LEGS),
                    player.getItemBySlot(EquipmentSlot.FEET)
            }) {
                if (stack.is(MagnetTags.CHAINMAIL_ARMOR)) chainmail++;
                else if (stack.is(MagnetTags.IRON_ARMOR)) iron++;
                else if (stack.is(MagnetTags.DIAMOND_ARMOR)) diamond++;
                else if (stack.is(MagnetTags.NETHERITE_ARMOR)) netherite++;
            }

            ModLogger.debug("[MAGNET] Magnetic armor: player={} chainmail={} iron={} diamond={} netherite={} totalWeight={}",
                    player.getName().getString(), chainmail, iron, diamond, netherite, totalWeight);
        }
    }
}
