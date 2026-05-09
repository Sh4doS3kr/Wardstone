package com.moonlight.coreprotect.tips;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * BossBar rotativa con mensajes promocionales, consejos y curiosidades.
 * Cambia cada 12 segundos con animación de progreso.
 */
public class BossBarAnnouncer implements Listener {

    private final CoreProtectPlugin plugin;
    private final List<BarMessage> promoMessages = new ArrayList<>();
    private final List<BarMessage> otherMessages = new ArrayList<>();
    private final Random random = new Random();
    private BossBar bossBar;
    private int tickCounter = 0;
    private int taskId = -1;

    // Duración de cada mensaje en segundos
    private static final int MESSAGE_DURATION = 7;
    // Probabilidad de que salga un mensaje de promo (0-100)
    private static final int PROMO_CHANCE = 40;

    private static class BarMessage {
        final String text;
        final BarColor color;
        BarMessage(String text, BarColor color) {
            this.text = text;
            this.color = color;
        }
    }

    public BossBarAnnouncer(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        loadMessages();
        Collections.shuffle(promoMessages);
        Collections.shuffle(otherMessages);
        createBossBar();
        startTask();
    }

    private void loadMessages() {
        // ═══════════════════════════════════════
        //  SERVIDOR / PROMOCIÓN (lista separada, aparece ~40% del tiempo)
        // ═══════════════════════════════════════
        promoMessages.add(new BarMessage("§b§l✦ §fEstás jugando en §b§lmlmc.lat §f— ¡El mejor servidor survival!", BarColor.BLUE));
        promoMessages.add(new BarMessage("§a§l✦ §fVisita nuestra tienda: §a§ltienda.moonlightmc.xyz §f— ¡Rangos y llaves!", BarColor.GREEN));
        promoMessages.add(new BarMessage("§9§l✦ §fÚnete a nuestro Discord: §9§ldiscord.mlmc.lat §f— ¡Comunidad activa!", BarColor.BLUE));
        promoMessages.add(new BarMessage("§d§l✦ §fSíguenos y comparte: §d§lmlmc.lat §f— ¡Ayúdanos a crecer!", BarColor.PINK));
        promoMessages.add(new BarMessage("§e§l✦ §f¿Te gusta el server? ¡Invita a tus amigos! §eIP: §lmlmc.lat", BarColor.YELLOW));
        promoMessages.add(new BarMessage("§6§l✦ §fApoya al servidor comprando en §6§ltienda.moonlightmc.xyz §f— ¡Gracias!", BarColor.YELLOW));
        promoMessages.add(new BarMessage("§b§l✦ §fMoonlight MC §8— §7Survival §b• §7PvP §b• §7Bosses §b• §7KOTH §b• §7Minijuegos", BarColor.BLUE));
        promoMessages.add(new BarMessage("§a§l✦ §f¿Necesitas ayuda? Pregunta en §a§ldiscord.mlmc.lat §f— ¡Staff activo!", BarColor.GREEN));
        promoMessages.add(new BarMessage("§b§l✦ §fConéctate con tus amigos en §b§lmlmc.lat §f— ¡Diversión asegurada!", BarColor.BLUE));
        promoMessages.add(new BarMessage("§6§l✦ §fRangos VIP con ventajas exclusivas en §6§ltienda.moonlightmc.xyz", BarColor.YELLOW));
        promoMessages.add(new BarMessage("§d§l✦ §fHaz §d/prestige §fy llega a P70 para premios legendarios. §dIP: mlmc.lat", BarColor.PINK));
        promoMessages.add(new BarMessage("§e§l✦ §fCompra llaves Moon en §e§ltienda.moonlightmc.xyz §f— ¡Los mejores drops!", BarColor.YELLOW));
        promoMessages.add(new BarMessage("§b§l✦ §fMoonlight MC — Actualizado a 1.21 §b• §7PvP mejorado §b• §7Nuevos bosses", BarColor.BLUE));
        promoMessages.add(new BarMessage("§a§l✦ §fReporta bugs y sugiere ideas en §a§ldiscord.mlmc.lat §f— ¡Te escuchamos!", BarColor.GREEN));
        promoMessages.add(new BarMessage("§6§l✦ §f¿Quieres VIP gratis? ¡Vota por nosotros! Info en §6§ldiscord.mlmc.lat", BarColor.YELLOW));
        promoMessages.add(new BarMessage("§d§l✦ §fSorteos y eventos exclusivos en §d§ldiscord.mlmc.lat §f— ¡No te pierdas nada!", BarColor.PINK));
        promoMessages.add(new BarMessage("§b§l✦ §fSomos survival con contenido original: Errante, KOTH, Bosses... §bmlmc.lat", BarColor.BLUE));
        promoMessages.add(new BarMessage("§e§l✦ §fCompra el rango §e§lLuna §fy obtén vuelo, kits y más en §etienda.moonlightmc.xyz", BarColor.YELLOW));
        promoMessages.add(new BarMessage("§a§l✦ §fNuevos jugadores: Escribe §a/cores §fpara proteger tu base. §aIP: mlmc.lat", BarColor.GREEN));
        promoMessages.add(new BarMessage("§6§l✦ §f¡Llaves gratis por votar! Info en §6§ldiscord.mlmc.lat", BarColor.YELLOW));

        // ═══════════════════════════════════════
        //  COMANDOS / FEATURES
        // ═══════════════════════════════════════
        otherMessages.add(new BarMessage("§e§l✦ §fUsa §e/esencias §fpara abrir la tienda de esencias. ¡Kits y armas especiales!", BarColor.YELLOW));
        otherMessages.add(new BarMessage("§e§l✦ §fUsa §e/prestige §fpara ver tu prestigio y misiones. ¡Iconos exclusivos!", BarColor.YELLOW));
        otherMessages.add(new BarMessage("§e§l✦ §fUsa §e/koth §fpara ir al evento KOTH. ¡Compite y gana llaves!", BarColor.YELLOW));
        otherMessages.add(new BarMessage("§e§l✦ §fUsa §e/boss §fpara luchar contra bosses épicos y ganar esencias.", BarColor.YELLOW));
        otherMessages.add(new BarMessage("§e§l✦ §fUsa §e/ah §fpara vender y comprar items en la subasta.", BarColor.YELLOW));
        otherMessages.add(new BarMessage("§e§l✦ §fUsa §e/trade <jugador> §fpara intercambiar items, dinero y esencias.", BarColor.YELLOW));
        otherMessages.add(new BarMessage("§e§l✦ §fUsa §e/racha §fpara ver tu racha diaria. ¡Llaves gratis cada día!", BarColor.YELLOW));
        otherMessages.add(new BarMessage("§e§l✦ §fUsa §e/pv §fpara guardar items en tu vault privado. ¡Seguridad total!", BarColor.YELLOW));
        otherMessages.add(new BarMessage("§e§l✦ §fUsa §e/mg §fpara unirte a los minijuegos cuando estén activos.", BarColor.YELLOW));
        otherMessages.add(new BarMessage("§e§l✦ §fUsa §e/errante §fpara ver tus misiones del Errante Oscuro.", BarColor.YELLOW));
        otherMessages.add(new BarMessage("§e§l✦ §fUsa §e/cores §fpara gestionar tu núcleo y proteger tu base.", BarColor.YELLOW));
        otherMessages.add(new BarMessage("§e§l✦ §fUsa §e/bounty <jugador> <$> §fpara poner recompensa por alguien.", BarColor.YELLOW));
        otherMessages.add(new BarMessage("§e§l✦ §fUsa §e/finishers §fpara equipar animaciones de muerte épicas.", BarColor.YELLOW));
        otherMessages.add(new BarMessage("§e§l✦ §fUsa §e/kits §fpara ver los kits disponibles. ¡Algunos tienen habilidades!", BarColor.YELLOW));
        otherMessages.add(new BarMessage("§e§l✦ §fUsa §e/pwarp §fpara visitar warps de otros jugadores.", BarColor.YELLOW));

        // ═══════════════════════════════════════
        //  CONSEJOS DE SUPERVIVENCIA
        // ═══════════════════════════════════════
        otherMessages.add(new BarMessage("§a§l✦ §fConsejo: Protege tu base con §e/cores§f. ¡Nadie podrá destruirla!", BarColor.GREEN));
        otherMessages.add(new BarMessage("§a§l✦ §fConsejo: Guarda tus items valiosos en §e/pv§f. ¡Son seguros!", BarColor.GREEN));
        otherMessages.add(new BarMessage("§a§l✦ §fConsejo: No salgas sin totem. ¡Una segunda oportunidad salva vidas!", BarColor.GREEN));
        otherMessages.add(new BarMessage("§a§l✦ §fConsejo: Lleva siempre perlas de Ender para escapar del peligro.", BarColor.GREEN));
        otherMessages.add(new BarMessage("§a§l✦ §fConsejo: La zona AFK te da esencias gratis. ¡Quédate ahí un rato!", BarColor.GREEN));
        otherMessages.add(new BarMessage("§a§l✦ §fConsejo: Revisa §e/racha §fdiariamente para no perder tu streak.", BarColor.GREEN));
        otherMessages.add(new BarMessage("§a§l✦ §fConsejo: Cada 30 min conectado se abre una ruleta con premios gratis.", BarColor.GREEN));
        otherMessages.add(new BarMessage("§a§l✦ §fConsejo: Los prestigios altos dan fly, VIP y muchos extras. §e/prestige", BarColor.GREEN));
        otherMessages.add(new BarMessage("§a§l✦ §fConsejo: Puedes pescar items raros. ¡Prueba suerte con la caña!", BarColor.GREEN));
        otherMessages.add(new BarMessage("§a§l✦ §fConsejo: Matar bosses da 12 esencias. ¡Ve a §e/boss §fcon amigos!", BarColor.GREEN));

        // ═══════════════════════════════════════
        //  COMPETITIVOS / PVP
        // ═══════════════════════════════════════
        otherMessages.add(new BarMessage("§c§l✦ §fEl KOTH da llaves y esencias al ganador. §e/koth §fpara ir.", BarColor.RED));
        otherMessages.add(new BarMessage("§c§l✦ §fPon bounties a tus enemigos con §e/bounty§f. ¡Que los cacen!", BarColor.RED));
        otherMessages.add(new BarMessage("§c§l✦ §fLas espadas de esencias tienen habilidades únicas. §e/esencias", BarColor.RED));
        otherMessages.add(new BarMessage("§c§l✦ §fEl Kit Demon tiene aura de fuego y habilidades pasivas OP.", BarColor.RED));
        otherMessages.add(new BarMessage("§c§l✦ §fEl set Void del Errante es el equipo más poderoso del server.", BarColor.RED));
    }

