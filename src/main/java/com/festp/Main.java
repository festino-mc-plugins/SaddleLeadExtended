package com.festp;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.festp.lead.LeashManager;
import com.festp.mount.MountManager;
import com.festp.utils.Utils;

public class Main extends JavaPlugin
{
	Config conf;
	private CraftManager craftManager;
	
	LeashManager leashManager;
	
	public void onEnable() {
		Logger.setLogger(getLogger());
    	PluginManager pm = getServer().getPluginManager();
    	
		Utils.setPlugin(this);
		Utils.onEnable();
		
		conf = new Config(this);
		Config.loadConfig();
    	craftManager = new CraftManager(this, getServer());

    	leashManager = new LeashManager(this);
    	MountManager mountManager = new MountManager();
    	pm.registerEvents(mountManager, this);
    	
    	InteractHandler interacts = new InteractHandler(this, leashManager);
    	pm.registerEvents(interacts, this);
    	
    	craftManager.addCrafts();
    	pm.registerEvents(craftManager, this);
    	
		Bukkit.getScheduler().scheduleSyncRepeatingTask(this,
			new Runnable() {
				public void run() {
					TaskList.tick();
					
					// saddled player hp updating
					interacts.onTick();
					
					// update saddled bears visual/hp
					mountManager.tick();
					
					// lasso and jumping rope
					leashManager.tick();
				}
			}, 0L, 1L);
		
	}
	
	public CraftManager getCraftManager()
	{
		return craftManager;
	}
	
	public void onDisable()
	{
		leashManager.onDisable();
		Utils.onDisable();
	}
}
