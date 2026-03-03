package com.andrei1058.spigot.sidebar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TabHeaderFooter {

    private final List<SidebarLine> headerLines;
    private final List<SidebarLine> footerLines;
    private final ConcurrentLinkedQueue<PlaceholderProvider> placeholders;

    public TabHeaderFooter(List<SidebarLine> headerLines,
                           List<SidebarLine> footerLines,
                           ConcurrentLinkedQueue<PlaceholderProvider> placeholders) {
        this.headerLines = new ArrayList<>(headerLines);
        this.footerLines = new ArrayList<>(footerLines);
        this.placeholders = new ConcurrentLinkedQueue<>(placeholders);
    }

    public List<SidebarLine> getHeaderLines() {
        return headerLines;
    }

    public List<SidebarLine> getFooterLines() {
        return footerLines;
    }

    public ConcurrentLinkedQueue<PlaceholderProvider> getPlaceholders() {
        return placeholders;
    }
}

