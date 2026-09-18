package com.moonlight.coreprotect.tips;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class TipsManager {

    private final CoreProtectPlugin plugin;
    private final List<String> tips = new ArrayList<>();
    private final List<String> prestigeTips = new ArrayList<>();
    private final Random random = new Random();
    private int taskId = -1;

    public TipsManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        loadTips();
        startBroadcastTask();
    }

    private void loadTips() {
        tips.add("Usa §e/cores §7para gestionar tu núcleo de protección y upgrades.");
        tips.add("Coloca un núcleo para proteger tu zona. ¡Nadie podrá romper tus bloques!");
        tips.add("Mejora tu núcleo hasta nivel 20 para agrandar tu zona protegida.");
        tips.add("Activa el upgrade §eAnti-Explosión §7para evitar daños de TNT y creepers en tu zona.");
        tips.add("El upgrade §eAnti-Mob §7evita que mobs hostiles aparezcan dentro de tu zona protegida.");
        tips.add("Activa §eNo PvP §7en tu núcleo para que nadie pueda atacarte en tu zona.");
        tips.add("El upgrade §eAuto-Heal §7te regenera vida cuando estás en tu zona protegida.");
        tips.add("¡Puedes teletransportarte a tu núcleo con el upgrade §eTeleporte al Core§7!");
        tips.add("Pon §eHora Fija §7en tu núcleo para tener siempre de día o de noche en tu zona.");
        tips.add("El upgrade §eAnti-Phantom §7evita que los phantoms aparezcan sobre tu base.");
        tips.add("Coloca un cofre junto a tu núcleo y activa §eGenerador de Recursos §7para obtener minerales automáticamente.");
        tips.add("Agrega miembros a tu zona con §e/cores add <jugador>§7. ¡Pueden construir contigo!");
        tips.add("Usa §e/esencias §7para ver tu saldo de esencias y entrar a la tienda.");
        tips.add("Las esencias se obtienen al matar bosses, ganar KOTH y jugar en la zona AFK.");
        tips.add("En la tienda de esencias puedes comprar espadas especiales con habilidades únicas.");
        tips.add("La §eEspada Dash §7te permite teletransportarte hacia tu enemigo al atacar.");
        tips.add("La §eEspada Terremoto §7crea un terremoto que lanza a los enemigos por los aires.");
        tips.add("Usa §e/ah §7para abrir la casa de subastas y vender items a otros jugadores.");
        tips.add("Puedes poner una recompensa por la cabeza de alguien con §e/bounty poner <jugador> <cantidad>§7.");
        tips.add("Consulta los jugadores más buscados con §e/bounty top§7.");
        tips.add("Usa §e/pv §7para acceder a tu vault privado. ¡Guarda items de forma segura!");
        tips.add("Los rangos VIP desbloquean más vaults privados. ¡Hasta 5 cofres seguros!");
        tips.add("Escribe §e/finishers §7para ver y equipar animaciones de muerte personalizadas.");
        tips.add("El evento §eKOTH §7se activa periódicamente. Usa §e/koth §7para ir y competir por premios.");
        tips.add("Captura la zona del KOTH manteniéndote dentro. ¡El que más puntos tenga, gana!");
        tips.add("El KOTH tiene modos especiales: §eClásico§7, §cVale Todo§7, §8Nocturno §7y §dCooperativo§7.");
        tips.add("En el modo §cVale Todo §7del KOTH no hay reglas. ¡Todo vale para ganar!");
        tips.add("Usa §e/boss §7para ir a la arena de bosses y enfrentarte a criaturas épicas.");
        tips.add("Los bosses dropean §eesencias §7y items legendarios. ¡No te los pierdas!");
        tips.add("Cada boss tiene mecánicas únicas. ¡Aprende sus patrones para derrotarlos!");
        tips.add("El §eErrante §7aparece en el mundo con misiones especiales. ¡Búscalo y habla con él!");
        tips.add("Usa §e/errante §7para ver el progreso de tus misiones del Errante.");
        tips.add("Las misiones del Errante desbloquean equipo Void legendario con habilidades únicas.");
        tips.add("La §eHoja del Vacío §7tiene una habilidad pasiva que drena vida al golpear.");
        tips.add("El §eArco del Vacío §7dispara flechas especiales con efectos de área.");
        tips.add("Las §eBotas del Vacío §7te dan velocidad y reducen el daño por caída.");
        tips.add("El §ePico del Vacío §7mina más rápido y tiene habilidades especiales de minado.");
        tips.add("El §ePeto del Vacío §7te da absorción y efectos defensivos pasivos.");
        tips.add("Los §eLeggings del Vacío §7aumentan tu resistencia y te dan habilidades de evasión.");
        tips.add("El §eMazo del Vacío §7genera ondas de choque al golpear el suelo.");
        tips.add("Completa todas las misiones del Errante para obtener el set completo de Void.");
        tips.add("Puedes saltarte el diálogo de lore del Errante escribiendo §esaltar §7o §eskip §7en el chat.");
        tips.add("Usa §e/racha §7para ver tu racha de días conectados y reclamar recompensas diarias.");
        tips.add("¡Conecta 7 días seguidos para obtener §bLlaves Raras §7como recompensa de racha!");
        tips.add("La racha diaria llega hasta el §6día 150 §7con recompensas cada vez mejores.");
        tips.add("A los 30 días de racha recibes §6Llaves Especiales §7y §aesencias§7. ¡No la pierdas!");
        tips.add("Si no te conectas un día, ¡tu racha se reinicia! Conéctate a diario.");
        tips.add("Usa §e/tiempojugado §7para ver tus recompensas por tiempo de juego acumulado.");
        tips.add("Hay recompensas de tiempo jugado desde §e15 minutos §7hasta §63 meses§7.");
        tips.add("Cuanto más juegas, mejores llaves y premios recibes. ¡Las §5Moon Keys §7son las mejores!");
        // Prestige tips are loaded separately for weighted selection
        loadPrestigeTips();
        tips.add("Usa §e/kits §7para ver los kits premium disponibles.");
        tips.add("El §cKit Demon §7tiene habilidades pasivas de fuego y un aura infernal.");
        tips.add("No dejes items valiosos sin protección. ¡Usa §e/pv §7para guardarlos en tu vault!");
        tips.add("La zona AFK te da esencias pasivamente. ¡Quédate un rato y gana premios!");
        tips.add("Los minijuegos se activan periódicamente. Usa §e/mg §7para unirte cuando estén activos.");
        tips.add("Puedes salir de un minijuego en cualquier momento con §e/leave§7.");
        tips.add("Las manzanas doradas encantadas tienen un cooldown de 5 minutos. ¡Úsalas con cuidado!");
        tips.add("Los vex no pueden entrar en zonas protegidas ni en el spawn. ¡Tu base está segura!");
        tips.add("Cuando mates a un jugador, se activará tu Death Finisher si tienes uno equipado.");
        tips.add("La pesca tiene drops especiales. ¡Prueba tu suerte pescando en cualquier lago!");
        tips.add("Los spawners del servidor tienen barras de progreso holográficas para que sepas cuándo spawnean.");
        tips.add("Puedes ver el diagnóstico de mobs cerca tuyo si eres admin con §e/mobdiag§7.");
        tips.add("¡Los cubees son mascotas! Fabrica un §eBocadillo Cubee §7para domesticarlos.");
        tips.add("Usa §e/petsinfo §7para aprender sobre los diferentes tipos de cubees.");
        tips.add("¡Si encuentras un cofre abandonado en una estructura, podrá tener loot especial!");
        tips.add("El sistema anti-combate evita que huyas de peleas PvP. ¡Lucha hasta el final!");
        tips.add("¿Quieres intercambiar con alguien? Usa §e/trade <jugador>§7. Dinero, esencias e ítems en tiempo real.");
        tips.add("El §e/trade §7tiene confirmación de 10 segundos. ¡Ambos jugadores deben aceptar antes de intercambiar!");
        tips.add("Cada §a30 minutos §7conectado se abre una §eRuleta de Conexión §7con premios automáticos. ¡No te desconectes!");
    }

    private void loadPrestigeTips() {
        prestigeTips.add("Usa §e/prestige §7para ver el sistema de prestigios. ¡Desbloquea iconos como <glyph:rank5> <glyph:rank15> <glyph:rank30>!");
        prestigeTips.add("Los prestigios te dan §biconos exclusivos §7en el chat: <glyph:rank1> <glyph:rank10> <glyph:rank20> <glyph:rank40> <glyph:rank70> §e/prestige");
        prestigeTips.add("¡Cada prestigio tiene un §bicono §7único! Desde <glyph:rank1> hasta <glyph:rank70>. Usa §e/prestige§7.");
        prestigeTips.add("Completa misiones para subir de prestigio: minar, matar mobs, PvP... Usa §e/prestige §7para ver tu progreso.");
        prestigeTips.add("El ranking de prestigios muestra a los más dedicados del servidor. Usa §e/prestige §7y haz click en §eTop§7.");
        prestigeTips.add("¡Las recompensas de prestigio incluyen §5Llaves Moon§7, dinero, esencias y más! <glyph:rank25> <glyph:rank50> §e/prestige§7.");
        prestigeTips.add("A partir del §6§lPrestigio 40 §7las recompensas son §bMEGA OP§7: vuelo, llaves, VIP... <glyph:rank40> §e/prestige");
        prestigeTips.add("¿Sabías que al llegar a <glyph:rank70> §6§lP70 §7recibes §cLlaves de Espadas§7, §dMoon§7, §bFly permanente §7y más? §e/prestige");
        prestigeTips.add("Tiers: §6Cobre <glyph:rank5> §8→ §fHierro <glyph:rank15> §8→ §eOro <glyph:rank25> §8→ §9Lapis <glyph:rank35> §8→ §aEsmeralda <glyph:rank45> §8→ §bDiamante <glyph:rank55> §8→ §dAmatista <glyph:rank65> §e/prestige");
        prestigeTips.add("Los prestigios altos dan §bvuelo§7, §aVIP§7, §6dinero §7y §dllaves§7. <glyph:rank50> <glyph:rank60> <glyph:rank70> §e/prestige");
    }

    private void startBroadcastTask() {
        // Every 3 minutes = 3600 ticks
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) return;

            String tip;
            // 40% chance of showing a prestige tip
            if (!prestigeTips.isEmpty() && random.nextInt(100) < 40) {
                tip = prestigeTips.get(random.nextInt(prestigeTips.size()));
            } else if (!tips.isEmpty()) {
                tip = tips.get(random.nextInt(tips.size()));
            } else {
                return;
            }

            String message = SmallCaps.convert("§6§l💡 CONSEJO §8» §7" + tip);

            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage(message);
            Bukkit.broadcastMessage("");
        }, 3600L, 3600L).getTaskId();
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }
}
