package com.festp.utils;

import java.text.DecimalFormat;
import java.util.HashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Turtle;
import org.bukkit.entity.Vex;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import com.festp.Main;

public class Utils {
	private static Main plugin;
	private static Team teamNoCollide;
	public static final double EPSILON = 0.0001;
	
	public static void setPlugin(Main pl) {
		plugin = pl;
	}

	public static Main getPlugin() {
		return plugin;
	}
	
	public static void printError(String msg) {
		plugin.getLogger().severe(msg);
	}

	public static void onEnable()
	{
		// create no collide team
		String team_name = "SLE_NoCollide"; // SLE is SaddleLeadExtended, limit of 16 characters
		Server server = plugin.getServer();
		Scoreboard sb = server.getScoreboardManager().getMainScoreboard();
		teamNoCollide = sb.getTeam(team_name);
		if (teamNoCollide == null)
			teamNoCollide = sb.registerNewTeam(team_name);
		teamNoCollide.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
	}
	public static void onDisable()
	{
		teamNoCollide.unregister(); // if plugin will be removed anywhen
	}
	
	public static void setNoCollide(Entity e, boolean val)
	{
		String entry = e.getUniqueId().toString();
		if (val) {
			if (!teamNoCollide.hasEntry(entry))
				teamNoCollide.addEntry(entry);
		} else
			teamNoCollide.removeEntry(entry);
	}
	
	public static void noGravityTemp(LivingEntity e, int ticks)
	{
		e.removePotionEffect(PotionEffectType.LEVITATION);
		if (ticks > 0)
			e.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, ticks, 255));
	}
	
	public static String toString(Vector v) {
		if (v == null)
			return "(null)";
		DecimalFormat dec = new DecimalFormat("#0.00");
		return ("("+dec.format(v.getX())+"; "
				  +dec.format(v.getY())+"; "
				  +dec.format(v.getZ())+")")
				.replace(',', '.');
	}
	public static String toString(Location l) {
		if (l == null) return toString((Vector)null);
		return toString(new Vector(l.getX(), l.getY(), l.getZ()));
	}
	public static String toString(Block b) {
		if (b == null) return toString((Location)null);
		return toString(b.getLocation());
	}
	
	public static <T extends LivingEntity> T spawnBeacon(Location l, Class<T> entity_type, String beacon_id, boolean gravity) {
 		T new_beacon =  l.getWorld().spawn(l, entity_type, (beacon) ->
 		{
 			if (entity_type == Vex.class)
 				beacon.getEquipment().setItemInMainHand(null); // must be applied immediately
 	 		
 			beacon.setInvisible(true);
 	 		beacon.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100000000, 1, false, false));
 	 		beacon.setInvulnerable(true);
 	 		if (!gravity) {
 	 	 		beacon.setAI(false);
 	 	 		beacon.setGravity(false);
 	 		}
 	 		beacon.setSilent(true);
 	 		beacon.setCollidable(false);
 	 		
 	 		if (beacon instanceof Turtle) {
 	 			Turtle turtle = (Turtle)beacon;
 	 			turtle.setBaby();
 	 			turtle.setAgeLock(true);
 	 		}

 	 		if (beacon instanceof ArmorStand) {
 	 			ArmorStand stand = (ArmorStand)beacon;
 	 			stand.setVisible(false);
 	 			stand.setSmall(true);
 	 		}
 	 		setBeaconData(beacon, beacon_id); // must be applied immediately
        });
 		
 		return new_beacon;
	}
	public static void setBeaconData(LivingEntity beacon, String beaconId)
	{
		ItemStack identificator = new ItemStack(Material.BARRIER); // to identify issues
    	NamespacedKey key = new NamespacedKey(plugin, beaconId);
		ItemMeta meta = identificator.getItemMeta();
		meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte)0);
		identificator.setItemMeta(meta);
 		//if(beacon instanceof ArmorStand)
 		beacon.getEquipment().setChestplate(identificator);
 		//else
 	 	//	beacon.getEquipment().setHelmet(identificator);
	}
	public static boolean hasBeaconData(LivingEntity beacon, String beaconId)
	{
		ItemStack identificator = beacon.getEquipment().getChestplate();
		if (identificator == null || !identificator.hasItemMeta())
			return false;
    	NamespacedKey key = new NamespacedKey(plugin, beaconId);;
	 	return identificator.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
	}
	
	public static boolean contains(Object[] list, Object find)
	{
		for (Object m : list)
			if (m == find)
				return true;
		return false;
	}
	
	/**@return <b>null</b> if the <b>stack</b> was only given<br>
	 * <b>Item</b> if at least one item was dropped*/
	public static void giveOrDrop(Entity entity, ItemStack stack)
	{
		if (!(entity instanceof Player) || !((Player)entity).isOnline())
		{
			dropUngiven(entity.getLocation(), stack);
			return;
		}
		Player player = (Player)entity;
		HashMap<Integer, ItemStack> res = player.getInventory().addItem(stack);
		if (res.isEmpty())
			return;
		dropUngiven(entity.getLocation(), res.get(0));
	}
	private static Item dropUngiven(Location l, ItemStack stack)
	{
		Item item = l.getWorld().dropItem(l, stack);
		item.setVelocity(new Vector());
		item.setPickupDelay(0);
		return item;
	}
}
