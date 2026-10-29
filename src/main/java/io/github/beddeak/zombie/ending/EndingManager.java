package io.github.beddeak.zombie.ending;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.Msg;
import io.github.beddeak.zombie.WorldBar;
import io.github.beddeak.zombie.config.Settings;
import io.github.beddeak.zombie.data.BlockPos;
import io.github.beddeak.zombie.data.DataStore;
import io.github.beddeak.zombie.data.EndingData;
import io.github.beddeak.zombie.data.PlayerStats;
import io.github.beddeak.zombie.data.WorldData;
import io.github.beddeak.zombie.logic.EndingRules;
import io.github.beddeak.zombie.logic.ResultBoard;
import io.github.beddeak.zombie.logic.SpawnLocator;
import io.github.beddeak.zombie.spawn.BukkitSampler;
import io.github.beddeak.zombie.zombie.Tags;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * The radio ending: craft and place the radio, request rescue (finale: defend the radio), then run to the escape
 * point (escape). Progress is saved in data.yml and resumes paused after a restart.
 */
public final class EndingManager {
    private static final class Runtime {
        WorldBar bar;
        TextDisplay display;
        boolean waitingToResume;
        final Map<UUID, Location> compass = new HashMap<>();
        /** Glowing helipad, light pillar and label at the escape point. */
        final List<Display> escapeMarkers = new ArrayList<>();
    }

    private static final int GROUND_SAMPLES = 16;
    private static final int GROUND_SAMPLE_DISTANCE = 16;
    /** The result board follows the ending titles after a short pause. */
    private static final long RESULT_DELAY_TICKS = 60;

    private final Apocalypse core;
    private final Map<String, Runtime> runtimes = new HashMap<>();
    private final Map<UUID, Long> confirmations = new HashMap<>();
    private Material radioBlock = Material.JUKEBOX;
    private Material antennaBlock = Material.END_ROD;

    public EndingManager(Apocalypse core) {
        this.core = core;
    }

    // ---- radio item and recipe

    public void registerRecipe() {
        unregisterRecipe();
        Settings.Ending ending = core.settings().ending();
        radioBlock = placeable(ending.radioBlock(), Material.JUKEBOX, "radio-block");
        antennaBlock = placeable(ending.antennaBlock(), Material.END_ROD, "antenna-block");
        Bukkit.addRecipe(EndingItem.RADIO.recipe(radioBlock, ending.radioRecipe()));
        Bukkit.addRecipe(EndingItem.ANTENNA.recipe(antennaBlock, ending.antennaRecipe()));
        for (World world : core.activeWorlds()) { discoverRecipe(world.getPlayers()); }
    }

    private Material placeable(Material material, Material fallback, String key) {
        if (material.isBlock() && material.isItem()) { return material; }
        core.logger().warning("ending." + key + " " + material + " is not a placeable block; using " + fallback + ".");
        return fallback;
    }

    public void unregisterRecipe() {
        for (EndingItem kind : EndingItem.values()) { Bukkit.removeRecipe(kind.recipeKey()); }
    }

    public void discoverRecipe(Collection<? extends Player> players) {
        for (Player player : players) {
            for (EndingItem kind : EndingItem.values()) { player.discoverRecipe(kind.recipeKey()); }
        }
    }

    public ItemStack radioItem() {
        return EndingItem.RADIO.create(radioBlock);
    }

    public ItemStack antennaItem() {
        return EndingItem.ANTENNA.create(antennaBlock);
    }

    public ItemStack item(EndingItem kind) {
        return kind == EndingItem.RADIO ? radioItem() : antennaItem();
    }

    /** The antenna is in place: registered, still there, and touching the radio's top or a side. */
    public boolean hasAntenna(World world) {
        BlockPos radio = core.store().radios().get(world.getName());
        BlockPos antenna = core.store().antennas().get(world.getName());
        if (radio == null || antenna == null
                || !EndingRules.attached(antenna.x() - radio.x(), antenna.y() - radio.y(), antenna.z() - radio.z())) {
            return false;
        }
        return !world.isChunkLoaded(antenna.x() >> 4, antenna.z() >> 4) || antenna.center(world).getBlock().getType() == antennaBlock;
    }

    // ---- placing, breaking, protecting

