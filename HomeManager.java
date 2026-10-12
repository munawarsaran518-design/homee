package in.mantra.homes;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class HomeManager {

    public static final int MAX_HOMES = 45; // GUI hard cap (5 rows)

    public record Home(String name, String world, double x, double y, double z, float yaw, float pitch) {
        public Location toLocation() {
            World w = Bukkit.getWorld(world);
            if (w == null) return null;
            return new Location(w, x, y, z, yaw, pitch);
        }
    }

    private final HomesPlugin plugin;
    private final File file;
    private final Map<UUID, LinkedHashMap<String, Home>> homes = new HashMap<>();

    public HomeManager(HomesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yml.getConfigurationSection("players");
        if (players == null) return;
        for (String uuidStr : players.getKeys(false)) {
            UUID uuid;
            try { uuid = UUID.fromString(uuidStr); } catch (IllegalArgumentException e) { continue; }
            ConfigurationSection sec = players.getConfigurationSection(uuidStr);
            if (sec == null) continue;
            LinkedHashMap<String, Home> map = new LinkedHashMap<>();
            for (String name : sec.getKeys(false)) {
                ConfigurationSection h = sec.getConfigurationSection(name);
                if (h == null) continue;
                map.put(name, new Home(name, h.getString("world", "world"),
                        h.getDouble("x"), h.getDouble("y"), h.getDouble("z"),
                        (float) h.getDouble("yaw"), (float) h.getDouble("pitch")));
            }
            homes.put(uuid, map);
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, LinkedHashMap<String, Home>> e : homes.entrySet()) {
            for (Home h : e.getValue().values()) {
                String base = "players." + e.getKey() + "." + h.name() + ".";
                yml.set(base + "world", h.world());
                yml.set(base + "x", h.x());
                yml.set(base + "y", h.y());
                yml.set(base + "z", h.z());
                yml.set(base + "yaw", h.yaw());
                yml.set(base + "pitch", h.pitch());
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save homes.yml: " + ex.getMessage());
        }
    }

    public LinkedHashMap<String, Home> getHomes(UUID uuid) {
        return homes.computeIfAbsent(uuid, k -> new LinkedHashMap<>());
    }

    public Home getHome(UUID uuid, String name) {
        return getHomes(uuid).get(name.toLowerCase());
    }

    public void setHome(UUID uuid, String name, Location loc) {
        name = name.toLowerCase();
        getHomes(uuid).put(name, new Home(name, loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()));
        save();
    }

    public boolean deleteHome(UUID uuid, String name) {
        boolean removed = getHomes(uuid).remove(name.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }

    /** Next free auto name: home, home2, home3... */
    public String nextFreeName(UUID uuid) {
        Map<String, Home> map = getHomes(uuid);
        if (!map.containsKey("home")) return "home";
        for (int i = 2; i <= MAX_HOMES + 1; i++) {
            if (!map.containsKey("home" + i)) return "home" + i;
        }
        return "home" + System.currentTimeMillis();
    }

    public int getLimit(Player p) {
        int limit = plugin.getConfig().getInt("homes.default-limit", 3);
        if (p.hasPermission("homes.limit.unlimited")) return MAX_HOMES;
        for (PermissionAttachmentInfo info : p.getEffectivePermissions()) {
            String perm = info.getPermission().toLowerCase();
            if (info.getValue() && perm.startsWith("homes.limit.")) {
                try {
                    limit = Math.max(limit, Integer.parseInt(perm.substring("homes.limit.".length())));
                } catch (NumberFormatException ignored) { }
            }
        }
        return Math.min(limit, MAX_HOMES);
    }
}
