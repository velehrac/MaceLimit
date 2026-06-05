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
    // PrepareItemCraftEvent — zakáže craftění mace pokud již existuje,
    // a VŽDY zakáže craftění mace v auto-crafteru (crafter bloku).
    // -----------------------------------------------------------------------
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType() != Material.MACE) return;

        // Auto-crafter blok — vždy zakázat, bez ohledu na to zda mace existuje
        if (!(event.getView().getPlayer() instanceof Player)) {
            event.getInventory().setResult(new ItemStack(Material.AIR));
            return;
        }

        // Hráč — zakázat pokud mace už existuje
        if (maceExistsOnServer()) {
            event.getInventory().setResult(new ItemStack(Material.AIR));
        }
    }

    // -----------------------------------------------------------------------
    // CraftItemEvent — zpráva pro hráče + broadcast při úspěšném craftu
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

        // Shift-click by dal celý stack → vynutit 1 kus
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
        }

        // Broadcast — mace vycraftěna
        Bukkit.broadcastMessage(ChatColor.GOLD + "[Mace] Hrac " + ChatColor.WHITE + player.getName()
                + ChatColor.GOLD + " vycraftil Mace!");
    }

    // -----------------------------------------------------------------------
    // InventoryMoveItemEvent — autocrafter se pokusí přesunout mace hopperem
    // Zablokujeme a vymažeme.
    // -----------------------------------------------------------------------
    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (event.getItem().getType() != Material.MACE) return;
        if (event.getSource().getType() == InventoryType.CRAFTER) {
            event.setCancelled(true);
            event.getSource().remove(Material.MACE);
            Bukkit.broadcastMessage(ChatColor.RED + "[Mace] Auto-crafter vycraftil Mace — Mace byla znicena!");
        }
    }

    // -----------------------------------------------------------------------
    // PlayerDeathEvent / ItemDespawnEvent — broadcast při zničení mace
    // Sledujeme dropped itemy: pokud mace zmizí ze světa (despawn),
    // broadcastujeme zprávu.
    // -----------------------------------------------------------------------
    @EventHandler
    public void onItemDespawn(org.bukkit.event.entity.ItemDespawnEvent event) {
        if (event.getEntity().getItemStack().getType() == Material.MACE) {
            Bukkit.broadcastMessage(ChatColor.RED + "[Mace] Mace byla znicena (despawn).");
        }
    }

    // Mace shozena na zem hráčem — sledujeme přes PlayerDropItemEvent
    // (samotný drop není zničení, ale pokud ji hráč zahodí do ohně/void atd.)
    // Zničení v inventáři (lávou, void, smrt) zachytíme přes PlayerDeathEvent.
    @EventHandler
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        for (ItemStack drop : event.getDrops()) {
            if (drop != null && drop.getType() == Material.MACE) {
                // Mace je mezi dropy — nebyla zničena, jen spadla na zem
                // (broadcast přijde až při případném despawnu)
                return;
            }
        }
        // Mace nebyla v dropech — buď ji hráč neměl, nebo zmizela (keep inventory?)
        // Zkontrolujeme zda hráč před smrtí mace měl
        Player player = event.getEntity();
        // Inventář je už prázdný při PlayerDeathEvent pokud keepInventory=false,
        // proto kontrolujeme dropy výše. Pokud keep=true, mace zůstane v inventáři.
    }

    // Mace zničena ohněm/lávou jako dropped item entity
    @EventHandler
    public void onEntityCombust(org.bukkit.event.entity.EntityCombustEvent event) {
        if (event.getEntity() instanceof Item itemEntity) {
            if (itemEntity.getItemStack().getType() == Material.MACE) {
                Bukkit.broadcastMessage(ChatColor.RED + "[Mace] Mace byla znicena (ohen/lava).");
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
    // onInventoryClick — totemy: pokud hráč má >3, přebytečné vyhodit
    // -----------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null) return;

        InventoryType clickedType = clickedInv.getType();
        boolean clickedIsExternal = clickedType != InventoryType.PLAYER
                && clickedType != InventoryType.CRAFTING;

        // --- Blokace blokovaných itemů do kontejnerů ---
        if (!event.isShiftClick() && clickedIsExternal) {
            if (cursor != null && cursor.getType() != Material.AIR && isBlocked(cursor)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Tuto vec nelze ulozit do zadneho kontejneru!");
                return;
            }
        }
        if (event.isShiftClick() && !clickedIsExternal
                && event.getInventory().getType() != InventoryType.PLAYER
                && event.getInventory().getType() != InventoryType.CRAFTING) {
            if (current != null && isBlocked(current)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Tuto vec nelze ulozit do zadneho kontejneru!");
                return;
            }
        }

        // --- Totemy: po akci zkontroluj a vyhoď přebytečné ---
        // Použijeme scheduledTask aby se spustil PO dokončení události
        Bukkit.getScheduler().runTask(this, () -> enforceTotemLimit(player));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Bukkit.getScheduler().runTask(this, () -> enforceTotemLimit(player));
    }

    /**
     * Pokud má hráč více než MAX_TOTEMS totemů v inventáři,
     * přebytečné vyhodí na zem u jeho pozice.
     */
    private void enforceTotemLimit(Player player) {
        int count = countTotemsInInventory(player);
        if (count <= MAX_TOTEMS) return;

        int toRemove = count - MAX_TOTEMS;
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = contents.length - 1; i >= 0 && toRemove > 0; i--) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != Material.TOTEM_OF_UNDYING) continue;

            int amount = item.getAmount();
            if (amount <= toRemove) {
                // Vyhoď celý stack
                player.getWorld().dropItemNaturally(player.getLocation(), item.clone());
                player.getInventory().setItem(i, null);
                toRemove -= amount;
            } else {
                // Vyhoď jen část
                ItemStack drop = item.clone();
                drop.setAmount(toRemove);
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
                item.setAmount(amount - toRemove);
                toRemove = 0;
            }
        }

        if (count - MAX_TOTEMS > 0) {
            player.sendMessage(ChatColor.YELLOW + "Prebytecne totemy byly vyhozeny z vaseho inventare (limit: " + MAX_TOTEMS + ").");
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
