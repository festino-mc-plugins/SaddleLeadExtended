package com.festp;

import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class Config {
	
	private static JavaPlugin plugin;
	private static MemoryConfiguration c;

	public static boolean playerLeashPlayers = false;
	
	public Config(JavaPlugin jp) {
		this.plugin = jp;
		this.c = jp.getConfig();
	}
	
	public static void loadConfig()
	{
		c.addDefault("player-leash-players", true);
		c.options().copyDefaults(true);
		plugin.saveConfig();
		//getConfig().save(file);

		/// config reload
		Config.playerLeashPlayers = plugin.getConfig().getBoolean("player-leash-players");

		Logger.info("Config reloaded.");
	}
	
	public static void saveConfig()
	{
		c.set("player-leash-players", Config.playerLeashPlayers);

		plugin.saveConfig();

		Logger.info("Config successfully saved.");
	}
	
	public static JavaPlugin plugin() {
		return plugin;
	}
}