    public void placed(BlockPlaceEvent event) {
        if (EndingItem.ANTENNA.is(event.getItemInHand())) {
            placedAntenna(event);
            return;
        }
        if (!EndingItem.RADIO.is(event.getItemInHand())) { return; }
        Player player = event.getPlayer();
        World world = event.getBlock().getWorld();
        if (!core.active(world)) {
            event.setCancelled(true);
            player.sendMessage(Msg.warn("라디오는 좀비 아포칼립스가 진행 중인 월드에서만 설치할 수 있습니다."));
            return;
        }
        BlockPos existing = core.store().radios().get(world.getName());
        if (existing != null && existing.center(world).getBlock().getType() == radioBlock) {
            event.setCancelled(true);
            player.sendMessage(Msg.warn("이 월드에는 이미 라디오가 있습니다: " + existing));
            return;
        }
        String problem = groundProblem(event.getBlock());
        if (problem != null) {
            event.setCancelled(true);
            player.sendMessage(Msg.warn(problem));
            return;
        }
        core.store().radios().put(world.getName(), BlockPos.of(event.getBlock()));
        core.save();
        player.sendMessage(Msg.good(hasAntenna(world) ? "라디오를 설치했습니다. 두 번 우클릭하면 구조를 요청합니다. 요청하면 좀비가 몰려옵니다!"
                : "라디오를 설치했습니다. 라디오 바로 위나 옆에 안테나를 붙여 설치해야 구조를 요청할 수 있습니다."));
        world.playSound(event.getBlock().getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
    }

    private void placedAntenna(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        World world = event.getBlock().getWorld();
        if (!core.active(world)) {
            event.setCancelled(true);
            player.sendMessage(Msg.warn("안테나는 좀비 아포칼립스가 진행 중인 월드에서만 설치할 수 있습니다."));
            return;
        }
        BlockPos radio = core.store().radios().get(world.getName());
        if (radio == null) {
            event.setCancelled(true);
            player.sendMessage(Msg.warn("먼저 라디오를 설치하세요. 안테나는 라디오 바로 위나 옆에 붙여 설치합니다."));
            return;
        }
        BlockPos placed = BlockPos.of(event.getBlock());
        if (!EndingRules.attached(placed.x() - radio.x(), placed.y() - radio.y(), placed.z() - radio.z())) {
            event.setCancelled(true);
            player.sendMessage(Msg.warn("안테나는 라디오 바로 위나 옆에 붙여 설치해야 합니다. 라디오 위치: " + radio));
            return;
        }
        if (hasAntenna(world)) {
            event.setCancelled(true);
            player.sendMessage(Msg.warn("라디오에 이미 안테나가 있습니다."));
            return;
        }
        core.store().antennas().put(world.getName(), placed);
        core.save();
        player.sendMessage(Msg.good("안테나를 설치했습니다. 이제 라디오를 두 번 우클릭하면 구조를 요청할 수 있습니다."));
        world.playSound(event.getBlock().getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
    }

    public void broken(BlockBreakEvent event) {
        World world = event.getBlock().getWorld();
        BlockPos antenna = core.store().antennas().get(world.getName());
        if (antenna != null && antenna.matches(event.getBlock())) {
            brokenAntenna(event);
            return;
        }
        BlockPos pos = core.store().radios().get(world.getName());
        if (pos == null || !pos.matches(event.getBlock())) { return; }
        Player player = event.getPlayer();
        WorldData data = core.data(world);
        if (data != null && (data.ending.phase == EndingData.Phase.FINALE || data.ending.phase == EndingData.Phase.ESCAPE)) {
            event.setCancelled(true);
            player.sendMessage(Msg.warn("구조가 진행 중이라 라디오를 옮길 수 없습니다."));
            return;
        }
        if (!player.isSneaking()) {
            event.setCancelled(true);
            player.sendMessage(Msg.warn("라디오를 회수하려면 웅크린 채로 부수세요."));
            return;
        }
        event.setDropItems(false);
        Location center = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        world.dropItemNaturally(center, radioItem());
        core.store().radios().remove(world.getName());
        removeDisplay(world);
        core.save();
        player.sendMessage(Msg.info("라디오를 회수했습니다."));
    }

    private void brokenAntenna(BlockBreakEvent event) {
        World world = event.getBlock().getWorld();
        Player player = event.getPlayer();
        WorldData data = core.data(world);
        if (data != null && (data.ending.phase == EndingData.Phase.FINALE || data.ending.phase == EndingData.Phase.ESCAPE)) {
            event.setCancelled(true);
            player.sendMessage(Msg.warn("구조가 진행 중이라 안테나를 옮길 수 없습니다."));
            return;
        }
        if (!player.isSneaking()) {
            event.setCancelled(true);
            player.sendMessage(Msg.warn("안테나를 회수하려면 웅크린 채로 부수세요."));
            return;
        }
        event.setDropItems(false);
        world.dropItemNaturally(event.getBlock().getLocation().add(0.5, 0.5, 0.5), antennaItem());
        core.store().antennas().remove(world.getName());
        core.save();
        player.sendMessage(Msg.info("안테나를 회수했습니다."));
    }

    /** Keeps the radio and its antenna out of an explosion's block list. */
    public void protect(World world, List<Block> blocks) {
        BlockPos pos = core.store().radios().get(world.getName());
        if (pos != null) { blocks.removeIf(pos::matches); }
        BlockPos antenna = core.store().antennas().get(world.getName());
        if (antenna != null) { blocks.removeIf(antenna::matches); }
    }

    // ---- interaction

    public void clicked(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) { return; }
        World world = block.getWorld();
        BlockPos pos = core.store().radios().get(world.getName());
        if (pos == null || !pos.matches(block)) { return; }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) { return; }
        Player player = event.getPlayer();
        WorldData data = core.data(world);
        if (data == null) {
            player.sendMessage(Msg.warn("좀비 아포칼립스가 진행 중일 때만 라디오가 작동합니다."));
            return;
        }
        EndingData ending = data.ending;
        switch (ending.phase) {
            case FINALE -> player.sendMessage(Msg.info("구조대가 오고 있습니다. 남은 시간 " + Msg.clock(ending.remainingTicks / 20) + " — 라디오를 지키세요!"));
            case ESCAPE -> player.sendMessage(Msg.info("탈출 지점: " + ending.escapePoint + " (남은 시간 " + Msg.clock(ending.remainingTicks / 20) + ")"));
            case DONE -> player.sendMessage(Msg.info("이미 구조가 끝났습니다."));
            case NONE -> request(world, data, player);
        }
    }

