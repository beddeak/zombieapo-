package io.github.beddeak.zombie.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.config.Settings;
import io.github.beddeak.zombie.logic.SpawnLocator;
import io.github.beddeak.zombie.spawn.BukkitSampler;
import io.github.beddeak.zombie.spawn.ReplaceRules;
import io.github.beddeak.zombie.zombie.Keys;
import io.github.beddeak.zombie.zombie.SpawnContext;
import io.github.beddeak.zombie.zombie.Tags;
import io.github.beddeak.zombie.zombie.ZombieFactory;
import io.github.beddeak.zombie.zombie.ZombieType;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Spawn replacement, sun immunity, registry tracking and cleanup of leftovers in loaded chunks. */
public final class ZombieListener implements Listener {
    private static final int GROUND_SEARCH = 48;
    private final Apocalypse core;

    public ZombieListener(Apocalypse core) {
        this.core = core;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void spawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        World world = entity.getWorld();
        if (!core.active(world)) { return; }
        ReplaceRules.Action decision = ReplaceRules.decide(entity.getType(), event.getSpawnReason(), core.settings().replace());
        if (decision != ReplaceRules.Action.IGNORE && !ReplaceRules.fromSpawner(event.getSpawnReason())
                && core.quietSurface(entity.getLocation())) {
            event.setCancelled(true);
            return;
        }
        switch (decision) {
            case TAG -> {
                Zombie zombie = (Zombie) entity;
                if (Tags.type(zombie) != null) { return; }
                if (core.crowded(zombie.getLocation())) {
                    event.setCancelled(true);
                    return;
                }
                SpawnContext context = core.context(world);
                core.factory().apply(zombie, core.factory().pick(zombie.getLocation(), zombie.getType(), context, 0), context);
            }
            case REPLACE -> {
                event.setCancelled(true);
                replace(entity.getLocation(), event.getSpawnReason());
            }
            case IGNORE -> { }
        }
    }

    private void replace(Location location, CreatureSpawnEvent.SpawnReason reason) {
        Settings settings = core.settings();
        if (core.registry().size() >= settings.spawning().globalCap() || core.crowded(location)) { return; }
        if (ReplaceRules.fromSpawner(reason) && core.registry().countNear(location, 16) >= settings.replace().spawnerNearbyCap()) { return; }
        Class<? extends Zombie> variant = core.factory().variantFor(location);
        Location target = location;
        if (variant != Drowned.class) {
            target = ground(location);
            if (target == null) { return; }
            variant = core.factory().variantFor(target);
        }
        SpawnContext context = core.context(location.getWorld());
        ZombieType type = core.factory().pick(target, ZombieFactory.entityType(variant), context, 0);
        core.factory().spawn(target, variant, type, context);
    }

    /** Flying mobs (phantoms, ghasts) are replaced by a zombie standing on the ground below them. */
    private static Location ground(Location location) {
        BukkitSampler sampler = new BukkitSampler(location.getWorld());
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        for (int drop = 0; drop <= GROUND_SEARCH; drop++) {
            if (SpawnLocator.standable(sampler, x, y - drop, z)) {
                return new Location(location.getWorld(), x + 0.5, y - drop, z + 0.5, location.getYaw(), 0);
            }
        }
        return null;
    }

    /** Only sunlight (a combust event without a block or entity source) is cancelled; fire and lava still burn. */
    @EventHandler(ignoreCancelled = true)
    public void combust(EntityCombustEvent event) {
        if (event.getClass() == EntityCombustEvent.class && event.getEntity() instanceof Zombie
                && core.active(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void transform(EntityTransformEvent event) {
        Entity original = event.getEntity();
        ZombieType type = Tags.type(original);
        if (type == null) { return; }
        if (type == ZombieType.TANK || type == ZombieType.PLAYER) {
            event.setCancelled(true);
            return;
        }
        for (Entity converted : event.getTransformedEntities()) {
            if (converted instanceof Zombie zombie && core.active(zombie.getWorld())) {
                copy(original, zombie);
                SpawnContext context = core.context(zombie.getWorld()).withHorde(Tags.integer(original, Keys.HORDE, 0))
                        .withGeneration(Tags.integer(original, Keys.GENERATION, 1)).withOwner(Tags.uuid(original, Keys.OWNER));
                ZombieType kept = type.fits(zombie.getType()) && type != ZombieType.PILLAR ? type : ZombieType.DEFAULT;
                core.factory().apply(zombie, kept, context);
            } else {
                Tags.strip(converted);
            }
        }
    }

    private static void copy(Entity from, Entity to) {
        PersistentDataContainer source = from.getPersistentDataContainer();
        PersistentDataContainer target = to.getPersistentDataContainer();
        String stolenFrom = source.get(Keys.STOLEN_FROM, PersistentDataType.STRING);
        if (stolenFrom != null) { target.set(Keys.STOLEN_FROM, PersistentDataType.STRING, stolenFrom); }
        Long mark = source.get(Keys.MARK, PersistentDataType.LONG);
        if (mark != null) { target.set(Keys.MARK, PersistentDataType.LONG, mark); }
    }

    @EventHandler
    public void added(EntityAddToWorldEvent event) {
        core.registry().track(event.getEntity());
    }

    @EventHandler
    public void removed(EntityRemoveFromWorldEvent event) {
        core.registry().forget(event.getEntity());
    }

    @EventHandler
    public void loaded(EntitiesLoadEvent event) {
        World world = event.getWorld();
        boolean active = core.active(world);
        for (Entity entity : event.getEntities()) {
            String helper = Tags.helper(entity);
            if (helper != null) {
                boolean staleDisplay = Tags.RADIO_DISPLAY.equals(helper)
                        && !(entity instanceof TextDisplay display && core.ending().currentDisplay(display));
                if (!active || Tags.HOOK.equals(helper) || Tags.ROCK_DISPLAY.equals(helper) || Tags.ESCAPE_MARKER.equals(helper)
                        || staleDisplay) {
                    entity.remove();
                }
                continue;
            }
            if (!Tags.custom(entity)) { continue; }
            Zombie zombie = (Zombie) entity;
            if (!active) {
                core.abilities().thief().release(zombie);
                core.registry().forget(zombie);
                zombie.remove();
            } else if (Tags.type(zombie) == ZombieType.PLAYER) {
                UUID owner = Tags.uuid(zombie, Keys.OWNER);
                UUID current = owner == null ? null : core.store().playerZombies().get(owner);
                if (current != null && !current.equals(zombie.getUniqueId())) {
                    core.registry().forget(zombie);
                    zombie.remove();
                }
            }
        }
    }
}
