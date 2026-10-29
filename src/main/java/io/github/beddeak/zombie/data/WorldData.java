package io.github.beddeak.zombie.data;

/** Saved state of one world where the apocalypse is running. */
public final class WorldData {
    /** Minecraft day index ({@code fullTime / 24000}) that counts as day 1. */
    public long startDay;
    public int lastDayShown;
    /** Regular horde: the day it belongs to, the tick of that day it starts at, and whether it ran. */
    public int hordeDay;
    public long hordeTime;
    public boolean hordeDone;
    /** Last day whose every-N-days tank already spawned. */
    public int tankDay;
    /** Day whose blood moon already had its tank. */
    public int bloodMoonTankDay;
    /** Ticks left of the preparation countdown after {@code /apocalypse start}; 0 once the apocalypse runs. */
    public int preparingTicks;
    public int preparingTotal;
    /** Ticks left in which nothing spawns on the surface after the start. */
    public int quietSurfaceTicks;
    /** Radio or antenna being assembled at a crafting table; {@code assemblyItem} is null when nothing is. */
    public String assemblyItem;
    public BlockPos assemblyPos;
    public int assemblyRemaining;
    public int assemblyTotal;
    public java.util.UUID assemblyCrafter;
    public final EndingData ending = new EndingData();
    /** Kills, deaths and thefts per player since the start. */
    public final java.util.Map<java.util.UUID, PlayerStats> stats = new java.util.LinkedHashMap<>();

    public WorldData(long startDay) {
        this.startDay = startDay;
    }

    public PlayerStats stats(java.util.UUID player) {
        return stats.computeIfAbsent(player, id -> new PlayerStats());
    }
}
