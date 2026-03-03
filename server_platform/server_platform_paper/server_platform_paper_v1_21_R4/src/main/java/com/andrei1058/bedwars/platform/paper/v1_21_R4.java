package com.andrei1058.bedwars.platform.paper;

import com.andrei1058.bedwars.api.server.VersionSupport;
import com.andrei1058.spigot.sidebar.SidebarManager;
import org.bukkit.plugin.java.JavaPlugin;

@SuppressWarnings("unused")
public class v1_21_R4 extends PaperPlatform {

    @SuppressWarnings("unused")
    public v1_21_R4() {
        SidebarManager.init();
    }

    @Override
    public VersionSupport getOldWrapper(JavaPlugin plugin) {
        return new v1_21_R4_NMS(plugin, "v1_21_R4");
    }

    @Override
    public String getVersion() {
        return "1.21.11";
    }
}
