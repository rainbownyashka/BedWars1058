package com.andrei1058.bedwars.sidebar;

import com.andrei1058.bedwars.BedWars;
import com.andrei1058.bedwars.api.arena.GameState;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.language.Language;
import com.andrei1058.bedwars.api.language.Messages;
import com.andrei1058.bedwars.arena.Arena;
import com.andrei1058.spigot.sidebar.SidebarLine;
import com.google.gson.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static com.andrei1058.bedwars.api.language.Language.getScoreboard;

public final class ScoreboardDebugService {

    public static final List<String> DUMPABLE_STATES = Arrays.asList(
            "lobby",
            "waiting.player",
            "waiting.spectator",
            "starting.player",
            "starting.spectator",
            "playing.alive",
            "playing.spectator",
            "playing.eliminated",
            "restarting.winner-alive",
            "restarting.winner-eliminated",
            "restarting.loser",
            "restarting.spectator"
    );

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ScoreboardDebugService() {
    }

    public static Snapshot capture(Player player, String forcedState) {
        IArena arena = Arena.getArenaByPlayer(player);
        String group = arena == null ? "Default" : arena.getGroup();
        String stateKey = forcedState == null ? detectStateKey(player, arena) : forcedState;

        List<String> scoreboard = resolveScoreboard(player, arena, group, stateKey);
        List<String> titleRaw = new ArrayList<>();
        List<String> linesRaw = new ArrayList<>();
        if (!scoreboard.isEmpty()) {
            titleRaw = Arrays.asList(scoreboard.get(0).split(","));
            if (scoreboard.size() > 1) {
                linesRaw = new ArrayList<>(scoreboard.subList(1, scoreboard.size()));
            }
        }

        BwSidebar normalizer = new BwSidebar(player);
        String titleRendered = normalizer.normalizeTitle(titleRaw).getLine();
        List<SidebarLine> normalized = normalizer.normalizeLines(linesRaw);
        List<String> linesNormalized = new ArrayList<>();
        for (SidebarLine line : normalized) {
            linesNormalized.add(line.getLine());
        }

        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("{player}", player.getDisplayName());
        placeholders.put("{playerName}", player.getName());
        placeholders.put("{on}", arena == null ? String.valueOf(Bukkit.getOnlinePlayers().size()) : String.valueOf(arena.getPlayers().size()));
        placeholders.put("{group}", group);
        if (arena != null) {
            placeholders.put("{map}", arena.getDisplayName());
            placeholders.put("{map_name}", arena.getArenaName());
        }

        return new Snapshot(
                "scoreboard-snapshot-v1",
                Instant.now().toString(),
                player.getName(),
                arena == null ? null : arena.getArenaName(),
                group,
                stateKey,
                titleRaw,
                titleRendered,
                linesRaw,
                linesNormalized,
                placeholders
        );
    }

    public static String dump(Snapshot snapshot, String fileName) {
        try {
            Path root = BedWars.plugin.getDataFolder().toPath().resolve("scoreboard-debug");
            Files.createDirectories(root);
            String name = sanitizeFileName(fileName == null || fileName.isEmpty() ? snapshot.getStateKey().replace('.', '_') : fileName);
            Path file = root.resolve(name + ".json");
            Files.writeString(file, GSON.toJson(snapshot.toJson()), StandardCharsets.UTF_8);
            return file.toAbsolutePath().toString();
        } catch (IOException ex) {
            throw new RuntimeException("Could not dump scoreboard snapshot", ex);
        }
    }

