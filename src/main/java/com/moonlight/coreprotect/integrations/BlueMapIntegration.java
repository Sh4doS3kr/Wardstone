package com.moonlight.coreprotect.integrations;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.ProtectedRegion;
import com.moonlight.coreprotect.structures.StructureManager;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import org.bukkit.Bukkit;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public class BlueMapIntegration {

    private static final String MARKER_SET_ID = "wardstone-regions";
    private static final String MARKER_SET_LABEL = "Zonas Protegidas";

    private static final String STRUCT_MARKER_SET_ID = "wardstone-structures";
    private static final String STRUCT_MARKER_SET_LABEL = "Estructuras";

    // Rojo semi-opaco (regiones)
    private static final Color FILL_COLOR = new Color(200, 30, 30, 0.35f);
    private static final Color LINE_COLOR = new Color(180, 20, 20, 0.8f);

    // Cian semi-opaco (estructuras)
    private static final Color STRUCT_FILL_COLOR = new Color(0, 210, 210, 0.30f);
    private static final Color STRUCT_LINE_COLOR = new Color(0, 180, 180, 0.85f);

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
            plugin.getLogger().info("BlueMap detectado. Dibujando zonas protegidas y estructuras en el mapa...");
            updateAllMarkers();
            updateAllStructureMarkers();
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

    // ==================== STRUCTURE MARKERS ====================

    public void updateAllStructureMarkers() {
        if (!enabled) return;
        StructureManager sm = plugin.getStructureManager();
        if (sm == null) return;

        BlueMapAPI.getInstance().ifPresent(api -> {
            // Clear existing structure marker sets
            for (BlueMapMap map : api.getMaps()) {
                map.getMarkerSets().remove(STRUCT_MARKER_SET_ID);
            }

            Map<String, StructureManager.StructureData> structures = sm.getPlacedStructures();
            if (structures.isEmpty()) return;

            for (Map.Entry<String, StructureManager.StructureData> entry : structures.entrySet()) {
                addStructureMarker(api, entry.getKey(), entry.getValue());
            }

            plugin.getLogger().info("[BlueMap] " + structures.size() + " estructuras dibujadas en el mapa.");
        });
    }

    public void addStructureMarker(String structId, StructureManager.StructureData data) {
        if (!enabled) return;
        BlueMapAPI.getInstance().ifPresent(api -> addStructureMarker(api, structId, data));
    }

    public void removeStructureMarker(String structId) {
        if (!enabled) return;
        BlueMapAPI.getInstance().ifPresent(api -> {
            String markerId = "struct-" + structId;
            for (BlueMapMap map : api.getMaps()) {
                MarkerSet markerSet = map.getMarkerSets().get(STRUCT_MARKER_SET_ID);
                if (markerSet != null) {
                    markerSet.getMarkers().remove(markerId);
                }
            }
        });
    }

    private void addStructureMarker(BlueMapAPI api, String structId, StructureManager.StructureData data) {
        org.bukkit.World bukkitWorld = Bukkit.getWorld(data.world);
        if (bukkitWorld == null) return;

        Optional<BlueMapWorld> optWorld = api.getWorld(bukkitWorld);
        if (optWorld.isEmpty()) return;

        BlueMapWorld bmWorld = optWorld.get();

        int radius = getStructureRadius(data.type);
        double minX = data.x - radius;
        double maxX = data.x + radius + 1;
        double minZ = data.z - radius;
        double maxZ = data.z + radius + 1;

        Shape shape = Shape.createRect(minX, minZ, maxX, maxZ);

        // Strip color codes from display name for the label
        String cleanName = data.type.displayName.replaceAll("§[0-9a-fk-or]", "");
        String label = cleanName + " (" + data.x + ", " + data.y + ", " + data.z + ")";
        String markerId = "struct-" + structId;

        ShapeMarker marker = ShapeMarker.builder()
                .label(label)
                .shape(shape, data.y)
                .fillColor(STRUCT_FILL_COLOR)
                .lineColor(STRUCT_LINE_COLOR)
                .lineWidth(2)
                .depthTestEnabled(false)
                .build();

        for (BlueMapMap map : bmWorld.getMaps()) {
            MarkerSet markerSet = map.getMarkerSets()
                    .computeIfAbsent(STRUCT_MARKER_SET_ID, id -> MarkerSet.builder()
                            .label(STRUCT_MARKER_SET_LABEL)
                            .defaultHidden(false)
                            .toggleable(true)
                            .build());

            markerSet.getMarkers().put(markerId, marker);
        }
    }

    private int getStructureRadius(StructureManager.StructureType type) {
        return switch (type) {
            case WELL -> 7;
            case RUINED_SHRINE -> 6;
            case FORGOTTEN_ALTAR -> 4;
            case WANDERER_CAMP -> 5;
            case WATCHTOWER -> 4;
            case DUNGEON -> 5;
            case OBELISK -> 4;
            case COLOSSEUM -> 10;
            case FORTRESS -> 8;
            case LIBRARY -> 6;
            case GRAVEYARD -> 8;
            case PYRAMID -> 12;
            case SHIPWRECK -> 8;
            case TITAN_ARENA -> 25;
        };
    }

    public boolean isEnabled() {
        return enabled;
    }
}
