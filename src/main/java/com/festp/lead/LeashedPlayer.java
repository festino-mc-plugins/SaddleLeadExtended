package com.festp.lead;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import com.festp.utils.NMSUtils;
import com.festp.utils.Utils;

public class LeashedPlayer
{
	// because Vex, for example, have vanilla lead break mechanics
	public static final Class<? extends LivingEntity> BEACON_CLASS = Bat.class;
	public static final String BEACON_ID = "leashed_entity";
	private static final double DIST_CLIMBING = 0.3, PLAYER_CLIMBING_VELOCITY = 0.3; // TODO: DIST_CLIMBING -> around fence
	private static final float ANGLE_TOP = 5f, ANGLE_BOTTOM = 5f;
	private static final float HORIZONTAL_PULL_FACTOR = 0.05f, VERTICAL_PULL_FACTOR = 0.05f;
	private static final double EDGE_FORCE_FACTOR = 0.4d;

	private ItemStack leadDrops;
	LivingEntity workaround;
	Entity leashed;
	private double maxR, curR, breakRsquared;
	private double pullR, pullRsquared;
	private int cooldownNew = 0;
	private static final int TICKS_NEW = 60;
	private int cooldownBreak = 0;
	private static final int TICKS_BREAK = 20;
	private int cooldownRemove = 0;
	private boolean controlled = false; // if controlled or hanging under holder

	private Vector lastVelocity = new Vector();
	private Location lastLoc;
	
	public LeashedPlayer(Entity holder, Entity leashed, ItemStack drops)
	{
		cooldownBreak = TICKS_BREAK;
		workaround = (LivingEntity) Utils.spawnBeacon(leashed.getLocation(), BEACON_CLASS, BEACON_ID, false);
		this.leashed = leashed;
		workaround.setLeashHolder(holder);
		lastLoc = leashed.getLocation();
		leadDrops = drops;
		maxR = LeashManager.getLeadLength(leadDrops);
		curR = maxR;
		recalcPulling();
		//double break_R = maxR + Math.sqrt(2 * maxR); // 8 -> 12, 30 -> ~38, 100 -> ~114
		double breakR = maxR + 1;
		if (maxR >= 1)
			breakR += Math.log(maxR)/Math.log(2); // 8 -> 12, 30 -> ~36, 100 -> ~107,6
		breakRsquared = breakR * breakR;
	}
	
	public boolean tick()
	{
		if(cooldownNew > 0) {
			cooldownNew--;
		}
		else if(cooldownBreak > 0) {
			cooldownBreak--;
		}
		else if( !workaround.isDead() && workaround.isLeashed() && !leashed.isDead() 
				&& workaround.getWorld() == leashed.getWorld() && workaround.getLeashHolder().getWorld() == leashed.getWorld()
				&& !(leashed instanceof Player && !((Player)leashed).isOnline() ) )
		{
			double dist2 = leashed.getLocation().distanceSquared(workaround.getLeashHolder().getLocation());
				
			if (dist2 > breakRsquared && cooldownRemove <= 0)
			{
				Utils.giveOrDrop(leashed, leadDrops);
				removeWorkaround();
				return false;
			}
			else
			{
				Vector v = leashed.getVelocity();
				
				double vert = v.getY();
				if (Math.abs(vert - 2.6) < 0.4) // 2.5 in balance, 2.7 in average, 2.9 a maximum, but 2.27 also real
					v.setY(0);
				if (Math.abs(vert - 11.2) < 1) // Spigot high level levitation workaround
					v.setY(0);
					
				if (dist2 > pullRsquared)
				{
					v = getVelocityToLeashHolder();
				}
				
				//System.out.printf("   v 1: "+Utils.toString(leashed.getVelocity())+"   " + leashed.getVelocity().getY());
				Vector totalVelocity = applyPlayerPullVelocity(v);
				if (!leashed.getVelocity().equals(totalVelocity))
					leashed.setVelocity(totalVelocity);
				//System.out.printf("   v 2: "+Utils.toString(leashed.getVelocity()));

				if (leashed instanceof LivingEntity)
					if (isUnderHolder() && (controlled || dist2 > pullRsquared))
						setGravity(false);
					else
						setGravity(true);
			}
			
			if (leashed instanceof Player) {
				Player p = (Player)leashed;
				if (p.isSneaking()) {
					cooldownNew = TICKS_NEW;
					Utils.giveOrDrop(leashed, leadDrops); // because of cooldown
					removeWorkaround();
				}
			}
			
			if (cooldownRemove > 0)
			{
				if (dist2 <= pullRsquared)
					cooldownRemove = 0;
				cooldownRemove--;
				// transition to vanilla lead
				if (cooldownRemove <= 0 && leashed instanceof LivingEntity && LeashManager.isLeashable(leashed)) 
				{
					((LivingEntity)leashed).setLeashHolder(workaround.getLeashHolder());
					removeWorkaround();
				}
			}
		}
		else
		{
			if (!workaround.isDead() && workaround.isLeashed())
				Utils.giveOrDrop(leashed, leadDrops);
			removeWorkaround();
			return false;
		}

		workaround.teleport(leashed);
		lastLoc = leashed.getLocation();
		lastVelocity = leashed.getVelocity();
		workaround.setVelocity(lastVelocity); // more actual leash render
		return true;
	}
	
