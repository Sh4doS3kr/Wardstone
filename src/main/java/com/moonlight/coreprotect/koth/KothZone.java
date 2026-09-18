package com.moonlight.coreprotect.koth;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Modelo de datos para la zona de captura dentro del mundo KOTH.
 * Define las coordenadas rectangulares donde los jugadores ganan puntos
 * al permanecer dentro.
 * 
 * La zona siempre está en el mundo "koth".
 */
public class KothZone {

    // === Estados del KOTH ===
    public enum KothState {
        IDLE, // Esperando jugadores
        CAPTURING, // Un equipo está capturando (ganando puntos)
        CONTESTED, // Múltiples equipos en la zona
        CAPTURED // Alguien ganó
    }

    // Coordenadas de la zona de captura dentro del mundo KOTH
    private int x1, z1, x2, z2;

    // Estado en tiempo real
    private KothState state;
    private String capturingTeam; // Equipo/jugador dominante en la zona
    private boolean active; // Si el evento está corriendo

    // Tiempo restante del evento (en segundos)
    private int timeRemaining;
    private int totalDuration;

    public KothZone(int x1, int z1, int x2, int z2, int durationSeconds) {
        // Normalizar coordenadas
        this.x1 = Math.min(x1, x2);
        this.z1 = Math.min(z1, z2);
        this.x2 = Math.max(x1, x2);
        this.z2 = Math.max(z1, z2);
        this.totalDuration = durationSeconds;
        this.timeRemaining = durationSeconds;
        this.state = KothState.IDLE;
        this.capturingTeam = null;
        this.active = false;
    }

    /**
     * Comprueba si una ubicación está dentro de la zona de captura.
     * Solo funciona en el mundo KOTH. Ignora Y.
     */
    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null)
            return false;
        if (!loc.getWorld().getName().equals(KothWorld.getWorldName()))
            return false;

        int px = loc.getBlockX();
        int pz = loc.getBlockZ();

        return px >= x1 && px <= x2 && pz >= z1 && pz <= z2;
    }

    /**
     * Obtiene el mundo KOTH.
     */
    public World getWorld() {
        return Bukkit.getWorld(KothWorld.getWorldName());
    }

    /**
     * Reduce el tiempo restante en 1 segundo.
     * 
     * @return true si el tiempo se acabó (fin del evento)
     */
    public boolean tickTime() {
        if (timeRemaining > 0) {
            timeRemaining--;
        }
        return timeRemaining <= 0;
    }

    /**
     * Obtiene el porcentaje de tiempo transcurrido (0.0 a 1.0).
     */
    public double getTimePercentage() {
        if (totalDuration <= 0)
            return 0;
        return 1.0 - ((double) timeRemaining / totalDuration);
    }

    /**
     * Resetea la zona para un nuevo evento.
     */
    public void reset() {
        this.timeRemaining = totalDuration;
        this.state = KothState.IDLE;
        this.capturingTeam = null;
    }

    // === Getters y Setters ===

    public String getWorldName() {
        return KothWorld.getWorldName();
    }

    public int getX1() {
        return x1;
    }

    public int getZ1() {
        return z1;
    }

    public int getX2() {
        return x2;
    }

    public int getZ2() {
        return z2;
    }

    public void setCoords(int x1, int z1, int x2, int z2) {
        this.x1 = Math.min(x1, x2);
        this.z1 = Math.min(z1, z2);
        this.x2 = Math.max(x1, x2);
        this.z2 = Math.max(z1, z2);
    }

    public KothState getState() {
        return state;
    }

    public void setState(KothState state) {
        this.state = state;
    }

    public String getCapturingTeam() {
        return capturingTeam;
    }

    public void setCapturingTeam(String team) {
        this.capturingTeam = team;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getTimeRemaining() {
        return timeRemaining;
    }

    public void setTimeRemaining(int seconds) {
        this.timeRemaining = seconds;
    }

    public int getTotalDuration() {
        return totalDuration;
    }

    public void setTotalDuration(int seconds) {
        this.totalDuration = seconds;
    }
}
