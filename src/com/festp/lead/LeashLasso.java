package com.festp.lead;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LeashHitch;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Turtle;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.festp.utils.NMSUtils;
import com.festp.utils.Utils;
import com.festp.utils.UtilsType;

// spawn thrown beacon, which will die on collide(top or bottom)
// (and spawn leash hitch if collides with fence - directly or above(air, water, e.t.c);
// lose lead if lava; (despawn delay if cactus))
// returns on player quit (TO DO)
public class LeashLasso
{
	public static final String BEACON_ID = "beacon_lasso";
	private static LeashManager manager = null;
	public static final Class<? extends LivingEntity> projectile_class = Turtle.class;
	private static final Class<? extends LivingEntity> beacon_class = Bat.class; // because Turtle has vanilla leash mechanics
	private static final double LEAD_LOWERING = -0.8, FENCE_HALF_WIDTH = (4 / 16) / 2, MOB_LEASH_R = 0.1, EPSILON = 0.001;
	private static final int STICKY_DESPAWN_DELAY = 20, REMOVE_COOLDOWN = 60;
	private static final Material[] STICKY_BLOCKS = { Material.CACTUS, Material.SLIME_BLOCK };
	
	ItemStack lead_drops;
	Entity holder;
	LivingEntity projectile;
	LivingEntity workaround;
	Location last_pos;
	Vector old_velocity;
	int despawn_delay = -1;
	
	public static void setLeashManager(LeashManager manager)
	{
		if(LeashLasso.manager == null)
			LeashLasso.manager = manager;
	}
	
	public LeashLasso(Entity holder, Vector velocity, ItemStack drops)
	{
		this.holder = holder;
		old_velocity = velocity;
		Location spawn_loc = getThrowLocation(holder);
		last_pos = spawn_loc;
		
		projectile = Utils.spawnBeacon(spawn_loc, projectile_class, BEACON_ID, true);
		projectile.setGravity(true);
		projectile.setVelocity(velocity);
		Utils.setNoCollide(projectile, true);
		
		//because beacon is armorstand that can't draw leash
		workaround = Utils.spawnBeacon(spawn_loc, beacon_class, BEACON_ID, false);
		workaround.setLeashHolder(holder);
		
		lead_drops = drops;

		//if top of projectile in sticky block
		if (Utils.contains(STICKY_BLOCKS, projectile.getLocation().add(0, projectile.getHeight(), 0).getBlock().getType())) {
			process_sticky();
		}
	}
	
	/** if lasso returns, lead will be dropped, else spawn hitch or leash mob or lasso dies
	  * @return <b>true</b> - if lasso will be alive this tick */
	public boolean tick()
	{
		if (despawn_delay >= 0) {
			if(despawn_delay > 0) {
				despawn_delay--;
				return true;
			}
			else {
				dropLead();
				despawnLasso();
				return false;
			}
		}
		teleport_workaround();
		workaround.setVelocity(projectile.getVelocity());
		Location current_pos = projectile.getLocation();
		boolean on_ground = projectile.isOnGround();
			
		Block current = projectile.getLocation().getBlock();
		//unleash/break distance -> return lead
		if (!workaround.isLeashed() || projectile.getWorld() != holder.getWorld() || workaround.getWorld() != holder.getWorld()) {
			despawnLasso();
			return false;
		}
		if (holder instanceof Player && !((Player)holder).isOnline()
				|| projectile.getLocation().distanceSquared(holder.getLocation()) > LeashManager.LASSO_BREAK_SQUARE
				|| current.getType() == Material.WATER) {
			dropLead();
			despawnLasso();
			return false;
		}
		//collide cactus/slime block -> return with delay
		if (getFacedBlock(last_pos, STICKY_BLOCKS) != null) {
			process_sticky();
			return true;
		}
		//collide lava/fire -> kill
		else if (current.getType() == Material.LAVA || current.getType() == Material.FIRE) {
			despawnLasso();
			return false;
		}
		//collide top/bottom -> return lead
		if (projectile.getVelocity().lengthSquared() < EPSILON) {
			Location beacon_top = projectile.getLocation().add(0, projectile.getHeight()*0.5, 0);
			Location ceiling = current.getRelative(BlockFace.UP).getLocation();
			if (ceiling.subtract(beacon_top).getY() < EPSILON)
			{
				dropLead();
				despawnLasso();
				return false;
			}
		}
		
		//collide with fence -> hitch
		if (UtilsType.isFence(current.getType())) {
			if (isFacedFence(projectile.getLocation(), projectile.getVelocity()))
			{
				spawnLeashHitch(current);
				despawnLasso();
				return false;
			}
		}
		if (on_ground) {
			if (UtilsType.isFence(current.getRelative(BlockFace.DOWN).getType()) && UtilsType.isAir(current.getType()))
				spawnLeashHitch(current.getRelative(BlockFace.DOWN));
			else
				dropLead();
			despawnLasso();
			return false;
		}
		
		for (Entity e : projectile.getNearbyEntities(MOB_LEASH_R, MOB_LEASH_R, MOB_LEASH_R))
			if (e instanceof LivingEntity && manager.canLeash(e))
			{
				leashEntity((LivingEntity)e);
				despawnLasso();
				return false;
			}
		
		last_pos = current_pos;
		old_velocity = projectile.getVelocity();
		return true;
	}
	