    public static CompareResult compare(Player player, String baselineNameOrState) {
        Snapshot current = capture(player, null);
        Path root = BedWars.plugin.getDataFolder().toPath().resolve("scoreboard-debug");
        String fileName = baselineNameOrState.endsWith(".json")
                ? baselineNameOrState
                : sanitizeFileName(baselineNameOrState.replace('.', '_')) + ".json";
        Path file = root.resolve(fileName);
        if (!Files.exists(file)) {
            return new CompareResult(false, file.toAbsolutePath().toString(),
                    Collections.singletonList("Baseline file not found."));
        }

        try {
            JsonObject baseline = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            List<String> diffs = new ArrayList<>();

            String baselineState = getString(baseline, "stateKey");
            if (!Objects.equals(baselineState, current.stateKey)) {
                diffs.add("stateKey: expected '" + baselineState + "' got '" + current.stateKey + "'");
            }

            String baselineTitle = getString(baseline, "titleRendered");
            if (!Objects.equals(baselineTitle, current.titleRendered)) {
                diffs.add("titleRendered mismatch");
            }

            List<String> baselineLines = getStringArray(baseline, "linesNormalized");
            if (baselineLines.size() != current.linesNormalized.size()) {
                diffs.add("linesNormalized size: expected " + baselineLines.size() + " got " + current.linesNormalized.size());
            }

            int max = Math.min(baselineLines.size(), current.linesNormalized.size());
            for (int i = 0; i < max; i++) {
                if (!Objects.equals(baselineLines.get(i), current.linesNormalized.get(i))) {
                    diffs.add("line[" + i + "] mismatch");
                }
            }

            return new CompareResult(diffs.isEmpty(), file.toAbsolutePath().toString(), diffs);
        } catch (Exception ex) {
            return new CompareResult(false, file.toAbsolutePath().toString(),
                    Collections.singletonList("Compare error: " + ex.getMessage()));
        }
    }

    private static String detectStateKey(Player player, IArena arena) {
        if (arena == null) return "lobby";
        GameState status = arena.getStatus();
        if (status == GameState.waiting) {
            return arena.isSpectator(player) ? "waiting.spectator" : "waiting.player";
        }
        if (status == GameState.starting) {
            return arena.isSpectator(player) ? "starting.spectator" : "starting.player";
        }
        if (status == GameState.playing) {
            if (!arena.isSpectator(player)) return "playing.alive";
            ITeam holderExTeam = arena.getExTeam(player.getUniqueId());
            return holderExTeam == null ? "playing.spectator" : "playing.eliminated";
        }
        if (status == GameState.restarting) {
            ITeam holderTeam = arena.getTeam(player);
            ITeam holderExTeam = holderTeam == null ? arena.getExTeam(player.getUniqueId()) : null;
            if (holderTeam == null && holderExTeam == null) return "restarting.spectator";
            if (holderTeam == null && holderExTeam != null && holderExTeam.equals(arena.getWinner())) {
                return "restarting.winner-eliminated";
            }
            if (holderExTeam == null && holderTeam != null && holderTeam.equals(arena.getWinner())) {
                return "restarting.winner-alive";
            }
            return "restarting.loser";
        }
        return "lobby";
    }

    private static List<String> resolveScoreboard(Player player, IArena arena, String group, String stateKey) {
        if ("lobby".equals(stateKey)) {
            return Language.getList(player, Messages.SCOREBOARD_LOBBY);
        }

        String prefix = "sidebar." + group + ".";
        switch (stateKey) {
            case "waiting.player":
                return getScoreboard(player, prefix + "waiting.player", Messages.SCOREBOARD_DEFAULT_WAITING);
            case "waiting.spectator":
                return getScoreboard(player, prefix + "waiting.spectator", Messages.SCOREBOARD_DEFAULT_WAITING_SPEC);
            case "starting.player":
                return getScoreboard(player, prefix + "starting.player", Messages.SCOREBOARD_DEFAULT_STARTING);
            case "starting.spectator":
                return getScoreboard(player, prefix + "starting.spectator", Messages.SCOREBOARD_DEFAULT_STARTING_SPEC);
            case "playing.alive":
                return getScoreboard(player, prefix + "playing.alive", Messages.SCOREBOARD_DEFAULT_PLAYING);
            case "playing.spectator":
                return getScoreboard(player, prefix + "playing.spectator", Messages.SCOREBOARD_DEFAULT_PLAYING_SPEC);
            case "playing.eliminated":
                return getScoreboard(player, prefix + "playing.eliminated", Messages.SCOREBOARD_DEFAULT_PLAYING_SPEC_ELIMINATED);
            case "restarting.winner-alive":
                return getScoreboard(player, prefix + "restarting.winner-alive", Messages.SCOREBOARD_DEFAULT_RESTARTING_WIN1);
            case "restarting.winner-eliminated":
                return getScoreboard(player, prefix + "restarting.winner-eliminated", Messages.SCOREBOARD_DEFAULT_RESTARTING_WIN2);
            case "restarting.loser":
                return getScoreboard(player, prefix + "restarting.loser", Messages.SCOREBOARD_DEFAULT_RESTARTING_LOSER);
            case "restarting.spectator":
                return getScoreboard(player, prefix + "restarting.spectator", Messages.SCOREBOARD_DEFAULT_RESTARTING_SPEC);
            default:
                if (arena != null) {
                    return resolveScoreboard(player, arena, group, detectStateKey(player, arena));
                }
                return Collections.emptyList();
        }
    }

