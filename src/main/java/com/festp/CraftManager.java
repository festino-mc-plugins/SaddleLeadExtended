	package com.festp;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.festp.lead.LeashManager;

public class CraftManager implements Listener {
	public enum CraftTag { ONLY_SPECIFIC };
	
	Server server;
	Main plugin;
	
	List<NamespacedKey> recipe_keys = new ArrayList<>();
	
	public CraftManager(Main plugin, Server server) {
		this.plugin = plugin;
		this.server = server;
	}
	
	public void addCrafts() {
		addLeadCraft(3);
	}
	
	public void giveRecipe(Player p, String recipe) {
		Bukkit.getServer().dispatchCommand(p, "recipe give "+p.getName()+" "+recipe);
	}
	public void giveOwnRecipe(Player p, String recipe) {
		giveRecipe(p, plugin.getName().toLowerCase()+":"+recipe);
	}
	public void giveRecipe(HumanEntity player, NamespacedKey key) {
		player.discoverRecipe(key);
	}
	
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player p = event.getPlayer();
		for (NamespacedKey recipe_name : recipe_keys) {
			giveRecipe(p, recipe_name);
		}
	}
	
	public boolean addCraftbookRecipe(NamespacedKey key) {
		if (recipe_keys.contains(key))
			return false;
		recipe_keys.add(key);
		return true;
	}
	
	private void addLeadCraft(double lengthMult) {
		String lengthStr = ("" + lengthMult).replace('.', '_');
		String name___lead_kx = "lead_" + lengthStr +"x";

    	NamespacedKey key___lead_kx = new NamespacedKey(plugin, name___lead_kx);
		recipe_keys.add(key___lead_kx);
    	
    	// long lead
    	ItemStack lead_k = new ItemStack(Material.LEAD, 1);
    	ItemMeta lead_k_meta = lead_k.getItemMeta();
    	NamespacedKey key = new NamespacedKey(plugin, LeashManager.LENGTH_KEY);
    	lead_k_meta.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, LeashManager.DEFAULT_R * 1.25 * lengthMult);
    	lead_k_meta.setDisplayName("Long lead");
    	lead_k.setItemMeta(lead_k_meta);
    	lead_k = applyTag(lead_k, CraftTag.ONLY_SPECIFIC);
    	ShapelessRecipe lead_kx = new ShapelessRecipe(key___lead_kx, lead_k);
    	lead_kx.addIngredient(3, Material.LEAD);
    	server.addRecipe(lead_kx);
	}
	
	public ItemStack applyTag(ItemStack item, CraftTag tag)
	{
    	NamespacedKey key = new NamespacedKey(plugin, tag.toString().toLowerCase());
    	ItemMeta meta = item.getItemMeta();
    	meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte)1);
    	item.setItemMeta(meta);
		return item;
	}
	
	public boolean hasTag(ItemStack item, CraftTag tag)
	{
		NamespacedKey key = new NamespacedKey(plugin, tag.toString().toLowerCase());
    	ItemMeta meta = item.getItemMeta();
    	return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
	}
	
	//test necessary item tags (in craft grid)
	@EventHandler
    public void onPrepareItemCraft(PrepareItemCraftEvent event)
	{
		CraftingInventory ci = event.getInventory();
		ItemStack[] matrix = ci.getMatrix();

		for (int i = 0; i < matrix.length; i++) {
			if (matrix[i] != null && hasTag(matrix[i], CraftTag.ONLY_SPECIFIC))
			{
				//try to find these recipes
				ci.setResult(null);
				return;
			}
		}
	}
}
