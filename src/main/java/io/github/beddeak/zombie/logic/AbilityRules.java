package io.github.beddeak.zombie.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.IntPredicate;
import org.bukkit.Material;

public final class AbilityRules {
    private AbilityRules() {}

    public static boolean canSplit(int generation, int maxGeneration, int nearby, int nearbyCap, boolean globalRoom) {
        return (maxGeneration == 0 || generation < maxGeneration) && nearby < nearbyCap && globalRoom;
    }

    public static double generationFactor(double perGeneration, int generation) {
        return Math.pow(perGeneration, Math.max(0, generation - 1));
    }

    public static boolean weaponOrArmor(Material material) {
        String name = material.name().toUpperCase(Locale.ROOT);
        return name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_SPEAR") || name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
                || material == Material.TRIDENT || material == Material.MACE || material == Material.BOW
                || material == Material.CROSSBOW || material == Material.SHIELD || material == Material.ELYTRA;
    }

    public static int theftSlot(int size, int heldSlot, IntPredicate present, IntPredicate weaponOrArmor, Random random) {
        for (boolean weapons : new boolean[]{false, true}) {
            IntPredicate wanted = slot -> present.test(slot) && weaponOrArmor.test(slot) == weapons;
            int hotbar = pickSlot(Math.min(9, size), heldSlot, wanted, random);
            if (hotbar >= 0) { return hotbar; }
            List<Integer> inventory = new ArrayList<>();
            for (int slot = 9; slot < size; slot++) {
                if (wanted.test(slot)) { inventory.add(slot); }
            }
            if (!inventory.isEmpty()) { return inventory.get(random.nextInt(inventory.size())); }
            if (heldSlot >= 0 && heldSlot < size && wanted.test(heldSlot)) { return heldSlot; }
        }
        return -1;
    }

    public static int pickSlot(int size, int heldSlot, IntPredicate eligible, Random random) {
        List<Integer> candidates = new ArrayList<>();
        for (int slot = 0; slot < size; slot++) {
            if (slot != heldSlot && eligible.test(slot)) { candidates.add(slot); }
        }
        return candidates.isEmpty() ? -1 : candidates.get(random.nextInt(candidates.size()));
    }

    public static final class Shake {
        private final int presses;
        private final long windowTicks;
        private final Deque<Long> times = new ArrayDeque<>();

        public Shake(int presses, long windowTicks) {
            this.presses = presses;
            this.windowTicks = windowTicks;
        }

        public boolean press(long now) {
            times.addLast(now);
            while (!times.isEmpty() && now - times.peekFirst() > windowTicks) { times.removeFirst(); }
            if (times.size() >= presses) {
                times.clear();
                return true;
            }
            return false;
        }
    }
}
