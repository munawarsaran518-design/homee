package in.mantra.homes;

import in.mantra.homes.HomeManager.Home;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class HomeCommands implements CommandExecutor, TabCompleter {

    private static final Pattern VALID = Pattern.compile("[a-z0-9_-]{1,16}");

    private final HomesPlugin plugin;

    public HomeCommands(HomesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(plugin.msg("player-only"));
            return true;
        }
        HomeManager hm = plugin.homes();

        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "homes" -> plugin.gui().openMenu(p);

            case "sethome" -> {
                String name = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : null;
                if (name == null) name = hm.getHome(p.getUniqueId(), "home") == null ? "home" : null;
                if (name == null) {
                    // "home" is taken: open menu if full, else make the next free name
                    int limit = hm.getLimit(p);
                    if (hm.getHomes(p.getUniqueId()).size() >= limit) {
                        p.sendMessage(plugin.msg("limit-reached", "max", limit));
                        return true;
                    }
                    name = hm.nextFreeName(p.getUniqueId());
                }
                if (!VALID.matcher(name).matches()) {
                    p.sendMessage(plugin.msg("invalid-name"));
                    return true;
                }
                boolean exists = hm.getHome(p.getUniqueId(), name) != null;
                int limit = hm.getLimit(p);
                int used = hm.getHomes(p.getUniqueId()).size();
                if (!exists && used >= limit) {
                    p.sendMessage(plugin.msg("limit-reached", "max", limit));
                    return true;
                }
                hm.setHome(p.getUniqueId(), name, p.getLocation());
                if (exists) p.sendMessage(plugin.msg("home-moved", "name", name));
                else p.sendMessage(plugin.msg("home-set", "name", name, "used", used + 1, "max", limit));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
            }

            case "home" -> {
                if (args.length == 0) {
                    var all = hm.getHomes(p.getUniqueId());
                    if (all.isEmpty()) p.sendMessage(plugin.msg("no-homes"));
                    else if (all.size() == 1) go(p, all.values().iterator().next());
                    else plugin.gui().openMenu(p);
                    return true;
                }
                Home h = hm.getHome(p.getUniqueId(), args[0]);
                if (h == null) {
                    p.sendMessage(plugin.msg("home-not-found", "name", args[0]));
                    return true;
                }
                go(p, h);
            }

            case "delhome" -> {
                if (args.length == 0) {
                    plugin.gui().openMenu(p);
                    return true;
                }
                if (hm.deleteHome(p.getUniqueId(), args[0]))
                    p.sendMessage(plugin.msg("home-deleted", "name", args[0].toLowerCase(Locale.ROOT)));
                else
                    p.sendMessage(plugin.msg("home-not-found", "name", args[0]));
            }
        }
        return true;
    }

    private void go(Player p, Home h) {
        Location loc = h.toLocation();
        if (loc == null) {
            p.sendMessage(plugin.msg("world-missing"));
            return;
        }
        plugin.teleports().teleport(p, loc, h.name());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (!(sender instanceof Player p) || args.length != 1) return out;
        String n = cmd.getName().toLowerCase(Locale.ROOT);
        if (n.equals("home") || n.equals("delhome") || n.equals("sethome")) {
            for (String name : plugin.homes().getHomes(p.getUniqueId()).keySet()) {
                if (name.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(name);
            }
        }
        return out;
    }
}
