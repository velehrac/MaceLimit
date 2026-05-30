package cz.maceLimit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
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
        if (event.getInventory().getType() != InventoryType.ENDER_CHEST) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack item = event.getCursor();
        if (item == null || item.getType() == Material.AIR) {
            item = event.getCurrentItem();
        }
        if (item == null) return;

        if (item.getType() == Material.MACE || isBlockedAltarItem(item)) {
            event.setCancelled(true);
            player.sendMessage("§cTuto vec nelze dat do ender chestu!");
        }
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
