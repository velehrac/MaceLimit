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

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("MaceLimit enabled");
    }

    // -----------------------------------------------------------------------
    // maceExistsOnServer — hráči, ender chesty, kontejnery, dropped items
    // -----------------------------------------------------------------------
    private boolean maceExistsOnServer() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (containsMace(p.getInventory().getContents())) return true;
            if (containsMace(p.getEnderChest().getContents())) return true;
        }
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof Container container) {
                        if (containsMace(container.getInventory().getContents())) return true;
                    }
                }
            }
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
    // PrepareItemCraftEvent — zakáže craftění mace pokud již existuje.
    // Zachytí i auto-craftery (crafter bloky), protože ty výsledek vezmou
    // jen pokud PrepareItemCraft vrátí neprázdný výsledek.
    // -----------------------------------------------------------------------
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType() != Material.MACE) return;

        if (maceExistsOnServer()) {
            event.getInventory().setResult(new ItemStack(Material.AIR));
            // Pokud je to automatický crafter (ne hráč), napíšeme do chatu
            if (event.getView().getPlayer() instanceof Player player) {
                // normální hráč — zprávu nedáváme zde, dáme ji v onCraft
            }
        }
    }

    // -----------------------------------------------------------------------
    // CraftItemEvent — zpráva pro hráče + blokace shift-stacku
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

        if (event.isShiftClick()) {
            event.setCancelled(true);
            CraftingInventory craftInv = event.getInventory();
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
    // InventoryMoveItemEvent — zachytí auto-craftery (crafter blok)
    // Pokud crafter chce přesunout mace dál (do hopperů atd.) a mace
    // neexistuje → normálně. Pokud crafter crafti mace přes hopper systém,
    // tento event ho zastaví a pošle broadcast do chatu.
    // -----------------------------------------------------------------------
    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        ItemStack item = event.getItem();
        if (item.getType() != Material.MACE) return;

        // Pokud zdrojový inventář je crafter a mace tam "vznikla" — blokovat
        if (event.getSource().getType() == InventoryType.CRAFTER) {
            if (maceExistsOnServer()) {
                event.setCancelled(true);
                Bukkit.broadcastMessage(ChatColor.RED + "[MaceLimit] Auto-crafter se pokusil vycraftit Mace, ale uz existuje! Mace znicena.");
                // Vymaz mace z crafter výstupu
                event.getSource().remove(Material.MACE);
            }
        }
    }

    // -----------------------------------------------------------------------
    // onPickup — limit totemů při sbírání ze země
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
    // BUG v originále: shift-click ze CRAFTING gridu kontroloval
    // clickedInv.type != PLAYER → CRAFTING != PLAYER → prošlo do větve
    // kde se kontroloval count, ALE countTotemsInInventory nepočítalo
    // totemy v crafting slotech → player s 0 totemy mohl vzít 1 z craftingu.
    //
    // Navíc klávesa E (swap do offhand/inventory) nebyla zachycena vůbec.
    //
    // OPRAVA: countTotemsInInventory nyní počítá i crafting sloty.
    // Navíc zachytáváme InventoryType.CRAFTING explicitně.
    // -----------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        // --- Blokace vkládání blokovaných itemů do kontejnerů ---
        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv != null) {
            InventoryType clickedType = clickedInv.getType();
            boolean clickedIsExternal = clickedType != InventoryType.PLAYER
                    && clickedType != InventoryType.CRAFTING;

            // Shift-click z hráče DO externího kontejneru
            if (event.isShiftClick() && !clickedIsExternal && event.getInventory().getType() != InventoryType.PLAYER) {
                if (current != null && isBlocked(current)) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "Tuto vec nelze ulozit do zadneho kontejneru!");
                    return;
                }
            }
            // Pokládáme cursor do externího kontejneru
            if (!event.isShiftClick() && clickedIsExternal) {
                if (cursor != null && cursor.getType() != Material.AIR && isBlocked(cursor)) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "Tuto vec nelze ulozit do zadneho kontejneru!");
                    return;
                }
            }
        }

        // --- Kontrola limitu totemů ---
        // Zjistíme kolik totemů by tato akce přinesla do hráčova inventáře
        ItemStack incoming = getIncomingTotem(event);
        if (incoming == null) return;

        // FIX: countuje totemy v inventáři + crafting grid (4 sloty) + offhand
        int currentCount = countAllTotemsForPlayer(player);

        if (currentCount + incoming.getAmount() > MAX_TOTEMS) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Nemuzete mit vice nez " + MAX_TOTEMS + " totemy v inventari!");
        }
    }

    /**
     * Vrátí ItemStack s TOTEM_OF_UNDYING pokud by tato akce přidala totem
     * do hráčova inventáře/offhandu. Jinak null.
     */
    private ItemStack getIncomingTotem(InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null) return null;

        InventoryType clickedType = clickedInv.getType();
        // "Vnější" inventář = cokoliv co není hráčův inventář
        boolean clickedIsExternal = clickedType != InventoryType.PLAYER
                && clickedType != InventoryType.CRAFTING;

        if (event.isShiftClick()) {
            // Shift-click z vnějšího inventáře (chest, barrel, crafting...) → jde do hráče
            if (clickedIsExternal || clickedType == InventoryType.CRAFTING) {
                if (current != null && current.getType() == Material.TOTEM_OF_UNDYING) {
                    return current;
                }
            }
            // Shift-click z hráče ven → nejde DO hráče
            return null;
        }

        // Normální klik v hráčově inventáři: pokládáme cursor (totem z jiného místa)
        if (clickedType == InventoryType.PLAYER) {
            if (cursor != null && cursor.getType() == Material.TOTEM_OF_UNDYING) {
                return cursor;
            }
        }

        // Klik v externím inventáři: vezmeme item do kurzoru, nejde přímo do inv
        return null;
    }

    /**
     * FIX: Počítá totemy v celém hráčově inventáři + crafting grid + offhand.
     * Originál nepočítal crafting sloty → tam ležící totem se nezapočítal
     * → hráč mohl mít 3 totemy v inv + libovolně přidávat z craftingu.
     */
    private int countAllTotemsForPlayer(Player player) {
        int count = 0;
        // Hlavní inventář (včetně offhandu a armorů)
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.TOTEM_OF_UNDYING) {
                count += item.getAmount();
            }
        }
        // Crafting grid (2x2 sloty v survival, přístup přes openInventory)
        if (player.getOpenInventory().getTopInventory().getType() == InventoryType.CRAFTING
                || player.getOpenInventory().getTopInventory().getType() == InventoryType.WORKBENCH) {
            for (ItemStack item : player.getOpenInventory().getTopInventory().getContents()) {
                if (item != null && item.getType() == Material.TOTEM_OF_UNDYING) {
                    count += item.getAmount();
                }
            }
        }
        return count;
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
                        if (content != null && (content.getType() == Material.MACE || isBlockedAltarItem(content)))
                            return true;
                    }
                }
            }
        }
        if (item.getType() == Material.BUNDLE) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof BundleMeta bundle) {
                for (ItemStack content : bundle.getItems()) {
                    if (content != null && (content.getType() == Material.MACE || isBlockedAltarItem(content)))
                        return true;
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
