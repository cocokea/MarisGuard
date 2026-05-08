package com.maris7.guard.antiesp.command;

import com.maris7.guard.antiesp.esper.AbstractEsperManager;
import com.maris7.guard.antiesp.menu.EsperMenu;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class EsperCommand implements CommandExecutor, TabCompleter {

    private final AbstractEsperManager esperManager;

    public EsperCommand(AbstractEsperManager esperManager) {
        this.esperManager = esperManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(esperManager.message("command.usage"));
                return true;
            }
            EsperMenu.open(player, esperManager, 0);
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("alerts")) {
            esperManager.toggleAlerts(sender);
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            esperManager.handleResetCommand(sender, args[1]);
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("check")) {
            final Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(esperManager.message("command.player-not-found"));
                return true;
            }
            esperManager.handleCheckCommand(sender, target);
            return true;
        }

        sender.sendMessage(esperManager.message("command.usage"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("check", "reset", "alerts");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("check") || args[0].equalsIgnoreCase("reset"))) {
            final List<String> matches = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    matches.add(player.getName());
                }
            }
            return matches;
        }
        return List.of();
    }
}