    private static String sanitizeFileName(String input) {
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static List<String> getStringArray(JsonObject object, String key) {
        JsonArray array = object.getAsJsonArray(key);
        if (array == null) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (JsonElement element : array) {
            result.add(element.getAsString());
        }
        return result;
    }

    public static final class Snapshot {
        private final String schemaVersion;
        private final String timestamp;
        private final String playerName;
        private final String arenaName;
        private final String group;
        private final String stateKey;
        private final List<String> titleRaw;
        private final String titleRendered;
        private final List<String> linesRaw;
        private final List<String> linesNormalized;
        private final Map<String, String> placeholdersResolved;

        public Snapshot(String schemaVersion, String timestamp, String playerName, String arenaName, String group,
                        String stateKey, List<String> titleRaw, String titleRendered, List<String> linesRaw,
                        List<String> linesNormalized, Map<String, String> placeholdersResolved) {
            this.schemaVersion = schemaVersion;
            this.timestamp = timestamp;
            this.playerName = playerName;
            this.arenaName = arenaName;
            this.group = group;
            this.stateKey = stateKey;
            this.titleRaw = new ArrayList<>(titleRaw);
            this.titleRendered = titleRendered;
            this.linesRaw = new ArrayList<>(linesRaw);
            this.linesNormalized = new ArrayList<>(linesNormalized);
            this.placeholdersResolved = new LinkedHashMap<>(placeholdersResolved);
        }

        public JsonObject toJson() {
            JsonObject root = new JsonObject();
            root.addProperty("schemaVersion", schemaVersion);
            root.addProperty("timestamp", timestamp);
            root.addProperty("playerName", playerName);
            if (arenaName == null) {
                root.add("arenaName", JsonNull.INSTANCE);
            } else {
                root.addProperty("arenaName", arenaName);
            }
            root.addProperty("group", group);
            root.addProperty("stateKey", stateKey);

            JsonArray titleRawArray = new JsonArray();
            for (String value : titleRaw) titleRawArray.add(value);
            root.add("titleRaw", titleRawArray);
            root.addProperty("titleRendered", titleRendered);

            JsonArray linesRawArray = new JsonArray();
            for (String value : linesRaw) linesRawArray.add(value);
            root.add("linesRaw", linesRawArray);

            JsonArray linesNormalizedArray = new JsonArray();
            for (String value : linesNormalized) linesNormalizedArray.add(value);
            root.add("linesNormalized", linesNormalizedArray);

            JsonObject placeholders = new JsonObject();
            for (Map.Entry<String, String> entry : placeholdersResolved.entrySet()) {
                placeholders.addProperty(entry.getKey(), entry.getValue());
            }
            root.add("placeholdersResolved", placeholders);
            return root;
        }

        public String getStateKey() {
            return stateKey;
        }

        public String getGroup() {
            return group;
        }

        public String getTitleRendered() {
            return titleRendered;
        }

        public List<String> getLinesNormalized() {
            return Collections.unmodifiableList(linesNormalized);
        }
    }

    public static final class CompareResult {
        private final boolean matched;
        private final String baselinePath;
        private final List<String> diffs;

        public CompareResult(boolean matched, String baselinePath, List<String> diffs) {
            this.matched = matched;
            this.baselinePath = baselinePath;
            this.diffs = new ArrayList<>(diffs);
        }

        public boolean isMatched() {
            return matched;
        }

        public String getBaselinePath() {
            return baselinePath;
        }

        public List<String> getDiffs() {
            return Collections.unmodifiableList(diffs);
        }
    }
}
