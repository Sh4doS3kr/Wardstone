package com.moonlight.coreprotect.koth;

import com.google.gson.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Persiste toda la configuración posicional del KOTH en koth.json.
 * Esto es independiente de config.yml, por lo que recargar el plugin
 * NO borra spawn, zona, mapa ni centro.
 *
 * Campos guardados:
 *  - spawn (x, y, z, yaw, pitch)
 *  - zone  (x1, z1, x2, z2)
 *  - map   (x1, z1, x2, z2)
 *  - center (x, y, z)
 */
public class KothConfig {

    private final JavaPlugin plugin;
    private final File jsonFile;
    private JsonObject data;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public KothConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.jsonFile = new File(plugin.getDataFolder(), "koth.json");
        load();
    }

    // ==========================================
    // PERSISTENCIA
    // ==========================================

    public void load() {
        if (!jsonFile.exists()) {
            data = new JsonObject();
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(jsonFile), StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(reader);
            data = el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            plugin.getLogger().warning("[KOTH] No se pudo leer koth.json: " + e.getMessage());
            data = new JsonObject();
        }
    }

    public void save() {
        try {
            if (!jsonFile.getParentFile().exists()) jsonFile.getParentFile().mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(jsonFile), StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[KOTH] No se pudo guardar koth.json: " + e.getMessage());
        }
    }

    // ==========================================
    // SPAWN
    // ==========================================

    public void setSpawn(Location loc) {
        JsonObject spawn = new JsonObject();
        spawn.addProperty("world", loc.getWorld().getName());
        spawn.addProperty("x", loc.getX());
        spawn.addProperty("y", loc.getY());
        spawn.addProperty("z", loc.getZ());
        spawn.addProperty("yaw", loc.getYaw());
        spawn.addProperty("pitch", loc.getPitch());
        data.add("spawn", spawn);
        save();
    }

    public Location getSpawn() {
        if (!data.has("spawn")) return null;
        JsonObject s = data.getAsJsonObject("spawn");
        String worldName = s.has("world") ? s.get("world").getAsString() : KothWorld.getWorldName();
        World w = Bukkit.getWorld(worldName);
        if (w == null) return null;
        return new Location(w,
                s.get("x").getAsDouble(),
                s.get("y").getAsDouble(),
                s.get("z").getAsDouble(),
                s.has("yaw")   ? s.get("yaw").getAsFloat()   : 0f,
                s.has("pitch") ? s.get("pitch").getAsFloat() : 0f);
    }

    // ==========================================
    // ZONA DE CAPTURA
    // ==========================================

    public void setZone(int x1, int z1, int x2, int z2) {
        JsonObject zone = new JsonObject();
        zone.addProperty("x1", Math.min(x1, x2));
        zone.addProperty("z1", Math.min(z1, z2));
        zone.addProperty("x2", Math.max(x1, x2));
        zone.addProperty("z2", Math.max(z1, z2));
        data.add("zone", zone);
        save();
    }

    public int getZoneX1() { return getInt("zone", "x1", -10); }
    public int getZoneZ1() { return getInt("zone", "z1", -10); }
    public int getZoneX2() { return getInt("zone", "x2",  10); }
    public int getZoneZ2() { return getInt("zone", "z2",  10); }
    public boolean hasZone() { return data.has("zone"); }

    // ==========================================
    // LÍMITES DEL MAPA
    // ==========================================

    public void setMap(int x1, int z1, int x2, int z2) {
        JsonObject map = new JsonObject();
        map.addProperty("x1", Math.min(x1, x2));
        map.addProperty("z1", Math.min(z1, z2));
        map.addProperty("x2", Math.max(x1, x2));
        map.addProperty("z2", Math.max(z1, z2));
        data.add("map", map);
        save();
    }

    public int getMapX1() { return getInt("map", "x1", -100); }
    public int getMapZ1() { return getInt("map", "z1", -100); }
    public int getMapX2() { return getInt("map", "x2",  100); }
    public int getMapZ2() { return getInt("map", "z2",  100); }

    // ==========================================
    // CENTRO (FARO)
    // ==========================================

    public void setCenter(Location loc) {
        JsonObject center = new JsonObject();
        center.addProperty("world", loc.getWorld().getName());
        center.addProperty("x", loc.getX());
        center.addProperty("y", loc.getY());
        center.addProperty("z", loc.getZ());
        data.add("center", center);
        save();
    }

    public Location getCenter() {
        if (!data.has("center")) return null;
        JsonObject c = data.getAsJsonObject("center");
        String worldName = c.has("world") ? c.get("world").getAsString() : KothWorld.getWorldName();
        World w = Bukkit.getWorld(worldName);
        if (w == null) return null;
        return new Location(w,
                c.get("x").getAsDouble(),
                c.get("y").getAsDouble(),
                c.get("z").getAsDouble());
    }

    // ==========================================
    // HELPERS
    // ==========================================

    private int getInt(String section, String key, int def) {
        if (!data.has(section)) return def;
        JsonObject obj = data.getAsJsonObject(section);
        return obj.has(key) ? obj.get(key).getAsInt() : def;
    }
}
