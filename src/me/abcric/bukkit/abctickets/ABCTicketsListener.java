package me.abcric.bukkit.abctickets;

import java.util.ArrayList;
import java.util.List;

import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;

import com.sk89q.worldguard.bukkit.WGBukkit;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

public class ABCTicketsListener implements Listener {
	ABCTickets plugin;

	public ABCTicketsListener(ABCTickets plugin) {
		this.plugin = plugin;
	}

	/**
	 * Handle players entering minecarts
	 * @param event the entering event
	 */
	@EventHandler(ignoreCancelled = true)
	public void onVehicleEnterEvent(VehicleEnterEvent event) {	
		// handle players only
		if (!(event.getEntered() instanceof Player))
			return;

		Vehicle vehicle = event.getVehicle();
		Player player = (Player) event.getEntered();

		// handle minecarts only
		if (!(vehicle instanceof Minecart)) return;

		// if player has the free tickets permission, ignore further checks
		if (player.hasPermission("ABCTickets.free"))
			return;

		// get regions at this spot
		ApplicableRegionSet regions = WGBukkit
				.getRegionManager(event.getVehicle().getWorld())
				.getApplicableRegions(vehicle.getLocation());

		if (regions == null)
			return;

		boolean has_required_tickets = false;

		List<String> required_tickets = new ArrayList<>();
		// enumerate tickets
		for (ProtectedRegion region : regions) {
			if (plugin.RequiredTickets.containsKey(region))
				required_tickets.addAll(plugin.RequiredTickets.get(region));
		}

		if (required_tickets == null || required_tickets.isEmpty())
			return;

		List<String> used_tickets = new ArrayList<>();
		// check if player has all tickets
		if (plugin.tickets.containsKey(player)) {
			for (String s : plugin.tickets.get(player)) {
				if (required_tickets.contains(s)) {
					required_tickets.remove(s);
					used_tickets.add(s);
				}
			}
		}

		if (required_tickets.isEmpty())
			has_required_tickets = true;

		if (has_required_tickets) {
			// consume tickets
			List<String> l = plugin.tickets.get(player);
			if (l != null) {
				for (String s : used_tickets) {
					l.remove(s);
				}
				plugin.tickets.put(player, l);
			}
			if(used_tickets.size() == 1) {
				player.sendMessage("You used a " + used_tickets.get(0) + " ticket to ride the vehicle.");
			} else {
				String message = "You used tickets " + String.join(",", used_tickets) + " to ride the vehicle.";
				player.sendMessage(message);
			}
		} else {
			event.setCancelled(true);
			String message = "You need the following ticket(s) to ride here: ";
			for (String ticket : required_tickets) {
				message += ticket + ", ";
			}
			player.sendMessage(message.replaceAll("[, ]+$", ""));
		}
	}

