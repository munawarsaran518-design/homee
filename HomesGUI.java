package in.mantra.homes;

import in.mantra.homes.HomeManager.Home;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class HomesGUI implements Listener {

    private static final Material[] BEDS = {
            Material.RED_BED, Material.ORANGE_BED, Material.YELLOW_BED, Material.LIME_BED,
            Material.CYAN_BED, Material.LIGHT_BLUE_BED, Material.BLUE_BED, Material.PURPLE_BED,
            Material.MAGENTA_BED, Material.PINK_BED
    };

    private enum Type { LIST, CONFIRM_DELETE }

    private static final class MenuHolder implements InventoryHolder {
        final Type type;
        final Map<Integer, String> homeSlots = new HashMap<>();
        final Set<Integer> emptySlots = new HashSet<>();
        String target; // for confirm menu
        Inventory inv;

        MenuHolder(Type type) { this.type = type; }

        @Override
        public Inventory getInventory() { return inv; }
    }

    private final HomesPlugin plugin;

    public HomesGUI(HomesPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- Menus ----------

    public void openMenu(Player p) {
        HomeManager hm = plugin.homes();
        int limit = hm.getLimit(p);
        List<Home> list = new ArrayList<>(hm.getHomes(p.getUniqueId()).values());
        int shown = Math.min(HomeManager.MAX_HOMES, Math.max(limit, list.size()));
        int rows = Math.max(1, (int) Math.ceil(shown / 9.0));
        int size = (rows + 1) * 9;

        MenuHolder holder = new MenuHolder(Type.LIST);
        Inventory inv = Bukkit.createInventory(holder, size,
                HomesPlugin.MM.deserialize("<dark_gray><bold>Homes</bold> <gray>(" + list.size() + "/" + limit + ")"));
        holder.inv = inv;

        for (int i = 0; i < rows * 9; i++) {
            if (i < list.size()) {
                Home h = list.get(i);
                holder.homeSlots.put(i, h.name());
                inv.setItem(i, homeItem(h, i));
            } else if (i < limit) {
                holder.emptySlots.add(i);
                inv.setItem(i, item(Material.GRAY_DYE, "<gray><bold>Empty Slot",
                        "<dark_gray>No home set here.", "",
                        "<yellow>Click <gray>to set a home at your location"));
            } else {
                inv.setItem(i, item(Material.RED_STAINED_GLASS_PANE, "<red><bold>Locked",
                        "<gray>Get a higher rank for more homes."));
            }
        }

        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = rows * 9; i < size; i++) inv.setItem(i, filler);
        inv.setItem(size - 5, item(Material.BARRIER, "<red><bold>Close"));
        inv.setItem(size - 9, item(Material.OAK_SIGN, "<gold><bold>Your Homes",
                "<gray>Homes used: <white>" + list.size() + "<gray>/<white>" + limit, "",
                "<green>Left-click <gray>a bed to teleport",
                "<red>Right-click <gray>a bed to delete",
                "<yellow>Click an empty slot <gray>to set a home"));

        p.openInventory(inv);
    }

    private void openConfirm(Player p, String homeName) {
        Home h = plugin.homes().getHome(p.getUniqueId(), homeName);
        if (h == null) { openMenu(p); return; }

        MenuHolder holder = new MenuHolder(Type.CONFIRM_DELETE);
        holder.target = h.name();
        Inventory inv = Bukkit.createInventory(holder, 27,
                HomesPlugin.MM.deserialize("<dark_red><bold>Delete " + h.name() + "?"));
        holder.inv = inv;

        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);
        inv.setItem(11, item(Material.LIME_CONCRETE, "<green><bold>Confirm", "<gray>Delete home <white>" + h.name()));
        inv.setItem(13, homeItem(h, 0));
        inv.setItem(15, item(Material.RED_CONCRETE, "<red><bold>Cancel", "<gray>Go back"));
        p.openInventory(inv);
    }

    private ItemStack homeItem(Home h, int index) {
        Material bed = BEDS[index % BEDS.length];
        return item(bed, "<green><bold>" + h.name(),
                "<gray>World: <white>" + h.world(),
                "<gray>X: <white>" + (int) h.x() + " <gray>Y: <white>" + (int) h.y() + " <gray>Z: <white>" + (int) h.z(),
                "",
                "<green>Left-click <gray>to teleport",
                "<red>Right-click <gray>to delete");
    }

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(HomesPlugin.MM.deserialize(name).decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            List<Component> lines = new ArrayList<>();
            for (String l : lore) lines.add(HomesPlugin.MM.deserialize(l).decoration(TextDecoration.ITALIC, false));
            meta.lore(lines);
        }
        it.setItemMeta(meta);
        return it;
    }

    // ---------- Events ----------

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof MenuHolder) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;

        int slot = e.getSlot();
        HomeManager hm = plugin.homes();

        if (holder.type == Type.CONFIRM_DELETE) {
            if (slot == 11) {
                if (hm.deleteHome(p.getUniqueId(), holder.target)) {
                    p.sendMessage(plugin.msg("home-deleted", "name", holder.target));
                    p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                }
                openMenu(p);
            } else if (slot == 15) {
                click(p);
                openMenu(p);
            }
            return;
        }

        // LIST menu
        if (slot == e.getInventory().getSize() - 5) {
            p.closeInventory();
            return;
        }

        String name = holder.homeSlots.get(slot);
        if (name != null) {
            Home h = hm.getHome(p.getUniqueId(), name);
            if (h == null) { openMenu(p); return; }
            if (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT) {
                click(p);
                openConfirm(p, name);
            } else {
                Location loc = h.toLocation();
                p.closeInventory();
                if (loc == null) {
                    p.sendMessage(plugin.msg("world-missing"));
                    return;
                }
                plugin.teleports().teleport(p, loc, h.name());
            }
            return;
        }

        if (holder.emptySlots.contains(slot)) {
            int limit = hm.getLimit(p);
            int used = hm.getHomes(p.getUniqueId()).size();
            if (used >= limit) {
                p.sendMessage(plugin.msg("limit-reached", "max", limit));
                return;
            }
            String newName = hm.nextFreeName(p.getUniqueId());
            hm.setHome(p.getUniqueId(), newName, p.getLocation());
            p.sendMessage(plugin.msg("home-set", "name", newName, "used", used + 1, "max", limit));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
            openMenu(p);
        }
    }

    private void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1f);
    }
}
