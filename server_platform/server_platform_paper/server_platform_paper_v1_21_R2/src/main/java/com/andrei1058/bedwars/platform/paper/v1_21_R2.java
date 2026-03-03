package com.andrei1058.bedwars.platform.paper;

import com.andrei1058.bedwars.api.server.VersionSupport;
import com.andrei1058.spigot.sidebar.SidebarManager;
import org.bukkit.plugin.java.JavaPlugin;

@SuppressWarnings("unused")
public class v1_21_R2 extends PaperPlatform {

    @SuppressWarnings("unused")
    public v1_21_R2() {
        SidebarManager.init();
    }

    @Override
    public VersionSupport getOldWrapper(JavaPlugin plugin) {
        return new v1_21_R2_NMS(plugin, "v1.21.3");
    }

    @Override
    public String getVersion() {
        return "1.21.3";
    }
}
