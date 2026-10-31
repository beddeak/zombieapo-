package io.github.beddeak.zombie.command;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.Msg;
import io.github.beddeak.zombie.config.LiveConfig;
import io.github.beddeak.zombie.data.WorldData;
import io.github.beddeak.zombie.event.HordeManager;
import io.github.beddeak.zombie.logic.DayMath;
import io.github.beddeak.zombie.weapon.Molotov;
import io.github.beddeak.zombie.zombie.TestEgg;
import io.github.beddeak.zombie.zombie.ZombieType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ApocalypseCommand implements CommandExecutor, TabCompleter {
    public static final String PERMISSION = "zombieapoc.admin";
    static final List<String> SUBCOMMANDS = List.of("start", "stop", "status", "stats", "reload", "set", "limit", "spawn", "egg", "molotov", "day",
            "bloodmoon", "wave", "radio", "finale", "escape");
    private static final int MAX_SPAWN = 50;
    private static final int MAX_EGGS = 64;
    private static final String GLOBAL_CAP = "spawning.extra.global-cap";
    private static final Map<String, String> MAIN_SETTINGS = new LinkedHashMap<>();

    static {
        MAIN_SETTINGS.put("spawning.extra.per-player-cap", "주변 좀비 상한");
        MAIN_SETTINGS.put("spawning.extra.per-attempt", "추가 스폰 1회 수");
        MAIN_SETTINGS.put("spawning.extra.interval-ticks", "추가 스폰 주기(틱)");
        MAIN_SETTINGS.put("spawning.extra.global-cap", "서버 전체 상한");
        MAIN_SETTINGS.put("spawning.daylight.cap-multiplier", "낮 지상 상한 배율");
        MAIN_SETTINGS.put("spawning.daylight.per-attempt", "낮 지상 1회 수");
        MAIN_SETTINGS.put("spawning.monster-spawn-limit", "바닐라 몬스터 상한");
        MAIN_SETTINGS.put("blood-moon.cap-multiplier", "블러드문 상한 배율");
        MAIN_SETTINGS.put("horde.base-size", "웨이브 1인당 수");
        MAIN_SETTINGS.put("difficulty.health-per-day", "하루당 체력 증가");
    }

    @FunctionalInterface public interface Reloader {
        List<String> reload() throws Exception;
    }

    private final Apocalypse core;
    private final Reloader reloader;
    private final LiveConfig live;
    private final Logger logger;

    public ApocalypseCommand(Apocalypse core, Reloader reloader, LiveConfig live, Logger logger) {
        this.core = core;
        this.reloader = reloader;
        this.live = live;
        this.logger = logger;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(message("권한이 없습니다."));
            return true;
        }
        String subcommand = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "start" -> start(sender, args);
            case "stop" -> stop(sender, args);
            case "status" -> status(sender);
            case "stats" -> stats(sender, args);
            case "reload" -> {
                if (args.length == 1) { reload(sender); } else { usage(sender, label); }
            }
            case "set" -> set(sender, args, label);
            case "limit" -> limit(sender, args, label);
            case "spawn" -> spawn(sender, args, label);
            case "egg" -> egg(sender, args, label);
            case "molotov" -> molotov(sender, args);
            case "day" -> day(sender, args, label);
            case "bloodmoon" -> bloodMoon(sender, args, label);
            case "wave", "horde" -> horde(sender, args, label);
            case "radio" -> radio(sender, args, label);
            case "finale" -> finale(sender, args, label);
            case "escape" -> escape(sender, args, label);
            default -> usage(sender, label);
        }
        return true;
    }

    private void usage(CommandSender sender, String label) {
        sender.sendMessage(message("사용법: /" + label + " <" + String.join("|", SUBCOMMANDS) + ">"));
    }

    private void start(CommandSender sender, String[] args) {
        Integer grace = null;
        String worldName = null;
        for (int index = 1; index < args.length; index++) {
            String argument = args[index];
            if (argument.equalsIgnoreCase("now")) {
                grace = 0;
            } else if (number(argument) != null) {
                grace = number(argument);
            } else {
                worldName = argument;
            }
        }
        if (grace != null && (grace < 0 || grace > 86_400)) {
            sender.sendMessage(message("준비 시간은 0~86400초 사이여야 합니다."));
            return;
        }
        World world = world(sender, worldName == null ? new String[0] : new String[]{"start", worldName}, 1, false);
        if (world == null) { return; }
        int seconds = grace != null ? grace : core.defaultGraceSeconds();
        if (!core.start(world, seconds)) {
            int left = core.preparingSeconds(world);
            sender.sendMessage(message(left > 0 ? world.getName() + "에서는 이미 시작을 준비하고 있습니다. (남은 시간 " + Msg.clock(left) + ")"
                    : world.getName() + "에서는 이미 진행 중입니다."));
            return;
        }
        sender.sendMessage(message(seconds > 0 ? world.getName() + "에서 " + Msg.clock(seconds) + " 뒤 좀비 아포칼립스가 시작됩니다."
                : world.getName() + "에서 좀비 아포칼립스를 시작했습니다."));
    }

    private void stop(CommandSender sender, String[] args) {
        World world = world(sender, args, 1, false);
        if (world == null) { return; }
        sender.sendMessage(core.stop(world) ? message(world.getName() + "에서 좀비 아포칼립스를 끝냈습니다.")
                : message(world.getName() + "에서는 진행 중이 아닙니다."));
    }

    private void status(CommandSender sender) {
        List<World> worlds = core.registeredWorlds();
        if (worlds.isEmpty()) {
            sender.sendMessage(message("진행 중인 월드가 없습니다."));
            return;
        }
        for (World world : worlds) {
            if (!core.active(world)) {
                sender.sendMessage(message(world.getName() + ": 시작 준비 중 (남은 시간 " + Msg.clock(core.preparingSeconds(world)) + ")"));
                continue;
            }
            long timeOfDay = DayMath.timeOfDay(core.fullTime(world));
            sender.sendMessage(message(world.getName() + ": " + core.day(world) + "일차, 시각 " + timeOfDay
                    + (core.bloodMoon(world) ? ", 블러드문" : "")
                    + (core.hordes().running(world) ? ", 좀비 웨이브 진행 중(남은 " + core.hordes().remaining(world) + ")" : "")
                    + (core.quietSurfaceSeconds(world) > 0 ? ", 지상 스폰 대기 " + Msg.clock(core.quietSurfaceSeconds(world)) : "")));
            Map<ZombieType, Integer> counts = core.registry().counts(world);
            int total = counts.values().stream().mapToInt(Integer::intValue).sum();
            List<String> parts = new ArrayList<>();
            counts.forEach((type, count) -> parts.add(type.displayName() + " " + count));
            sender.sendMessage(Component.text("  커스텀 좀비 " + total + "마리" + (parts.isEmpty() ? "" : ": " + String.join(", ", parts))));
            for (String line : core.ending().status(world)) { sender.sendMessage(Component.text("  " + line)); }
            for (String line : core.assembly().status(world)) { sender.sendMessage(Component.text("  " + line)); }
        }
    }

    private void stats(CommandSender sender, String[] args) {
        World world = world(sender, args, 1, true);
        if (world == null) { return; }
        for (Component line : core.ending().resultBoard(world)) { sender.sendMessage(line); }
    }

    private void reload(CommandSender sender) {
        try {
            List<String> warnings = reloader.reload();
            sender.sendMessage(warnings.isEmpty() ? message("설정을 다시 불러왔습니다.")
                    : message("설정을 다시 불러왔습니다. 경고 " + warnings.size() + "개는 서버 로그를 확인하세요."));
        } catch (Exception exception) {
            logger.log(Level.WARNING, "ZombieApocalypse config reload failed; keeping previous settings", exception);
            sender.sendMessage(message("설정을 불러오지 못해 이전 설정을 유지합니다. 서버 로그를 확인하세요."));
        }
    }

    private void limit(CommandSender sender, String[] args, String label) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("day")) {
            String[] dayArgs = new String[args.length];
            dayArgs[0] = "day";
            dayArgs[1] = "set";
            System.arraycopy(args, 2, dayArgs, 2, args.length - 2);
            day(sender, dayArgs, label);
            return;
        }
        if (args.length > 2) {
            sender.sendMessage(message("사용법: /" + label + " limit [수] 또는 /" + label + " limit day <일차> [월드]"));
            return;
        }
        if (args.length == 2) {
            set(sender, new String[]{"set", GLOBAL_CAP, args[1]}, label);
            return;
        }
        try {
            sender.sendMessage(message("서버 전체 좀비 상한: " + live.value(GLOBAL_CAP) + ". 바꾸기: /" + label + " limit <수>, 날짜 바꾸기: /"
                    + label + " limit day <일차>"));
        } catch (Exception exception) {
            logger.log(Level.WARNING, "ZombieApocalypse could not read config.yml", exception);
            sender.sendMessage(message("config.yml을 읽지 못했습니다. 서버 로그를 확인하세요."));
        }
    }

    private void set(CommandSender sender, String[] args, String label) {
        try {
            if (args.length < 2) {
                sender.sendMessage(message("실시간 조정: /" + label + " set <설정> <값> — 바로 적용되고 config.yml에 저장됩니다. 탭을 누르면 모든 설정이 나옵니다."));
                for (Map.Entry<String, String> entry : MAIN_SETTINGS.entrySet()) {
                    String key = entry.getKey();
                    sender.sendMessage(Component.text("  " + entry.getValue() + "  ", NamedTextColor.GRAY)
                            .append(Component.text(key + " = " + live.value(key), NamedTextColor.WHITE))
                            .clickEvent(ClickEvent.suggestCommand("/" + label + " set " + key + " "))
                            .hoverEvent(HoverEvent.showText(Component.text("클릭하면 입력창에 넣습니다"))));
                }
                return;
            }
            String key = args[1];
            if (!live.known(key)) {
                sender.sendMessage(message("모르는 설정입니다: " + key + ". /" + label + " set 뒤에서 탭을 누르면 목록이 나옵니다."));
                return;
            }
            if (args.length == 2) {
                sender.sendMessage(message(key + " = " + live.value(key)));
                return;
            }
            LiveConfig.Change change = live.set(key, String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
            if (change.problem() != null) {
                sender.sendMessage(message("바꾸지 않았습니다. " + change.problem()));
                return;
            }
            core.settings(change.settings());
            logger.info(sender.getName() + " set " + key + ": " + change.previous() + " -> " + change.value());
            sender.sendMessage(message(key + ": " + change.previous() + " → " + change.value() + " (바로 적용, config.yml에 저장)"));
        } catch (Exception exception) {
            logger.log(Level.WARNING, "ZombieApocalypse could not change config.yml", exception);
            sender.sendMessage(message("config.yml을 읽거나 쓰지 못했습니다. 서버 로그를 확인하세요."));
        }
    }

    private void spawn(CommandSender sender, String[] args, String label) {
        Optional<ZombieType> type = args.length >= 2 ? ZombieType.parse(args[1]) : Optional.empty();
        if (type.isEmpty() || type.get() == ZombieType.PLAYER) {
            sender.sendMessage(message("사용법: /" + label + " spawn <타입> [수] — 타입: " + String.join(", ", typeNames())));
            return;
        }
        int count = 1;
        if (args.length >= 3) {
            Integer parsed = number(args[2]);
            if (parsed == null || parsed < 1 || parsed > MAX_SPAWN) {
                sender.sendMessage(message("수는 1~" + MAX_SPAWN + " 사이여야 합니다."));
                return;
            }
            count = parsed;
        }
        Location location = spawnLocation(sender, args);
        if (location == null) {
            sender.sendMessage(message("콘솔에서는 위치를 지정해야 합니다: /" + label + " spawn <타입> <수> <월드> <x> <y> <z>"));
            return;
        }
        if (!core.active(location.getWorld())) {
            sender.sendMessage(message(location.getWorld().getName() + "에서는 아포칼립스가 꺼져 있습니다. 먼저 start 하세요."));
            return;
        }
        for (int index = 0; index < count; index++) {
            if (type.get() == ZombieType.TANK) {
                core.tanks().spawnAt(location);
            } else {
                core.factory().spawn(location, type.get(), core.context(location.getWorld()));
            }
        }
        sender.sendMessage(message(type.get().displayName() + " 좀비 " + count + "마리를 소환했습니다."));
    }

    private Location spawnLocation(CommandSender sender, String[] args) {
        if (args.length >= 7) {
            World world = Bukkit.getWorld(args[3]);
            Integer x = number(args[4]);
            Integer y = number(args[5]);
            Integer z = number(args[6]);
            return world == null || x == null || y == null || z == null ? null : new Location(world, x + 0.5, y, z + 0.5);
        }
        if (!(sender instanceof Player player)) { return null; }
        Block block = player.getTargetBlockExact(32);
        if (block != null) { return block.getLocation().add(0.5, 1, 0.5); }
        return player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(3));
    }

    private void molotov(CommandSender sender, String[] args) {
        int count = 1;
        if (args.length >= 2) {
            Integer parsed = number(args[1]);
            if (parsed == null || parsed < 1 || parsed > MAX_EGGS) {
                sender.sendMessage(message("수는 1~" + MAX_EGGS + " 사이여야 합니다."));
                return;
            }
            count = parsed;
        }
        Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player player ? player : null;
        if (target == null) {
            sender.sendMessage(message("받을 플레이어를 찾을 수 없습니다."));
            return;
        }
        for (int left = count; left > 0; left -= Molotov.MAX_STACK) {
            for (ItemStack rest : target.getInventory().addItem(Molotov.create(Math.min(left, Molotov.MAX_STACK))).values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), rest);
            }
        }
        sender.sendMessage(message(target.getName() + "에게 화염병을 " + count + "개 줬습니다."));
    }

    private void egg(CommandSender sender, String[] args, String label) {
        String which = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        boolean all = which.equals("all");
        Optional<ZombieType> type = all ? Optional.empty() : ZombieType.parse(which);
        if (!all && (type.isEmpty() || type.get() == ZombieType.PLAYER)) {
            sender.sendMessage(message("사용법: /" + label + " egg <타입|all> [수] [플레이어] — 타입: " + String.join(", ", typeNames())));
            return;
        }
        int count = 1;
        if (args.length >= 3) {
            Integer parsed = number(args[2]);
            if (parsed == null || parsed < 1 || parsed > MAX_EGGS) {
                sender.sendMessage(message("수는 1~" + MAX_EGGS + " 사이여야 합니다."));
                return;
            }
            count = parsed;
        }
        Player target = args.length >= 4 ? Bukkit.getPlayerExact(args[3]) : sender instanceof Player player ? player : null;
        if (target == null) {
            sender.sendMessage(message("받을 플레이어를 찾을 수 없습니다."));
            return;
        }
        List<ZombieType> types = all ? eggTypes() : List.of(type.get());
        for (ZombieType eggType : types) {
            for (ItemStack left : target.getInventory().addItem(TestEgg.create(eggType, count)).values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), left);
            }
        }
        String what = all ? "모든 좀비 스폰알(" + types.size() + "종)" : type.get().displayName() + " 좀비 스폰알";
        sender.sendMessage(message(target.getName() + "에게 " + what + "을 " + count + "개씩 줬습니다."));
    }

    private static List<ZombieType> eggTypes() {
        List<ZombieType> types = new ArrayList<>();
        for (ZombieType type : ZombieType.values()) {
            if (type != ZombieType.PLAYER) { types.add(type); }
        }
        return types;
    }

    private void day(CommandSender sender, String[] args, String label) {
        boolean set = args.length >= 2 && args[1].equalsIgnoreCase("set");
        World world = world(sender, args, set ? 3 : 1, true);
        if (world == null) { return; }
        if (!set) {
            sender.sendMessage(message(world.getName() + ": " + core.day(world) + "일차 (시각 " + DayMath.timeOfDay(core.fullTime(world)) + ")"));
            return;
        }
        Integer day = args.length >= 3 ? number(args[2]) : null;
        if (day == null || day < 1 || day > 100_000) {
            sender.sendMessage(message("사용법: /" + label + " day set <1 이상의 날짜>"));
            return;
        }
        core.setDay(world, day);
        sender.sendMessage(message(world.getName() + "을(를) " + day + "일차로 바꿨습니다."));
    }

    private void bloodMoon(CommandSender sender, String[] args, String label) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (!action.equals("start") && !action.equals("stop")) {
            sender.sendMessage(message("사용법: /" + label + " bloodmoon <start|stop>"));
            return;
        }
        World world = world(sender, args, 2, true);
        if (world == null) { return; }
        WorldData data = core.data(world);
        if (action.equals("start")) {
            core.bloodMoonManager().forceStart(world, data);
            sender.sendMessage(message("블러드문을 시작했습니다. 다음 아침까지 이어집니다."));
        } else {
            core.bloodMoonManager().forceStop(world, data);
            sender.sendMessage(message("블러드문을 끝냈습니다. 이번 밤에는 다시 뜨지 않습니다."));
        }
    }

    private void horde(CommandSender sender, String[] args, String label) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (!action.equals("start") && !action.equals("stop")) {
            sender.sendMessage(message("사용법: /" + label + " wave <start|stop>"));
            return;
        }
        World world = world(sender, args, 2, true);
        if (world == null) { return; }
        if (action.equals("start")) {
            if (core.hordes().start(world, HordeManager.Kind.MANUAL)) {
                sender.sendMessage(message("좀비 웨이브를 시작했습니다."));
            } else {
                sender.sendMessage(message(core.hordes().running(world) ? "이미 좀비 웨이브가 진행 중입니다." : "싸울 수 있는 플레이어가 없습니다."));
            }
        } else if (core.hordes().running(world)) {
            core.hordes().stop(world);
            sender.sendMessage(message("좀비 웨이브를 끝냈습니다. 이미 나온 좀비는 남아 있습니다."));
        } else {
            sender.sendMessage(message("진행 중인 좀비 웨이브가 없습니다."));
        }
    }

    private void radio(CommandSender sender, String[] args, String label) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "give" -> {
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player player ? player : null;
                if (target == null) {
                    sender.sendMessage(message("받을 플레이어를 찾을 수 없습니다."));
                    return;
                }
                for (ItemStack left : target.getInventory().addItem(core.ending().radioItem(), core.ending().antennaItem()).values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), left);
                }
                sender.sendMessage(message(target.getName() + "에게 라디오와 안테나를 줬습니다(조립 없이 바로)."));
            }
            case "status" -> {
                World world = world(sender, args, 2, false);
                if (world == null) { return; }
                for (String line : core.ending().status(world)) { sender.sendMessage(message(line)); }
                for (String line : core.assembly().status(world)) { sender.sendMessage(message(line)); }
            }
            case "reset" -> {
                World world = world(sender, args, 2, true);
                if (world == null) { return; }
                core.ending().reset(world, core.data(world));
                sender.sendMessage(message("엔딩 진행을 초기화했습니다. 라디오는 그대로 남아 있습니다."));
            }
            default -> sender.sendMessage(message("사용법: /" + label + " radio <give [플레이어]|status|reset>"));
        }
    }

    private void finale(CommandSender sender, String[] args, String label) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (!action.equals("start") && !action.equals("skip")) {
            sender.sendMessage(message("사용법: /" + label + " finale <start|skip>"));
            return;
        }
        World world = world(sender, args, 2, true);
        if (world == null) { return; }
        String problem = action.equals("start") ? core.ending().startFinale(world, core.data(world), sender.getName())
                : core.ending().skipFinale(world, core.data(world));
        sender.sendMessage(message(problem == null ? (action.equals("start") ? "구조 피날레를 시작했습니다." : "피날레를 건너뛰고 탈출 단계로 넘어갔습니다.") : problem));
    }

    private void escape(CommandSender sender, String[] args, String label) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("skip")) {
            sender.sendMessage(message("사용법: /" + label + " escape skip"));
            return;
        }
        World world = world(sender, args, 2, true);
        if (world == null) { return; }
        String problem = core.ending().skipEscape(world, core.data(world));
        sender.sendMessage(message(problem == null ? "탈출 단계를 끝내고 엔딩으로 넘어갔습니다." : problem));
    }

    private World world(CommandSender sender, String[] args, int index, boolean mustBeActive) {
        World world;
        if (args.length > index) {
            world = Bukkit.getWorld(args[index]);
            if (world == null) {
                sender.sendMessage(message("월드를 찾을 수 없습니다: " + args[index]));
                return null;
            }
        } else if (sender instanceof Entity entity) {
            world = entity.getWorld();
        } else if (sender instanceof BlockCommandSender block) {
            world = block.getBlock().getWorld();
        } else {
            List<World> active = core.registeredWorlds();
            if (active.size() == 1) {
                world = active.get(0);
            } else if (!mustBeActive && active.isEmpty() && !Bukkit.getWorlds().isEmpty()) {
                world = Bukkit.getWorlds().get(0);
            } else {
                sender.sendMessage(message("월드 이름을 지정해 주세요."));
                return null;
            }
        }
        if (mustBeActive && !core.active(world)) {
            int left = core.preparingSeconds(world);
            sender.sendMessage(message(left > 0 ? world.getName() + "에서는 아직 시작을 준비하고 있습니다. (남은 시간 " + Msg.clock(left) + ")"
                    : world.getName() + "에서는 아포칼립스가 꺼져 있습니다."));
            return null;
        }
        return world;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION) || args.length == 0) { return List.of(); }
        String sub = args[0].toLowerCase(Locale.ROOT);
        List<String> options;
        if (args.length == 1) {
            options = SUBCOMMANDS;
        } else if (args.length == 2) {
            options = switch (sub) {
                case "start" -> concat(List.of("now", "60", "300"), worldNames());
                case "stop", "stats" -> worldNames();
                case "set" -> live.complete(args[1]);
                case "limit" -> List.of("day", "1000", "2000");
                case "spawn" -> typeNames();
                case "egg" -> eggNames();
                case "molotov" -> List.of("1", "8", "16");
                case "day" -> List.of("set");
                case "bloodmoon", "wave", "horde" -> List.of("start", "stop");
                case "radio" -> List.of("give", "status", "reset");
                case "finale" -> List.of("start", "skip");
                case "escape" -> List.of("skip");
                default -> List.of();
            };
        } else if (args.length == 3) {
            options = switch (sub) {
                case "radio" -> args[1].equalsIgnoreCase("give")
                        ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList() : worldNames();
                case "set" -> valueHints(args[1]);
                case "limit" -> args[1].equalsIgnoreCase("day") ? List.of("1", "2", "3", "7") : List.of();
                case "molotov" -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                case "spawn", "egg" -> List.of("1", "5", "10");
                case "bloodmoon", "wave", "horde", "finale", "escape" -> worldNames();
                default -> List.of();
            };
        } else if (args.length == 4 && sub.equals("egg")) {
            options = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        } else {
            options = List.of();
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private List<String> valueHints(String key) {
        if (!live.known(key)) { return List.of(); }
        try {
            Object value = live.value(key);
            return value instanceof Boolean ? List.of("true", "false") : List.of(String.valueOf(value));
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static List<String> typeNames() {
        List<String> names = new ArrayList<>();
        for (ZombieType type : ZombieType.values()) {
            if (type != ZombieType.PLAYER) { names.add(type.configKey()); }
        }
        return names;
    }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    private static List<String> eggNames() {
        List<String> names = new ArrayList<>(List.of("all"));
        names.addAll(typeNames());
        return names;
    }

    private static List<String> worldNames() {
        return Bukkit.getWorlds().stream().map(World::getName).toList();
    }

    private static Integer number(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    static Component message(String text) {
        return Msg.info(text);
    }
}
