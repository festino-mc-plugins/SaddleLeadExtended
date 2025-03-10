package com.festp.mount;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.PolarBear;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Consumer;
import org.bukkit.util.Vector;

import com.festp.utils.Utils;

public class SaddledBear {
	private static final LootTable EMPTY_LOOTTABLE = LootTables.BAT.getLootTable();
	private static final PotionEffect EFFECT_INVISIBILITY = new PotionEffect(PotionEffectType.INVISIBILITY, 10000, 0, false, false, false);
	private static final PotionEffect EFFECT_IMMORTALITY = new PotionEffect(PotionEffectType.RESISTANCE, 5, 5, false, false, false);
	private static final PotionEffect EFFECT_WITHER = new PotionEffect(PotionEffectType.WITHER, 10000, 10, false, false, false);
	private static final Vector SADDLE_SHIFT = new Vector(0, 0.55, 0);
	private static final double JUMP_STRENGTH = 0.43;
	private static final double JUMP_POWER_THRESHOLD = 0.55; // min 4/9
	private static final String LINK_LORE = "linked_to_bear";
	private static final double HORSE_SPEED_MULTIPLIER = 0.4;
	
	private PolarBear bear;
	private Horse controller;
	private Pig saddlePig;

	private static final int START_DELAY = 20;
	private int cooldown = START_DELAY;
	
	public SaddledBear(PolarBear saddled)
	{
		bear = saddled;
		saddlePig = bear.getWorld().spawn(bear.getLocation().add(SADDLE_SHIFT), Pig.class, new Consumer<Pig>() {
			@Override
			public void accept(Pig pig) {
				pig.setGravity(false);
				pig.setAI(false);
				pig.setSilent(true);
				pig.setSaddle(true);
				pig.setLootTable(EMPTY_LOOTTABLE); 
				Utils.setNoCollide(pig, true);
				applyInvisibility(pig);
				link(pig);
			} });
	}
	
	public List<Entity> getPassengers()
	{
		// add second passenger to list
		return bear.getPassengers();
	}
	
	public void addPassenger(Entity entity)
	{
		if (cooldown > 0)
			return;
		if (controller != null) {
			// add second passenger?
			return;
		}
		controller = bear.getWorld().spawn(bear.getLocation(), Horse.class, new Consumer<Horse>() {
			@Override
			public void accept(Horse horse) {
				horse.setSilent(true);
				Utils.setNoCollide(horse, true);
				horse.setAdult();
				horse.setTamed(true);
				horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
				horse.setLootTable(EMPTY_LOOTTABLE);
				horse.setRemoveWhenFarAway(true);
				applyInvisibility(horse);
				double bearSpeed = bear.getAttribute(Utils.getMovementSpeedAttribute()).getBaseValue() * HORSE_SPEED_MULTIPLIER;
				double bearHealth = bear.getAttribute(Utils.getMaxHealthAttribute()).getBaseValue();
				horse.getAttribute(Utils.getMovementSpeedAttribute()).setBaseValue(bearSpeed);
				horse.getAttribute(Utils.getMaxHealthAttribute()).setBaseValue(bearHealth);
				horse.setJumpStrength(JUMP_STRENGTH);
				link(horse);
				horse.addPassenger(entity);
			} });
		updateHealth();
	}
	
	public boolean update()
	{
		if (cooldown > 0)
			cooldown--;
		if (!bear.isValid() || bear.isDead()) {
			remove();
			if (bear.isDead())
				bear.getWorld().dropItemNaturally(bear.getLocation(), new ItemStack(Material.SADDLE));
			return false;
		}
		if (controller != null) {
			if (controller.getPassengers().size() == 0) {
				controller.remove();
				controller = null;
			} else {
				updateHealth();
				applyInvisibility(controller);
				stopBurning(controller);
				bear.teleport(controller.getLocation()); // TODO try move 1 tick forward, if it is not in solid or lava
				bear.setVelocity(controller.getVelocity());
			}
		}
		applyInvisibility(saddlePig);
		applyImmortality(saddlePig);
		//applyWither(visual);
		stopBurning(saddlePig);
		saddlePig.teleport(bear.getLocation().add(SADDLE_SHIFT));
		saddlePig.setVelocity(bear.getVelocity());
		return true;
	}
	
	private void updateHealth() {
		controller.setHealth(bear.getHealth());
	}
	
	public PolarBear getBear() {
		return bear;
	}
	
	public Pig getSaddle() {
		return saddlePig;
	}
	
	public Horse getHorse() {
		return controller;
	}
	
	public boolean isPart(Entity entity) {
		return entity.equals(bear)
				|| entity.equals(controller)
				|| entity.equals(saddlePig);
	}
	
	public void remove() {
		if (controller != null)
			controller.remove();
		saddlePig.remove();
	}

	public void onJump(float power) {
		if (power < JUMP_POWER_THRESHOLD) {
			bear.getWorld().playSound(bear.getEyeLocation(), Sound.ENTITY_POLAR_BEAR_WARNING, SoundCategory.PLAYERS, 0.5f, 1.0f);
		}
	}
	
	public static void stopBurning(Entity entity) {
		if (entity.getFireTicks() > 0)
			entity.setFireTicks(0);
	}
	
	public static void applyInvisibility(LivingEntity le) {
		le.setInvisible(true);
		le.addPotionEffect(EFFECT_INVISIBILITY);
	}
	
	public static void applyImmortality(LivingEntity le) {
		le.addPotionEffect(EFFECT_IMMORTALITY);
	}
	
	public static void applyWither(LivingEntity le) {
		le.addPotionEffect(EFFECT_WITHER);
	}
	
	public static void setSaddled(PolarBear bear, boolean saddled) {
		if (saddled) {
			bear.getEquipment().setChestplate(new ItemStack(Material.SADDLE));
		} else {
			bear.getEquipment().setChestplate(new ItemStack(Material.AIR));
		}
	}
	
	public static boolean isSaddled(PolarBear bear) {
		return bear.getEquipment().getChestplate().getType() == Material.SADDLE;
	}

	private static boolean isLinked(Entity entity) {
		if (!(entity instanceof LivingEntity))
			return false;
		ItemStack helmet = ((LivingEntity) entity).getEquipment().getHelmet();
		return helmet != null && helmet.hasItemMeta()
				&& helmet.getItemMeta().getLore() != null
				&& helmet.getItemMeta().getLore().size() >= 1
				&& helmet.getItemMeta().getLore().get(0).equals(LINK_LORE);
	}
	
	private void link(LivingEntity le) {
		ItemStack helmet = new ItemStack(Material.OAK_BUTTON);
		ItemMeta meta = helmet.getItemMeta();
		meta.setLore(Arrays.asList(LINK_LORE, bear.getUniqueId().toString()));
		helmet.setItemMeta(meta);
		le.getEquipment().setHelmet(helmet);
	}

	public static void removeLinkedEntities(Chunk chunk) {
		Entity[] entities = chunk.getEntities();
		for (int i = entities.length - 1; i >= 0; i--) {
			Entity en = entities[i];
			removeIfLinkedEntity(en);
		}
	}
	public static void removeIfLinkedEntity(Entity entity) {
		if (isLinked(entity)) {
			entity.remove();
		}
	}
}
