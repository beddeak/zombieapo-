package io.github.beddeak.zombie.ending;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.Msg;
import io.github.beddeak.zombie.WorldBar;
import io.github.beddeak.zombie.config.Settings;
import io.github.beddeak.zombie.data.BlockPos;
import io.github.beddeak.zombie.data.EndingData;
import io.github.beddeak.zombie.data.WorldData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

public final class AssemblyManager {
    private final Apocalypse core;
    private final Map<String, WorldBar> bars = new HashMap<>();
    private final Set<String> waiting = new HashSet<>();

    public AssemblyManager(Apocalypse core) {
        this.core = core;
    }

    public boolean running(World world) {
        WorldData data = core.data(world);
        return data != null && data.assemblyItem != null;
    }

    public boolean protects(Block block) {
        WorldData data = core.data(block.getWorld());
        return data != null && data.assemblyItem != null && data.assemblyPos != null && data.assemblyPos.matches(block);
    }

    public void crafted(CraftItemEvent event) {
        EndingItem kind = EndingItem.of(event.getRecipe());
        if (kind == null) { return; }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) { return; }
        World world = player.getWorld();
        WorldData data = core.data(world);
        if (!core.active(world) || data == null) {
            player.sendMessage(Msg.warn(kind.displayName() + "은(는) 좀비 아포칼립스가 진행 중인 월드에서만 만들 수 있습니다."));
            return;
        }
        if (data.assemblyItem != null) {
            player.sendMessage(Msg.warn("이미 " + kindOf(data).displayName() + " 조립이 진행 중입니다. 끝난 뒤에 만들 수 있습니다."));
            return;
        }
        if (data.ending.phase == EndingData.Phase.FINALE || data.ending.phase == EndingData.Phase.ESCAPE) {
            player.sendMessage(Msg.warn("구조가 진행 중일 때는 조립할 수 없습니다."));
            return;
        }
        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();
        for (int slot = 0; slot < matrix.length; slot++) {
            ItemStack item = matrix[slot];
            if (item == null || item.isEmpty()) { continue; }
            item.setAmount(item.getAmount() - 1);
            matrix[slot] = item.getAmount() > 0 ? item : null;
        }
        inventory.setMatrix(matrix);
        inventory.setResult(null);
        Location table = inventory.getLocation() != null ? inventory.getLocation() : player.getLocation();
        Settings.Assembly settings = core.settings().ending().assembly();
        int seconds = kind == EndingItem.RADIO ? settings.radioSeconds() : settings.antennaSeconds();
        core.runLater(1, player::closeInventory);
        if (seconds <= 0) {
            hand(player.getUniqueId(), core.ending().item(kind), table);
            return;
        }
        data.assemblyItem = kind.name();
        data.assemblyPos = BlockPos.of(table);
        data.assemblyTotal = seconds * 20;
        data.assemblyRemaining = data.assemblyTotal;
        data.assemblyCrafter = player.getUniqueId();
        core.save();
        core.hordes().startContinuous(world, settings.waveSizeMultiplier(), settings.gapMin(), settings.gapMax());
        world.playSound(table, Sound.BLOCK_ANVIL_USE, 1.5f, 0.8f);
        core.announce(world, Msg.alert(player.getName() + "이(가) " + kind.displayName() + " 조립을 시작했습니다! " + Msg.clock(seconds)
                + " 동안 작업대 " + (int) settings.radius() + "블록 안에서 버티세요. 비우면 조립이 멈춥니다."));
    }

    public void second(World world, WorldData data) {
        if (data.assemblyItem == null) { return; }
        EndingItem kind = kindOf(data);
        Settings.Assembly settings = core.settings().ending().assembly();
        Location table = data.assemblyPos.center(world).add(0, 0.5, 0);
        boolean near = core.survivors(world).stream().anyMatch(player -> {
            Location location = player.getLocation();
            return location.distanceSquared(table) <= settings.radius() * settings.radius();
        });
        if (waiting.contains(world.getName())) {
            if (!near) {
                bar(world, kind.displayName() + " 조립 일시정지 — 작업대 근처로 오세요", data);
                return;
            }
            waiting.remove(world.getName());
            core.hordes().startContinuous(world, settings.waveSizeMultiplier(), settings.gapMin(), settings.gapMax());
        }
        if (near) {
            data.assemblyRemaining = Math.max(0, data.assemblyRemaining - 20);
            world.spawnParticle(Particle.ELECTRIC_SPARK, table.clone().add(0, 0.6, 0), 8, 0.35, 0.2, 0.35, 0.02);
            if (data.assemblyRemaining % 60 == 0) { world.playSound(table, Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1f); }
        }
        bar(world, kind.displayName() + " 조립 중 " + Msg.clock((data.assemblyRemaining + 19) / 20) + " / 남은 좀비 " + core.hordes().remaining(world)
                + (near ? "" : " — 작업대 근처로 오세요 (멈춤)"), data);
        if (data.assemblyRemaining <= 0) {
            complete(world, data, kind, table);
        } else if (data.assemblyRemaining % 200 == 0) {
            core.save();
        }
    }

    private void complete(World world, WorldData data, EndingItem kind, Location table) {
        UUID crafter = data.assemblyCrafter;
        clearState(world, data);
        core.hordes().stop(world);
        hand(crafter, core.ending().item(kind), table);
        world.playSound(table, Sound.BLOCK_ANVIL_USE, 1.5f, 1.2f);
        world.playSound(table, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1f);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, table.clone().add(0, 1, 0), 40, 0.5, 0.6, 0.5, 0.3);
        Msg.title(world.getPlayers(), kind.displayName() + " 완성!", NamedTextColor.GOLD, "");
        core.announce(world, Msg.good(kind.displayName() + " 조립이 끝났습니다! " + (kind == EndingItem.RADIO
                ? "하늘이 보이는 땅 위에 설치하고 안테나를 붙이세요." : "라디오 바로 위나 옆에 붙여 설치하세요.")));
    }

    public void clear(World world, WorldData data) {
        if (data.assemblyItem != null) {
            EndingItem kind = kindOf(data);
            Location table = data.assemblyPos.center(world).add(0, 0.5, 0);
            UUID crafter = data.assemblyCrafter;
            clearState(world, data);
            hand(crafter, core.ending().item(kind), table);
        }
        waiting.remove(world.getName());
    }

    public void restoreAfterRestart() {
        for (World world : core.activeWorlds()) {
            if (core.data(world).assemblyItem != null) { waiting.add(world.getName()); }
        }
    }

    public void shutdown() {
        for (WorldBar bar : bars.values()) { bar.hide(); }
        bars.clear();
        waiting.clear();
    }

    public List<String> status(World world) {
        List<String> lines = new ArrayList<>();
        WorldData data = core.data(world);
        if (data != null && data.assemblyItem != null) {
            lines.add("조립: " + kindOf(data).displayName() + " (남은 " + Msg.clock((data.assemblyRemaining + 19) / 20) + ", 작업대 " + data.assemblyPos + ")");
        }
        return lines;
    }

    private void clearState(World world, WorldData data) {
        data.assemblyItem = null;
        data.assemblyPos = null;
        data.assemblyRemaining = 0;
        data.assemblyTotal = 0;
        data.assemblyCrafter = null;
        core.save();
        WorldBar bar = bars.remove(world.getName());
        if (bar != null) { bar.hide(); }
    }

    private static void hand(UUID crafter, ItemStack item, Location table) {
        Player player = crafter == null ? null : Bukkit.getPlayer(crafter);
        if (player != null) {
            for (ItemStack left : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
            return;
        }
        table.getWorld().dropItemNaturally(table.clone().add(0, 0.6, 0), item);
    }

    private void bar(World world, String text, WorldData data) {
        WorldBar bar = bars.computeIfAbsent(world.getName(), name -> new WorldBar(Component.text(text), BossBar.Color.BLUE));
        bar.update(Component.text(text, NamedTextColor.AQUA), (double) (data.assemblyTotal - data.assemblyRemaining) / Math.max(1, data.assemblyTotal));
        bar.sync(world.getPlayers());
    }

    private static EndingItem kindOf(WorldData data) {
        try {
            return EndingItem.valueOf(data.assemblyItem.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return EndingItem.RADIO;
        }
    }
}
