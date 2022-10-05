package com.festp.lead;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import com.festp.Config;
import com.festp.utils.NMSUtils;
import com.festp.utils.Utils;
import com.festp.utils.UtilsWorld;

public class LeashManager {
	private static JavaPlugin plugin;
	
	public static final String LENGTH_KEY = "lead_length";
	public static final double DEFAULT_R = 8, DEFAULT_R_SQUARE = DEFAULT_R*DEFAULT_R,
			BREAK_AREA = 4,
			LASSO_BREAK_SQUARE = 20 * 20,
			PULL_MARGIN = 0;
	private static final double THROW_POWER = 6.5;
	private List<LeashedPlayer> leashedPlayers = new ArrayList<>();
	private List<LeashLasso> thrownLasso = new ArrayList<>();
	private static final Predicate<Entity> entity_filter = (e) -> isLeashable(e);
	
	public LeashManager(JavaPlugin plugin)
	{
		LeashManager.plugin = plugin;
		LeashLasso.setLeashManager(this);
		onEnable();
	}
	
	public void onEnable() { }
	
	public void onDisable()
	{
		for (int i = 0; i < leashedPlayers.size(); i++) {
			LeashedPlayer lp = leashedPlayers.get(i);
			lp.removeWorkaround();
		}
		leashedPlayers.clear();
		for (int i = 0; i < thrownLasso.size(); i++) {
			LeashLasso lasso = thrownLasso.get(i);
			lasso.dropLead();
			lasso.despawnLasso();
		}
		thrownLasso.clear();
	}
	
	public void tick()
	{
		for (int i = leashedPlayers.size() - 1; i >= 0; i--) {
			LeashedPlayer lp = leashedPlayers.get(i);
			if (!Config.playerLeashPlayers || !lp.tick()) {
				leashedPlayers.remove(i);
			}
		}
		for  (int i = thrownLasso.size() - 1; i >= 0; i--) {
			LeashLasso ll = thrownLasso.get(i);
			if (!ll.tick()) {
				thrownLasso.remove(i);
			}
		}
	}
	
	public boolean click(Entity rightclicked, Player clicking, ItemStack hand)
	{
		for (int i = leashedPlayers.size()-1; i >= 0; i--) {
			LeashedPlayer lp = leashedPlayers.get(i);
			if (lp.leashed == rightclicked || lp.workaround == rightclicked) {
				if (lp.isCooldownless()) {
					lp.workaround.remove();
					Utils.giveOrDrop(clicking, new ItemStack(Material.LEAD, 1));
					leashedPlayers.remove(i);
				}
				return true;
			}
		}
		
		if (hand != null && canLeash(rightclicked))
		{
			if (hand.getType() == Material.LEAD && rightclicked instanceof Player) {
				ItemStack drop = hand.clone();
				drop.setAmount(1);
	    		addLeashed(clicking, rightclicked, drop);
	        	if (clicking.getGameMode() != GameMode.CREATIVE)
	        		hand.setAmount(hand.getAmount() - 1);
	        	return true;
	    	}
		}
		return false;
	}
	
	public LeashedPlayer addLeashed(Entity holder, Entity leashed, ItemStack drops)
	{
		if (!Config.playerLeashPlayers)
			return null;
		for (LeashedPlayer lp : leashedPlayers)
			if (lp.workaround.getLeashHolder() == holder)
				return null;
		LeashedPlayer lp = new LeashedPlayer(holder, leashed, drops);
		leashedPlayers.add(lp);
		return lp;
	}
	
	public LeashedPlayer addLeashed(Entity holder, Entity leashed, ItemStack drops, int removeCooldown)
	{
		LeashedPlayer lp = addLeashed(holder, leashed, drops);
		if (lp == null)
			return null;
		lp.setRemoveCooldown(removeCooldown);
		return lp;
	}
	
	public void throwLasso(Entity holder, ItemStack leadDrops)
	{
		thrownLasso.add(new LeashLasso(holder, UtilsWorld.throwVector(holder.getLocation(), THROW_POWER), leadDrops));
	}
	
