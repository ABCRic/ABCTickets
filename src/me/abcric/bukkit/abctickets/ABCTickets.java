package me.abcric.bukkit.abctickets;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.sk89q.worldguard.bukkit.WGBukkit;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

public class ABCTickets extends JavaPlugin {
	public Map<Player, List<String>> tickets = new HashMap<>(); // Tickets owned by each player
	public Map<ProtectedRegion, List<String>> RequiredTickets = new HashMap<>(); // Tickets required per region
	public FileConfiguration config; // Plugin configuration
	public static Economy eco; // Economy instance for money operations

	@Override
	public void onEnable() {
		getLogger().info("Starting up...");

		// load economy from Vault
		getLogger().info("Setting up economy...");
		if (!setupEconomy()) {
			getLogger().severe("Vault not found, launch failed.");
			getLogger().severe("This plugin requires Vault. Please obtain Vault before using this plugin.");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		// load config
		getLogger().info("Loading config...");
		saveDefaultConfig();
		config = getConfig();
		getConfig().options().copyDefaults(true);
		saveConfig();
		loadRequiredTickets();

		// setup listeners and commands
		getServer().getPluginManager().registerEvents(new ABCTicketsListener(this), this);
		getCommand("abctickets").setExecutor(new ABCTicketsCommandExecutor(this));

		getLogger().info("Enabled.");
	}

	/**
	 * Load tickets for all regions.
	 */
	private void loadRequiredTickets() {
		if(!getConfig().contains("regions")) return;
		Set<String> keys = ((MemorySection)getConfig().get("regions")).getKeys(false);
		if(keys != null) {
			for(String s : keys) {
				RequiredTickets.put(WGBukkit.getRegionManager(getServer().getWorlds().get(0)).getRegion(s), getConfig().getStringList("regions." + s));
			}
		}
	}

	/**
	 * Setup economy using Vault
	 * @return True if economy was successfully setup. False otherwise.
	 */
	private boolean setupEconomy() {
		if (getServer().getPluginManager().getPlugin("Vault") == null) {
			return false;
		}
		RegisteredServiceProvider<Economy> rsp = getServer()
				.getServicesManager().getRegistration(Economy.class);
		if (rsp == null) {
			return false;
		}
		eco = rsp.getProvider();
		return eco != null;
	}
}