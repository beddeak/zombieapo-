package io.github.beddeak.zombie.data;

public final class WorldData {
    public long startDay;
    public int lastDayShown;
    public int hordeDay;
    public long hordeTime;
    public boolean hordeDone;
    public int tankDay;
    public int bloodMoonTankDay;
    public int preparingTicks;
    public int preparingTotal;
    public int quietSurfaceTicks;
    public String assemblyItem;
    public BlockPos assemblyPos;
    public int assemblyRemaining;
    public int assemblyTotal;
    public java.util.UUID assemblyCrafter;
    public final EndingData ending = new EndingData();
    public final java.util.Map<java.util.UUID, PlayerStats> stats = new java.util.LinkedHashMap<>();

    public WorldData(long startDay) {
        this.startDay = startDay;
    }

    public PlayerStats stats(java.util.UUID player) {
        return stats.computeIfAbsent(player, id -> new PlayerStats());
    }
}
