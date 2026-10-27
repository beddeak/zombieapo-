package io.github.beddeak.zombie.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Reads one config section with validation. Invalid or out-of-range values fall back to the default and
 * add a warning, and keys that were never read are reported as unknown by {@link #finish()}.
 */
public final class ConfigReader {
    private final ConfigurationSection section;
    private final String path;
    private final List<String> warnings;
    private final Set<String> used = new HashSet<>();
    private final List<ConfigReader> children = new ArrayList<>();

    private ConfigReader(ConfigurationSection section, String path, List<String> warnings) {
        this.section = section;
        this.path = path;
        this.warnings = warnings;
    }

    public static ConfigReader root(ConfigurationSection section, List<String> warnings) {
        return new ConfigReader(section, "", warnings);
    }

    public ConfigReader child(String key) {
        Object value = raw(key);
        ConfigurationSection childSection = null;
        if (value instanceof ConfigurationSection configurationSection) {
            childSection = configurationSection;
        } else if (value != null) {
            warnings.add(at(key) + ": 하위 설정 묶음이어야 합니다. 모든 값에 기본값을 사용합니다.");
        }
        ConfigReader child = new ConfigReader(childSection, at(key), warnings);
        children.add(child);
        return child;
    }

    public double number(String key, double fallback, double min, double max) {
        Object value = raw(key);
        if (value == null) { return fallback; }
        if (!(value instanceof Number number)) {
            warn(key, "숫자가 아닙니다.", fallback);
            return fallback;
        }
        double result = number.doubleValue();
        if (!Double.isFinite(result) || result < min || result > max) {
            warn(key, describeRange(min, max), fallback);
            return fallback;
        }
        return result;
    }

    public int integer(String key, int fallback, int min, int max) {
        Object value = raw(key);
        if (value == null) { return fallback; }
        if (!(value instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())) {
            warn(key, "정수가 아닙니다.", fallback);
            return fallback;
        }
        long result = number.longValue();
        if (result < min || result > max) {
            warn(key, describeRange(min, max), fallback);
            return fallback;
        }
        return (int) result;
    }

    public boolean bool(String key, boolean fallback) {
        Object value = raw(key);
        if (value == null) { return fallback; }
        if (!(value instanceof Boolean result)) {
            warn(key, "true 또는 false여야 합니다.", fallback);
            return fallback;
        }
        return result;
    }

    public String text(String key, String fallback) {
        Object value = raw(key);
        if (value == null) { return fallback; }
        if (value instanceof ConfigurationSection || value instanceof List<?>) {
            warn(key, "문자열이어야 합니다.", fallback);
            return fallback;
        }
        return value.toString();
    }

    public List<String> strings(String key, List<String> fallback) {
        Object value = raw(key);
        if (value == null) { return fallback; }
        if (!(value instanceof List<?> list)) {
            warn(key, "목록이어야 합니다.", fallback);
            return fallback;
        }
        List<String> result = new ArrayList<>();
        for (Object element : list) { result.add(String.valueOf(element).trim()); }
        return List.copyOf(result);
    }

    /** A list of strings kept exactly as written (spaces matter, e.g. crafting shape rows); null when absent. */
    public List<String> lines(String key) {
        Object value = raw(key);
        if (value == null) { return null; }
        if (!(value instanceof List<?> list)) {
            warn(key, "목록이어야 합니다.", "기본값");
            return null;
        }
        List<String> result = new ArrayList<>();
        for (Object element : list) { result.add(String.valueOf(element)); }
        return List.copyOf(result);
    }

    /** A sub-section read as text values, e.g. the ingredient keys of a crafting shape; null when absent. */
    public Map<String, String> entries(String key) {
        Object value = raw(key);
        if (value == null) { return null; }
        if (!(value instanceof ConfigurationSection map)) {
            warn(key, "키: 값 형식이어야 합니다.", "기본값");
            return null;
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String entry : map.getKeys(false)) { result.put(entry, String.valueOf(map.get(entry)).trim()); }
        return result;
    }

    public List<Integer> integers(String key, List<Integer> fallback, int min, int max) {
        Object value = raw(key);
        if (value == null) { return fallback; }
        if (!(value instanceof List<?> list)) {
            warn(key, "숫자 목록이어야 합니다.", fallback);
            return fallback;
        }
        List<Integer> result = new ArrayList<>();
        for (Object element : list) {
            if (!(element instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())
                    || number.longValue() < min || number.longValue() > max) {
                warn(key, "각 값은 " + min + "~" + max + " 사이의 정수여야 합니다.", fallback);
                return fallback;
            }
            result.add(number.intValue());
        }
        return List.copyOf(result);
    }

    /** Reads {@code [min, max]} (or a single number meaning both ends) and returns {@code {min, max}}. */
    public int[] range(String key, int fallbackMin, int fallbackMax, int min, int max) {
        Object value = raw(key);
        int[] fallback = {fallbackMin, fallbackMax};
        if (value == null) { return fallback; }
        List<?> list = value instanceof List<?> values ? values : List.of(value);
        if (list.size() < 1 || list.size() > 2) {
            warnRange(key, fallback);
            return fallback;
        }
        int[] result = new int[2];
        for (int index = 0; index < 2; index++) {
            Object element = list.get(Math.min(index, list.size() - 1));
            if (!(element instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())
                    || number.longValue() < min || number.longValue() > max) {
                warnRange(key, fallback);
                return fallback;
            }
            result[index] = number.intValue();
        }
        if (result[0] > result[1]) {
            warnRange(key, fallback);
            return fallback;
        }
        return result;
    }

    /** Reports keys that exist in the file but were never read, for this section and every child. */
    public void finish() {
        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (!used.contains(key)) { warnings.add(at(key) + ": 알 수 없는 설정이라 무시합니다."); }
            }
        }
        for (ConfigReader child : children) { child.finish(); }
    }

    void warn(String key, String problem, Object fallback) {
        warnings.add(at(key) + ": " + problem + " 기본값 " + format(fallback) + "을(를) 사용합니다.");
    }

    Object raw(String key) {
        used.add(key);
        return section == null ? null : section.get(key);
    }

    private void warnRange(String key, int[] fallback) {
        warn(key, "[최소, 최대] 형식의 정수 목록이어야 합니다.", "[" + fallback[0] + ", " + fallback[1] + "]");
    }

    private String at(String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    private static String describeRange(double min, double max) {
        if (max >= Integer.MAX_VALUE) { return format(min) + " 이상이어야 합니다."; }
        return format(min) + "~" + format(max) + " 사이여야 합니다.";
    }

    private static String format(Object value) {
        if (value instanceof Double number && number == Math.rint(number) && Math.abs(number) < 1e9) {
            return String.format(Locale.ROOT, "%.1f", number);
        }
        return String.valueOf(value);
    }
}
