package com.andrei1058.spigot.sidebar;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.NameTagVisibility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Sidebar {

    private static final Logger LOG = Bukkit.getLogger();
    private static final Set<String> WARNED_KEYS = ConcurrentHashMap.newKeySet();

    private SidebarLine title;
    private final List<SidebarLine> lines = new ArrayList<>();
    private final ConcurrentLinkedQueue<PlaceholderProvider> placeholders = new ConcurrentLinkedQueue<>();
    private final Map<String, PlayerTabData> tabs = new LinkedHashMap<>();

    private Player viewer;
    private Scoreboard scoreboard;
    private Objective objective;

    private boolean warnedPushOtherTeams = false;
    private boolean warnedHealthSupport = false;

    Sidebar(SidebarLine title, List<SidebarLine> lines, Collection<PlaceholderProvider> placeholders) {
        this.title = title;
        this.lines.addAll(lines);
        this.placeholders.addAll(placeholders);
    }

    public void add(Player player) {
        this.viewer = player;
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective existing = scoreboard.getObjective("bw_sidebar");
        this.objective = existing == null ? scoreboard.registerNewObjective("bw_sidebar", "dummy") : existing;
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        applyTitle();
        rerenderSidebar();
        applyAllTabs();
        player.setScoreboard(scoreboard);
    }

    public void remove(Player player) {
        if (this.viewer != null && this.viewer.equals(player)) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            this.viewer = null;
            this.objective = null;
            this.scoreboard = null;
            this.tabs.clear();
        }
    }

    public void clearLines() {
        this.lines.clear();
        rerenderSidebar();
    }

    public void setTitle(SidebarLine title) {
        this.title = title;
        applyTitle();
    }

    public void addLine(SidebarLine line) {
        this.lines.add(line);
        rerenderSidebar();
    }

    public void refreshTitle() {
        tickAnimated(title);
        applyTitle();
    }

    public void refreshPlaceholders() {
        rerenderSidebar();
        applyAllTabs();
    }

    public void playerTabRefreshAnimation() {
        for (PlayerTabData data : tabs.values()) {
            tickAnimated(data.prefix);
            tickAnimated(data.suffix);
        }
        applyAllTabs();
    }

    public void playerHealthRefreshAnimation() {
        warnUnsupportedOnce("health-animation", "Sidebar fallback does not animate health icon frames.");
    }

    public void setPlayerHealth(Player player, int health) {
        warnUnsupportedOnce("health-values", "Sidebar fallback does not publish per-player health values to tab.");
    }

    public void showPlayersHealth(SidebarLine line, boolean inTab) {
        warnUnsupportedOnce("health-display", "Sidebar fallback does not provide native tab/lobby health display.");
    }

    public void hidePlayersHealth() {
        // No-op in fallback implementation.
    }

    public void removeTabs() {
        for (String id : new ArrayList<>(tabs.keySet())) {
            removeTab(id);
        }
    }

    public void removeTab(String identifier) {
        tabs.remove(identifier);
        if (scoreboard == null) return;
        Team team = scoreboard.getTeam(tabTeamName(identifier));
        if (team != null) {
            team.unregister();
        }
    }

    public PlayerTab playerTabCreate(String identifier,
                                     Player target,
                                     SidebarLine prefix,
                                     SidebarLine suffix,
                                     PlayerTab.PushingRule pushingRule,
                                     Collection<PlaceholderProvider> dynamicPlaceholders) {
        if (pushingRule == PlayerTab.PushingRule.PUSH_OTHER_TEAMS && !warnedPushOtherTeams) {
            warnedPushOtherTeams = true;
            LOG.warning("[BedWars][SidebarFallback] PUSH_OTHER_TEAMS ordering is approximated in fallback mode.");
        }
        PlayerTab tab = new PlayerTab(identifier, target, pushingRule);
        PlayerTabData data = new PlayerTabData(tab, prefix, suffix, new ConcurrentLinkedQueue<>(dynamicPlaceholders));
        tabs.put(identifier, data);
        applyTab(data);
        return tab;
    }

    public ConcurrentLinkedQueue<PlaceholderProvider> getPlaceholders() {
        return placeholders;
    }

    public void addPlaceholder(PlaceholderProvider provider) {
        placeholders.add(provider);
    }

    public void removePlaceholder(String placeholder) {
        placeholders.removeIf(p -> p.getPlaceholder().equals(placeholder));
    }

    private void applyTitle() {
        if (objective == null || title == null) return;
        objective.setDisplayName(limit(applyPlaceholders(title.getLine(), placeholders), 128));
    }

    private void rerenderSidebar() {
        if (objective == null || scoreboard == null) return;
        for (String entry : new ArrayList<>(scoreboard.getEntries())) {
            scoreboard.resetScores(entry);
        }
        int count = Math.min(lines.size(), 15);
        for (int i = 0; i < count; i++) {
            SidebarLine line = lines.get(i);
            tickAnimated(line);
            String rendered = applyPlaceholders(line.getLine(), placeholders);
            String entry = uniqueEntry(limit(rendered, 64), i);
            objective.getScore(entry).setScore(count - i);
        }
    }

    private void applyAllTabs() {
        for (PlayerTabData data : tabs.values()) {
            applyTab(data);
        }
    }

    private void applyTab(PlayerTabData data) {
        if (scoreboard == null) return;
        Team team = scoreboard.getTeam(tabTeamName(data.tab.getIdentifier()));
        if (team == null) {
            team = scoreboard.registerNewTeam(tabTeamName(data.tab.getIdentifier()));
        }

        String prefix = applyPlaceholders(data.prefix.getLine(), data.placeholders);
        String suffix = applyPlaceholders(data.suffix.getLine(), data.placeholders);
        setTeamPrefix(team, limit(prefix, 64));
        setTeamSuffix(team, limit(suffix, 64));

        if (!team.hasEntry(data.tab.getPlayer().getName())) {
            team.addEntry(data.tab.getPlayer().getName());
        }
        applyNameTagVisibility(team, data.tab.getNameTagVisibility());
    }

    private static String applyPlaceholders(String line, Collection<PlaceholderProvider> providers) {
        String out = line == null ? "" : line;
        for (PlaceholderProvider provider : providers) {
            String key = provider.getPlaceholder();
            String value;
            try {
                value = provider.getValue();
            } catch (Exception ex) {
                warnOnce("provider-ex:" + key, "[BedWars][SidebarFallback] Placeholder provider threw for " + key + ": " + ex.getMessage(), ex);
                value = "<error>";
            }
            if (value == null) {
                warnOnce("provider-null:" + key, "[BedWars][SidebarFallback] Placeholder " + key + " returned null.");
                value = "<null>";
            }
            out = out.replace(key, value);
        }
        return ChatColor.translateAlternateColorCodes('&', out);
    }

    private static void tickAnimated(SidebarLine line) {
        if (line instanceof SidebarLineAnimated) {
            ((SidebarLineAnimated) line).tick();
        }
    }

    private static String tabTeamName(String id) {
        String lowered = id.toLowerCase(Locale.ROOT);
        return ("bwtab_" + lowered).substring(0, Math.min(16, ("bwtab_" + lowered).length()));
    }

    private static void applyNameTagVisibility(Team team, PlayerTab.NameTagVisibility visibility) {
        try {
            Class<?> optionClass = Class.forName("org.bukkit.scoreboard.Team$Option");
            Class<?> optionStatusClass = Class.forName("org.bukkit.scoreboard.Team$OptionStatus");
            Object option = Enum.valueOf((Class<Enum>) optionClass.asSubclass(Enum.class), "NAME_TAG_VISIBILITY");
            Object status = Enum.valueOf(
                    (Class<Enum>) optionStatusClass.asSubclass(Enum.class),
                    visibility == PlayerTab.NameTagVisibility.NEVER ? "NEVER" : "ALWAYS"
            );
            team.getClass().getMethod("setOption", optionClass, optionStatusClass).invoke(team, option, status);
            return;
        } catch (Throwable ignored) {
            // Old API path.
        }

        team.setNameTagVisibility(
                visibility == PlayerTab.NameTagVisibility.NEVER
                        ? NameTagVisibility.NEVER
                        : NameTagVisibility.ALWAYS
        );
    }

    private static void setTeamPrefix(Team team, String value) {
        try {
            team.setPrefix(value);
        } catch (Throwable ex) {
            warnOnce("prefix-fail", "[BedWars][SidebarFallback] Could not set team prefix: " + ex.getMessage(), ex);
        }
    }

    private static void setTeamSuffix(Team team, String value) {
        try {
            team.setSuffix(value);
        } catch (Throwable ex) {
            warnOnce("suffix-fail", "[BedWars][SidebarFallback] Could not set team suffix: " + ex.getMessage(), ex);
        }
    }

    private static String uniqueEntry(String base, int index) {
        String normalized = base == null || base.isEmpty() ? " " : base;
        String suffix = ChatColor.values()[index % ChatColor.values().length].toString();
        return limit(normalized + suffix, 64);
    }

    private static String limit(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    private void warnUnsupportedOnce(String key, String message) {
        if ("health-display".equals(key) || "health-animation".equals(key) || "health-values".equals(key)) {
            if (warnedHealthSupport) return;
            warnedHealthSupport = true;
        }
        warnOnce("unsupported:" + key, "[BedWars][SidebarFallback] " + message);
    }

    private static void warnOnce(String key, String msg) {
        if (WARNED_KEYS.add(key)) {
            LOG.warning(msg);
        }
    }

    private static void warnOnce(String key, String msg, Throwable error) {
        if (WARNED_KEYS.add(key)) {
            LOG.log(Level.WARNING, msg, error);
        }
    }

    private static final class PlayerTabData {
        private final PlayerTab tab;
        private final SidebarLine prefix;
        private final SidebarLine suffix;
        private final ConcurrentLinkedQueue<PlaceholderProvider> placeholders;

        private PlayerTabData(PlayerTab tab, SidebarLine prefix, SidebarLine suffix,
                              ConcurrentLinkedQueue<PlaceholderProvider> placeholders) {
            this.tab = tab;
            this.prefix = prefix;
            this.suffix = suffix;
            this.placeholders = placeholders;
        }
    }
}
