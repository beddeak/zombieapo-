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
    /** Radio or antenna being assembled at a crafting table; {@code assemblyItem} is null when nothing is. */
    public String assemblyItem;
    public BlockPos assemblyPos;
    public int assemblyRemaining;
    public int assemblyTotal;
    public java.util.UUID assemblyCrafter;
    public final EndingData ending = new EndingData();

    public WorldData(long startDay) {
        this.startDay = startDay;
    }
}
