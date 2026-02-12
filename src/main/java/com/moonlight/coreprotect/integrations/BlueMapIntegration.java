package com.moonlight.coreprotect.integrations;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.ProtectedRegion;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import org.bukkit.Bukkit;

import java.util.Collection;
import java.util.Optional;

public class BlueMapIntegration {

    private static final String MARKER_SET_ID = "wardstone-regions";
    private static final String MARKER_SET_LABEL = "Zonas Protegidas";

    // Rojo semi-opaco
    private static final Color FILL_COLOR = new Color(200, 30, 30, 0.35f);
    private static final Color LINE_COLOR = new Color(180, 20, 20, 0.8f);

    private final CoreProtectPlugin plugin;
    private boolean enabled = false;

    public BlueMapIntegration(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        try {
            Class.forName("de.bluecolored.bluemap.api.BlueMapAPI");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("BlueMap no detectado. Integración desactivada.");
            return;
        }

        BlueMapAPI.onEnable(api -> {
            enabled = true;
            plugin.getLogger().info("BlueMap detectado. Dibujando zonas protegidas en el mapa...");
            updateAllMarkers();
        });

        BlueMapAPI.onDisable(api -> {
            enabled = false;
        });
    }

    public void updateAllMarkers() {
        if (!enabled) return;

        BlueMapAPI.getInstance().ifPresent(api -> {
            // Clear existing marker sets from all maps
            for (BlueMapMap map : api.getMaps()) {
                map.getMarkerSets().remove(MARKER_SET_ID);
            }

            Collection<ProtectedRegion> regions = plugin.getProtectionManager().getAllRegions();
            if (regions.isEmpty()) return;

            for (ProtectedRegion region : regions) {
                addRegionMarker(api, region);
            }
        });
    }

    public void addRegionMarker(ProtectedRegion region) {
        if (!enabled) return;
        BlueMapAPI.getInstance().ifPresent(api -> addRegionMarker(api, region));
    }

    public void removeRegionMarker(ProtectedRegion region) {
        if (!enabled) return;
        BlueMapAPI.getInstance().ifPresent(api -> {
            String worldName = region.getWorldName();
            String markerId = "region-" + region.getId().toString();

            for (BlueMapMap map : api.getMaps()) {
                MarkerSet markerSet = map.getMarkerSets().get(MARKER_SET_ID);
                if (markerSet != null) {
                    markerSet.getMarkers().remove(markerId);
                }
            }
        });
    }

    private void addRegionMarker(BlueMapAPI api, ProtectedRegion region) {
        org.bukkit.World bukkitWorld = Bukkit.getWorld(region.getWorldName());
        if (bukkitWorld == null) return;

        Optional<BlueMapWorld> optWorld = api.getWorld(bukkitWorld);
        if (optWorld.isEmpty()) return;

        BlueMapWorld bmWorld = optWorld.get();

        int halfSize = region.getSize() / 2;
        double minX = region.getCoreX() - halfSize;
        double maxX = region.getCoreX() + halfSize + 1;
        double minZ = region.getCoreZ() - halfSize;
        double maxZ = region.getCoreZ() + halfSize + 1;

        // Shape uses (x, z) as (x, y) in Vector2d
        Shape shape = Shape.createRect(minX, minZ, maxX, maxZ);

        String ownerName = Bukkit.getOfflinePlayer(region.getOwner()).getName();
        if (ownerName == null) ownerName = "Desconocido";

        String label = "Zona de " + ownerName + " (Nv." + region.getLevel() + ")";
        String markerId = "region-" + region.getId().toString();

        ShapeMarker marker = ShapeMarker.builder()
                .label(label)
                .shape(shape, region.getCoreY())
                .fillColor(FILL_COLOR)
                .lineColor(LINE_COLOR)
                .lineWidth(2)
                .depthTestEnabled(false)
                .build();

        for (BlueMapMap map : bmWorld.getMaps()) {
            MarkerSet markerSet = map.getMarkerSets()
                    .computeIfAbsent(MARKER_SET_ID, id -> MarkerSet.builder()
                            .label(MARKER_SET_LABEL)
                            .defaultHidden(false)
                            .toggleable(true)
                            .build());

            markerSet.getMarkers().put(markerId, marker);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