	/**
	 * Handle sign right-clicks
	 * @param event a player interaction event
	 */
	@EventHandler(ignoreCancelled = true)
	public void onPlayerInteractEvent(PlayerInteractEvent event) {	
		if(event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
		if(!(event.getClickedBlock().getType().equals(Material.SIGN_POST) ||
				event.getClickedBlock().getType().equals(Material.WALL_SIGN))) {
			return;
		}

		Sign sign = (Sign)event.getClickedBlock().getState();
		if(!(sign.getLine(0).equalsIgnoreCase("[booth]") ||
				sign.getLine(0).equalsIgnoreCase("[tickets]"))) {
			return;
		}

		if(sign.getLine(1).length() == 0) return;

		if(!event.getPlayer().hasPermission("ABCTickets.booth.use")) {
			event.getPlayer().sendMessage("You do not have permission to use booths.");
			return;
		}

		Double ticket_price;
		try {
			ticket_price = Double.valueOf(sign.getLine(2));
		} catch(NumberFormatException e) {
			plugin.getLogger().severe("Ticket price on the third line of the booth sign at " + sign.getLocation() + " is broken, fix it.");
			return;
		}

		/* if the sign is a booth, the player wants to buy a ticket
		 * and not do anything else, so we can cancel the interaction. */
		event.setCancelled(true);

		/* sign format
		 * [Booth] or [Tickets]
		 * Owner
		 * Price
		 * Ticket
		 */

		Player owner = Bukkit.getPlayer(sign.getLine(1));
		Player buyer = event.getPlayer();

		String buyer_name = buyer.getName();
		String owner_name = sign.getLine(1);

		String ticket = sign.getLine(3);

		if(ticket.isEmpty() || ticket == null) {
			plugin.getLogger().severe("Invalid ticket name on fourth line of booth sign at " + sign.getLocation() + ", fix it.");
		}

		if(buyer_name.equalsIgnoreCase(owner_name)) {
			if(!plugin.tickets.containsKey(buyer)) plugin.tickets.put(buyer, new ArrayList<String>());
			plugin.tickets.get(buyer).add(ticket);
			buyer.sendMessage("You got a '" + ticket + "' ticket from your own booth.");
			return;
		}

		if(ABCTickets.eco.has(buyer_name, ticket_price)) {

			EconomyResponse r1 = ABCTickets.eco.withdrawPlayer(buyer_name, ticket_price);
			if(!r1.transactionSuccess()) {
				plugin.getLogger().severe("Buyer.withdraw somehow failed");
				buyer.sendMessage(ChatColor.RED + "An internal error occurred.");
				return;
			}


			EconomyResponse r2 = ABCTickets.eco.depositPlayer(owner_name, ticket_price);
			if(!r2.transactionSuccess()) {
				plugin.getLogger().severe("Owner.deposit for " + owner_name + " failed");
			}


			if(plugin.tickets.containsKey(buyer)) {
				plugin.tickets.get(buyer).add(ticket);
			} else {
				List<String> s = new ArrayList<>();
				s.add(ticket);
				plugin.tickets.put(buyer, s);
			}

			buyer.sendMessage(ChatColor.GREEN + "You bought a '" + ticket + "' ticket for " + ABCTickets.eco.format(ticket_price) + ".");

			if(owner != null) owner.sendMessage(ChatColor.DARK_GREEN + "[Tickets] " + buyer.getDisplayName() + " has bought a '" + ticket + "' ticket from you for " + ABCTickets.eco.format(ticket_price) + ".");
			plugin.getLogger().info(buyer.getDisplayName() + " has bought a '" + ticket + "' ticket from " + owner_name + " for " + ABCTickets.eco.format(ticket_price) + ".");
		} else {
			buyer.sendMessage(ChatColor.RED + "You don't have enough money to buy that ticket!");
		}
	}

	@EventHandler
	public void onSignChangeEvent(SignChangeEvent event) {
		if(event.isCancelled()) return;




		if(!(event.getLine(0).equalsIgnoreCase("[booth]") || event.getLine(0).equalsIgnoreCase("[tickets]"))) {
			return;
		}

		Player player = event.getPlayer();


		if(!player.hasPermission("ABCTickets.booth.create")) {
			event.setLine(0, "[INVALID]");
			player.sendMessage(ChatColor.RED + "You do not have permission to create booths!");
			return;
		}


		if(event.getLine(2).isEmpty()) {
			event.setLine(0, "[INVALID]");
			player.sendMessage(ChatColor.RED + "Please fill in the ticket price on the third line!");
			return;
		}


		if(event.getLine(3).isEmpty()) {
			event.setLine(0, "[INVALID]");
			player.sendMessage(ChatColor.RED + "Please fill in the ticket name on the fourth line!");
			return;
		}


		if(event.getLine(1).isEmpty()) {
			event.setLine(1, player.getDisplayName());
			return;
		}

		player.sendMessage(ChatColor.GREEN + "Booth created.");
	}
}
