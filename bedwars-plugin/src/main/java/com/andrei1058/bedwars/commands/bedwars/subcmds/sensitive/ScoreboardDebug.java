/*
 * BedWars1058 - A bed wars mini-game.
 * Copyright (C) 2021 Andrei Dascălu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Contact e-mail: andrew.dascalu@gmail.com
 */

package com.andrei1058.bedwars.commands.bedwars.subcmds.sensitive;

import com.andrei1058.bedwars.api.BedWars;
import com.andrei1058.bedwars.api.command.ParentCommand;
import com.andrei1058.bedwars.api.command.SubCommand;
import com.andrei1058.bedwars.arena.Misc;
import com.andrei1058.bedwars.configuration.Permissions;
import com.andrei1058.bedwars.sidebar.ScoreboardDebugService;
import net.md_5.bungee.api.chat.ClickEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ScoreboardDebug extends SubCommand {

    public ScoreboardDebug(ParentCommand parent, String name) {
        super(parent, name);
        setPriority(12);
        showInList(true);
        setPermission(Permissions.PERMISSION_SCOREBOARD_DEBUG);
        setDisplayInfo(Misc.msgHoverClick(
                "§6 ▪ §7/" + getParent().getName() + " " + getSubCommandName() + " §8- §e sidebar dump/compare",
                "§fScoreboard debug:\n§7dump/show/compare/dumpall",
                "/" + getParent().getName() + " " + getSubCommandName() + " show",
                ClickEvent.Action.SUGGEST_COMMAND));
    }

    @Override
    public boolean execute(String[] args, CommandSender s) {
        if (!(s instanceof Player)) {
            s.sendMessage("§cThis command is player-only.");
            return true;
        }
        if (!hasPermission(s)) {
            s.sendMessage("§cNo permission.");
            return true;
        }
        Player p = (Player) s;

        String action = args.length == 0 ? "show" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "show":
                ScoreboardDebugService.Snapshot snapshot = ScoreboardDebugService.capture(p, null);
                s.sendMessage("§8§l▪ §6Scoreboard state: §e" + snapshot.getStateKey());
                s.sendMessage("§8§l▪ §6Group: §e" + snapshot.getGroup());
                s.sendMessage("§8§l▪ §6Title: §f" + snapshot.getTitleRendered());
                s.sendMessage("§8§l▪ §6Lines: §e" + snapshot.getLinesNormalized().size());
                return true;

            case "dump": {
                String forcedState = args.length >= 2 ? args[1] : null;
                String fileName = args.length >= 3 ? args[2] : null;
                ScoreboardDebugService.Snapshot snap = ScoreboardDebugService.capture(p, normalizeStateArg(forcedState));
                String path = ScoreboardDebugService.dump(snap, fileName);
                s.sendMessage("§aDumped scoreboard snapshot: §f" + path);
                s.sendMessage("§7State: §e" + snap.getStateKey() + " §7| Lines: §e" + snap.getLinesNormalized().size());
                return true;
            }

            case "compare": {
                String baseline = args.length >= 2 ? args[1] : null;
                if (baseline == null || baseline.isEmpty()) {
                    s.sendMessage("§cUsage: /" + getParent().getName() + " " + getSubCommandName() + " compare <baseline-file-or-state>");
                    return true;
                }
                ScoreboardDebugService.CompareResult result = ScoreboardDebugService.compare(p, baseline);
                if (result.isMatched()) {
                    s.sendMessage("§aBaseline matched: §f" + result.getBaselinePath());
                } else {
                    s.sendMessage("§cBaseline mismatch: §f" + result.getBaselinePath());
                    for (String diff : result.getDiffs()) {
                        s.sendMessage("§7- " + diff);
                    }
                }
                return true;
            }

            case "dumpall": {
                int count = 0;
                for (String state : ScoreboardDebugService.DUMPABLE_STATES) {
                    ScoreboardDebugService.Snapshot snap = ScoreboardDebugService.capture(p, state);
                    ScoreboardDebugService.dump(snap, state.replace('.', '_'));
                    count++;
                }
                s.sendMessage("§aDumped states: §e" + count + " §7to plugin debug folder.");
                return true;
            }

            default:
                s.sendMessage("§cUsage:");
                s.sendMessage("§7/" + getParent().getName() + " " + getSubCommandName() + " show");
                s.sendMessage("§7/" + getParent().getName() + " " + getSubCommandName() + " dump [state] [filename]");
                s.sendMessage("§7/" + getParent().getName() + " " + getSubCommandName() + " compare <baseline-file-or-state>");
                s.sendMessage("§7/" + getParent().getName() + " " + getSubCommandName() + " dumpall");
                return true;
        }
    }

    private String normalizeStateArg(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.equals("auto")) {
            return null;
        }
        return normalized;
    }

    @Override
    public List<String> getTabComplete() {
        return Arrays.asList("show", "dump", "compare", "dumpall");
    }

    @Override
    public boolean canSee(CommandSender s, BedWars api) {
        return hasPermission(s);
    }
}

