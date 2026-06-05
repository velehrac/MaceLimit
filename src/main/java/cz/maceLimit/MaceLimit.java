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
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MaceLimit extends JavaPlugin implements Listener {

    private static final int MAX_TOTEMS = 3;

    // Sledujeme UUID dropped mace item entit abychom poznali kdy jsou zničeny
    private final Set<UUID> trackedMaceItems = new HashSet<>();

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

        // Odstranění receptu na Crafter blok
        Bukkit.removeRecipe(new org.bukkit.NamespacedKey("minecraft", "crafter"));
        getLogger().info("Recept na Crafter blok byl odstranen.");

        // Každou sekundu zkontroluj zda sledované mace UUID stále existují.
        // Pokud entita zmizela a ItemDespawnEvent ji nezachytil (void, okamžitá smrt
        // v lávě bez combustion eventu), broadcastujeme zničení.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            trackedMaceItems.removeIf(uid -> {
                for (org.bukkit.World world : Bukkit.getWorlds()) {
                    if (world.getEntity(uid) != null) return false; // stále žije
                }
                // Entita neexistuje → mace zničena
                Bukkit.broadcastMessage(ChatColor.RED + "[Mace] Mace byla znicena.");
                return true;
            });
        }, 20L, 20L);
    }

    // -----------------------------------------------------------------------
    // maceExistsOnServer
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
    // PrepareItemCraftEvent — autocrafter blok nesmí nic craftit (výsledek
    // vždy vymazán). Hráč smí craftit mace pouze pokud ještě neexistuje.
    // -----------------------------------------------------------------------
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        // Autocrafter blok — viewer není Player → zakázat úplně vše
        if (!(event.getView().getPlayer() instanceof Player)) {
            event.getInventory().setResult(new ItemStack(Material.AIR));
            return;
        }

        // Hráč craftí mace — zakázat pokud mace už existuje
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType() != Material.MACE) return;
        if (maceExistsOnServer()) {
            event.getInventory().setResult(new ItemStack(Material.AIR));
        }
    }

    // -----------------------------------------------------------------------
    // CraftItemEvent — broadcast + blokace shift-stacku
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
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "[Mace] Hrac " + ChatColor.WHITE + player.getName()
                + ChatColor.GOLD + " vycraftil Mace!");
    }

    // -----------------------------------------------------------------------
    // InventoryMoveItemEvent — záloha: autocrafter posílá mace hopperem
    // -----------------------------------------------------------------------
    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (event.getItem().getType() != Material.MACE) return;
        if (event.getSource().getType() == InventoryType.CRAFTER) {
            event.setCancelled(true);
            event.getSource().remove(Material.MACE);
            Bukkit.broadcastMessage(ChatColor.RED + "[Mace] Autocrafter vycraftil Mace — okamzite znicena.");
        }
    }

    // -----------------------------------------------------------------------
    // Sledování dropped mace — zaregistrujeme UUID při spawnu entity
    // -----------------------------------------------------------------------
    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        if (event.getEntity().getItemStack().getType() == Material.MACE) {
            trackedMaceItems.add(event.getEntity().getUniqueId());
        }
    }

    // Přirozený despawn (5 minut na zemi)
    @EventHandler
    public void onItemDespawn(ItemDespawnEvent event) {
        if (event.getEntity().getItemStack().getType() != Material.MACE) return;
        trackedMaceItems.remove(event.getEntity().getUniqueId());
        Bukkit.broadcastMessage(ChatColor.RED + "[Mace] Mace byla znicena.");
    }

    // Mace začíná hořet (lávou, ohněm) — za 1 tick zkontrolujeme zda
    // item entita stále existuje. Pokud ne, broadcastujeme zničení.
    // (Lávou item entity okamžitě zmizí bez separátního "death" eventu)
    @EventHandler
    public void onEntityCombust(EntityCombustEvent event) {
        if (!(event.getEntity() instanceof Item itemEntity)) return;
        if (itemEntity.getItemStack().getType() != Material.MACE) return;

        UUID uid = itemEntity.getUniqueId();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            // Zkontrolujeme zda entita stále existuje
            boolean stillExists = false;
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                if (world.getEntity(uid) != null) {
                    stillExists = true;
                    break;
                }
            }
            if (!stillExists && trackedMaceItems.remove(uid)) {
                Bukkit.broadcastMessage(ChatColor.RED + "[Mace] Mace byla znicena.");
            }
        }, 1L);
    }

    // -----------------------------------------------------------------------
    // onPickup — limit totemů při sbírání ze země
    // -----------------------------------------------------------------------
    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getItem().getItemStack().getType() != Material.TOTEM_OF_UNDYING) return;
        if (countTotemsInInventory(player) >= MAX_TOTEMS) {
            event.setCancelled(true);
        }
    }

    // -----------------------------------------------------------------------
    // onEnderPearlUse
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
    // onInventoryClick — blokace kontejnerů + enforce totem limitu
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

        Bukkit.getScheduler().runTask(this, () -> enforceTotemLimit(player));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Bukkit.getScheduler().runTask(this, () -> enforceTotemLimit(player));
    }

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
                player.getWorld().dropItemNaturally(player.getLocation(), item.clone());
                player.getInventory().setItem(i, null);
                toRemove -= amount;
            } else {
                ItemStack drop = item.clone();
                drop.setAmount(toRemove);
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
                item.setAmount(amount - toRemove);
                toRemove = 0;
            }
        }

        player.sendMessage(ChatColor.YELLOW + "Prebytecne totemy byly vyhozeny (limit: " + MAX_TOTEMS + ").");
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
            if (meta instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox shulker) {
                for (ItemStack content : shulker.getInventory().getContents()) {
                    if (content != null && (content.getType() == Material.MACE || isBlockedAltarItem(content)))
                        return true;
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
