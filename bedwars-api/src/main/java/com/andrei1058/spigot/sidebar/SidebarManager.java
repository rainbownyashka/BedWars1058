package com.andrei1058.spigot.sidebar;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

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
        String header = renderMultiline(headerFooter.getHeaderLines(), headerFooter.getPlaceholders());
        String footer = renderMultiline(headerFooter.getFooterLines(), headerFooter.getPlaceholders());

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

    private static String renderMultiline(List<SidebarLine> lines, ConcurrentLinkedQueue<PlaceholderProvider> placeholders) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) out.append('\n');
            out.append(renderLine(lines.get(i), placeholders));
        }
        return out.toString();
    }

    private static String renderLine(SidebarLine line, Collection<PlaceholderProvider> placeholders) {
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
                LOG.warning("[BedWars][SidebarFallback] Header/footer placeholder returned null: " + placeholder.getPlaceholder());
                value = "<null>";
            }
            out = out.replace(placeholder.getPlaceholder(), value);
        }
        return out;
    }
}

