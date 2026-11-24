package org.pvpcore.features.kits;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Reads and retires the failed-write files produced by {@link KitRecordStore#shutdown}. */
final class FailedWriteRecovery {

    record Entry(String id, String data) {
    }

    private FailedWriteRecovery() {
    }

    static List<Path> files(Path dataFolder) {
        try (Stream<Path> paths = Files.list(dataFolder)) {
            return paths.filter(path -> path.getFileName().toString().startsWith("failed-kit-writes-")
                            && path.getFileName().toString().endsWith(".yml"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    static List<Entry> read(Path dataFolder) {
        List<Entry> entries = new ArrayList<>();
        for (Path file : files(dataFolder)) {
            entries.addAll(readFile(file));
        }
        return entries;
    }

    static List<Entry> readFile(Path file) {
        List<Entry> entries = new ArrayList<>();
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
            for (String key : yaml.getKeys(true)) {
                if (yaml.isConfigurationSection(key)) continue;
                if (key.startsWith("kits.")) {
                    entries.add(new Entry(key.substring("kits.".length()), yaml.getString(key)));
                } else if (key.startsWith("delete.")) {
                    entries.add(new Entry(key.substring("delete.".length()), KitRecordStore.DELETED));
                }
            }
        } catch (RuntimeException corrupt) {
            // Leave the file in place for manual recovery; never abort enable on bad YAML.
        }
        return entries;
    }

    /** Removes superseded ids from a recovery file still kept for retry. */
    static void rewrite(Path file, Map<String, String> remaining) {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            remaining.forEach((id, data) -> {
                if (KitRecordStore.DELETED.equals(data)) {
                    yaml.set("delete." + id, true);
                } else {
                    yaml.set("kits." + id, data);
                }
            });
            org.pvpcore.config.ConfigFiles.write(yaml, file);
        } catch (IOException e) {
            // The old file stays intact; replay will be attempted again on next enable.
        }
    }

    static void markApplied(Path file) throws IOException {
        Files.move(file, Path.of(file + ".applied"), StandardCopyOption.REPLACE_EXISTING);
    }
}