	public void setRemoveCooldown(int ticks)
	{
		cooldownRemove = ticks;
	}
	
	private Vector getVelocityToLeashHolder()
	{
		Location locHolder = workaround.getLeashHolder().getLocation();
		Location locLeashed = leashed.getLocation();
		double dx = locHolder.getX() - locLeashed.getX(),
			   dy = locHolder.getY() - locLeashed.getY(),
			   dz = locHolder.getZ() - locLeashed.getZ();
		Vector velocityEdge = new Vector( dx, dy, dz );
		double dlen = velocityEdge.length() - curR;
		velocityEdge.multiply(new Vector(HORIZONTAL_PULL_FACTOR, VERTICAL_PULL_FACTOR, HORIZONTAL_PULL_FACTOR));
		velocityEdge.normalize();
		Vector velocity = velocityEdge.clone();
		velocityEdge.multiply(EDGE_FORCE_FACTOR);
		velocity.multiply(dlen);
		velocity.multiply(new Vector(HORIZONTAL_PULL_FACTOR, VERTICAL_PULL_FACTOR, HORIZONTAL_PULL_FACTOR));
		velocity.add(velocityEdge);

		RayTraceResult ray_res = leashed.getWorld().rayTraceBlocks(locLeashed, velocity,
				leashed.getWidth()/2 + Utils.EPSILON, FluidCollisionMode.NEVER);
		if (ray_res != null && ray_res.getHitBlock() != null)
			if(leashed.isOnGround() || velocity.getY() < 0.3)
				velocity.add(new Vector(0, 0.3, 0));
		
		return velocity;
	}

	// get player influence if player is under or is above leash holder
	// head angles(pitch): stop swinging, climb up
	private Vector applyPlayerPullVelocity(Vector v)
	{
		if (leashed instanceof Player) // can't test sprint because it fires only on move
		{
			Location cam = leashed.getLocation();
			float pitch = cam.getPitch();
			double lengthChange;
			// stop swinging (5 degrees from the bottom)
			if (pitch >= 90 - ANGLE_BOTTOM)
			{
				lengthChange = Math.max(0, maxR - curR);
				lengthChange = Math.min(PLAYER_CLIMBING_VELOCITY, lengthChange);
				curR += lengthChange;
				recalcPulling();
				double minDown = Math.max(v.getY() - lengthChange, -lengthChange); // limited min velocity
				double totalDown = Math.min(v.getY(), minDown); // if current velocity lower than player can
				v.setY(totalDown);
				controlled = true;
			}
			// climb up (5 degrees from the top)
			else if (pitch <= -90 + ANGLE_TOP)
			{
				if (isUnderHolder())
				{
					lengthChange = Math.max(0, curR - leashed.getHeight() - 0.5);
					lengthChange = Math.min(PLAYER_CLIMBING_VELOCITY, lengthChange);
					curR -= lengthChange;
					recalcPulling();
					double maxUp = Math.min(v.getY() + lengthChange, lengthChange); // limited max velocity
					double totalUp = Math.max(v.getY(), maxUp); // if current velocity higher than player can
					v.setY(totalUp);
					controlled = true;
				}
			}
		}
		
		return v;
	}
	private void recalcPulling() {
		pullR = curR - LeashManager.PULL_MARGIN;
		pullRsquared = pullR * pullR;
	}
	
	private boolean isUnderHolder() {
		Location dxz = leashed.getLocation().subtract(workaround.getLeashHolder().getLocation());
		dxz.setY(0);
		return dxz.lengthSquared() < DIST_CLIMBING * DIST_CLIMBING;
	}
	
	public void setGravity(boolean isGravityEnabled)
	{
		if (leashed instanceof LivingEntity)
			if (isGravityEnabled)
				Utils.noGravityTemp((LivingEntity)leashed, 0);
			else
				Utils.noGravityTemp((LivingEntity)leashed, 50);
	}

	public boolean isCooldownless() {
		return cooldownNew == 0 && cooldownBreak == 0;
	}
	
	public void removeWorkaround() {
		setGravity(true);
		if(workaround.isLeashed() && NMSUtils.isLeashHitch(workaround.getLeashHolder()))
			workaround.getLeashHolder().remove();
		workaround.remove();
	}
	
}