    private BarMessage pickNextMessage() {
        if (random.nextInt(100) < PROMO_CHANCE && !promoMessages.isEmpty()) {
            return promoMessages.get(random.nextInt(promoMessages.size()));
        }
        if (!otherMessages.isEmpty()) {
            return otherMessages.get(random.nextInt(otherMessages.size()));
        }
        return promoMessages.get(random.nextInt(promoMessages.size()));
    }

    private void createBossBar() {
        BarMessage msg = pickNextMessage();
        bossBar = Bukkit.createBossBar(
                SmallCaps.convert(msg.text),
                msg.color,
                BarStyle.SOLID
        );
        bossBar.setVisible(true);

        // Añadir todos los jugadores online
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isInMinigame(p)) {
                bossBar.addPlayer(p);
            }
        }
    }

    private void startTask() {
        // Tick cada segundo
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tickCounter++;

            // Actualizar progreso (cuenta regresiva)
            double progress = 1.0 - ((double) tickCounter / MESSAGE_DURATION);
            if (progress < 0) progress = 0;
            bossBar.setProgress(progress);

            // Cambiar mensaje cada MESSAGE_DURATION segundos
            if (tickCounter >= MESSAGE_DURATION) {
                tickCounter = 0;

                BarMessage msg = pickNextMessage();
                bossBar.setTitle(SmallCaps.convert(msg.text));
                bossBar.setColor(msg.color);
                bossBar.setProgress(1.0);
            }

            // Limpiar jugadores en minijuegos (tienen su propia bossbar)
            for (Player p : new ArrayList<>(bossBar.getPlayers())) {
                if (isInMinigame(p)) {
                    bossBar.removePlayer(p);
                }
            }
        }, 20L, 20L).getTaskId();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Añadir con delay para evitar conflictos con otras bossbars de join
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = event.getPlayer();
            if (p.isOnline() && !isInMinigame(p)) {
                bossBar.addPlayer(p);
            }
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        bossBar.removePlayer(event.getPlayer());
    }

    private boolean isInMinigame(Player p) {
        return p.getWorld().getName().equals(
                com.moonlight.coreprotect.minigames.MiniGameWorld.getWorldName());
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar.setVisible(false);
        }
    }
}