    private void request(World world, WorldData data, Player player) {
        Settings.Ending settings = core.settings().ending();
        if (!hasAntenna(world)) {
            player.sendMessage(Msg.warn("안테나가 없어서 신호를 보낼 수 없습니다. 레시피북에서 안테나 조합법을 확인해 만든 뒤 라디오 바로 위나 옆에 설치하세요."));
            return;
        }
        if (core.assembly().running(world)) {
            player.sendMessage(Msg.warn("조립이 끝난 뒤에 구조를 요청할 수 있습니다."));
            return;
        }
        int day = core.day(world);
        if (day < settings.minDay()) {
            player.sendMessage(Msg.warn("아직 신호가 잡히지 않습니다. " + settings.minDay() + "일차부터 구조를 요청할 수 있습니다."));
            return;
        }
        long now = core.ticks();
        Long first = confirmations.get(player.getUniqueId());
        if (first != null && now - first <= settings.confirmSeconds() * 20L) {
            confirmations.remove(player.getUniqueId());
            startFinale(world, data, player.getName());
            return;
        }
        confirmations.put(player.getUniqueId(), now);
        player.sendMessage(Msg.warn("한 번 더 우클릭하면 구조를 요청합니다. " + Msg.clock(settings.finale().seconds())
                + " 동안 좀비가 끝없이 몰려옵니다! (" + settings.confirmSeconds() + "초 안에)"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 0.8f);
    }

    // ---- phases

    public boolean inFinale(World world) {
        WorldData data = core.data(world);
        return data != null && data.ending.phase == EndingData.Phase.FINALE;
    }

    /** Starts the finale. Returns a problem description, or null when it started. */
    public String startFinale(World world, WorldData data, String requester) {
        if (core.store().radios().get(world.getName()) == null) { return "이 월드에 설치된 라디오가 없습니다."; }
        if (data.ending.phase != EndingData.Phase.NONE) { return "이미 엔딩이 진행 중이거나 끝났습니다. /apocalypse radio reset 으로 초기화할 수 있습니다."; }
        Settings.Finale finale = core.settings().ending().finale();
        EndingData ending = data.ending;
        ending.reset();
        ending.phase = EndingData.Phase.FINALE;
        ending.totalTicks = finale.seconds() * 20;
        ending.remainingTicks = ending.totalTicks;
        core.hordes().startContinuous(world, finale.hordeSizeMultiplier(), finale.gapMin(), finale.gapMax());
        Msg.title(world.getPlayers(), "구조 요청!", NamedTextColor.GOLD, "구조대가 올 때까지 " + Msg.clock(finale.seconds()) + " 동안 라디오를 지키세요");
        Location radio = radioLocation(world);
        for (int ring = 0; ring < 3; ring++) {
            core.runLater(1 + ring * 12L, () -> world.playSound(radio, Sound.BLOCK_BELL_USE, 3f, 0.8f));
        }
        core.announce(world, Msg.alert(requester + "이(가) 구조를 요청했습니다! " + Msg.clock(finale.seconds())
                + " 동안 라디오 " + (int) core.settings().ending().finale().radius() + "블록 안에서 버티세요. 비우면 타이머가 멈춥니다."));
        core.save();
        return null;
    }

    /** Ends the finale right away and starts the escape. */
    public String skipFinale(World world, WorldData data) {
        if (data.ending.phase != EndingData.Phase.FINALE) { return "피날레가 진행 중이 아닙니다."; }
        beginEscape(world, data);
        return null;
    }

    public String skipEscape(World world, WorldData data) {
        if (data.ending.phase != EndingData.Phase.ESCAPE) { return "탈출 단계가 진행 중이 아닙니다."; }
        finish(world, data);
        return null;
    }

    public void second(World world, WorldData data) {
        Runtime runtime = runtime(world);
        maintainRadio(world, data, runtime);
        switch (data.ending.phase) {
            case FINALE -> finaleSecond(world, data, runtime);
            case ESCAPE -> escapeSecond(world, data, runtime);
            default -> { }
        }
        if (core.ticks() % 200 == 0 && data.ending.phase != EndingData.Phase.NONE) { core.save(); }
    }

    private void finaleSecond(World world, WorldData data, Runtime runtime) {
        EndingData ending = data.ending;
        Settings.Finale finale = core.settings().ending().finale();
        Location radio = radioLocation(world);
        boolean defended = radio != null && core.survivors(world).stream().anyMatch(player -> {
            Location location = player.getLocation();
            double dx = location.getX() - radio.getX();
            double dz = location.getZ() - radio.getZ();
            return dx * dx + dz * dz <= finale.radius() * finale.radius() && Math.abs(location.getY() - radio.getY()) <= finale.height();
        });
        if (runtime.waitingToResume) {
            if (!defended) {
                bar(world, runtime, "구조 요청 일시정지 — 라디오 근처로 모이세요", ending);
                return;
            }
            runtime.waitingToResume = false;
            core.hordes().startContinuous(world, finale.hordeSizeMultiplier(), finale.gapMin(), finale.gapMax());
        }
        if (defended) { ending.remainingTicks = Math.max(0, ending.remainingTicks - 20); }
        int elapsed = ending.elapsedSeconds();
        for (int mark : finale.tankAtSeconds()) {
            if (elapsed >= mark && !ending.tanksSpawned.contains(mark)) {
                ending.tanksSpawned.add(mark);
                if (radio != null) { core.tanks().spawnNear(world, radio); }
            }
        }
        bar(world, runtime, "구조대 도착까지 " + Msg.clock(ending.remainingTicks / 20) + " / 남은 좀비 " + core.hordes().remaining(world)
                + (defended ? "" : " — 라디오를 지키세요! (멈춤)"), ending);
        if (ending.remainingTicks <= 0) { beginEscape(world, data); }
    }

    private void beginEscape(World world, WorldData data) {
        EndingData ending = data.ending;
        Settings.Escape escape = core.settings().ending().escape();
        ending.phase = EndingData.Phase.ESCAPE;
        ending.totalTicks = escape.seconds() * 20;
        ending.remainingTicks = ending.totalTicks;
        Location radio = radioLocation(world);
        ending.escapePoint = chooseEscapePoint(world, radio != null ? radio : world.getSpawnLocation(), escape.distance());
        startEscapeHorde(world, ending);
        Msg.title(world.getPlayers(), "구조 헬기가 도착했습니다!", NamedTextColor.GOLD,
                "탈출 지점으로 가세요! " + ending.escapePoint.x() + ", " + ending.escapePoint.z());
        for (Player player : world.getPlayers()) { player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 2f, 0.6f); }
        core.announce(world, Msg.alert("구조 헬기가 도착했습니다! " + Msg.clock(escape.seconds()) + " 안에 탈출 지점(" + ending.escapePoint
                + ")에 도착하세요. 나침반과 신호탄이 방향을 알려줍니다."));
        core.save();
    }

