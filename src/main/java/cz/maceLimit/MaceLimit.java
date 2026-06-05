package cz.maceLimit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
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

    // ALLOWED_TYPES = {PLAYER, CRAFTING} — typy inventáře kde lze klikat bez blokace kontejner-check
    private static final Set<InventoryType> ALLOWED_TYPES = Set.of(
            InventoryType.PLAYER, InventoryType.CRAFTING
    );

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("MaceLimit enabled");
    }

    // -----------------------------------------------------------------------
    // maceExistsOnServer — kontroluje inventáře hráčů, ender chesty,
    // všechny kontejnery ve světě A dropped items na zemi
    // -----------------------------------------------------------------------
    private boolean maceExistsOnServer() {
        // Inventáře online hráčů + ender chesty
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (containsMace(p.getInventory().getContents())) return true;
            if (containsMace(p.getEnderChest().getContents())) return true;
        }
        // Kontejnery a dropped items ve světech
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof Container container) {
                        if (containsMace(container.getInventory().getContents())) return true;
                    }
                }
            }
            // FIX: kontrola mace ležící na zemi jako dropped item entity
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
    // onCraft — blokuje craftění mace pokud již existuje
    // Zachytává i shift-click (dal by celý stack → vynutíme 1 kus)
    // -----------------------------------------------------------------------
    @EventHandler
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getRecipe().getResult();
        if (result.getType() != Material.MACE) return;

        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (maceExistsOnServer()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Mace uz existuje na serveru! Muze byt jen 1.");
            return;
        }

        // Shift-click by craftil celý stack — zrušíme event, ručně přidáme 1 kus
        if (event.isShiftClick()) {
            event.setCancelled(true);
            CraftingInventory craftInv = event.getInventory();
            // Spotřebuj ingredience (slot 0 je výsledek, 1+ jsou ingredience)
            for (int i = 1; i < craftInv.getSize(); i++) {
                ItemStack ingredient = craftInv.getItem(i);
                if (ingredient != null && ingredient.getType() != Material.AIR) {
                    ingredient.setAmount(ingredient.getAmount() - 1);
                }
            }
            player.getInventory().addItem(new ItemStack(Material.MACE, 1));
            player.sendMessage(ChatColor.GREEN + "Vycraftil jsi Mace!");
        }
    }

    // -----------------------------------------------------------------------
    // FIX: PrepareItemCraftEvent — zablokuje i auto-craftery
    // Tento event se spustí PŘED CraftItemEvent, takže ho zachytí dřív
    // než auto-crafter stihne sebrat výsledek
    // -----------------------------------------------------------------------
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType() != Material.MACE) return;

        if (maceExistsOnServer()) {
            // Vymaž výsledek z crafting gridu → auto-crafter nemá co vzít
            event.getInventory().setResult(new ItemStack(Material.AIR));
        }
    }

    // -----------------------------------------------------------------------
    // onPickup — kontrola limitu totemů při sbírání ze země
    // -----------------------------------------------------------------------
    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getItem().getItemStack().getType() != Material.TOTEM_OF_UNDYING) return;

        int count = countTotemsInInventory(player);
        if (count >= MAX_TOTEMS) {
            event.setCancelled(true);
        }
    }

    // -----------------------------------------------------------------------
    // onEnderPearlUse — ender perly zakázány
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
    // onInventoryClick — opravená logika pro totemy
    //
    // Originální kód měl bug: při shift-clicku Z chestu kontroloval
    // getClickedInventory().getType() == PLAYER, ale chest vrátí CHEST,
    // takže podmínka selžela a totem prošel.
    //
    // Oprava: kontrolujeme kam totem PŮJDE (do hráčova inventáře),
    // ne odkud přichází. Spočítáme aktuální počet a zablokujeme pokud >= 3.
    // -----------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        // --- Blokace vkládání blokovaných itemů do kontejnerů ---
        // Pokud klikáme DO kontejneru (ne do player/crafting inventáře)
        // a kurzor drží blokovaný item → zablokovat
        if (event.getClickedInventory() != null
                && !ALLOWED_TYPES.contains(event.getClickedInventory().getType())) {
            if (cursor != null && cursor.getType() != Material.AIR && isBlocked(cursor)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Tuto vec nelze ulozit do zadneho kontejneru!");
                return;
            }
        }

        // Shift-click z hráčova inventáře DO kontejneru — blokovat blokované itemy
        if (event.isShiftClick()
                && event.getClickedInventory() != null
                && event.getClickedInventory().getType() == InventoryType.PLAYER) {
            // Cílový inventář je getInventory() (ten druhý)
            if (!ALLOWED_TYPES.contains(event.getInventory().getType())) {
                if (current != null && isBlocked(current)) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "Tuto vec nelze ulozit do zadneho kontejneru!");
                    return;
                }
            }
        }

        // --- Kontrola limitu totemů ---
        // Zjistíme, jestli by tato akce přidala totem do hráčova inventáře
        ItemStack incoming = getIncomingToPlayer(event);
        if (incoming == null || incoming.getType() != Material.TOTEM_OF_UNDYING) return;

        int currentCount = countTotemsInInventory(player);
        int incomingAmount = incoming.getAmount();

        if (currentCount + incomingAmount > MAX_TOTEMS) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Nemuzete mit vice nez " + MAX_TOTEMS + " totemy v inventari!");
        }
    }

    /**
     * Vrátí ItemStack který by šel DO hráčova inventáře touto akcí,
     * nebo null pokud akce nepřidává nic hráči.
     */
    private ItemStack getIncomingToPlayer(InventoryClickEvent event) {
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return null;

        boolean clickedIsPlayer = clicked.getType() == InventoryType.PLAYER;
        boolean clickedIsExternal = !ALLOWED_TYPES.contains(clicked.getType());

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        // Shift-click z externího inventáře (chest, barrel...) → jde do hráče
        if (event.isShiftClick() && clickedIsExternal) {
            return current;
        }

        // Shift-click z hráčova inventáře → jde DO externího, ne do hráče → ignorovat
        if (event.isShiftClick() && clickedIsPlayer) {
            return null;
        }

        // Normální klik — pokládáme cursor do hráčova inventáře
        if (!event.isShiftClick() && clickedIsPlayer) {
            return (cursor != null && cursor.getType() != Material.AIR) ? cursor : null;
        }

        // Klik do externího inventáře — kurzor jde do chestu, ne do hráče
        return null;
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
        // Mace je vždy blokovaná (nelze dávat do kontejnerů)
        if (item.getType() == Material.MACE) return true;
        // Blokace altar itemů (copper equipment)
        if (isBlockedAltarItem(item)) return true;
        // Blokace uvnitř shulker boxů
        if (SHULKER_MATERIALS.contains(item.getType())) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof BlockStateMeta bsm) {
                if (bsm.getBlockState() instanceof ShulkerBox shulker) {
                    for (ItemStack content : shulker.getInventory().getContents()) {
                        if (content != null && content.getType() == Material.MACE) return true;
                        if (content != null && isBlockedAltarItem(content)) return true;
                    }
                }
            }
        }
        // Blokace uvnitř bundlů
        if (item.getType() == Material.BUNDLE) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof BundleMeta bundle) {
                for (ItemStack content : bundle.getItems()) {
                    if (content != null && content.getType() == Material.MACE) return true;
                    if (content != null && isBlockedAltarItem(content)) return true;
                }
            }
        }
        return false;
    }

    private boolean isBlockedAltarItem(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        String name = ChatColor.stripColor(meta.getDisplayName());
        return BLOCKED_NAMES.contains(name);
    }
}
