package io.github.beddeak.zombie.ability;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.zombie.Tags;
import io.github.beddeak.zombie.zombie.ZombieType;
import java.util.EnumMap;
import java.util.Map;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.ProjectileHitEvent;

public final class Abilities {
    private static final Ability NONE = new Ability() {};
    private static final int PIGLIN_ANGER = 1_000_000;
    private static final double PIGLIN_SIGHT = 24;
    private final Apocalypse core;
    private final Map<ZombieType, Ability> abilities = new EnumMap<>(ZombieType.class);
    private final ThiefAbility thief;
    private final JockeyAbility jockey;
    private final SpitterAbility spitter;
    private final TankAbility tank;

    public Abilities(Apocalypse core) {
        this.core = core;
        thief = new ThiefAbility(core);
        jockey = new JockeyAbility(core);
        spitter = new SpitterAbility(core);
        tank = new TankAbility(core);
        abilities.put(ZombieType.SPRINTER, new SimpleAbilities.Sprinter(core));
        abilities.put(ZombieType.BOOMER, new SimpleAbilities.Boomer(core));
        abilities.put(ZombieType.BOMBER, new BomberAbility(core));
        abilities.put(ZombieType.THROWER, new SimpleAbilities.Thrower(core));
        abilities.put(ZombieType.TOXIC, new SimpleAbilities.Toxic(core));
        abilities.put(ZombieType.FROST, new SimpleAbilities.Frost(core));
        abilities.put(ZombieType.JUMPER, new SimpleAbilities.Jumper(core));
        abilities.put(ZombieType.NINJA, new SimpleAbilities.Ninja(core));
        abilities.put(ZombieType.GRABBER, new GrabberAbility(core));
        abilities.put(ZombieType.MULTIPLIER, new SimpleAbilities.Multiplier(core));
        abilities.put(ZombieType.SCREAMER, new SimpleAbilities.Screamer(core));
        abilities.put(ZombieType.HEALER, new SimpleAbilities.Healer(core));
        abilities.put(ZombieType.SPITTER, spitter);
        abilities.put(ZombieType.VAMPIRE, new SimpleAbilities.Vampire(core));
        abilities.put(ZombieType.THIEF, thief);
        abilities.put(ZombieType.NECROMANCER, new NecromancerAbility(core));
        abilities.put(ZombieType.JOCKEY, jockey);
        abilities.put(ZombieType.WITCH, new WitchAbility(core));
        abilities.put(ZombieType.TANK, tank);
    }

    public Ability get(ZombieType type) {
        return type == null ? NONE : abilities.getOrDefault(type, NONE);
    }

    public void tick() {
        for (Zombie zombie : core.registry().snapshot()) {
            if (!core.active(zombie.getWorld())) { continue; }
            if (zombie instanceof PigZombie piglin) { provoke(piglin); }
            get(core.registry().type(zombie)).tick(zombie);
        }
    }

    private void provoke(PigZombie piglin) {
        if (piglin.getAnger() < PIGLIN_ANGER / 2) {
            piglin.setAngry(true);
            piglin.setAnger(PIGLIN_ANGER);
        }
        if (!(piglin.getTarget() instanceof Player)) {
            Player player = Support.nearest(piglin.getLocation(), PIGLIN_SIGHT);
            if (player != null) { piglin.setTarget(player); }
        }
    }

    public boolean projectileLanded(Projectile projectile, ProjectileHitEvent event) {
        String kind = Tags.helper(projectile);
        if (Tags.SPIT.equals(kind)) {
            spitter.landed(projectile, event);
            return true;
        }
        if (Tags.ROCK.equals(kind)) {
            tank.landed(projectile, event);
            return true;
        }
        return false;
    }

    public ThiefAbility thief() { return thief; }
    public JockeyAbility jockey() { return jockey; }
}
