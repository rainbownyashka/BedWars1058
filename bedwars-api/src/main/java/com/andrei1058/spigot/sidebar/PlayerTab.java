package com.andrei1058.spigot.sidebar;

import org.bukkit.entity.Player;

public final class PlayerTab {

    public enum PushingRule {
        NEVER,
        PUSH_OTHER_TEAMS
    }

    public enum NameTagVisibility {
        ALWAYS,
        NEVER
    }

    private final String identifier;
    private final Player player;
    private final PushingRule pushingRule;
    private NameTagVisibility nameTagVisibility = NameTagVisibility.ALWAYS;

    public PlayerTab(String identifier, Player player, PushingRule pushingRule) {
        this.identifier = identifier;
        this.player = player;
        this.pushingRule = pushingRule;
    }

    public String getIdentifier() {
        return identifier;
    }

    public Player getPlayer() {
        return player;
    }

    public PushingRule getPushingRule() {
        return pushingRule;
    }

    public NameTagVisibility getNameTagVisibility() {
        return nameTagVisibility;
    }

    public void setNameTagVisibility(NameTagVisibility nameTagVisibility) {
        this.nameTagVisibility = nameTagVisibility;
    }
}