    private void startEscapeHorde(World world, EndingData ending) {
        Settings.Escape escape = core.settings().ending().escape();
        core.hordes().startContinuous(world, escape.hordeSizeMultiplier(), escape.gapMin(), escape.gapMax());
        core.hordes().extraPoint(world, ending.escapePoint.center(world), escape.pointZombies());
    }

    private void escapeSecond(World world, WorldData data, Runtime runtime) {
        EndingData ending = data.ending;
        Settings.Escape escape = core.settings().ending().escape();
        List<Player> survivors = core.survivors(world);
        if (runtime.waitingToResume) {
            if (survivors.isEmpty()) {
                bar(world, runtime, "탈출 일시정지 — 생존자를 기다리는 중", ending);
                return;
            }
            runtime.waitingToResume = false;
            startEscapeHorde(world, ending);
        }
        if (!survivors.isEmpty()) { ending.remainingTicks = Math.max(0, ending.remainingTicks - 20); }
        Location point = ending.escapePoint.center(world);
        for (Player player : survivors) {
            Location location = player.getLocation();
            double dx = location.getX() - point.getX();
            double dz = location.getZ() - point.getZ();
            if (dx * dx + dz * dz <= escape.radius() * escape.radius() && Math.abs(location.getY() - point.getY()) <= 6) {
                escaped(world, data, player, point);
            }
        }
        signal(world, point);
        ensureEscapeMarkers(world, point, runtime);
        for (Player player : core.survivors(world)) {
            Location location = player.getLocation();
            runtime.compass.computeIfAbsent(player.getUniqueId(), id -> player.getCompassTarget());
            player.setCompassTarget(point);
            double dx = point.getX() - location.getX();
            double dz = point.getZ() - location.getZ();
            player.sendActionBar(Component.text("탈출 지점 " + EndingRules.arrow(location.getYaw(), dx, dz) + " "
                    + (int) Math.sqrt(dx * dx + dz * dz) + "m  |  남은 시간 " + Msg.clock(ending.remainingTicks / 20), NamedTextColor.GOLD));
        }
        bar(world, runtime, "탈출까지 " + Msg.clock(ending.remainingTicks / 20) + " / 탈출 " + ending.escaped.size() + "명 / 남은 좀비 "
                + core.hordes().remaining(world), ending);
        if (EndingRules.escapeOver(ending.escaped.size(), stillRunning(world, ending), ending.remainingTicks)) { finish(world, data); }
    }

