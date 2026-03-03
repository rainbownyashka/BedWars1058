package com.andrei1058.spigot.sidebar;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SidebarManager {

    private static final Logger LOG = Bukkit.getLogger();
    private static SidebarManager instance;
    private static boolean warnedHeaderFooterUnsupported = false;
    private static boolean warnedPapiParsingFailure = false;

    private SidebarManager() {
    }

    public static SidebarManager init() {
        if (instance == null) {
            instance = new SidebarManager();
            LOG.warning("[BedWars][SidebarFallback] Using in-tree sidebar fallback implementation.");
        }
        return instance;
    }

    public static SidebarManager getInstance() {
        return init();
    }

    public Sidebar createSidebar(SidebarLine title,
                                 List<SidebarLine> lines,
                                 Collection<PlaceholderProvider> placeholders) {
        return new Sidebar(title, lines, placeholders);
    }

    public void sendHeaderFooter(Player player, TabHeaderFooter headerFooter) {
        String header = renderMultiline(player, headerFooter.getHeaderLines(), headerFooter.getPlaceholders());
        String footer = renderMultiline(player, headerFooter.getFooterLines(), headerFooter.getPlaceholders());

        try {
            Method m = Player.class.getMethod("setPlayerListHeaderFooter", String.class, String.class);
            m.invoke(player, header, footer);
            return;
        } catch (NoSuchMethodException ignored) {
            // Try legacy Spigot method below.
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "[BedWars][SidebarFallback] Failed to set tab header/footer via direct API.", ex);
            return;
        }

        try {
            Object spigot = player.spigot();
            Class<?> spigotClass = spigot.getClass();
            Class<?> baseComponent = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
            Class<?> textComponent = Class.forName("net.md_5.bungee.api.chat.TextComponent");

            Object headerComponent = textComponent.getConstructor(String.class).newInstance(header);
            Object footerComponent = textComponent.getConstructor(String.class).newInstance(footer);

            Method setMethod = spigotClass.getMethod("setPlayerListHeaderFooter", baseComponent, baseComponent);
            setMethod.invoke(spigot, headerComponent, footerComponent);
        } catch (Exception ex) {
            if (!warnedHeaderFooterUnsupported) {
                warnedHeaderFooterUnsupported = true;
                LOG.warning("[BedWars][SidebarFallback] Header/footer API is unavailable on this runtime: " + ex.getClass().getSimpleName());
            }
        }
    }

    private static String renderMultiline(Player player, List<SidebarLine> lines, ConcurrentLinkedQueue<PlaceholderProvider> placeholders) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) out.append('\n');
            out.append(renderLine(player, lines.get(i), placeholders));
        }
        return out.toString();
    }

    private static String renderLine(Player player, SidebarLine line, Collection<PlaceholderProvider> placeholders) {
        String out = line.getLine();
        for (PlaceholderProvider placeholder : placeholders) {
            String value;
            try {
                value = placeholder.getValue();
            } catch (Exception ex) {
                LOG.warning("[BedWars][SidebarFallback] Header/footer placeholder failed: " + placeholder.getPlaceholder());
                value = "<error>";
            }
            if (value == null) {
                if ("{playerName}".equalsIgnoreCase(placeholder.getPlaceholder())) {
                    value = player.getName();
                } else {
                    value = "";
                }
            }
            out = out.replace(placeholder.getPlaceholder(), value);
        }
        return parseWithPlaceholderApi(player, out);
    }

    private static String parseWithPlaceholderApi(Player player, String input) {
        Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (papi == null || !papi.isEnabled()) {
            return input;
        }

        String output = input;
        try {
            Class<?> placeholderApiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");

            // Supports both %placeholder% and {placeholder} formats.
            Method bracket = placeholderApiClass.getMethod("setBracketPlaceholders", Player.class, String.class);
            output = (String) bracket.invoke(null, player, output);

            Method regular = placeholderApiClass.getMethod("setPlaceholders", Player.class, String.class);
            output = (String) regular.invoke(null, player, output);
        } catch (Exception ex) {
            if (!warnedPapiParsingFailure) {
                warnedPapiParsingFailure = true;
                LOG.warning("[BedWars][SidebarFallback] PlaceholderAPI parsing failed: " + ex.getClass().getSimpleName());
            }
        }
        return output;
    }
}