	public void dropLead()
	{
		Utils.giveOrDrop(holder, lead_drops);
	}
	public void despawnLasso()
	{
		workaround.remove();
		projectile.remove();
	}
	
	private void leashEntity(LivingEntity e)
	{
		manager.addLeashed(holder, e, lead_drops, REMOVE_COOLDOWN);
	}
	
	private void spawnLeashHitch(Block b)
	{
		Location hitch_loc = b.getLocation();
		LeashHitch hitch = NMSUtils.spawnLeashHitch(hitch_loc);
		manager.addLeashed(hitch, holder, lead_drops, REMOVE_COOLDOWN);
		despawnLasso();
	}
	
	private void teleport_workaround()
	{
		workaround.teleport(projectile.getLocation().add(0, LEAD_LOWERING, 0));
	}
	
	private void process_sticky()
	{
		projectile.setGravity(false);
		projectile.teleport(getFacingLocation());
		projectile.setVelocity(new Vector());
		teleport_workaround();
		despawn_delay = STICKY_DESPAWN_DELAY;
	}
	
	public static Location getThrowLocation(Entity holder)
	{
		return holder.getLocation().add(0, holder.getHeight() * 0.9, 0); //Eye location for any Entity, not only LivingEntity
	}
	
	/** @return <b>true</b> - true if moves to X/Z center of block */
	private static boolean isToCenter(Location loc, Vector velocity)
	{
		Location center = loc.getBlock().getLocation().add(0.5, 0.5, 0.5);
		Location shift = center.subtract(loc);
		return shift.getX()*velocity.getX() >= 0 && shift.getZ()*velocity.getZ() >= 0;
	}
	private static double axis_dist(Location loc)
	{
		Location center = loc.getBlock().getLocation().add(0.5, 0.5, 0.5);
		Location shift = center.subtract(loc);
		return Math.min(Math.abs(shift.getX()), Math.abs(shift.getZ()));
	}
	private boolean isFacedFence(Location loc, Vector velocity)
	{
		return axis_dist(loc) + EPSILON <= projectile.getWidth() * 0.5 + FENCE_HALF_WIDTH /*&& isToCenter(loc, velocity)*/;
	}
	
	private boolean almostZero(double d)
	{
		return -EPSILON < d && d < EPSILON;
	}
	
	private Block getFacedBlock(Location loc, Material[] type)
	{
		Vector new_v = projectile.getVelocity();
		double x1 = old_velocity.getX(), x2 = new_v.getX();
		double y1 = old_velocity.getY(), y2 = new_v.getY();
		double z1 = old_velocity.getZ(), z2 = new_v.getZ();
		Block main_block = loc.getBlock();
		//x moving had stopped
		if (!almostZero(x1) && almostZero(x2)) {
			Block faced;
			if (x1 < 0)
				faced = main_block.getRelative(-1, 0, 0);
			else
				faced = main_block.getRelative(1, 0, 0);
			if (Utils.contains(type, faced.getType()))
				return faced;
		}
		//y moving had stopped
		//y rebound
		if (!almostZero(y1) && almostZero(y2) || y2 - y1 > 0) {
			Block faced;
			if (y1 < 0)
				faced = main_block.getRelative(0, -1, 0);
			else
				faced = main_block.getRelative(0, 1, 0);
			if (Utils.contains(type, faced.getType()))
				return faced;
		}
		//z moving had stopped
		if (!almostZero(z1) && almostZero(z2)) {
			Block faced;
			if (z1 < 0)
				faced = main_block.getRelative(0, 0, -1);
			else
				faced = main_block.getRelative(0, 0, 1);
			if (Utils.contains(type, faced.getType()))
				return faced;
		}
		return null;
	}
	private Location getFacingLocation()
	{
		double v1 = old_velocity.lengthSquared();
		double v2 = projectile.getVelocity().lengthSquared();
		Location result = last_pos.clone();
		//(loc_old * v_old + loc_new * v_new) / (v_old + v_new)
		result.multiply(v1).add(projectile.getLocation().multiply(v2)).multiply(1 / (v1 + v2));
		
		Vector new_v = projectile.getVelocity();
		double x1 = old_velocity.getX(), x2 = new_v.getX();
		double y1 = old_velocity.getY(), y2 = new_v.getY();
		double z1 = old_velocity.getZ(), z2 = new_v.getZ();
		//x moving had stopped
		if (!almostZero(x1) && almostZero(x2)) {
			if (x1 < 0)
				result.setX(Math.floor(result.getX()));
			else
				result.setX(Math.ceil(result.getX()));
		}
		//y moving had stopped
		//y rebound
		if (!almostZero(y1) && almostZero(y2) || y1 * y2 < 0) {
			if (y1 < 0)
				result.setY(Math.floor(result.getY()));
			else
				result.setY(Math.ceil(result.getY()));
		}
		//z moving had stopped
		if (!almostZero(z1) && almostZero(z2)) {
			if (z1 < 0)
				result.setZ(Math.floor(result.getZ()));
			else
				result.setZ(Math.ceil(result.getZ()));
		}
		return result;
	}
}
