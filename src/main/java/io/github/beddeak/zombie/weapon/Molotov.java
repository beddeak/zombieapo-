package io.github.beddeak.zombie.weapon;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.ability.Effects;
import io.github.beddeak.zombie.config.Settings;
import io.github.beddeak.zombie.zombie.Keys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

public final class Molotov {
    public static final int MAX_STACK = 16;
    private static final Color FIRE_RESISTANCE = Color.fromRGB(0xFF9900);
    private static final double VIEW_DISTANCE = 96;
    private final Apocalypse core;
    private final Map<UUID, Burn> burning = new HashMap<>();

    public Molotov(Apocalypse core) {
        this.core = core;
    }

    public static ItemStack create(int amount) {
        ItemStack item = ItemStack.of(Material.SPLASH_POTION);
        item.editMeta(PotionMeta.class, meta -> {
            meta.setColor(FIRE_RESISTANCE);
            meta.setMaxStackSize(MAX_STACK);
            meta.displayName(Component.text("화염병", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(line("던지면 깨지면서 바닥에 불이 번집니다"), line("탱크는 불에 약합니다")));
            meta.getPersistentDataContainer().set(Keys.MOLOTOV, PersistentDataType.BYTE, (byte) 1);
        });
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.POTION_CONTENTS).build());
        item.setAmount(Math.max(1, Math.min(MAX_STACK, amount)));
        return item;
    }

    public static boolean is(ItemStack item) {
        return item != null && !item.isEmpty() && item.getPersistentDataContainer().has(Keys.MOLOTOV, PersistentDataType.BYTE);
    }

    public static ShapelessRecipe recipe() {
        ShapelessRecipe recipe = new ShapelessRecipe(Keys.MOLOTOV, create(1));
        recipe.addIngredient(Material.FLINT);
        recipe.addIngredient(Material.STRING);
        recipe.addIngredient(Material.GLASS_BOTTLE);
        return recipe;
    }

    private static Component line(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    public void landed(ThrownPotion potion, ProjectileHitEvent event) {
        World world = potion.getWorld();
        if (!core.active(world)) { return; }
        Location center;
        if (event.getHitEntity() != null) {
            center = event.getHitEntity().getLocation();
        } else if (event.getHitBlock() != null && event.getHitBlockFace() != null) {
            center = event.getHitBlock().getRelative(event.getHitBlockFace()).getLocation().add(0.5, 0, 0.5);
        } else {
            center = potion.getLocation();
        }
        if (center.getBlock().isLiquid()) {
            world.playSound(center, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1f);
            return;
        }
        double radius = core.settings().molotov().radius();
        world.playSound(center, Sound.ITEM_FIRECHARGE_USE, 1.5f, 0.8f);
        world.spawnParticle(Particle.FLAME, center.clone().add(0, 0.5, 0), 40, radius / 2, 0.3, radius / 2, 0.05);
        UUID thrower = potion.getShooter() instanceof Player player ? player.getUniqueId() : null;
        core.effects().add(new Fire(world, center, spots(center, radius), thrower));
    }

    private void ignite(LivingEntity living, UUID thrower, Settings.Molotov options) {
        long until = core.ticks() + Math.max(1, options.burnSeconds()) * 20L;
        Burn burn = burning.get(living.getUniqueId());
        if (burn != null) {
            burn.until = Math.max(burn.until, until);
            if (thrower != null) { burn.thrower = thrower; }
            return;
        }
        burn = new Burn(living, until, thrower);
        burning.put(living.getUniqueId(), burn);
        core.effects().add(burn);
    }

    private static List<Block> spots(Location center, double radius) {
        List<Block> spots = new ArrayList<>();
        Block origin = center.getBlock();
        int reach = (int) Math.ceil(radius);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (dx * dx + dz * dz > radius * radius) { continue; }
                for (int dy = 1; dy >= -2; dy--) {
                    Block block = origin.getRelative(dx, dy, dz);
                    if (block.getType().isAir() && block.getRelative(BlockFace.DOWN).getType().isSolid()) {
                        spots.add(block);
                        break;
                    }
                }
            }
        }
        return spots;
    }

    private final class Fire implements Effects.Effect {
        private final World world;
        private final Location center;
        private final List<Block> spots;
        private final UUID thrower;
        private final BlockData fire = Material.FIRE.createBlockData();
        private int ticks;

        Fire(World world, Location center, List<Block> spots, UUID thrower) {
            this.world = world;
            this.center = center;
            this.spots = spots;
            this.thrower = thrower;
        }

        @Override public World world() {
            return world;
        }

        @Override public boolean tick() {
            Settings.Molotov options = core.settings().molotov();
            if (ticks >= options.seconds() * 20) { return false; }
            if (ticks % 20 == 0) { show(); }
            if (ticks % 10 == 0) { burn(options); }
            if (!spots.isEmpty() && ticks % 2 == 0) {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                Location spot = spots.get(random.nextInt(spots.size())).getLocation().add(0.5, 0.2, 0.5);
                world.spawnParticle(Particle.FLAME, spot, 2, 0.3, 0.2, 0.3, 0.01);
                if (random.nextInt(4) == 0) { world.spawnParticle(Particle.LARGE_SMOKE, spot, 1, 0.2, 0.3, 0.2, 0.01); }
            }
            if (ticks % 30 == 0) { world.playSound(center, Sound.BLOCK_FIRE_AMBIENT, 1.5f, 1f); }
            ticks++;
            return true;
        }

        private void show() {
            for (Player player : world.getPlayers()) {
                if (player.getLocation().distanceSquared(center) > VIEW_DISTANCE * VIEW_DISTANCE) { continue; }
                for (Block spot : spots) {
                    player.sendBlockChange(spot.getLocation(), spot.getType().isAir() ? fire : spot.getBlockData());
                }
            }
        }

        private void burn(Settings.Molotov options) {
            double radius = options.radius();
            for (Entity entity : world.getNearbyEntities(center, radius, 2, radius)) {
                if (!(entity instanceof LivingEntity living) || living.isDead()) { continue; }
                double dx = living.getLocation().getX() - center.getX();
                double dz = living.getLocation().getZ() - center.getZ();
                if (dx * dx + dz * dz > radius * radius) { continue; }
                if (living instanceof Player player) {
                    if (options.burnPlayers() && player.getGameMode() != GameMode.SPECTATOR && player.getGameMode() != GameMode.CREATIVE) {
                        player.setFireTicks(Math.max(player.getFireTicks(), 100));
                    }
                    continue;
                }
                ignite(living, thrower, options);
            }
        }

        @Override public void end() {
            for (Player player : world.getPlayers()) {
                for (Block spot : spots) { player.sendBlockChange(spot.getLocation(), spot.getBlockData()); }
            }
        }
    }

    private final class Burn implements Effects.Effect {
        private final LivingEntity living;
        private long until;
        private UUID thrower;
        private int ticks;

        Burn(LivingEntity living, long until, UUID thrower) {
            this.living = living;
            this.until = until;
            this.thrower = thrower;
        }

        @Override public World world() {
            return living.getWorld();
        }

        @Override public boolean tick() {
            if (!living.isValid() || living.isDead() || living.isInWater() || core.ticks() > until) { return false; }
            if (ticks % 20 == 0) { hurt(core.settings().molotov()); }
            if (ticks % 4 == 0) {
                living.getWorld().spawnParticle(Particle.FLAME, living.getLocation().add(0, living.getHeight() / 2, 0), 3,
                        living.getWidth() / 2, living.getHeight() / 3, living.getWidth() / 2, 0.01);
            }
            ticks++;
            return true;
        }

        private void hurt(Settings.Molotov options) {
            AttributeInstance maxHealth = living.getAttribute(Attribute.MAX_HEALTH);
            double amount = options.damage() + (maxHealth == null ? 0 : maxHealth.getValue() * options.maxHealthPercent() / 100);
            if (amount <= 0) { return; }
            DamageSource.Builder source = DamageSource.builder(DamageType.IN_FIRE);
            Player player = thrower == null ? null : Bukkit.getPlayer(thrower);
            if (player != null && player.getWorld().equals(living.getWorld())) { source.withCausingEntity(player).withDirectEntity(player); }
            int frames = living.getNoDamageTicks();
            double last = living.getLastDamage();
            living.setNoDamageTicks(0);
            living.damage(amount, source.build());
            if (!living.isValid() || living.isDead()) { return; }
            living.setNoDamageTicks(frames);
            living.setLastDamage(last);
            living.setFireTicks(Math.max(living.getFireTicks(), (int) Math.max(20, until - core.ticks())));
        }

        @Override public void end() {
            burning.remove(living.getUniqueId(), this);
        }
    }
}