    /** Players who have not escaped yet, including the ones on the respawn screen. */
    private static int stillRunning(World world, EndingData ending) {
        int count = 0;
        for (Player player : world.getPlayers()) {
            GameMode mode = player.getGameMode();
            if ((mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) && !ending.escaped.contains(player.getUniqueId())) { count++; }
        }
        return count;
    }

    private void escaped(World world, WorldData data, Player player, Location point) {
        EndingData ending = data.ending;
        ending.escaped.add(player.getUniqueId());
        ending.originalModes.put(player.getUniqueId(), player.getGameMode());
        player.setGameMode(GameMode.SPECTATOR);
        Location original = runtime(world).compass.remove(player.getUniqueId());
        if (original != null) { player.setCompassTarget(original); }
        Msg.title(List.of(player), "탈출 성공!", NamedTextColor.GREEN, "구조 헬기에 올라탔습니다");
        firework(point, Color.LIME);
        core.announce(world, Msg.good(player.getName() + "이(가) 탈출에 성공했습니다!"));
        core.save();
    }

    private void finish(World world, WorldData data) {
        EndingData ending = data.ending;
        Settings.Ending settings = core.settings().ending();
        ending.phase = EndingData.Phase.DONE;
        core.hordes().stop(world);
        Runtime runtime = runtime(world);
        if (runtime.bar != null) {
            runtime.bar.hide();
            runtime.bar = null;
        }
        restoreCompass(runtime);
        removeEscapeMarkers(runtime);
        List<String> saved = new ArrayList<>();
        for (UUID id : ending.escaped) { saved.add(name(id)); }
        List<String> left = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (ending.escaped.contains(player.getUniqueId())) {
                Msg.title(List.of(player), "구조 성공!", NamedTextColor.GOLD, "무사히 탈출했습니다");
            } else {
                left.add(player.getName());
                Msg.title(List.of(player), "구조 헬기를 놓쳤습니다...", NamedTextColor.RED, "좀비 세상에 남겨졌습니다");
            }
        }
        Location point = ending.escapePoint != null ? ending.escapePoint.center(world) : world.getSpawnLocation();
        for (int index = 0; index < 5; index++) {
            core.runLater(1 + index * 10L, () -> firework(point, index2Color()));
        }
        core.announce(world, Msg.good("엔딩! 탈출: " + (saved.isEmpty() ? "없음" : String.join(", ", saved))
                + " / 낙오: " + (left.isEmpty() ? "없음" : String.join(", ", left))));
        core.announce(world, Msg.info(settings.message()));
        List<Component> board = resultBoard(world);
        core.runLater(RESULT_DELAY_TICKS, () -> board.forEach(line -> core.announce(world, line)));
        core.save();
        long delay = settings.autoStopDelaySeconds() * 20L;
        if (settings.autoStop()) {
            core.runLater(delay, () -> {
                WorldData current = core.data(world);
                if (current != null && current.ending.phase == EndingData.Phase.DONE) { core.stop(world); }
            });
        } else {
            core.runLater(delay, () -> {
                WorldData current = core.data(world);
                if (current != null) { restorePlayers(world, current.ending); }
            });
        }
    }

    /**
     * Kills, deaths and thefts of everyone who took part, plus the awards. After the ending each player is marked as
     * escaped or left behind.
     */
    public List<Component> resultBoard(World world) {
        WorldData data = core.data(world);
        if (data == null) { return List.of(); }
        boolean done = data.ending.phase == EndingData.Phase.DONE;
        Set<UUID> players = new LinkedHashSet<>();
        data.stats.forEach((id, stats) -> {
            if (!stats.empty()) { players.add(id); }
        });
        if (done) { players.addAll(data.ending.escaped); }
        List<ResultBoard.Entry> entries = new ArrayList<>();
        for (UUID id : players) {
            String tag = done ? (data.ending.escaped.contains(id) ? "탈출" : "낙오") : null;
            entries.add(new ResultBoard.Entry(name(id), tag, data.stats.getOrDefault(id, new PlayerStats())));
        }
        ResultBoard.Board board = ResultBoard.build(core.day(world), entries);
        List<Component> lines = new ArrayList<>();
        lines.add(Msg.prefix().append(Component.text(board.header(), NamedTextColor.GOLD, TextDecoration.BOLD)));
        board.rows().forEach(row -> lines.add(Component.text(row, NamedTextColor.WHITE)));
        board.awards().forEach(award -> lines.add(Component.text(award, NamedTextColor.YELLOW)));
        return lines;
    }

    private static Color index2Color() {
        Color[] colors = {Color.LIME, Color.YELLOW, Color.ORANGE, Color.AQUA, Color.FUCHSIA};
        return colors[ThreadLocalRandom.current().nextInt(colors.length)];
    }

    // ---- reset, stop, restart

    /** Clears ending progress but keeps the radio and the apocalypse running. */
    public void reset(World world, WorldData data) {
        core.hordes().stop(world);
        restorePlayers(world, data.ending);
        Runtime runtime = runtimes.remove(world.getName());
        if (runtime != null) {
            if (runtime.bar != null) { runtime.bar.hide(); }
            restoreCompass(runtime);
            removeEscapeMarkers(runtime);
            if (runtime.display != null && runtime.display.isValid()) { runtime.display.remove(); }
        }
        data.ending.reset();
        core.save();
    }

    /** The apocalypse stopped in this world. */
    public void clear(World world, WorldData data) {
        reset(world, data);
    }

    public void restoreAfterRestart() {
        for (World world : core.activeWorlds()) {
            EndingData.Phase phase = core.data(world).ending.phase;
            if (phase == EndingData.Phase.FINALE || phase == EndingData.Phase.ESCAPE) {
                runtime(world).waitingToResume = true;
                core.logger().info("Ending in " + world.getName() + " resumes when survivors return (" + phase + ")");
            }
        }
    }

    public void shutdown() {
        for (Runtime runtime : runtimes.values()) {
            if (runtime.bar != null) { runtime.bar.hide(); }
            if (runtime.display != null && runtime.display.isValid()) { runtime.display.remove(); }
            restoreCompass(runtime);
            removeEscapeMarkers(runtime);
        }
        runtimes.clear();
        confirmations.clear();
    }

    /** Restores game modes of escaped players whose restore was postponed, and teaches the recipe. */
    public void joined(Player player) {
        DataStore.PendingMode pending = core.store().pendingModes().remove(player.getUniqueId());
        if (pending != null) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                World world = Bukkit.getWorld(pending.world());
                if (world != null && pending.pos() != null) { player.teleport(pending.pos().center(world).add(0, 0.1, 0)); }
                player.setGameMode(pending.mode());
            }
            core.save();
        }
        if (core.active(player.getWorld())) { discoverRecipe(List.of(player)); }
    }

    public List<String> status(World world) {
        List<String> lines = new ArrayList<>();
        BlockPos radio = core.store().radios().get(world.getName());
        lines.add("라디오: " + (radio == null ? "설치 안 됨" : radio.toString()) + ", 안테나: " + (hasAntenna(world) ? "있음" : "없음"));
        WorldData data = core.data(world);
        if (data == null) { return lines; }
        EndingData ending = data.ending;
        lines.add("단계: " + switch (ending.phase) {
            case NONE -> "대기";
            case FINALE -> "구조 피날레 (남은 " + Msg.clock(ending.remainingTicks / 20) + ")";
            case ESCAPE -> "탈출 (남은 " + Msg.clock(ending.remainingTicks / 20) + ", 지점 " + ending.escapePoint + ")";
            case DONE -> "완료";
        });
        if (!ending.escaped.isEmpty()) {
            lines.add("탈출: " + String.join(", ", ending.escaped.stream().map(EndingManager::name).toList()));
        }
        return lines;
    }

    // ---- helpers

    private void restorePlayers(World world, EndingData ending) {
        Location safe = ending.escapePoint != null ? ending.escapePoint.center(world).add(0, 0.1, 0) : world.getSpawnLocation();
        for (UUID id : ending.escaped) {
            GameMode mode = ending.originalModes.getOrDefault(id, GameMode.SURVIVAL);
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                core.store().pendingModes().put(id, new DataStore.PendingMode(mode, world.getName(), BlockPos.of(safe)));
            } else if (player.getGameMode() == GameMode.SPECTATOR) {
                player.teleport(safe);
                player.setGameMode(mode);
            }
        }
        ending.escaped.clear();
        ending.originalModes.clear();
        core.save();
    }

    private void maintainRadio(World world, WorldData data, Runtime runtime) {
        BlockPos antenna = core.store().antennas().get(world.getName());
        if (antenna != null && world.isChunkLoaded(antenna.x() >> 4, antenna.z() >> 4)
                && antenna.center(world).getBlock().getType() != antennaBlock
                && (data.ending.phase == EndingData.Phase.NONE || data.ending.phase == EndingData.Phase.DONE)) {
            core.store().antennas().remove(world.getName());
            core.save();
        }
        BlockPos pos = core.store().radios().get(world.getName());
        if (pos == null) {
            removeDisplay(world);
            return;
        }
        if (!world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) { return; }
        Location center = pos.center(world);
        if (center.getBlock().getType() != radioBlock) {
            if (data.ending.phase == EndingData.Phase.NONE || data.ending.phase == EndingData.Phase.DONE) {
                core.store().radios().remove(world.getName());
                removeDisplay(world);
                core.save();
            }
            return;
        }
        if (runtime.display == null || !runtime.display.isValid()) {
            runtime.display = world.spawn(center.clone().add(0, 2.3, 0), TextDisplay.class, display -> {
                display.setBillboard(Display.Billboard.CENTER);
                display.setPersistent(false);
                display.setShadowed(true);
                Tags.helper(display, Tags.RADIO_DISPLAY);
            });
        }
        EndingData ending = data.ending;
        Component text = switch (ending.phase) {
            case NONE -> Component.text("라디오\n", NamedTextColor.GOLD).append(hasAntenna(world)
                    ? Component.text("두 번 우클릭: 구조 요청", NamedTextColor.WHITE)
                    : Component.text("안테나를 위나 옆에 설치하세요", NamedTextColor.YELLOW));
            case FINALE -> Component.text("구조 요청 중\n", NamedTextColor.RED).append(Component.text(Msg.clock(ending.remainingTicks / 20), NamedTextColor.WHITE));
            case ESCAPE -> Component.text("구조 헬기 도착!\n", NamedTextColor.GOLD).append(Component.text("탈출 지점 " + ending.escapePoint, NamedTextColor.WHITE));
            case DONE -> Component.text("구조 완료", NamedTextColor.GREEN);
        };
        runtime.display.text(text);
    }

    /**
     * Why the radio cannot stand here, or null. It must be on solid, natural-height ground under the open sky so the
     * zombies can always walk up to it; a radio in the air or in a sealed bunker would end the game.
     */
    private String groundProblem(Block block) {
        World world = block.getWorld();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        boolean openAbove = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) <= y;
        int solidBelow = 0;
        for (int depth = 1; depth <= EndingRules.SOLID_BELOW; depth++) {
            Block below = block.getRelative(0, -depth, 0);
            if (!below.getType().isSolid() || below.isLiquid()) { break; }
            solidBelow++;
        }
        List<Integer> surroundings = new ArrayList<>();
        for (int index = 0; index < GROUND_SAMPLES; index++) {
            double angle = Math.PI * 2 * index / GROUND_SAMPLES;
            int sampleX = x + (int) Math.round(Math.cos(angle) * GROUND_SAMPLE_DISTANCE);
            int sampleZ = z + (int) Math.round(Math.sin(angle) * GROUND_SAMPLE_DISTANCE);
            if (world.isChunkLoaded(sampleX >> 4, sampleZ >> 4)) {
                surroundings.add(world.getHighestBlockYAt(sampleX, sampleZ, HeightMap.MOTION_BLOCKING_NO_LEAVES));
            }
        }
        return switch (EndingRules.ground(y, openAbove, solidBelow, surroundings)) {
            case OK -> null;
            case COVERED -> "라디오는 하늘이 보이는 땅 위에만 설치할 수 있습니다. 지붕 아래나 동굴 안에는 놓을 수 없습니다.";
            case FLOATING -> "라디오는 단단한 땅 위에만 설치할 수 있습니다. 공중이나 얇은 발판 위에는 놓을 수 없습니다.";
            case TOO_HIGH -> "라디오는 지면 높이에만 설치할 수 있습니다. 주변 땅보다 너무 높습니다.";
        };
    }

    /** A glowing helipad, a tall glowing light pillar and a see-through label, so the escape point is easy to find. */
    private void ensureEscapeMarkers(World world, Location point, Runtime runtime) {
        if (!world.isChunkLoaded(point.getBlockX() >> 4, point.getBlockZ() >> 4)) { return; }
        if (!runtime.escapeMarkers.isEmpty() && runtime.escapeMarkers.stream().allMatch(Display::isValid)) { return; }
        removeEscapeMarkers(runtime);
        runtime.escapeMarkers.add(world.spawn(point, BlockDisplay.class, pad -> {
            pad.setBlock(Material.YELLOW_CONCRETE.createBlockData());
            pad.setTransformation(new Transformation(new Vector3f(-2.5f, 0, -2.5f), new AxisAngle4f(), new Vector3f(5, 0.1f, 5), new AxisAngle4f()));
            glow(pad, 5, 1);
        }));
        runtime.escapeMarkers.add(world.spawn(point, BlockDisplay.class, beam -> {
            beam.setBlock(Material.YELLOW_STAINED_GLASS.createBlockData());
            beam.setTransformation(new Transformation(new Vector3f(-0.4f, 0, -0.4f), new AxisAngle4f(), new Vector3f(0.8f, 80, 0.8f), new AxisAngle4f()));
            glow(beam, 1, 80);
        }));
        runtime.escapeMarkers.add(world.spawn(point.clone().add(0, 4, 0), TextDisplay.class, label -> {
            label.text(Component.text("구조 헬기 (탈출 지점)", NamedTextColor.GOLD));
            label.setBillboard(Display.Billboard.CENTER);
            label.setSeeThrough(true);
            label.setShadowed(true);
            label.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(3, 3, 3), new AxisAngle4f()));
            glow(label, 6, 2);
        }));
    }

    private static void glow(Display display, float width, float height) {
        display.setGlowing(true);
        display.setGlowColorOverride(Color.YELLOW);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setViewRange(8f);
        display.setDisplayWidth(width);
        display.setDisplayHeight(height);
        display.setPersistent(false);
        Tags.helper(display, Tags.ESCAPE_MARKER);
    }

    private static void removeEscapeMarkers(Runtime runtime) {
        for (Display marker : runtime.escapeMarkers) {
            if (marker.isValid()) { marker.remove(); }
        }
        runtime.escapeMarkers.clear();
    }

    private void removeDisplay(World world) {
        Runtime runtime = runtimes.get(world.getName());
        if (runtime != null && runtime.display != null) {
            if (runtime.display.isValid()) { runtime.display.remove(); }
            runtime.display = null;
        }
    }

    /** True for the display currently shown above a radio; other radio displays are stale. */
    public boolean currentDisplay(TextDisplay display) {
        for (Runtime runtime : runtimes.values()) {
            if (runtime.display != null && runtime.display.getUniqueId().equals(display.getUniqueId())) { return true; }
        }
        return false;
    }

    private void bar(World world, Runtime runtime, String text, EndingData ending) {
        if (runtime.bar == null) { runtime.bar = new WorldBar(Component.text(text), BossBar.Color.YELLOW); }
        runtime.bar.update(Component.text(text, NamedTextColor.GOLD), (double) ending.remainingTicks / Math.max(1, ending.totalTicks));
        runtime.bar.sync(world.getPlayers());
    }

    private void signal(World world, Location point) {
        for (int height = 0; height < 48; height += 2) {
            world.spawnParticle(Particle.END_ROD, point.getX(), point.getY() + height, point.getZ(), 1, 0.05, 0.2, 0.05, 0, null, true);
        }
        world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, point.getX(), point.getY() + 1, point.getZ(), 3, 0.3, 0.5, 0.3, 0.02, null, true);
        if (core.ticks() % 100 == 0) { firework(point.clone().add(0, 30, 0), Color.RED); }
    }

    private Location radioLocation(World world) {
        BlockPos pos = core.store().radios().get(world.getName());
        return pos == null ? null : pos.center(world).add(0, 0.5, 0);
    }

    /** A dry, solid surface spot about {@code distance} blocks from the radio in a random direction. */
    private BlockPos chooseEscapePoint(World world, Location radio, int distance) {
        BlockPos fallback = null;
        boolean nether = world.getEnvironment() == World.Environment.NETHER;
        BukkitSampler sampler = new BukkitSampler(world);
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            int[] column = EndingRules.column(radio.getX(), radio.getZ(), angle, distance + ThreadLocalRandom.current().nextInt(-10, 11));
            if (nether) {
                Optional<SpawnLocator.Spot> spot = SpawnLocator.cave(sampler, column[0], radio.getBlockY(), column[1]);
                if (spot.isPresent()) { return new BlockPos(spot.get().x(), spot.get().y(), spot.get().z()); }
                continue;
            }
            int y = world.getHighestBlockYAt(column[0], column[1], HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block ground = world.getBlockAt(column[0], y, column[1]);
            BlockPos candidate = new BlockPos(column[0], y + 1, column[1]);
            if (!ground.isLiquid() && ground.getType().isSolid()) { return candidate; }
            if (fallback == null) { fallback = candidate; }
        }
        return fallback != null ? fallback : BlockPos.of(radio);
    }

    private void firework(Location location, Color color) {
        World world = location.getWorld();
        world.spawn(location, Firework.class, firework -> {
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(color).withFade(Color.WHITE).build());
            meta.setPower(0);
            firework.setFireworkMeta(meta);
            firework.setTicksToDetonate(1);
            Tags.helper(firework, "firework");
        });
    }

    private void restoreCompass(Runtime runtime) {
        runtime.compass.forEach((id, target) -> {
            Player player = Bukkit.getPlayer(id);
            if (player != null) { player.setCompassTarget(target); }
        });
        runtime.compass.clear();
    }

    private Runtime runtime(World world) {
        return runtimes.computeIfAbsent(world.getName(), name -> new Runtime());
    }

    private static String name(UUID id) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(id);
        return player.getName() == null ? id.toString().substring(0, 8) : player.getName();
    }
}
