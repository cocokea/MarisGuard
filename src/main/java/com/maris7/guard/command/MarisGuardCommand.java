package com.maris7.guard.command;

import com.maris7.guard.MarisGuard;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public final class MarisGuardCommand implements CommandExecutor, TabCompleter {
    private final MarisGuard plugin;

    public MarisGuardCommand(MarisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("marisguard.admin")) {
                sender.sendMessage(plugin.getMessageConfig().get("guard.no-permission"));
                return true;
            }
            plugin.reloadRuntimeFiles();
            sender.sendMessage(plugin.getMessageConfig().get("guard.reload-success"));
            return true;
        }

        sender.sendMessage(plugin.getMessageConfig().get("guard.usage"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload");
        }
        return List.of();
    }
}