	//invoke on LEFT_CLICK with LEAD in hand
	public void throwTargetLasso(Entity holder, ItemStack leadDrops)
	{
		Location throwLoc = LeashLasso.getThrowLocation(holder);
		//raycast, search for any(not only leashable) entity or block
		RayTraceResult ray_result = holder.getWorld().rayTrace(throwLoc, UtilsWorld.throwVector(throwLoc, 1), LASSO_BREAK_SQUARE,
				FluidCollisionMode.ALWAYS, false, 0.1, entity_filter);
		Vector v = null;
		if (ray_result != null)
			v = ray_result.getHitPosition();
		if (v != null)
		{
			Location target = new Location(holder.getWorld(), v.getX(), v.getY(), v.getZ());
			Vector start_v = UtilsWorld.throwVector(throwLoc, target, THROW_POWER);
			if (start_v != null)
				thrownLasso.add(new LeashLasso(holder, start_v, leadDrops));
			else
				throwLasso(holder, leadDrops);
		}
		else
			throwLasso(holder, leadDrops);
	}
	
	public static boolean isLeashable(Entity e)
	{
		EntityType et = e.getType();
		// TODO may check if entity is already leashed
		// TODO add multi-version support
		switch (et) {
		// 1.6.1-
		case CHICKEN:
		case PIG:
		case SHEEP:
		case COW:
		case SNOWMAN:
		case WOLF:
		case MUSHROOM_COW:
		case IRON_GOLEM:
		case OCELOT:
		case CAT:
		case DONKEY:
		case HORSE:
		case MULE:
		// 1.8
		case RABBIT:
		// 1.10
		case POLAR_BEAR:
		// 1.11
		case LLAMA:
		// 1.12
		case PARROT:
		// 1.13
		case DOLPHIN:
		// 1.14
		case TRADER_LLAMA:
		case FOX:
		// 1.15
		case BEE:
		// 1.16
		case SKELETON_HORSE:
		case ZOMBIE_HORSE:
		case HOGLIN:
		case STRIDER:
		// 1.16.2
		case ZOGLIN:
		// 1.17
		case SQUID:
		case GLOW_SQUID:
		case GOAT:
		// 1.18
		case AXOLOTL:
		// 1.19
		case ALLAY:
		case FROG:
			return true;
		default:
			return false;
		}
	}
	
	public boolean canLeash(Entity e) {
		if (e instanceof LivingEntity && ((LivingEntity)e).isLeashed())
			return false;
		
		for (LeashedPlayer le : leashedPlayers)
			if (le.leashed == e)
				return false;
		
		return isLeashable(e);
	}
	
	public boolean isWorkaround(Entity e)
	{
		for (LeashedPlayer lp : leashedPlayers) {
			if (lp.workaround == e) {
				return true;
			}
		}
		return false;
	}
	
	public void removeByLeashHolder(Player p)
	{
		for (int i = leashedPlayers.size() - 1; i >= 0; i--) {
			LeashedPlayer lp = leashedPlayers.get(i);
			if (lp.workaround.getLeashHolder() == p) {
				lp.workaround.remove();
				Utils.giveOrDrop(lp.leashed, new ItemStack(Material.LEAD, 1));
				leashedPlayers.remove(i);
			}
		}
	}
	
	public void onUnleash(PlayerUnleashEntityEvent event)
	{
		LivingEntity entity = (LivingEntity)event.getEntity();
		//unleash without lead drop
		if ( entity.isLeashed() && NMSUtils.isLeashHitch(entity.getLeashHolder())) {
			for (int i = leashedPlayers.size() - 1; i >= 0; i--) {
				LeashedPlayer lp = leashedPlayers.get(i);
				if (lp.workaround == entity) {
					lp.removeWorkaround();
					leashedPlayers.remove(i);
				}
			}
		}
		//cancel lasso unleash
		if (Utils.hasBeaconData(entity, LeashLasso.BEACON_ID))
			event.setCancelled(true);
	}
	
	public static double getLeadLength(ItemStack lead)
	{
		if (lead == null || lead.getType() != Material.LEAD)
			return 0;

    	NamespacedKey key = new NamespacedKey(plugin, LeashManager.LENGTH_KEY);
    	PersistentDataContainer container = lead.getItemMeta().getPersistentDataContainer();
		if (!container.has(key, PersistentDataType.DOUBLE))
			return DEFAULT_R;

		return container.get(key, PersistentDataType.DOUBLE);
	}
}
