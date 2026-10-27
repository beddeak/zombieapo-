package io.github.beddeak.zombie;

import static org.junit.jupiter.api.Assertions.*;

import io.github.beddeak.zombie.command.ApocalypseCommand;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;

class PluginDescriptorTest {
    @Test void processedDescriptorHasLoadableEntrypointAliasPermissionAndAuthor() throws Exception {
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream("/plugin.yml"))) {
            PluginDescriptionFile descriptor = new PluginDescriptionFile(stream);
            assertEquals("ZombieApocalypse", descriptor.getName());
            assertEquals("0.2.0", descriptor.getVersion());
            assertEquals("26.2", descriptor.getAPIVersion());
            assertEquals(List.of("beddeak"), descriptor.getAuthors());
            assertNotNull(Class.forName(descriptor.getMain()));
            assertEquals(List.of("apoc"), descriptor.getCommands().get("apocalypse").get("aliases"));
            assertTrue(descriptor.getPermissions().stream().anyMatch(permission -> permission.getName().equals(ApocalypseCommand.PERMISSION)
                    && permission.getDefault() == PermissionDefault.OP));
        }
    }

    @Test void bundledConfigIsValidYaml() throws Exception {
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream("/config.yml"))) {
            new YamlConfiguration().loadFromString(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
