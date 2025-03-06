package com.festp;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LeashHitch;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Turtle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;

import com.festp.lead.LeashManager;
import com.festp.lead.LeashedPlayer;
import com.festp.utils.NMSUtils;
import com.festp.utils.Utils;
import com.festp.utils.UtilsType;

public class InteractHandler implements Listener
{
	public static final String BEACON_SADDLE_ID = "saddlemob";
	public static final Class<? extends LivingEntity> BEACON_SADDLE_CLASS = Turtle.class;
	
	List<LivingEntity> worldBeacons = new ArrayList<>();
	
	Main plugin;
	Server server;
	LeashManager leashManager;
	
	public InteractHandler(Main pl, LeashManager lm) {
		this.plugin = pl;
		this.server = pl.getServer();
		this.leashManager = lm;
	}
	
	private LivingEntity spawnSaddleBeacon(Location l) {
		return Utils.spawnBeacon(l, BEACON_SADDLE_CLASS, BEACON_SADDLE_ID, false);
	}
	public boolean isSaddleBeacon(LivingEntity e) {
		return Utils.hasBeaconData(e, BEACON_SADDLE_ID);
	}
	public boolean isLeashBeacon(LivingEntity e) {
		return Utils.hasBeaconData(e, LeashedPlayer.BEACON_ID);
	}
	
	public void onTick()
	{
		List<LivingEntity> removedBeacons = new ArrayList<>();
		for (LivingEntity beacon : worldBeacons)
		{
			if (!beacon.isValid()) {
				removedBeacons.add(beacon);
				continue;
			}
			if (beacon.getFireTicks() > 0) {
				beacon.setFireTicks(0);
			}
			
			// saddled entities
			if (isSaddleBeacon(beacon)) {
				if (beacon.getPassengers().size() == 0 || beacon.getVehicle() == null) {
					beacon.remove();
					continue;
				}
				
				LivingEntity vehiclePlayer = (LivingEntity)beacon.getVehicle();
				if (vehiclePlayer.getEquipment().getHelmet() == null || vehiclePlayer.getEquipment().getHelmet().getType() != Material.SADDLE) {
					// saddle has been taken off
					beacon.remove();
					continue;
				}
				
				beacon.getAttribute(Utils.getMaxHealthAttribute()).setBaseValue( vehiclePlayer.getAttribute(Utils.getMaxHealthAttribute()).getBaseValue() );
				beacon.setHealth( vehiclePlayer.getHealth() );
				continue;
			}
			// leashed players
			else if (isLeashBeacon(beacon)) {
				if (!leashManager.isWorkaround(beacon) || !beacon.isLeashed()) {
					//System.out.print("remove leash beacon");
					beacon.getWorld().dropItem(beacon.getLocation(), new ItemStack(Material.LEAD, 1));
					beacon.remove();
				}
			}
		}
		
		for (LivingEntity beacon : removedBeacons)
			worldBeacons.remove(beacon);
		
		// System.out.print(worldBeacons.size() +"   " + world_items.size());
	}
	
	// loading new beacons(saddle/leash)
	public void addEntity(Entity e)
	{
		if (BEACON_SADDLE_CLASS.isInstance(e))
		{
			LivingEntity beacon = (LivingEntity) e;
			if (isSaddleBeacon(beacon) || isLeashBeacon(beacon))
				worldBeacons.add(beacon);
		}
	}
	
	@EventHandler
	public void onEntitySpawn(EntitySpawnEvent event) {
		addEntity(event.getEntity());
	}
	
	@EventHandler
	public void onChunkLoad(ChunkLoadEvent event) {
		for (Entity e : event.getChunk().getEntities())
			addEntity(e);
	}
	
	/** Saddled players clocks */
	@EventHandler
	public void onPlayerInteractEntity(PlayerInteractEntityEvent event)
	{
		if (event.isCancelled()) return;
		
		// TODO fix unsaddle/sit-unleash priority
		
        Entity rightclicked = event.getRightClicked();
        Player clicker = event.getPlayer();
		
        if (!isPassenger(rightclicked, clicker) && rightclicked instanceof LivingEntity)
        {
    	   ItemStack hat = ((LivingEntity)rightclicked).getEquipment().getHelmet();
    	   if (rightclicked.getPassengers().size() == 0 && hat != null && hat.getType() == Material.SADDLE)
    	   {
        	   //ride on entity
    		   LivingEntity temp = spawnSaddleBeacon(rightclicked.getLocation());
        	   rightclicked.addPassenger(temp);
        	   temp.addPassenger(clicker);
        	   return;
    	   }
        }
       
        ItemStack mainHand = event.getPlayer().getInventory().getItemInMainHand();
        ItemStack offHand = event.getPlayer().getInventory().getItemInOffHand();
        ItemStack hand = mainHand != null ? mainHand : (offHand != null ? offHand : null);
       
        boolean cancelled = leashManager.click(rightclicked, event.getPlayer(), hand);
        if (cancelled) {
        	event.setCancelled(true);
        	return;
        }
    }
	private boolean isPassenger(Entity target, Entity vehicle) {
		for (Entity passenger : vehicle.getPassengers())
			if (passenger == target)
				return true;
			else {
				boolean loop = isPassenger(target, passenger);
				if (loop)
					return true;
			}
		return false;
	}

	/** lasso */
	@SuppressWarnings("deprecation")
	@EventHandler//(ignoreCancelled = true)
	public void onPlayerInteract(PlayerInteractEvent event)
	{
		if (event.isCancelled() && !(event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_AIR))
			return;

		// TODO: cancel if clicked on leashed entity
		// jump rope and lasso
		ItemStack hand = event.getItem();
		if (hand != null && hand.getType() == Material.LEAD)
		{
			Player player = event.getPlayer();
			ItemStack leadDrops = hand.clone();
			leadDrops.setAmount(1);
			if (event.getAction() == Action.RIGHT_CLICK_BLOCK && UtilsType.isFence(event.getClickedBlock().getType())) {
				
				List <Entity> entities = player.getNearbyEntities(15, 15, 15);
				for (Entity e : entities)
					if (e instanceof LivingEntity && ((LivingEntity)e).isLeashed() && ((LivingEntity)e).getLeashHolder() == player)
						return;
				
				event.setCancelled(true);
				Location hitchLoc = event.getClickedBlock().getLocation();
				LeashHitch hitch = NMSUtils.spawnLeashHitch(hitchLoc);
				leashManager.addLeashed(hitch, player, leadDrops);
		    	if (player.getGameMode() != GameMode.CREATIVE)
		    		hand.setAmount(hand.getAmount() - 1);
			}
			else if (event.getAction() == Action.RIGHT_CLICK_AIR
					|| event.getAction() == Action.RIGHT_CLICK_BLOCK && !UtilsType.isInteractable(event.getClickedBlock().getType())) {
				leashManager.throwLasso(player, leadDrops);
		    	if (player.getGameMode() != GameMode.CREATIVE)
		    		hand.setAmount(hand.getAmount() - 1);
			}
			else if (event.getAction() == Action.LEFT_CLICK_AIR)
			{
				leashManager.throwTargetLasso(player, leadDrops);
		    	if (player.getGameMode() != GameMode.CREATIVE)
		    		hand.setAmount(hand.getAmount() - 1);
			}
		}
	}
	
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		leashManager.removeByLeashHolder(event.getPlayer());
	}

	@EventHandler
	public void onEntityUnleash(PlayerUnleashEntityEvent event) {
		leashManager.onUnleash(event);
	}
}
