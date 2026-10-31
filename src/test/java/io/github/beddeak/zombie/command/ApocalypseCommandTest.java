package io.github.beddeak.zombie.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.config.LiveConfig;
import io.github.beddeak.zombie.config.Settings;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApocalypseCommandTest {
    private static final String USAGE = "사용법: /apoc <start|stop|status|stats|reload|set|limit|spawn|egg|molotov|day|bloodmoon|wave|radio|finale|escape>";
    private final Apocalypse core = mock(Apocalypse.class);
    private final ApocalypseCommand.Reloader reloader = mock(ApocalypseCommand.Reloader.class);
    private final LiveConfig live = mock(LiveConfig.class);
    private final Logger logger = mock(Logger.class);
    private final Command command = mock(Command.class);
    private final Player player = mock(Player.class);
    private final World world = mock(World.class);
    private final ApocalypseCommand executor = new ApocalypseCommand(core, reloader, live, logger);

    private void admin() {
        when(player.hasPermission(ApocalypseCommand.PERMISSION)).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("world");
    }

    @ParameterizedTest @ValueSource(strings = {"start", "stop", "status", "stats", "reload", "set", "limit", "spawn", "egg", "molotov", "day", "bloodmoon", "wave", "radio", "finale", "escape"})
    void ordinaryPlayersCannotUseAnySubcommand(String subcommand) throws Exception {
        executor.onCommand(player, command, "apocalypse", new String[]{subcommand});
        verify(player).sendMessage(ApocalypseCommand.message("권한이 없습니다."));
        verifyNoInteractions(core, reloader, live);
    }

    @Test void adminWithoutOrWithUnknownSubcommandSeesUsage() {
        admin();
        executor.onCommand(player, command, "apoc", new String[0]);
        executor.onCommand(player, command, "apoc", new String[]{"explode"});
        executor.onCommand(player, command, "apoc", new String[]{"reload", "extra"});
        verify(player, times(3)).sendMessage(ApocalypseCommand.message(USAGE));
        verifyNoInteractions(reloader);
    }

    @Test void startAndStopUseThePlayersWorld() {
        admin();
        when(core.start(world, 0)).thenReturn(true, false);
        when(core.stop(world)).thenReturn(true);
        executor.onCommand(player, command, "apoc", new String[]{"start"});
        executor.onCommand(player, command, "apoc", new String[]{"START"});
        executor.onCommand(player, command, "apoc", new String[]{"stop"});
        verify(player).sendMessage(ApocalypseCommand.message("world에서 좀비 아포칼립스를 시작했습니다."));
        verify(player).sendMessage(ApocalypseCommand.message("world에서는 이미 진행 중입니다."));
        verify(player).sendMessage(ApocalypseCommand.message("world에서 좀비 아포칼립스를 끝냈습니다."));
    }

    @Test void startUsesTheDefaultPreparationTimeUnlessGiven() {
        admin();
        when(core.defaultGraceSeconds()).thenReturn(300);
        when(core.start(eq(world), anyInt())).thenReturn(true);
        executor.onCommand(player, command, "apoc", new String[]{"start"});
        executor.onCommand(player, command, "apoc", new String[]{"start", "60"});
        executor.onCommand(player, command, "apoc", new String[]{"start", "now"});
        verify(core).start(world, 300);
        verify(core).start(world, 60);
        verify(core).start(world, 0);
        verify(player).sendMessage(ApocalypseCommand.message("world에서 5:00 뒤 좀비 아포칼립스가 시작됩니다."));
        verify(player).sendMessage(ApocalypseCommand.message("world에서 1:00 뒤 좀비 아포칼립스가 시작됩니다."));
        verify(player).sendMessage(ApocalypseCommand.message("world에서 좀비 아포칼립스를 시작했습니다."));
    }

    @Test void startRejectsNegativePreparationAndReportsAWorldThatIsPreparing() {
        admin();
        executor.onCommand(player, command, "apoc", new String[]{"start", "-5"});
        verify(player).sendMessage(ApocalypseCommand.message("준비 시간은 0~86400초 사이여야 합니다."));
        verify(core, never()).start(any(), anyInt());
        when(core.start(eq(world), anyInt())).thenReturn(false);
        when(core.preparingSeconds(world)).thenReturn(95);
        executor.onCommand(player, command, "apoc", new String[]{"start", "now"});
        verify(player).sendMessage(ApocalypseCommand.message("world에서는 이미 시작을 준비하고 있습니다. (남은 시간 1:35)"));
        executor.onCommand(player, command, "apoc", new String[]{"horde", "start"});
        verify(player).sendMessage(ApocalypseCommand.message("world에서는 아직 시작을 준비하고 있습니다. (남은 시간 1:35)"));
    }

    @Test void worldScopedCommandsRefuseInactiveWorlds() {
        admin();
        when(core.active(world)).thenReturn(false);
        executor.onCommand(player, command, "apoc", new String[]{"horde", "start"});
        executor.onCommand(player, command, "apoc", new String[]{"day", "set", "5"});
        verify(player, times(2)).sendMessage(ApocalypseCommand.message("world에서는 아포칼립스가 꺼져 있습니다."));
        verify(core, never()).setDay(any(), anyInt());
        verify(core, never()).hordes();
    }

    @Test void daySetRejectsBadNumbers() {
        admin();
        when(core.active(world)).thenReturn(true);
        executor.onCommand(player, command, "apoc", new String[]{"day", "set", "0"});
        executor.onCommand(player, command, "apoc", new String[]{"day", "set", "abc"});
        verify(player, times(2)).sendMessage(ApocalypseCommand.message("사용법: /apoc day set <1 이상의 날짜>"));
        executor.onCommand(player, command, "apoc", new String[]{"day", "set", "7"});
        verify(core).setDay(world, 7);
    }

    @Test void spawnRejectsUnknownTypesPlayerZombiesAndBadCounts() {
        admin();
        executor.onCommand(player, command, "apoc", new String[]{"spawn", "dragon"});
        executor.onCommand(player, command, "apoc", new String[]{"spawn", "player"});
        executor.onCommand(player, command, "apoc", new String[]{"spawn", "boomer", "500"});
        verify(player).sendMessage(ApocalypseCommand.message("수는 1~50 사이여야 합니다."));
        verify(core, never()).factory();
        verify(core, never()).tanks();
    }

    @Test void adminReloadReportsSuccessAndWarnings() throws Exception {
        admin();
        when(reloader.reload()).thenReturn(List.of()).thenReturn(List.of("a", "b"));
        executor.onCommand(player, command, "apocalypse", new String[]{"RELOAD"});
        executor.onCommand(player, command, "apocalypse", new String[]{"reload"});
        verify(player).sendMessage(ApocalypseCommand.message("설정을 다시 불러왔습니다."));
        verify(player).sendMessage(ApocalypseCommand.message("설정을 다시 불러왔습니다. 경고 2개는 서버 로그를 확인하세요."));
    }

    @Test void failedReloadIsLoggedAndReportedInsteadOfThrown() throws Exception {
        admin();
        IOException failure = new IOException("broken yaml");
        doThrow(failure).when(reloader).reload();
        assertTrue(executor.onCommand(player, command, "apocalypse", new String[]{"reload"}));
        verify(logger).log(eq(Level.WARNING), anyString(), same(failure));
        verify(player).sendMessage(ApocalypseCommand.message("설정을 불러오지 못해 이전 설정을 유지합니다. 서버 로그를 확인하세요."));
    }

    @Test void tabsExposeSubcommandsOnlyToAdmins() {
        assertTrue(executor.onTabComplete(player, command, "apoc", new String[]{""}).isEmpty());
        admin();
        assertEquals(List.of("reload", "radio"), executor.onTabComplete(player, command, "apoc", new String[]{"r"}));
        assertEquals(List.of("start", "stop", "status", "stats", "set", "spawn"), executor.onTabComplete(player, command, "apoc", new String[]{"s"}));
        assertTrue(executor.onTabComplete(player, command, "apoc", new String[]{"x"}).isEmpty());
        assertTrue(executor.onTabComplete(player, command, "apoc", new String[]{"reload", ""}).isEmpty());
        assertEquals(List.of("start", "stop"), executor.onTabComplete(player, command, "apoc", new String[]{"wave", ""}));
        assertEquals(List.of("boomer", "bomber"), executor.onTabComplete(player, command, "apoc", new String[]{"spawn", "bo"}));
        assertFalse(executor.onTabComplete(player, command, "apoc", new String[]{"spawn", ""}).contains("player"));
        assertEquals(List.of("egg", "escape"), executor.onTabComplete(player, command, "apoc", new String[]{"e"}));
        assertEquals(List.of("day"), executor.onTabComplete(player, command, "apoc", new String[]{"limit", "d"}));
        assertEquals(List.of("molotov"), executor.onTabComplete(player, command, "apoc", new String[]{"m"}));
        assertEquals(List.of("1", "2", "3", "7"), executor.onTabComplete(player, command, "apoc", new String[]{"limit", "day", ""}));
        assertEquals(List.of("all"), executor.onTabComplete(player, command, "apoc", new String[]{"egg", "a"}));
        List<String> eggs = executor.onTabComplete(player, command, "apoc", new String[]{"egg", ""});
        assertTrue(eggs.contains("tank") && eggs.contains("jockey"));
        assertFalse(eggs.contains("player"));
    }

    @Test void setAppliesCheckedChangesAndRefusesTheRest() throws Exception {
        admin();
        String key = "spawning.extra.per-player-cap";
        Settings next = Settings.defaults();
        when(live.known(key)).thenReturn(true);
        when(live.set(key, "60")).thenReturn(new LiveConfig.Change(next, 40, 60, null));
        when(live.set(key, "많이")).thenReturn(new LiveConfig.Change(null, 40, "많이", key + ": 숫자가 아닙니다."));
        executor.onCommand(player, command, "apoc", new String[]{"set", key, "60"});
        executor.onCommand(player, command, "apoc", new String[]{"set", key, "많이"});
        executor.onCommand(player, command, "apoc", new String[]{"set", "nope", "1"});
        verify(core).settings(next);
        verify(player).sendMessage(ApocalypseCommand.message(key + ": 40 → 60 (바로 적용, config.yml에 저장)"));
        verify(player).sendMessage(ApocalypseCommand.message("바꾸지 않았습니다. " + key + ": 숫자가 아닙니다."));
        verify(player).sendMessage(ApocalypseCommand.message("모르는 설정입니다: nope. /apoc set 뒤에서 탭을 누르면 목록이 나옵니다."));
        verify(live, never()).set(eq("nope"), anyString());
    }

    @Test void limitChangesTheServerCapAndTheDay() throws Exception {
        admin();
        String key = "spawning.extra.global-cap";
        Settings next = Settings.defaults();
        when(live.known(key)).thenReturn(true);
        when(live.value(key)).thenReturn(1000);
        when(live.set(key, "2000")).thenReturn(new LiveConfig.Change(next, 1000, 2000, null));
        when(core.active(world)).thenReturn(true);
        executor.onCommand(player, command, "apoc", new String[]{"limit"});
        executor.onCommand(player, command, "apoc", new String[]{"limit", "2000"});
        executor.onCommand(player, command, "apoc", new String[]{"limit", "day", "3"});
        executor.onCommand(player, command, "apoc", new String[]{"limit", "1", "2"});
        verify(player).sendMessage(ApocalypseCommand.message("서버 전체 좀비 상한: 1000. 바꾸기: /apoc limit <수>, 날짜 바꾸기: /apoc limit day <일차>"));
        verify(core).settings(next);
        verify(player).sendMessage(ApocalypseCommand.message(key + ": 1000 → 2000 (바로 적용, config.yml에 저장)"));
        verify(core).setDay(world, 3);
        verify(player).sendMessage(ApocalypseCommand.message("사용법: /apoc limit [수] 또는 /apoc limit day <일차> [월드]"));
    }

    @Test void molotovRejectsBadCounts() {
        admin();
        executor.onCommand(player, command, "apoc", new String[]{"molotov", "0"});
        executor.onCommand(player, command, "apoc", new String[]{"molotov", "많이"});
        executor.onCommand(player, command, "apoc", new String[]{"molotov", "65"});
        verify(player, times(3)).sendMessage(ApocalypseCommand.message("수는 1~64 사이여야 합니다."));
        verify(player, never()).getInventory();
    }

    @Test void setTabsCompleteKeysAndSuggestTheCurrentValue() throws Exception {
        admin();
        when(live.complete("sp")).thenReturn(List.of("spawning."));
        when(live.known("spawning.daylight.enabled")).thenReturn(true);
        when(live.value("spawning.daylight.enabled")).thenReturn(true);
        when(live.known("spawning.extra.per-attempt")).thenReturn(true);
        when(live.value("spawning.extra.per-attempt")).thenReturn(5);
        assertEquals(List.of("spawning."), executor.onTabComplete(player, command, "apoc", new String[]{"set", "sp"}));
        assertEquals(List.of("true", "false"), executor.onTabComplete(player, command, "apoc", new String[]{"set", "spawning.daylight.enabled", ""}));
        assertEquals(List.of("5"), executor.onTabComplete(player, command, "apoc", new String[]{"set", "spawning.extra.per-attempt", ""}));
    }

    @Test void eggRejectsUnknownTypesPlayerZombiesAndBadCounts() {
        admin();
        executor.onCommand(player, command, "apoc", new String[]{"egg"});
        executor.onCommand(player, command, "apoc", new String[]{"egg", "dragon"});
        executor.onCommand(player, command, "apoc", new String[]{"egg", "player"});
        executor.onCommand(player, command, "apoc", new String[]{"egg", "all", "65"});
        executor.onCommand(player, command, "apoc", new String[]{"egg", "boomer", "0"});
        verify(player, times(2)).sendMessage(ApocalypseCommand.message("수는 1~64 사이여야 합니다."));
        verify(player, never()).getInventory();
        verifyNoInteractions(core);
    }
}
