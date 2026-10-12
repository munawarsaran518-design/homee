package in.mantra.homes;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TeleportManager implements Listener {

    private static final class Pending {
        final Location start;
        BukkitTask task;
        Pending(Location start) { this.start = start; }
    }

    private final HomesPlugin plugin;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public TeleportManager(HomesPlugin plugin) {
        this.plugin = plugin;
    }

    public void teleport(Player player, Location dest, String homeName) {
        int warmup = plugin.getConfig().getInt("teleport.warmup-seconds", 3);
        cancel(player, false);

        if (warmup <= 0 || player.hasPermission("homes.bypass.warmup")) {
            finish(player, dest, homeName);
            return;
        }

        Pending pd = new Pending(player.getLocation());
        pending.put(player.getUniqueId(), pd);
        pd.task = new BukkitRunnable() {
            int left = warmup;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    pending.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                if (left <= 0) {
                    pending.remove(player.getUniqueId());
                    cancel();
                    finish(player, dest, homeName);
                    return;
                }
                player.sendActionBar(plugin.msg("teleporting-in", "seconds", left));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.4f);
                left--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void finish(Player player, Location dest, String homeName) {
        player.teleportAsync(dest).thenAccept(ok -> {
            if (ok) {
                player.sendMessage(plugin.msg("teleported", "name", homeName));
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            }
        });
    }

    public void cancel(Player player, boolean notify) {
        Pending pd = pending.remove(player.getUniqueId());
        if (pd == null) return;
        if (pd.task != null) pd.task.cancel();
        if (notify) {
            player.sendMessage(plugin.msg("teleport-cancelled"));
            player.sendActionBar(net.kyori.adventure.text.Component.empty());
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!plugin.getConfig().getBoolean("teleport.cancel-on-move", true)) return;
        if (!pending.containsKey(e.getPlayer().getUniqueId())) return;
        if (e.getTo() == null) return;
        if (e.getFrom().getBlockX() != e.getTo().getBlockX()
                || e.getFrom().getBlockY() != e.getTo().getBlockY()
                || e.getFrom().getBlockZ() != e.getTo().getBlockZ()) {
            cancel(e.getPlayer(), true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!plugin.getConfig().getBoolean("teleport.cancel-on-damage", true)) return;
        if (e.getEntity() instanceof Player p && pending.containsKey(p.getUniqueId())) {
            cancel(p, true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cancel(e.getPlayer(), false);
    }
}
