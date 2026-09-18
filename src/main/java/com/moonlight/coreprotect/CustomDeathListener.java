package com.moonlight.coreprotect;

import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class CustomDeathListener implements Listener {

    private static final Random RNG = new Random();

    // ── Colores ─────────────────────────────────────────────────────────────────
    private static final String V  = "§c"; // víctima
    private static final String K  = "§e"; // asesino
    private static final String M  = "§6"; // mob
    private static final String G  = "§7"; // texto gris
    private static final String W  = "§f"; // blanco

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        String v = V + victim.getName() + G;

        // ── Killed by player ────────────────────────────────────────────────────
        Player playerKiller = victim.getKiller();
        if (playerKiller != null) {
            String k = K + playerKiller.getName() + G;
            String w = weaponDisplay(playerKiller.getInventory().getItemInMainHand());
            EntityDamageEvent raw = victim.getLastDamageCause();
            boolean isProjectile = raw instanceof EntityDamageByEntityEvent
                    && ((EntityDamageByEntityEvent) raw).getDamager() instanceof Projectile;
            event.setDeathMessage(isProjectile ? pickPvPArrow(v, k, w) : pickPvP(v, k, w));
            return;
        }

        // ── Killed by mob ───────────────────────────────────────────────────────
        EntityDamageEvent dmgEvent = victim.getLastDamageCause();
        if (dmgEvent instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) dmgEvent).getDamager();

            // Proyectil de mob
            if (damager instanceof Projectile) {
                ProjectileSource src = ((Projectile) damager).getShooter();
                if (src instanceof Entity) damager = (Entity) src;
            }

            event.setDeathMessage(pickMob(v, damager));
            return;
        }

        // ── Environment ─────────────────────────────────────────────────────────
        if (dmgEvent != null) {
            event.setDeathMessage(pickEnv(dmgEvent.getCause(), v));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PvP — cuerpo a cuerpo
    // ═══════════════════════════════════════════════════════════════════════════
    private String pickPvP(String v, String k, String w) {
        return pick(Arrays.asList(
            v + " existía. " + k + " usó " + w + ". " + v + " ya no existía. Fin de la historia.",
            k + " ni se despeinó. Sacó " + w + " y " + v + " pasó a ser loot.",
            v + " amenazó a " + k + ". " + k + " respondió con " + w + ". No había contest.",
            k + " mató a " + v + " con " + w + " y siguió su camino sin ni mirar. Eso duele más que la muerte.",
            v + " tardó más en equiparse que en morir ante " + k + " y su " + w + ".",
            k + " archivó a " + v + " en la carpeta 'resuelto' con un solo " + w + ".",
            v + " gritó '\u00a1a mí!' antes del combate. Después del primer golpe de " + w + ", ya no gritó nada.",
            k + " jugó con " + v + " como el gato con el ratón. El gato tenía " + w + ".",
            v + " pensó que " + k + " tenía suerte. Lleva demasiados kills de 'suerte' seguidos.",
            k + " ni usó todo el combo. " + v + " se cayó en el segundo golpe de " + w + ".",
            v + " buscaba pelea. " + k + " le ofreció una lección con " + w + ". No era lo que pedía.",
            k + " no dijo nada. Sacó " + w + " y habló con los hechos. " + v + " escuchó.",
            v + " debía haber farmeado más antes de salir. " + k + " y su " + w + " se lo recuerdan.",
            k + " vio a " + v + " y pensó: '" + w + " sobra para esto'. Acertó.",
            v + " se creyó peligroso. " + k + " lo clasificó como 'fácil' con " + w + ".",
            k + " le hizo a " + v + " un speed run de su vida con " + w + ". Tiempo récord.",
            v + " tuvo una conversación con " + w + " de " + k + ". Fue corta y unilateral.",
            k + " le dio a " + v + " una evaluación de rendimiento con " + w + ". Nota: Insuficiente.",
            v + " pensó: 'si corro lo suficiente sobrevivo'. " + w + " de " + k + " discrepaba.",
            k + " liquidó a " + v + " con " + w + " y le echó un vistazo al loot. No merecía la pena.",
            v + " sacó su mejor equipo. " + k + " sacó " + w + ". No fue suficiente para " + v + ".",
            k + " le mandó a " + v + " un curriculum de sus fracasos. Lo firmó con " + w + ".",
            v + " llegó con full armadura y se fue con 0 corazones gracias a " + k + " y " + w + ".",
            k + " usó " + w + " como si fuera un trámite burocrático. " + v + " era el papeleo.",
            v + " pensó en hacer clutch. " + k + " pensó en el siguiente golpe de " + w + ". Uno de los dos acertó.",
            k + " demostró con " + w + " que hay diferencia entre tener armadura y saber usarla.",
            v + " le dijo a " + k + " 'ya verás'. " + k + " vio. " + v + " no vio nada más.",
            k + " le puso a " + v + " un 0 en supervivencia con " + w + ". Sorpresa de nadie.",
            v + " retó a " + k + " sin saber que " + k + " tenía " + w + " y demasiadas ganas.",
            k + " terminó la discusión con " + v + " antes de que " + v + " la empezara. Gracias a " + w + ".",
            v + " no era el problema de " + k + ". Era el trámite. " + w + " lo confirmó.",
            k + " usó " + w + " y " + v + " voló hacia el spawn como si llevara elítras pero sin.",
            v + " tenía plan. " + k + " tenía " + w + ". El plan de " + k + " era mejor.",
            k + " ni llevó poción. " + v + " no le hizo falta curar. Era demasiado rápido con " + w + "."
        ));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PvP — flechas/proyectiles
    // ═══════════════════════════════════════════════════════════════════════════
    private String pickPvPArrow(String v, String k, String w) {
        return pick(Arrays.asList(
            k + " decidió que " + v + " no valía ni el sprint. Sacó " + w + " y lo resolvió desde el sitio.",
            v + " corrió hacia " + k + ". El proyectil de " + w + " corrió hacia " + v + ". Llegaron al mismo sitio. Mal para " + v + ".",
            k + " apuntó " + w + " 0.5 segundos. " + v + " tardó 3 días en respawnear.",
            v + " estaba mirando el chat cuando el " + w + " de " + k + " le visitó. Mala elección de momento.",
            k + " tiene mejor puntería que escrúpulos. " + v + " lo confirma desde el spawn.",
            v + " llevó espada a un mundo donde " + k + " tiene " + w + " y puntería. Error de cálculo grave.",
            v + " ni llegó a sacar la espada. El " + w + " de " + k + " ya había terminado el trabajo.",
            k + " le mandó a " + v + " un proyectil de " + w + " con dedicatoria. Llegó antes que él.",
            v + " pensó que ponerse a cubierto serviría de algo. El " + w + " de " + k + " llega a todas partes.",
            k + " apagó a " + v + " desde lejos con " + w + " como quien apaga una vela. Sin dramatismo.",
            v + " corría en zigzag porque 'así no te dan'. " + k + " no leyó ese libro con " + w + ".",
            k + " usó " + w + " y " + v + " murió antes de enterarse de que había pelea.",
            v + " pedía 1v1 en el chat mientras le llovían flechas de " + w + " de " + k + ".",
            k + " le demostró a " + v + " que distancia + " + w + " es peor que espada de cerca. Mucho peor.",
            v + " y el " + w + " de " + k + " tuvieron un malentendido. Muy doloroso para " + v + ".",
            k + " ni se movió. " + v + " hizo todo el trabajo de ir hacia el proyectil de " + w + ".",
            v + " pensó que podía esquivarlo. El " + w + " de " + k + " no se equivoca."
        ));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Mobs
    // ═══════════════════════════════════════════════════════════════════════════
    private String pickMob(String v, Entity mob) {
        String m = M + mobName(mob) + G;

        if (mob instanceof Creeper) return pick(Arrays.asList(
            v + " escuchó el sssss. Pensó 'no pasa nada'. Pasó todo.",
            v + " y " + m + " tuvieron una reunión de trabajo. El " + m + " puso el orden del día. Y la bomba.",
            v + " estaba tan concentrado en lo suyo que ni vio al " + m + ". El " + m + " sí vio a " + v + ".",
            m + " le hizo a " + v + " una reforma express del terreno. Sin presupuesto previo.",
            v + " se acercó al " + m + " con curiosidad científica. Fue su última investigación.",
            v + " pensó: 'seguro que no explota'. Explotó.",
            v + " vio al " + m + " verde y pensó 'qué mono'. Último pensamiento.",
            v + " murió por " + m + ". La IA más básica del juego lo eliminó. Para reflexionar.",
            v + " se hizo una selfie con " + m + ". El " + m + " mejoró el ángulo a su manera.",
            m + " le dio a " + v + " una clase de explosión sin previo aviso ni reembolso."
        ));

        if (mob instanceof Skeleton || mob instanceof WitherSkeleton) return pick(Arrays.asList(
            v + " fue eliminado por alguien sin pulmones ni ojos. Y aun así le ganó.",
            v + " perdió ante " + m + ". Una entidad que ni siquiera puede ver en condiciones.",
            m + " decidió que " + v + " era demasiado lento para vivir.",
            v + " trajo espada a un duelo de arcos. Mala decisión, por si no quedaba claro.",
            v + " subestimó a " + m + ". " + m + " no subestimó a " + v + ".",
            v + " se ofreció como diana sin querer. " + m + " aceptó encantado.",
            m + " le mandó flechas a " + v + " mientras " + v + " todavía caminaba hacia él.",
            v + " murió por " + m + ". Alguien sin carne, sin cerebro y sin miss. Triste."
        ));

        if (mob instanceof Zombie || mob instanceof Husk || mob instanceof Drowned) return pick(Arrays.asList(
            v + " fue cazado por " + m + ". Un zombie. Sin correr. Sin apuntar. Sin cerebro.",
            v + " murió ante una entidad que no puede abrir puertas. Para reflexionar en el spawn.",
            m + " siguió a " + v + " hasta el final. Literalmente. Y sin correr.",
            v + " confió en sus piernas. Sus piernas le fallaron ante " + m + ".",
            v + " fue alcanzado por " + m + ". Por un zombie. Hay que entrenar más.",
            v + " le dijo a " + m + " 'no me vas a pillar'. " + m + " no entendió el mensaje. Sí lo pilló.",
            m + " convirtió a " + v + " en colega. El tipo de colega que muerde.",
            v + " tuvo miedo. Corrió. No fue suficiente. Era un zombie."
        ));

        if (mob instanceof Spider || mob instanceof CaveSpider) return pick(Arrays.asList(
            v + " se acercó a " + m + " sin escudo. Mala idea.",
            m + " saltó sobre " + v + " y " + v + " no lo vio venir.",
            v + " murió aracnofóbicamente.",
            m + " demostró que 8 patas son mejor que 2.",
            v + " estaba mirando el suelo cuando apareció " + m + " desde arriba."
        ));

        if (mob instanceof Blaze) return pick(Arrays.asList(
            v + " se acercó a " + m + " sin escudo antifuego.",
            m + " le mandó bolas de fuego a " + v + " con mucho entusiasmo.",
            v + " subestimó el Nether. El Nether cobró.",
            m + " redecorÓ a " + v + " con fuego.",
            v + " y " + m + " tuvieron un intercambio. Solo uno lanzó cosas."
        ));

        if (mob instanceof EnderDragon) return pick(Arrays.asList(
            v + " fue a matar al dragón. El dragón tenía otros planes.",
            v + " dijo 'el Ender Dragon es fácil'. El Ender Dragon escuchó.",
            m + " recibió a " + v + " en su dominio y le pidió que se fuera. Con fuerza.",
            v + " se aventuró al End sin estar listo. El End lo confirmó.",
            m + " le dio a " + v + " una lección sobre preparación."
        ));

        if (mob instanceof Witch) return pick(Arrays.asList(
            v + " fue hechizado por " + m + " sin poder hacer nada.",
            m + " le tiró pociones a " + v + " con muy mala leche.",
            v + " no tenía leche para contrarrestar lo que le tiró " + m + ".",
            m + " convirtió a " + v + " en un experimento fallido."
        ));

        if (mob instanceof Phantom) return pick(Arrays.asList(
            v + " llevaba demasiados días sin dormir. " + m + " lo resolvió.",
            m + " le recordó a " + v + " por qué hay que usar la cama.",
            v + " pensó que los fantasmas voladores no eran un problema. Era un problema."
        ));

        // Generic mob
        return pick(Arrays.asList(
            v + " fue derrotado por " + m + ". Qué vergüenza.",
            m + " acabó con " + v + " antes de que " + v + " pudiera reaccionar.",
            v + " subestimó a " + m + ". Última subestimación.",
            m + " le demostró a " + v + " que el mundo salvaje sigue siendo salvaje.",
            v + " y " + m + " se encontraron. Solo uno salió.",
            m + " no conocía a " + v + ", pero decidió acabar con eso rápido.",
            v + " intentó pelear con " + m + " y el resultado no fue el esperado.",
            m + " eliminó a " + v + " con desconcertante eficiencia.",
            v + " eligió el peor momento para no tener corazones.",
            m + " vio a " + v + " y tomó la iniciativa."
        ));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Entorno
    // ═══════════════════════════════════════════════════════════════════════════
    private String pickEnv(EntityDamageEvent.DamageCause cause, String v) {
        switch (cause) {

            case FALL: return pick(Arrays.asList(
                v + " pensó que el suelo era una sugerencia.",
                v + " descubrió que la gravedad no tiene bugs.",
                v + " calculó mal. Bastante mal.",
                v + " practicó el deporte de caída libre sin paracaídas.",
                v + " decidió que las escaleras son para gente sin imaginación.",
                v + " confundió el botón de saltar con el de volar.",
                v + " llegó al suelo sin haber sido invitado.",
                v + " hizo parkour. El parkour le ganó.",
                v + " cayó desde tan alto que la caída fue casi artística.",
                v + " se tiró esperando que el suelo dijera 'era broma'.",
                v + " prometió comprarse botas de caída antes de volver.",
                v + " pensó que había camino. No había.",
                v + " trató de hacer un salto épico. Solo salió lo segundo.",
                v + " y la gravedad llegaron a un acuerdo: " + v + " bajaba.",
                v + " olvidó que seguía existiendo el suelo.",
                v + " tomó el camino más directo al destino.",
                v + " exploró las profundidades. Involuntariamente."
            ));

            case FIRE:
            case FIRE_TICK: return pick(Arrays.asList(
                v + " descubrió que el fuego no cura.",
                v + " ahora es carbón. Uno muy caro.",
                v + " pensó que era inmune al fuego. El fuego discrepó.",
                v + " decidió hacer barbacoa siendo el ingrediente principal.",
                v + " murió de calor. Extremo total.",
                v + " era altamente inflamable. Qué sorpresa.",
                v + " eligió el peor momento para no tener leche.",
                v + " se preguntó hasta el final si el fuego se apagaba solo.",
                v + " recibió warm vibes. Demasiadas.",
                v + " entendió tarde que 'calor acogedor' tiene límites.",
                v + " prendió fuego a algo y no leyó la dirección del viento.",
                v + " ardió con mucha elegancia, si se mira desde lejos.",
                v + " quería ver el mundo arder. Empezó por él mismo."
            ));

            case LAVA: return pick(Arrays.asList(
                v + " vio la lava y pensó '¡qué bonita!'.",
                v + " decidió nadar en lava. Una vez en la vida.",
                v + " confundió la lava con el jacuzzi del Nether.",
                v + " cayó a la lava y tardó 0.2 segundos en arrepentirse.",
                v + " fue a explorar el Nether y el Nether lo exploró a él.",
                v + " abrazó la lava. La lava no era de las de soltar.",
                v + " pensó que tenía protección antifuego. No era ese tipo.",
                v + " se fundió. Literalmente.",
                v + " se tiró 'para ver qué pasaba'. Ya lo sabe.",
                v + " y sus ítems van por caminos distintos ahora.",
                v + " eligió el peor charco en el que caer.",
                v + " quería temperatura ambiente. Se pasó un poco.",
                v + " encontró el jacuzzi más peligroso del servidor."
            ));

            case DROWNING: return pick(Arrays.asList(
                v + " descubrió que no sabe nadar.",
                v + " fue a buscar tesoros marinos y se quedó de residente.",
                v + " pensó que podía aguantar más. No podía.",
                v + " confundió el océano con una piscina municipal.",
                v + " se le acabó la burbuja. Y el oxígeno.",
                v + " fue en busca de Atlantis. Se quedó por el camino.",
                v + " perdió el debate contra el agua.",
                v + " se olvidó de que los humanos necesitan aire.",
                v + " exploró el océano más de lo recomendado.",
                v + " y el agua llegaron a un acuerdo de no convivencia.",
                v + " descubrió el fondo marino. Sin salir.",
                v + " pensó que el oxígeno era un recurso ilimitado. No lo es."
            ));

            case SUFFOCATION: return pick(Arrays.asList(
                v + " se metió en un bloque y el bloque no quiso soltarle.",
                v + " descubrió que los bloques sólidos son sólidos de verdad.",
                v + " murió aplastado. Por bloques. No tiene más misterio.",
                v + " y la arquitectura tuvieron una disputa. Ganó la arquitectura.",
                v + " intentó ocupar el mismo espacio que un bloque.",
                v + " fue absorbido por las paredes. Definitivamente."
            ));

            case VOID: return pick(Arrays.asList(
                v + " fue más allá del mundo y el mundo no lo esperó.",
                v + " cayó al vacío buscando el suelo.",
                v + " descubrió que el mapa tiene límites. Desde abajo.",
                v + " fue a reflexionar al vacío. No hay señal ahí.",
                v + " cayó tanto que llegó a la nada absoluta.",
                v + " encontró el fondo. No era lo que esperaba.",
                v + " se despidió del mundo... por las malas.",
                v + " exploró la parte más profunda del servidor.",
                v + " descubrió que debajo del bedrock hay más vacío.",
                v + " cayó al infinito y más allá. Solo al infinito."
            ));

            case STARVATION: return pick(Arrays.asList(
                v + " murió de hambre a 50 bloques de comida.",
                v + " llevaba media hora con la barra vacía diciendo 'ya como luego'.",
                v + " priorizó el farmeo sobre comer. Error fatal.",
                v + " no era de los que paran a comer.",
                v + " murió de hambre en un mundo lleno de animales comestibles.",
                v + " se olvidó de comer. Su personaje no se lo perdonó.",
                v + " tenía pan en el inventario. No lo comió a tiempo.",
                v + " practicó el ayuno involuntario.",
                v + " se murió de hambre estando en mitad de un bioma de setas.",
                v + " optimizó tanto su ruta de farmeo que se olvidó de comer."
            ));

            case POISON: return pick(Arrays.asList(
                v + " bebió algo de dudosa procedencia.",
                v + " se comió algo que claramente no debía.",
                v + " fue envenenado. Sus últimas palabras fueron 'un momento'.",
                v + " aprendió qué es el veneno desde dentro.",
                v + " fue verde en vida y en muerte.",
                v + " confió en sus pociones de curación. No llegaron a tiempo.",
                v + " murió pensando que el efecto se iba a ir solo.",
                v + " murió en verde. Hay quien lo llama estilo."
            ));

            case WITHER: return pick(Arrays.asList(
                v + " tuvo el debuff Wither y decidió ignorarlo. El Wither no lo ignoró.",
                v + " fue consumido por la oscuridad. Y por el Wither.",
                v + " murió de negro por dentro.",
                v + " ignoró el icono morado hasta que fue demasiado tarde.",
                v + " pensó que el Wither se curaba solo. Se equivocó.",
                v + " murió podrido. En el sentido literal."
            ));

            case MAGIC: return pick(Arrays.asList(
                v + " fue hechizado por alguien con muy mala leche.",
                v + " no leyó los términos y condiciones de la magia.",
                v + " fue víctima de magia oscura. Y de alguien sin escrúpulos.",
                v + " creyó que la magia era cosa de fantasía. Error.",
                v + " murió sin entender muy bien qué pasó.",
                v + " descubrió el poder de las pociones de daño. Desde dentro.",
                v + " fue maldecido. Efectivamente."
            ));

            case LIGHTNING: return pick(Arrays.asList(
                v + " fue elegido por el rayo. No como algo positivo.",
                v + " era el punto más alto del terreno. El cielo lo notó.",
                v + " fue el pararrayos voluntario del servidor.",
                v + " tuvo el día con peor suerte estadística de la historia.",
                v + " recibió electricidad estática. Mucha.",
                v + " descubrió que estar al aire libre tiene riesgos.",
                v + " y el cielo tuvieron un desacuerdo."
            ));

            case THORNS: return pick(Arrays.asList(
                v + " pegó a alguien con espinas y salió peor parado.",
                v + " atacó al que no debía.",
                v + " aprendió qué es el encantamiento Espinas por las malas.",
                v + " se hirió atacando. Dice mucho.",
                v + " el objetivo resultó ser una pésima elección.",
                v + " rebotó su propio daño. Karma instantáneo."
            ));

            case HOT_FLOOR: return pick(Arrays.asList(
                v + " caminó sobre magma sin botas antifuego.",
                v + " descubrió que no todo el suelo del Nether es seguro.",
                v + " confundió el magma con el suelo normal.",
                v + " murió por no mirar dónde pisaba.",
                v + " el suelo le ofreció una experiencia calórica."
            ));

            case FLY_INTO_WALL: return pick(Arrays.asList(
                v + " pilotó el elytra directamente contra un muro.",
                v + " alcanzó altas velocidades. El muro también estaba ahí.",
                v + " confundió el acelerador con el freno del elytra.",
                v + " hizo un aterrizaje forzoso. Muy forzoso.",
                v + " descubrió que los muros son sólidos cuando ya era tarde.",
                v + " iba tan rápido que el mundo no pudo quitarse de en medio.",
                v + " exploró los límites del elytra. Y de la física.",
                v + " calculó la trayectoria mal. Varios bloques de diferencia.",
                v + " necesitaba frenos. No los tenía."
            ));

            case FREEZE: return pick(Arrays.asList(
                v + " se metió en nieve en polvo. Mala idea.",
                v + " se quedó tan quieto que se congeló del todo.",
                v + " descubrió que la nieve en polvo es mortalmente bonita.",
                v + " murió de frío en Minecraft. Eso requiere esfuerzo.",
                v + " se convirtió en estatua de hielo de forma permanente.",
                v + " eligió el blanco equivocado para caminar encima."
            ));

            case BLOCK_EXPLOSION:
            case ENTITY_EXPLOSION: return pick(Arrays.asList(
                v + " pensó que el TNT era decorativo.",
                v + " fue lanzado tan lejos que ni él sabe dónde cayó.",
                v + " tuvo una diferencia de opiniones con el TNT.",
                v + " puso TNT 'para ver qué hacía'. Ya lo sabe.",
                v + " estuvo muy cerca de la explosión. Demasiado.",
                v + " redecorÓ el terreno con sus propios restos.",
                v + " y el TNT llegaron a un acuerdo. " + v + " perdía.",
                v + " pensó que la explosión era de otro. No era de otro.",
                v + " sobrevivió a muchas cosas. La explosión, no.",
                v + " le quitó al servidor un jugador y al paisaje varios bloques."
            ));

            case CRAMMING: return pick(Arrays.asList(
                v + " murió aplastado por demasiados seres vivos a la vez.",
                v + " necesitaba más espacio personal del que tenía.",
                v + " fue víctima del límite de entidades por bloque.",
                v + " murió de apretujones. Únicamente en Minecraft."
            ));

            case DRAGON_BREATH: return pick(Arrays.asList(
                v + " se bañó en el aliento del dragón. Voluntariamente.",
                v + " se paró justo donde no había que pararse.",
                v + " descubrió qué huele el aliento del Ender Dragon. Desde dentro."
            ));

            default: return pick(Arrays.asList(
                v + " murió. Sí, así como suena.",
                v + " encontró su final de formas creativas.",
                v + " se fue sin avisar.",
                v + " tuvo un incidente del que prefiere no hablar.",
                v + " el mundo le dijo que ya era suficiente.",
                v + " murió y nadie sabe muy bien cómo.",
                v + " tuvo un accidente laboral.",
                v + " existe un poco menos que antes.",
                v + " se fue de la forma más inesperada posible.",
                v + " exploró nuevas formas de morir."
            ));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Utilidades
    // ═══════════════════════════════════════════════════════════════════════════
    private String weaponDisplay(ItemStack item) {
        if (item == null || item.getType() == Material.AIR)
            return "§fsus propias manos" + G;
        if (item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasDisplayName())
            return item.getItemMeta().getDisplayName() + G;
        return "§b" + translateMaterial(item.getType()) + G;
    }

    private String translateMaterial(Material m) {
        switch (m) {
            case WOODEN_SWORD:   return "espada de madera";
            case STONE_SWORD:    return "espada de piedra";
            case IRON_SWORD:     return "espada de hierro";
            case GOLDEN_SWORD:   return "espada de oro";
            case DIAMOND_SWORD:  return "espada de diamante";
            case NETHERITE_SWORD:return "espada de netherita";
            case WOODEN_AXE:     return "hacha de madera";
            case STONE_AXE:      return "hacha de piedra";
            case IRON_AXE:       return "hacha de hierro";
            case GOLDEN_AXE:     return "hacha de oro";
            case DIAMOND_AXE:    return "hacha de diamante";
            case NETHERITE_AXE:  return "hacha de netherita";
            case BOW:            return "arco";
            case CROSSBOW:       return "ballesta";
            case TRIDENT:        return "tridente";
            case WOODEN_PICKAXE: return "pico de madera";
            case STONE_PICKAXE:  return "pico de piedra";
            case IRON_PICKAXE:   return "pico de hierro";
            case GOLDEN_PICKAXE: return "pico de oro";
            case DIAMOND_PICKAXE:return "pico de diamante";
            case NETHERITE_PICKAXE: return "pico de netherita";
            case WOODEN_SHOVEL:  return "pala de madera";
            case STONE_SHOVEL:   return "pala de piedra";
            case IRON_SHOVEL:    return "pala de hierro";
            case DIAMOND_SHOVEL: return "pala de diamante";
            case NETHERITE_SHOVEL: return "pala de netherita";
            default: return m.name().toLowerCase().replace("_", " ");
        }
    }

    private <T> T pick(List<T> list) {
        return list.get(RNG.nextInt(list.size()));
    }

    private String mobName(Entity e) {
        if (e instanceof Creeper)        return "Creeper";
        if (e instanceof Skeleton)       return "Esqueleto";
        if (e instanceof WitherSkeleton) return "Esqueleto del Wither";
        if (e instanceof Stray)          return "Estrío";
        if (e instanceof Zombie)         return "Zombie";
        if (e instanceof Husk)           return "Polvoriento";
        if (e instanceof Drowned)        return "Ahogado";
        if (e instanceof Spider)         return "Araña";
        if (e instanceof CaveSpider)     return "Araña de cueva";
        if (e instanceof Blaze)          return "Blaze";
        if (e instanceof Enderman)       return "Enderman";
        if (e instanceof EnderDragon)    return "Ender Dragon";
        if (e instanceof Witch)          return "Bruja";
        if (e instanceof Phantom)        return "Fantasma";
        if (e instanceof Guardian)       return "Guardián";
        if (e instanceof ElderGuardian)  return "Guardián anciano";
        if (e instanceof Vindicator)     return "Vindicador";
        if (e instanceof Evoker)         return "Invocador";
        if (e instanceof Pillager)       return "Saqueador";
        if (e instanceof Ravager)        return "Devastador";
        if (e instanceof Wither)         return "Wither";
        if (e instanceof IronGolem)      return "Golem de hierro";
        if (e instanceof Slime)          return "Slime";
        if (e instanceof MagmaCube)      return "Cubo de magma";
        if (e instanceof Ghast)          return "Ghast";
        if (e instanceof Hoglin)         return "Hoglin";
        if (e instanceof Piglin)         return "Piglin";
        if (e instanceof PigZombie)      return "Piglin zombificado";
        if (e instanceof Shulker)        return "Shulker";
        if (e instanceof Silverfish)     return "Lepisma";
        if (e instanceof Endermite)      return "Endermite";
        if (e instanceof Bee)            return "Abeja";
        if (e instanceof Wolf)           return "Lobo";
        if (e instanceof LlamaSpit)      return "Llama (escupiendo)";
        return e.getType().name().replace("_", " ").toLowerCase();
    }
}
