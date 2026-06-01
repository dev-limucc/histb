package dev.limucc.histb.client.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Opens the HISTB? hub from ModMenu — everything is reachable from there. */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return HubScreen::new;
    }
}
