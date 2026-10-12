package in.mantra.homes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class HomesPlugin extends JavaPlugin {

    public static final MiniMessage MM = MiniMessage.miniMessage();

    private HomeManager homeManager;
    private TeleportManager teleportManager;
    private HomesGUI gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        homeManager = new HomeManager(this);
        teleportManager = new TeleportManager(this);
        gui = new HomesGUI(this);

        getServer().getPluginManager().registerEvents(teleportManager, this);
        getServer().getPluginManager().registerEvents(gui, this);

        HomeCommands cmds = new HomeCommands(this);
        for (String name : new String[]{"sethome", "home", "delhome", "homes"}) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(cmds);
                cmd.setTabCompleter(cmds);
            }
        }
    }

    @Override
    public void onDisable() {
        if (homeManager != null) homeManager.save();
    }

    public HomeManager homes() { return homeManager; }
    public TeleportManager teleports() { return teleportManager; }
    public HomesGUI gui() { return gui; }

    /** Builds a message from config. Placeholders are passed as key, value pairs. */
    public Component msg(String key, Object... kv) {
        String raw = getConfig().getString("messages." + key, "<red>Missing message: " + key);
        List<TagResolver> resolvers = new ArrayList<>();
        resolvers.add(Placeholder.parsed("prefix", getConfig().getString("messages.prefix", "")));
        for (int i = 0; i + 1 < kv.length; i += 2) {
            resolvers.add(Placeholder.unparsed(String.valueOf(kv[i]), String.valueOf(kv[i + 1])));
        }
        return MM.deserialize(raw, resolvers.toArray(new TagResolver[0]));
    }
}
