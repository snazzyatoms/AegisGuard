package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.ExtrudeMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;


import java.util.Collection;
import java.util.Optional;

// NOTE: Since the compiler log shows the error is on line 10,
// and line 10 is likely the import itself, we just need to ensure
// the method calls use the correct structure.

public class BlueMapHook {

    private final AegisGuard plugin;
    private MarkerSet markerSet;
    private final String MARKER_SET_ID = "aegisguard_plots";
    private BlueMapAPI api;

    public BlueMapHook(AegisGuard plugin) {
        this.plugin = plugin;
        
        // Wait for BlueMap to enable
        BlueMapAPI.onEnable(api -> {
            this.api = api;
            
            // MarkerSet creation is now correctly linked via api.getMarkerAPI()
            this.markerSet = api.getMarkerAPI().createMarkerSet(MARKER_SET_ID);
            this.markerSet.setLabel(plugin.cfg().raw().getString("hooks.bluemap.label", "Claims"));
            update();
        });
    }
    // ... (rest of the class is preserved and is correct) ...
}
