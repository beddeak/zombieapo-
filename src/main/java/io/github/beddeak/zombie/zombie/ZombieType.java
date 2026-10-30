package io.github.beddeak.zombie.zombie;

import java.util.Locale;
import java.util.Optional;
import org.bukkit.entity.EntityType;

public enum ZombieType {
    DEFAULT("기본", true),
    SPRINTER("스프린터", true),
    BOOMER("부머", true),
    BOMBER("봄버", true),
    THROWER("스로어", true),
    TOXIC("맹독", true),
    FROST("프로스트", true),
    JUMPER("점퍼", true),
    NINJA("닌자", true),
    GRABBER("그래버", true),
    MULTIPLIER("증식", true),
    SCREAMER("스크리머", true),
    HEALER("힐러", true),
    SPITTER("스피터", true),
    PILLAR("필러", true),
    VAMPIRE("흡혈귀", true),
    THIEF("도둑", true),
    NECROMANCER("네크로맨서", true),
    JOCKEY("자키", true),
    WITCH("위치", true),
    /** Spawned where a player died; never picked at random. */
    PLAYER("플레이어", false),
    /** Left 4 Dead style boss; only spawned by its own rules. */
    TANK("탱크", false);

    private final String displayName;
    private final boolean random;

    ZombieType(String displayName, boolean random) {
        this.displayName = displayName;
        this.random = random;
    }

    public String displayName() { return displayName; }

    /** Whether this type takes part in weighted random picks. */
    public boolean random() { return random; }

    public String configKey() { return name().toLowerCase(Locale.ROOT); }

    /** Whether a zombie of the given entity type can carry this type. Pillar, jockey and witch need a land zombie body. */
    public boolean fits(EntityType variant) {
        return switch (this) {
            case PILLAR, JOCKEY, WITCH, TANK, PLAYER -> variant == EntityType.ZOMBIE || variant == EntityType.HUSK;
            default -> true;
        };
    }

    public static Optional<ZombieType> parse(String text) {
        if (text == null) { return Optional.empty(); }
        try {
            return Optional.of(valueOf(text.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
