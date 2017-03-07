package me.abcric.bukkit.abctickets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.sk89q.worldguard.bukkit.WGBukkit;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

public class ABCTicketsCommandExecutor implements CommandExecutor {
	ABCTickets plugin;

	public ABCTicketsCommandExecutor(ABCTickets plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command,
			String label, String[] args) {
		// make sure we process our command only
		if (command.getName().equalsIgnoreCase("abctickets")) {
			if(args.length != 0) {
				if(!sender.hasPermission("ABCTickets.admin")) {
					sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
					return true;
				}
				if (args[0].equalsIgnoreCase("add")) {
					if(args.length != 3) sender.sendMessage(ChatColor.RED + "add <region> <ticket>");
					else addTicketToRegion(sender, args[1], args[2]);

					return true;
				} else if (args[0].equalsIgnoreCase("remove")) {
					if(args.length != 3) sender.sendMessage(ChatColor.RED + "remove <region> <ticket>");
					else removeTicketFromRegion(sender, args[1], args[2]);

					return true;
				} else if(args[0].equalsIgnoreCase("list")) {
					if(!(sender instanceof Player)) sender.sendMessage("This command must be used by a player.");

					if(args.length != 2) sender.sendMessage(ChatColor.RED + "Usage: list <region>");
					else {
						ProtectedRegion rg = WGBukkit.getRegionManager(((Player) sender).getWorld()).getRegion(args[1]);
						if(rg == null) sender.sendMessage("Region does not exist");
						else {
							if(plugin.RequiredTickets.containsKey(rg)) sender.sendMessage(plugin.RequiredTickets.get(rg) + "");
							else sender.sendMessage("Region '" + rg.getId() + "' does not require tickets.");
						}
					}

					return true;
				} else if(args[0].equalsIgnoreCase("listall")) {
					List<ProtectedRegion> rgs = new ArrayList<ProtectedRegion>(plugin.RequiredTickets.keySet());
					Collections.sort(rgs, new Comparator<ProtectedRegion>() {
						@Override
						public int compare(ProtectedRegion o1, ProtectedRegion o2) {
							return o1.getId().compareTo(o2.getId());
						}
					});
					for(ProtectedRegion rg : new TreeSet<ProtectedRegion>(plugin.RequiredTickets.keySet())) {
						sender.sendMessage(rg.getId() + ": " + plugin.RequiredTickets.get(rg));
					}

					return true;
				} else if (args[0].equalsIgnoreCase("help")) {
					doHelp(sender);

					return true;
				} else {
					sender.sendMessage("Unknown command. Showing help");
					doHelp(sender);
					return true;
				}
			} else {
				// no arguments: if player, show their tickets, else display help
				if(sender instanceof Player) {
					if(plugin.tickets.containsKey((Player)sender))
						sender.sendMessage("Your tickets: " + plugin.tickets.get((Player)sender) + ""); // TODO: improve ticket display
					else
						sender.sendMessage("You do not have tickets.");
				} else {
					doHelp(sender);
				}
				return true;
			}
		}
		return false;
	}

	/**
	 * Shows help text.
	 * @param sender Command sender to show the help text to.
	 */
	public void doHelp(CommandSender sender) {
		sender.sendMessage("ABCTickets Help");
		sender.sendMessage("/abctickets [command]");
		sender.sendMessage("No subcommand - shows what tickets you have");
		sender.sendMessage("add <region> <ticket> - adds ticket requirement to a region");
		sender.sendMessage("remove <region> <ticket> - removes ticket requirement from a region");
		sender.sendMessage("list <region> - lists ticket requirements for a region");
		sender.sendMessage("listall - lists all ticket requirements for all regions");
	}

	/**
	 * Adds a ticket to a region.
	 * @param sender The sender of the add command
	 * @param region The name of the region to add the ticket to
	 * @param ticket The name of the ticket to add to the region
	 */
	public void addTicketToRegion(CommandSender sender, String region,
			String ticket) {
		// TODO: allow specifying world via argument
		if (sender instanceof Player) {
			// get region from WorldGuard
			ProtectedRegion rg = WGBukkit.getRegionManager(
					((Player) sender).getWorld()).getRegion(region);
			if (rg != null) {
				// add ticket to region on our map
				if (plugin.RequiredTickets.containsKey(rg)) {
					plugin.RequiredTickets.get(rg).add(ticket);
				} else {
					List<String> s = new ArrayList<>();
					s.add(ticket);
					plugin.RequiredTickets.put(rg, s);
				}

				// save new added ticket to config
				if (plugin.getConfig().contains("regions." + rg.getId())) {
					List<String> tickets = plugin.getConfig().getStringList("regions." + rg.getId());
					tickets.add(ticket);
					plugin.getConfig().set("regions." + rg.getId(), tickets);
				} else {
					plugin.getConfig().set("regions." + rg.getId(),
							new String[] { ticket });
				}
				plugin.saveConfig();
				sender.sendMessage(ChatColor.GREEN + "Ticket added to region.");
			} else {
				sender.sendMessage(ChatColor.RED + "Region does not exist.");
			}
		} else {
			sender.sendMessage("This command must be used by a player.");
		}
	}

	public void removeTicketFromRegion(CommandSender sender, String region, String ticket) {
		// TODO: allow specifying world via argument
		if(sender instanceof Player) {
			// get region from WorldGuard
			ProtectedRegion rg = WGBukkit.getRegionManager(((Player) sender).getWorld()).getRegion(region);
			if(rg != null) {
				// remove ticket from region
				if(plugin.RequiredTickets.containsKey(rg)) {
					if(plugin.RequiredTickets.get(rg).contains(ticket)) {
						plugin.RequiredTickets.get(rg).remove(ticket);
						// if no tickets left in region, remove it from our list
						if(plugin.RequiredTickets.get(rg).isEmpty()) plugin.RequiredTickets.remove(rg);
					}
				}

				// remove ticket from region on our config
				if(plugin.getConfig().contains("regions." + rg.getId())) {
					if(plugin.getConfig().getStringList("regions." + rg.getId()).contains(ticket)) {
						List<String> tickets = plugin.getConfig().getStringList("regions." + rg.getId());
						tickets.remove(ticket);

						if(tickets.isEmpty()) plugin.getConfig().set("regions." + rg.getId(), null);
						else plugin.getConfig().set("regions." + rg.getId(), tickets);
					}
				}
				plugin.saveConfig();
				sender.sendMessage(ChatColor.GREEN + "Ticket '" + ticket + "' removed from region '" + region + "'.");
			} else {
				sender.sendMessage(ChatColor.RED + "Region '" + region + "' does not exist.");
			}
		} else {
			sender.sendMessage("This command must be used by a player.");
		}
	}
}
