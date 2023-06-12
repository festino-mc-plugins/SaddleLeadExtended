package com.festp.utils;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LeashHitch;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

import net.minecraft.core.BlockPosition;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.entity.decoration.EntityLeash;

public class NMSUtils
{
	private static final String BUKKIT_PACKAGE = "org.bukkit.craftbukkit.";
	
	/** format: "entity.CraftHorse" or "org.bukkit.craftbukkit.v1_18_R1.entity.CraftHorse" */
	private static Class<?> getBukkitClass(String name)
	{
		if (!name.startsWith(BUKKIT_PACKAGE)) {
			String version = Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3];
		    name = BUKKIT_PACKAGE + version + "." + name;
		}
		
		try {
			return Class.forName(name);
		} catch (ClassNotFoundException e) {
			return null;
		}
	}
	
	public static boolean isLeashHitch(Entity entity)
	{
		Class<?> clazz = getBukkitClass("entity.CraftLeash");
		return entity instanceof LeashHitch || clazz.isInstance(entity);//entity instanceof CraftLeash;//LeashHitch;
	}
	
	public static LeashHitch spawnLeashHitch(Location hitchLoc)
	{
		hitchLoc = hitchLoc.getBlock().getLocation();
		
		// neat way => "Unable to get CCW facing of up/down"
		//System.out.println(hitch_loc);
		//LeashHitch hitch = (LeashHitch) hitch_loc.getWorld().spawnEntity(hitch_loc, EntityType.LEASH_HITCH);//.spawn(hitch_loc, LeashHitch.class);

		/*
		// bugfix - edited code from: https://www.spigotmc.org/threads/1-13-2-exception-when-spawnentity-of-entitytype-leash_hitch.393082/
		// save fence type
		Material m_fence = hitch_loc.getBlock().getType();
		// select blocks, save it and set to air
		// block below
		hitch_loc.add(0, -1, 0);
		BlockData data_down = hitch_loc.getBlock().getBlockData();
		hitch_loc.getBlock().setType(Material.AIR);
		// block above
		hitch_loc.add(0, 2, 0);
		BlockData data_top = hitch_loc.getBlock().getBlockData();
		hitch_loc.getBlock().setType(Material.AIR);

		// select the fence block and update
		hitch_loc.add(0, -1, 0);
		hitch_loc.getBlock().setType(Material.AIR);
		hitch_loc.getBlock().setType(m_fence);
		
		LeashHitch hitch = hitch_loc.getWorld().spawn(hitch_loc, LeashHitch.class);

		// select blocks and back up data
		// block below
		hitch_loc.add(0, -1, 0);
		hitch_loc.getBlock().setBlockData(data_down);
		// block above
		hitch_loc.add(0, 2, 0);
		hitch_loc.getBlock().setBlockData(data_top);
		
		// bugs: chest below or above will drop its items
		*/
		
		// workaround from
		// https://hub.spigotmc.org/jira/browse/SPIGOT-4674?focusedCommentId=32808&page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel#comment-32808
		WorldServer nmsWorld;
		try {
			Class<?> craftWorldClass = getBukkitClass("CraftWorld");
			Object craftWorld = craftWorldClass.cast(hitchLoc.getWorld());
			Method getHandleMethod = craftWorldClass.getMethod("getHandle");
			nmsWorld = (WorldServer)getHandleMethod.invoke(craftWorld);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

		BlockPosition nmsPos = new BlockPosition((int)hitchLoc.getX(), (int)hitchLoc.getY(), (int)hitchLoc.getZ());
		EntityLeash nmsLeashHitch = new EntityLeash(nmsWorld, nmsPos);
		nmsWorld.addFreshEntity(nmsLeashHitch, SpawnReason.DEFAULT); // is equal to .b(nmsLeashHitch)
		LeashHitch hitch = (LeashHitch) nmsLeashHitch.getBukkitEntity();
		
		return hitch;
	}
}
