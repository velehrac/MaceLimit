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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

public class MaceLimit extends JavaPlugin implements Listener {

    private static final Set<Material> BLOCKED_ITEMS = Set.of(
        Material.MACE,
        Material.COPPER_HELMET,
        Material.COPPER_CHESTPLATE,
        Material.COPPER_LEGGINGS,
        Material.COPPER_BOOTS,
        Material.COPPER_PICKAXE
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

        if (BLOCKED_ITEMS.contains(item.getType())) {
            event.setCancelled(true);
            player.sendMessage("§cTuto vec nelze dat do ender chestu!");
        }
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
