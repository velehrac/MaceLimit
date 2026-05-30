package cz.maceLimit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

public class MaceLimit extends JavaPlugin implements Listener {

    private static final Set<String> BLOCKED_NAMES = Set.of(
        "Copper Helmet",
        "Copper Chestplate",
        "Copper Leggings",
        "Copper Boots",
        "Copper Pickaxe"
    );

    private static final Set<Material> SHULKER_MATERIALS = Set.of(
        Material.SHULKER_BOX,
        Material.WHITE_SHULKER_BOX, Material.ORANGE_SHULKER_BOX,
        Material.MAGENTA_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX,
        Material.YELLOW_SHULKER_BOX, Material.LIME_SHULKER_BOX,
        Material.PINK_SHULKER_BOX, Material.GRAY_SHULKER_BOX,
        Material.LIGHT_GRAY_SHULKER_BOX, Material.CYAN_SHULKER_BOX,
        Material.PURPLE_SHULKER_BOX, Material.BLUE_SHULKER_BOX,
        Material.BROWN_SHULKER_BOX, Material.GREEN_SHULKER_BOX,
        Material.RED_SHULKER_BOX, Material.BLACK_SHULKER_BOX
    );

    private static final Set<InventoryType> ALLOWED_TYPES = Set.of(
        InventoryType.PLAYER,
        InventoryType.CRAFTING
    );

    private static final int MAX_TOTEMS = 3;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("MaceLimit enabled");
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getRecipe().getResult();
        if (result.getType() != Material.MACE) return;
        if (maceExistsOnServer()) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage("§cMace uz existuje na serveru! Muze byt jen 1.");
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // --- Kontrola totemu ---
        // Hrac presouva item DO sveho inventare
        ItemStack incomingItem = null;

        if (event.isShiftClick()
                && event.getClickedInventory() != null
                && event.getClickedInventory().getType() != InventoryType.PLAYER
                && event.getInventory().getType() == InventoryType.PLAYER) {
            incomingItem = event.getCurrentItem();
        } else if (event.getClickedInventory() != null
                && event.getClickedInventory().getType() == InventoryType.PLAYER
                && event.getCursor() != null
                && event.getCursor().getType() == Material.TOTEM_OF_UNDYING) {
            incomingItem = event.getCursor();
        }

        if (incomingItem != null && incomingItem.getType() == Material.TOTEM_OF_UNDYING) {
            int count = countTotemsInInventory(player);
            if (count >= MAX_TOTEMS) {
                event.setCancelled(true);
                player.sendMessage("§cNemuzete mit vice nez " + MAX_TOTEMS + " totemy v inventari!");
                return;
            }
        }

        // --- Blokování copper/mace do kontejnerů ---
        ItemStack item = null;

        if (event.isShiftClick()
                && event.getClickedInventory() != null
                && event.getClickedInventory().getType() == InventoryType.PLAYER
                && !ALLOWED_TYPES.contains(event.getInventory().getType())) {
            item = event.getCurrentItem();
        } else if (event.getClickedInventory() != null
                && !ALLOWED_TYPES.contains(event.getClickedInventory().getType())
                && event.getClickedInventory().getType() != InventoryType.PLAYER) {
            item = event.getCursor();
            if (item == null || item.getType() == Material.AIR) {
                item = event.getCurrentItem();
            }
        } else {
            return;
        }

        if (item == null || item.getType() == Material.AIR) return;

        if (isBlocked(item)) {
            event.setCancelled(true);
            player.sendMessage("§cTuto vec nelze ulozit do zadneho kontejneru!");
        }
    }

    private int countTotemsInInventory(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.TOTEM_OF_UNDYING) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private boolean isBlocked(ItemStack item) {
        if (item == null) return false;
        if (item.getType() == Material.MACE) return true;
        if (isBlockedAltarItem(item)) return true;

        if (SHULKER_MATERIALS.contains(item.getType())) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof BlockStateMeta bsm) {
                if (bsm.getBlockState() instanceof ShulkerBox shulker) {
                    for (ItemStack content : shulker.getInventory().getContents()) {
                        if (content == null) continue;
                        if (content.getType() == Material.MACE) return true;
                        if (isBlockedAltarItem(content)) return true;
                    }
                }
            }
        }

        if (item.getType() == Material.BUNDLE) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof BundleMeta bundle) {
                for (ItemStack content : bundle.getItems()) {
                    if (content == null) continue;
                    if (content.getType() == Material.MACE) return true;
                    if (isBlockedAltarItem(content)) return true;
                }
            }
        }

        return false;
    }

    private boolean isBlockedAltarItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        String name = org.bukkit.ChatColor.stripColor(meta.getDisplayName());
        return BLOCKED_NAMES.contains(name);
    }

    public boolean maceExistsOnServer() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == Material.MACE) return true;
            }
            for (ItemStack item : player.getEnderChest().getContents()) {
                if (item != null && item.getType() == Material.MACE) return true;
            }
        }
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                for (org.bukkit.block.BlockState state : chunk.getTileEntities()) {
                    if (state instanceof org.bukkit.block.Container container) {
                        for (ItemStack item : container.getInventory().getContents()) {
                            if (item != null && item.getType() == Material.MACE) return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
