package cz.maceLimit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

public class MaceLimit extends JavaPlugin implements Listener {

    private static final int MAX_TOTEMS = 3;

    private static final Set<Material> SHULKER_MATERIALS = Set.of(
            Material.SHULKER_BOX, Material.WHITE_SHULKER_BOX, Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX, Material.YELLOW_SHULKER_BOX,
            Material.LIME_SHULKER_BOX, Material.PINK_SHULKER_BOX, Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX, Material.CYAN_SHULKER_BOX, Material.PURPLE_SHULKER_BOX,
            Material.BLUE_SHULKER_BOX, Material.BROWN_SHULKER_BOX, Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX, Material.BLACK_SHULKER_BOX
    );

    private static final Set<String> BLOCKED_NAMES = Set.of(
            "Copper Helmet", "Copper Chestplate", "Copper Leggings", "Copper Boots", "Copper Pickaxe"
    );

    private static final Set<InventoryType> ALLOWED_TYPES = Set.of(InventoryType.CRAFTING);

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("MaceLimit enabled");
    }

    // -----------------------------------------------------------------------
    // BUG 2 FIX: maceExistsOnServer nyní kontroluje i dropped items na zemi
    // -----------------------------------------------------------------------
    private boolean maceExistsOnServer() {
        // Kontrola inventářů všech online hráčů
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (containsMace(p.getInventory().getContents())) return true;
            if (containsMace(p.getEnderChest().getContents())) return true;
        }
        // Kontrola všech kontejnerů ve světě (chesty, barely, shulker boxy...)
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                for (org.bukkit.block.BlockState state : chunk.getTileEntities()) {
                    if (state instanceof Container container) {
                        if (containsMace(container.getInventory().getContents())) return true;
                    }
                }
            }
            // BUG 2 FIX: kontrola dropped items (entity na zemi)
            for (Item entity : world.getEntitiesByClass(Item.class)) {
                if (entity.getItemStack().getType() == Material.MACE) return true;
            }
        }
        return false;
    }

    private boolean containsMace(ItemStack[] contents) {
        if (contents == null) return false;
        for (ItemStack item : contents) {
            if (item != null && item.getType() == Material.MACE) return true;
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Crafting mace — blokuje pokud mace existuje kdekoliv (včetně na zemi)
    // -----------------------------------------------------------------------
    @EventHandler
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getRecipe().getResult();
        if (result.getType() != Material.MACE) return;

        if (maceExistsOnServer()) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            player.sendMessage(ChatColor.RED + "Mace uz existuje na serveru! Muze byt jen 1.");
            return;
        }

        // Shift-click by dal hráči celý stack — vynutit amount = 1
        if (event.isShiftClick()) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            // Ručně přidat 1 mace a spotřebovat ingredience
            CraftingInventory craftInv = event.getInventory();
            for (int i = 1; i < craftInv.getSize(); i++) {
                ItemStack ingredient = craftInv.getItem(i);
                if (ingredient != null) {
                    ingredient.setAmount(ingredient.getAmount() - 1);
                    craftInv.setItem(i, ingredient.getAmount() <= 0 ? null : ingredient);
                }
            }
            player.getInventory().addItem(new ItemStack(Material.MACE, 1));
            player.sendMessage(ChatColor.GREEN + "Vycraftil jsi Mace!");
        }
    }

    // -----------------------------------------------------------------------
    // Pickup mace ze země
    // -----------------------------------------------------------------------
    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getItem().getItemStack().getType() != Material.MACE) return;
        // Pickup je OK — mace smí být sebrana (je to ta jedna povolená)
    }

    // -----------------------------------------------------------------------
    // Ender perly zakázány
    // -----------------------------------------------------------------------
    @EventHandler
    public void onEnderPearlUse(PlayerInteractEvent event) {
        if (event.getItem() == null) return;
        if (event.getItem().getType() == Material.ENDER_PEARL) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Ender perly jsou na tomto serveru zakazany!");
        }
    }

    // -----------------------------------------------------------------------
    // BUG 1 + BUG 3 FIX: Kontrola totemů při klikání v inventáři
    //
    // Původní kód kontroloval jen InventoryType.PLAYER → nezachytil přesuny
    // z chestu (Shift+click, klávesa E). Nyní kontrolujeme všechny akce,
    // které by mohly přidat totem do hráčova inventáře.
    // -----------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // --- Kontrola blokovaných předmětů v kontejnerech ---
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        // Zablokování vkládání blokovaných itemů do kontejnerů
        if (event.getClickedInventory() != null
                && event.getClickedInventory().getType() != InventoryType.PLAYER
                && event.getClickedInventory().getType() != InventoryType.CRAFTING) {
            if (cursor != null && isBlocked(cursor)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Tuto vec nelze ulozit do zadneho kontejneru!");
                return;
            }
        }

        // Zablokování vkládání do shulker boxu / bundlu
        if (current != null && SHULKER_MATERIALS.contains(current.getType())) {
            if (cursor != null && isBlockedAltarItem(cursor)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Tuto vec nelze ulozit do zadneho kontejneru!");
                return;
            }
        }

        // --- BUG 1 + BUG 3 FIX: Kontrola totemů ---
        // Počítáme, kolik totemů by hráč měl PO akci
        ItemStack incoming = null;

        InventoryAction action = event.getAction();

        // Akce, které přidají item z externího inventáře (chest, atd.) do hráčova inventáře
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            // Shift+click — přesune current item do hráčova inventáře
            if (event.getClickedInventory() != null
                    && event.getClickedInventory().getType() != InventoryType.PLAYER) {
                incoming = current;
            }
        } else if (action == InventoryAction.PICKUP_ALL
                || action == InventoryAction.PICKUP_HALF
                || action == InventoryAction.PICKUP_ONE
                || action == InventoryAction.PICKUP_SOME) {
            // Vzít item do kurzoru — ale jen pokud klikáme mimo hráčův inventář
            if (event.getClickedInventory() != null
                    && event.getClickedInventory().getType() != InventoryType.PLAYER) {
                incoming = current;
            }
        } else if (action == InventoryAction.HOTBAR_MOVE_AND_READD
                || action == InventoryAction.HOTBAR_SWAP) {
            incoming = current;
        } else if (action == InventoryAction.PLACE_ALL
                || action == InventoryAction.PLACE_ONE
                || action == InventoryAction.PLACE_SOME
                || action == InventoryAction.SWAP_WITH_CURSOR) {
            // Pokládáme cursor do hráčova inventáře
            if (event.getClickedInventory() != null
                    && event.getClickedInventory().getType() == InventoryType.PLAYER) {
                incoming = cursor;
            }
        }

        if (incoming == null || incoming.getType() != Material.TOTEM_OF_UNDYING) return;

        // Spočítáme totemy, které hráč aktuálně má
        int currentCount = countTotemsInInventory(player);

        if (currentCount >= MAX_TOTEMS) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Nemuzete mit vice nez " + MAX_TOTEMS + " totemy v inventari!");
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
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String name = ChatColor.stripColor(meta.getDisplayName());
            if (BLOCKED_NAMES.contains(name)) return true;
        }
        return false;
    }

    private boolean isBlockedAltarItem(ItemStack item) {
        return isBlocked(item);
    }
}
