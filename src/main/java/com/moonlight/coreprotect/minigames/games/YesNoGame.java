package com.moonlight.coreprotect.minigames.games;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.minigames.*;
import com.moonlight.coreprotect.util.ActionBarUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Sí o No: Minijuego de preguntas con respuestas por movimiento.
 * Los jugadores deben moverse al lado verde (SÍ) o rojo (NO) en 8 segundos.
 * Detección por bloque bajo los pies. Una vez decides, no puedes volver.
 */
public class YesNoGame extends MiniGame {

    private static final int QUESTION_TIME = 8;
    private static final int TOTAL_QUESTIONS = 10;
    private static final String API_KEY = "YOUR_GEMINI_API_KEY_HERE";
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemma-3-27b-it:generateContent";

    private static final int ARENA_SIZE = 15;
    private static final int ARENA_HEIGHT = 12;
    private static final int CENTER_Y = 100;
    private static final int FLOOR_Y = CENTER_Y - ARENA_HEIGHT / 2; // 94

    private int currentQuestion = 0;
    private String currentQuestionText = "";
    private boolean currentAnswer = true;
    private final Map<UUID, Integer> playerScores = new HashMap<>();
    private final Set<UUID> answeredPlayers = new HashSet<>();
    private final Set<UUID> greenSide = new HashSet<>();
    private final Set<UUID> redSide = new HashSet<>();
    private boolean questionActive = false;
    private BukkitRunnable countdownTask = null;
    private BukkitRunnable detectionTask = null;
    private static final int MAX_USED_QUESTIONS = 60;
    private static final List<String> usedQuestions = new ArrayList<>();

    private CompletableFuture<QuestionData> prefetchedQuestion = null;

    public YesNoGame(CoreProtectPlugin plugin, MiniGameManager manager) {
        super(plugin, manager, MiniGameType.YES_NO);
    }

    @Override
    public void buildArena(World world) {
        // Paredes negras
        for (int x = -ARENA_SIZE; x <= ARENA_SIZE; x++) {
            for (int z = -ARENA_SIZE; z <= ARENA_SIZE; z++) {
                for (int y = FLOOR_Y; y <= CENTER_Y + ARENA_HEIGHT / 2; y++) {
                    if (Math.abs(x) == ARENA_SIZE || Math.abs(z) == ARENA_SIZE) {
                        world.getBlockAt(x, y, z).setType(Material.BLACK_CONCRETE);
                    }
                }
            }
        }

        // Suelo: primero todo negro base
        for (int x = -ARENA_SIZE; x <= ARENA_SIZE; x++) {
            for (int z = -ARENA_SIZE; z <= ARENA_SIZE; z++) {
                world.getBlockAt(x, FLOOR_Y, z).setType(Material.BLACK_CONCRETE);
            }
        }

        // Lado verde (SÍ) - derecha (x >= 2)
        for (int x = 2; x < ARENA_SIZE; x++) {
            for (int z = -ARENA_SIZE + 1; z < ARENA_SIZE; z++) {
                world.getBlockAt(x, FLOOR_Y, z).setType(Material.GREEN_CONCRETE);
            }
        }

        // Lado rojo (NO) - izquierda (x <= -2)
        for (int x = -ARENA_SIZE + 1; x <= -2; x++) {
            for (int z = -ARENA_SIZE + 1; z < ARENA_SIZE; z++) {
                world.getBlockAt(x, FLOOR_Y, z).setType(Material.RED_CONCRETE);
            }
        }

        // Línea blanca central (3 de ancho: x = -1, 0, 1) — se pone AL FINAL para no sobreescribirse
        for (int x = -1; x <= 1; x++) {
            for (int z = -ARENA_SIZE + 1; z < ARENA_SIZE; z++) {
                world.getBlockAt(x, FLOOR_Y, z).setType(Material.WHITE_CONCRETE);
            }
        }

        // Techo completamente de glowstone
        int ceilingY = CENTER_Y + ARENA_HEIGHT / 2;
        for (int x = -ARENA_SIZE; x <= ARENA_SIZE; x++) {
            for (int z = -ARENA_SIZE; z <= ARENA_SIZE; z++) {
                world.getBlockAt(x, ceilingY, z).setType(Material.GLOWSTONE);
            }
        }

        // Carteles visuales en las paredes: SÍ (verde) y NO (rojo)
        for (int y = FLOOR_Y + 2; y <= FLOOR_Y + 5; y++) {
            for (int z = -3; z <= 3; z++) {
                world.getBlockAt(ARENA_SIZE - 1, y, z).setType(Material.GREEN_CONCRETE);
                world.getBlockAt(-ARENA_SIZE + 1, y, z).setType(Material.RED_CONCRETE);
            }
        }
    }

    @Override
    public List<Location> getSpawnLocations(World world) {
        List<Location> spawns = new ArrayList<>();
        // Spawns en la línea blanca central, sobre el suelo (FLOOR_Y + 1)
        for (int i = 0; i < 16; i++) {
            double z = -7 + i;
            spawns.add(new Location(world, 0.5, FLOOR_Y + 1, z + 0.5));
        }
        return spawns;
    }

    @Override
    public void startGameLogic() {
        broadcastGame("§6§l🎯 §e¡SÍ O NO! §7Responde moviéndote al lado correcto.");
        broadcastGame("§6§l🎯 §aDerecha = SÍ §7| §cIzquierda = NO §7| §e" + QUESTION_TIME + "s §7para responder.");
        broadcastGame("§6§l🎯 §7" + TOTAL_QUESTIONS + " preguntas. ¡Una vez decides, NO puedes volver!");
        titleAlive("§6§l🎯 SÍ O NO 🎯", "§7¡Prepárate para responder!");

        for (UUID uuid : alivePlayers) {
            playerScores.put(uuid, 0);
        }

        // Primera pregunta después de 3 segundos
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (running) askNextQuestion();
        }, 60L);
    }

    @Override
    public void onTick() {
        // Detection runs on its own fast task, nothing needed here
    }

    @Override
    public void onCleanup() {
        stopTasks();
    }

    private void stopTasks() {
        if (countdownTask != null) {
            try { countdownTask.cancel(); } catch (Exception ignored) {}
            countdownTask = null;
        }
        if (detectionTask != null) {
            try { detectionTask.cancel(); } catch (Exception ignored) {}
            detectionTask = null;
        }
    }

    private void askNextQuestion() {
        if (!running) return;
        if (currentQuestion >= TOTAL_QUESTIONS || alivePlayers.size() <= 1) {
            endGame(null);
            return;
        }

        currentQuestion++;
        questionActive = true;
        answeredPlayers.clear();
        greenSide.clear();
        redSide.clear();

        broadcastGame("§6§l🎯 §7Pregunta " + currentQuestion + "/" + TOTAL_QUESTIONS + " §7- §c" + QUESTION_TIME + "s para responder!");

        CompletableFuture<QuestionData> future =
                (prefetchedQuestion != null && !prefetchedQuestion.isCancelled())
                ? prefetchedQuestion
                : generateQuestionAsync();
        prefetchedQuestion = null;

        future.thenAccept(questionData -> {
            if (!running) return;

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!running) return;

                currentQuestionText = questionData.question;
                currentAnswer = questionData.answer;
                synchronized (usedQuestions) {
                    usedQuestions.add(currentQuestionText);
                    if (usedQuestions.size() > MAX_USED_QUESTIONS) {
                        usedQuestions.remove(0);
                    }
                }

                broadcastGame("§e§l❓ " + currentQuestionText);

                for (UUID uuid : alivePlayers) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        String titleQ = currentQuestionText.length() > 40
                                ? currentQuestionText.substring(0, 37) + "..."
                                : currentQuestionText;
                        p.sendTitle("§e§l❓ " + titleQ, "§aDerecha=SÍ §7| §cIzquierda=NO", 5, 160, 5);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
                    }
                }

                startDetectionTask();
                startCountdownTask();

                // Pre-fetch the NEXT question in the background while players are answering
                if (currentQuestion < TOTAL_QUESTIONS) {
                    prefetchedQuestion = generateQuestionAsync();
                }
            });
        });
    }

    private void startDetectionTask() {
        // Detección ultrarrápida: cada 2 ticks (0.1s)
        detectionTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!running || !questionActive) {
                    cancel();
                    return;
                }
                detectPlayerPositions();
            }
        };
        detectionTask.runTaskTimer(plugin, 0L, 2L);
    }

    private void detectPlayerPositions() {
        for (UUID uuid : new ArrayList<>(alivePlayers)) {
            if (answeredPlayers.contains(uuid)) continue;

            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            // Detectar bloque bajo los pies del jugador
            Location feet = p.getLocation();
            Block blockUnder = feet.getWorld().getBlockAt(
                    feet.getBlockX(), FLOOR_Y, feet.getBlockZ());
            Material mat = blockUnder.getType();

            if (mat == Material.GREEN_CONCRETE) {
                // Eligió SÍ
                answeredPlayers.add(uuid);
                greenSide.add(uuid);
                p.sendMessage("§a§l✔ §7Decidiste: §a§lSÍ");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
                ActionBarUtil.send(p, "§a§l✔ ELEGISTE: SÍ §7(no puedes cambiar)");
                checkAllAnswered();
            } else if (mat == Material.RED_CONCRETE) {
                // Eligió NO
                answeredPlayers.add(uuid);
                redSide.add(uuid);
                p.sendMessage("§c§l✖ §7Decidiste: §c§lNO");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
                ActionBarUtil.send(p, "§c§l✖ ELEGISTE: NO §7(no puedes cambiar)");
                checkAllAnswered();
            }
        }
    }

    private void checkAllAnswered() {
        if (!questionActive) return;
        int answered = answeredPlayers.size();
        int alive = alivePlayers.size();
        if (answered >= alive) {
            questionActive = false;
            stopTasks();
            broadcastGame("§6§l⚡ §7¡Todos han respondido!");
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (running) processQuestionResults();
            }, 10L);
        }
    }

    private void startCountdownTask() {
        countdownTask = new BukkitRunnable() {
            int countdown = QUESTION_TIME;
            @Override
            public void run() {
                if (!running || !questionActive) {
                    cancel();
                    return;
                }

                if (countdown > 0) {
                    String color = countdown <= 3 ? "§c§l" : "§e";
                    for (UUID uuid : alivePlayers) {
                        if (answeredPlayers.contains(uuid)) continue;
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null && p.isOnline()) {
                            ActionBarUtil.send(p, color + "⏱ " + countdown + "s §7| §aDerecha=SÍ §7| §cIzquierda=NO");
                            if (countdown <= 3) {
                                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 2.0f);
                            }
                        }
                    }
                    countdown--;
                } else {
                    cancel();
                    questionActive = false;
                    stopTasks();
                    processQuestionResults();
                }
            }
        };
        countdownTask.runTaskTimer(plugin, 0L, 20L);
    }

    private void processQuestionResults() {
        if (!running) return;
        questionActive = false;

        boolean correctAnswer = currentAnswer;
        Set<UUID> correctSide = correctAnswer ? greenSide : redSide;
        Set<UUID> incorrectSide = correctAnswer ? redSide : greenSide;
        Set<UUID> noAnswer = new HashSet<>(alivePlayers);
        noAnswer.removeAll(greenSide);
        noAnswer.removeAll(redSide);

        String answerText = correctAnswer ? "§a§lSÍ ✔" : "§c§lNO ✖";
        broadcastGame("§6§l🎯 §7Respuesta correcta: " + answerText);

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendTitle(correctAnswer ? "§a§lSÍ" : "§c§lNO", "§7Respuesta correcta", 5, 30, 5);
            }
        }

        List<UUID> toEliminate = new ArrayList<>();
        toEliminate.addAll(incorrectSide);
        toEliminate.addAll(noAnswer);

        for (UUID uuid : toEliminate) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                if (incorrectSide.contains(uuid)) {
                    p.sendMessage("§c§l✖ §7Respuesta incorrecta - ¡Eliminado!");
                } else {
                    p.sendMessage("§7§l⏱ §7No respondiste a tiempo - ¡Eliminado!");
                }
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
            }
        }

        // Eliminar usando el sistema base
        for (UUID uuid : toEliminate) {
            if (alivePlayers.contains(uuid)) {
                eliminatePlayer(uuid);
            }
        }

        // Premiar correctos
        for (UUID uuid : correctSide) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                playerScores.put(uuid, playerScores.getOrDefault(uuid, 0) + 10);
                p.sendMessage("§a§l✔ §7¡Correcto! +10 puntos. §7Total: §e" + playerScores.get(uuid));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 2.0f);
            }
        }

        if (alivePlayers.isEmpty()) {
            broadcastGame("§c§l💀 §7¡Todos han sido eliminados! No hay ganador.");
            endGame(null);
            return;
        }
        if (alivePlayers.size() <= 1) return; // checkWinCondition ya se encarga

        broadcastGame("§6§l🏆 §7Supervivientes: §e" + alivePlayers.size());

        // TP al centro y siguiente ronda
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!running) return;
            World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
            for (UUID uuid : alivePlayers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline() && w != null) {
                    p.teleport(new Location(w, 0.5, FLOOR_Y + 1, 0.5));
                }
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (running) askNextQuestion();
            }, 40L);
        }, 60L);
    }

    @Override
    public void endGame(Player winner) {
        stopTasks();
        UUID winnerUUID = null;
        int maxScore = -1;
        for (UUID uuid : alivePlayers) {
            Integer score = playerScores.get(uuid);
            if (score != null && score > maxScore) {
                maxScore = score;
                winnerUUID = uuid;
            }
        }
        if (winnerUUID != null) {
            Player actualWinner = Bukkit.getPlayer(winnerUUID);
            if (actualWinner != null && actualWinner.isOnline()) {
                super.endGame(actualWinner);
                return;
            }
        }
        super.endGame(null);
    }

    private CompletableFuture<QuestionData> generateQuestionAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                StringBuilder usedList = new StringBuilder();
                synchronized (usedQuestions) {
                    for (String q : usedQuestions) {
                        usedList.append("- ").append(q).append("\\n");
                    }
                }
                String prompt = "Genera una pregunta de cultura general de sí o no. " +
                        "Responde ÚNICAMENTE en este formato exacto:\\n" +
                        "PREGUNTA: [tu pregunta]\\nRESPUESTA: [SÍ o NO]\\n" +
                        "La pregunta debe tener una respuesta OBJETIVA y verificable. " +
                        "Ejemplo: '¿Australia es un continente?' RESPUESTA: SÍ\\n" +
                        "Ejemplo: '¿El sol gira alrededor de la tierra?' RESPUESTA: NO\\n" +
                        "Haz preguntas difíciles pero con respuesta clara.\\n" +
                        "NO repitas estas preguntas ya usadas:\\n" + usedList +
                        "No incluyas nada más en tu respuesta.";

                String jsonInput = "{\"contents\":[{\"parts\":[{\"text\":\"" +
                        prompt.replace("\"", "\\\"") + "\"}]}]}";

                URL url = new URL(API_URL + "?key=" + API_KEY);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.getOutputStream().write(jsonInput.getBytes());

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                String responseText = response.toString();
                String question = "";
                boolean answer = true;

                // Buscar en el JSON el texto de respuesta
                int textIdx = responseText.indexOf("\"text\"");
                if (textIdx >= 0) {
                    int start = responseText.indexOf("\"", textIdx + 6) + 1;
                    int end = responseText.indexOf("\"", start);
                    if (start > 0 && end > start) {
                        String extracted = responseText.substring(start, end)
                                .replace("\\n", "\n").replace("\\\"", "\"");
                        String[] lines = extracted.split("\n");
                        for (String rl : lines) {
                            rl = rl.trim();
                            if (rl.toUpperCase().startsWith("PREGUNTA:")) {
                                question = rl.substring(9).trim();
                            } else if (rl.toUpperCase().startsWith("RESPUESTA:")) {
                                String at = rl.substring(10).trim().toUpperCase();
                                answer = at.contains("SÍ") || at.contains("SI") || at.startsWith("S");
                            }
                        }
                    }
                }

                if (question.isEmpty()) {
                    return getRandomFallback();
                }
                return new QuestionData(question, answer);

            } catch (Exception e) {
                return getRandomFallback();
            }
        });
    }

    private QuestionData getRandomFallback() {
        String[][] fallback = {
                // === CIENCIA ===
                {"¿El corazón humano tiene 4 cámaras?", "SÍ"},
                {"¿La Luna tiene su propia luz?", "NO"},
                {"¿El ADN tiene forma de doble hélice?", "SÍ"},
                {"¿Plutón sigue siendo un planeta oficial?", "NO"},
                {"¿El agua hierve a 100°C al nivel del mar?", "SÍ"},
                {"¿Los murciélagos son ciegos?", "NO"},
                {"¿El oro es más pesado que la plata?", "SÍ"},
                {"¿Los humanos usan solo el 10% del cerebro?", "NO"},
                {"¿Venus es el planeta más caliente del sistema solar?", "SÍ"},
                {"¿El sonido viaja más rápido en el agua que en el aire?", "SÍ"},
                {"¿El hierro es el metal más abundante en la Tierra?", "SÍ"},
                {"¿La vitamina C cura el resfriado?", "NO"},
                {"¿El diamante es la sustancia natural más dura?", "SÍ"},
                {"¿La luz tarda 8 minutos en llegar del Sol a la Tierra?", "SÍ"},
                {"¿Los electrones son más grandes que los protones?", "NO"},
                {"¿El oxígeno es el gas más abundante en la atmósfera?", "NO"},
                {"¿El cerebro humano pesa aproximadamente 1.4 kg?", "SÍ"},
                {"¿La temperatura del Sol supera los 5000°C en su superficie?", "SÍ"},
                {"¿Los rayos X fueron descubiertos por accidente?", "SÍ"},
                {"¿Un año luz mide distancia, no tiempo?", "SÍ"},
                {"¿El mercurio es el único metal líquido a temperatura ambiente?", "NO"},
                {"¿Los átomos están compuestos principalmente de espacio vacío?", "SÍ"},
                {"¿El nitrógeno es el gas más abundante en la atmósfera?", "SÍ"},
                {"¿La velocidad de la luz es de 300.000 km/s aproximadamente?", "SÍ"},
                {"¿Los agujeros negros emiten luz?", "NO"},
                {"¿Marte tiene dos lunas?", "SÍ"},
                {"¿Júpiter es el planeta más grande del sistema solar?", "SÍ"},
                {"¿Saturno podría flotar en agua?", "SÍ"},
                {"¿El Big Bang ocurrió hace 14 mil millones de años aprox?", "SÍ"},
                {"¿Los quarks son más pequeños que los átomos?", "SÍ"},
                {"¿La gravedad es más fuerte en la Luna que en la Tierra?", "NO"},
                // === GEOGRAFÍA ===
                {"¿La Gran Muralla China se ve desde el espacio?", "NO"},
                {"¿Rusia es el país más grande del mundo?", "SÍ"},
                {"¿El Everest es la montaña más alta del mundo?", "SÍ"},
                {"¿El Nilo es el río más largo del mundo?", "SÍ"},
                {"¿Australia es más grande que la Antártida?", "NO"},
                {"¿Brasil tiene frontera con todos los países de Sudamérica?", "NO"},
                {"¿Islandia es verde y Groenlandia es de hielo?", "SÍ"},
                {"¿El desierto del Sahara es el más grande del mundo?", "NO"},
                {"¿Tokio es la ciudad más poblada del mundo?", "SÍ"},
                {"¿Canadá tiene más lagos que el resto del mundo junto?", "SÍ"},
                {"¿El canal de Panamá conecta el Atlántico con el Pacífico?", "SÍ"},
                {"¿Mongolia es el país menos densamente poblado del mundo?", "SÍ"},
                {"¿África tiene 54 países?", "SÍ"},
                {"¿El Vaticano es el país más pequeño del mundo?", "SÍ"},
                {"¿Colombia tiene costa en dos océanos?", "SÍ"},
                {"¿El Monte Kilimanjaro está en Kenia?", "NO"},
                {"¿El río Amazonas desemboca en el Océano Pacífico?", "NO"},
                {"¿Suiza tiene salida al mar?", "NO"},
                {"¿Hawái pertenece a Estados Unidos?", "SÍ"},
                {"¿Madrid es la capital más alta de Europa?", "SÍ"},
                // === ANIMALES ===
                {"¿Los tiburones son mamíferos?", "NO"},
                {"¿Los pingüinos pueden volar?", "NO"},
                {"¿Los delfines son peces?", "NO"},
                {"¿Las arañas son insectos?", "NO"},
                {"¿El avestruz es el ave más grande del mundo?", "SÍ"},
                {"¿Los gatos tienen 9 vidas?", "NO"},
                {"¿Las abejas mueren después de picar?", "SÍ"},
                {"¿Los pulpos tienen 3 corazones?", "SÍ"},
                {"¿Los camellos almacenan agua en sus jorobas?", "NO"},
                {"¿El colibrí puede volar hacia atrás?", "SÍ"},
                {"¿Los koalas son osos?", "NO"},
                {"¿Las vacas tienen 4 estómagos?", "SÍ"},
                {"¿El calamar gigante existe?", "SÍ"},
                {"¿Los flamencos nacen rosados?", "NO"},
                {"¿Los elefantes le tienen miedo a los ratones?", "NO"},
                {"¿Los perros ven en blanco y negro?", "NO"},
                {"¿Las ballenas azules son los animales más grandes que han existido?", "SÍ"},
                {"¿Los caballos pueden dormir de pie?", "SÍ"},
                {"¿Las hormigas pueden cargar 50 veces su peso?", "SÍ"},
                {"¿Los loros pueden vivir más de 80 años?", "SÍ"},
                {"¿El ornitorrinco pone huevos?", "SÍ"},
                {"¿Las serpientes tienen párpados?", "NO"},
                {"¿Los gatos siempre caen de pie?", "NO"},
                // === HISTORIA ===
                {"¿Einstein reprobó matemáticas?", "NO"},
                {"¿Napoleón era muy bajito?", "NO"},
                {"¿Cleopatra era egipcia de nacimiento?", "NO"},
                {"¿La Primera Guerra Mundial empezó en 1914?", "SÍ"},
                {"¿Los vikingos usaban cascos con cuernos?", "NO"},
                {"¿El Titanic se hundió en su primer viaje?", "SÍ"},
                {"¿La Revolución Francesa fue en el siglo XVIII?", "SÍ"},
                {"¿Cristóbal Colón descubrió que la Tierra era redonda?", "NO"},
                {"¿Los gladiadores siempre peleaban a muerte?", "NO"},
                {"¿La muralla de Berlín cayó en 1989?", "SÍ"},
                {"¿Roma fue fundada en el año 753 a.C. según la tradición?", "SÍ"},
                {"¿Los aztecas vivían en lo que hoy es México?", "SÍ"},
                {"¿La imprenta fue inventada por Gutenberg?", "SÍ"},
                {"¿El hombre llegó a la Luna en 1969?", "SÍ"},
                {"¿Las pirámides de Egipto fueron construidas por esclavos?", "NO"},
                // === COMIDA ===
                {"¿El tomate es una fruta?", "SÍ"},
                {"¿El chocolate viene de un árbol?", "SÍ"},
                {"¿La pizza fue inventada en Estados Unidos?", "NO"},
                {"¿Los plátanos son radiactivos?", "SÍ"},
                {"¿La miel nunca caduca?", "SÍ"},
                {"¿El wasabi que se sirve normalmente es wasabi real?", "NO"},
                {"¿Las zanahorias eran originalmente moradas?", "SÍ"},
                {"¿El café es una semilla?", "SÍ"},
                {"¿El aguacate es una fruta?", "SÍ"},
                {"¿La fresa es una fruta?", "NO"},
                {"¿El chicle tarda 7 años en digerirse?", "NO"},
                {"¿Los cacahuetes son frutos secos?", "NO"},
                // === DEPORTES ===
                {"¿Un partido de fútbol dura 90 minutos?", "SÍ"},
                {"¿El golf se inventó en Escocia?", "SÍ"},
                {"¿En el tenis, 'love' significa cero?", "SÍ"},
                {"¿Brasil ha ganado 5 mundiales de fútbol?", "SÍ"},
                {"¿Los Juegos Olímpicos modernos empezaron en 1896?", "SÍ"},
                {"¿Un maratón son exactamente 42 kilómetros?", "NO"},
                {"¿En baloncesto NBA se juegan 4 cuartos?", "SÍ"},
                {"¿El cricket es el deporte más popular del mundo?", "NO"},
                {"¿Usain Bolt corrió los 100m en menos de 9.5 segundos?", "SÍ"},
                // === MINECRAFT / GAMING ===
                {"¿En Minecraft, el diamante es el material más fuerte?", "NO"},
                {"¿El Ender Dragon es el jefe final de Minecraft?", "SÍ"},
                {"¿Los Creepers fueron creados por un error?", "SÍ"},
                {"¿Minecraft fue creado por Notch?", "SÍ"},
                {"¿Las ovejas en Minecraft pueden ser de color rosa natural?", "SÍ"},
                {"¿La obsidiana se puede romper con pico de hierro?", "NO"},
                {"¿El Nether tiene techo de bedrock?", "SÍ"},
                {"¿Los enderman odian el agua?", "SÍ"},
                {"¿Puedes dormir en el Nether?", "NO"},
                {"¿El Wither tiene 3 cabezas?", "SÍ"},
                {"¿Los zombis pueden romper puertas en difícil?", "SÍ"},
                {"¿Los aldeanos pueden morir de hambre?", "NO"},
                // === CULTURA POP ===
                {"¿Mario fue originalmente carpintero?", "SÍ"},
                {"¿Star Wars fue creada por Steven Spielberg?", "NO"},
                {"¿Harry Potter tiene una cicatriz en forma de rayo?", "SÍ"},
                {"¿Pikachu es tipo fuego?", "NO"},
                {"¿El verdadero nombre de Batman es Bruce Wayne?", "SÍ"},
                {"¿Darth Vader dice 'Luke, yo soy tu padre'?", "NO"},
                {"¿Sonic es un erizo?", "SÍ"},
                {"¿Goku es de la Tierra?", "NO"},
                {"¿El anillo de El Señor de los Anillos fue forjado por Sauron?", "SÍ"},
                {"¿Gandalf es un humano?", "NO"},
                {"¿Iron Man se llama Tony Stark?", "SÍ"},
                {"¿Thanos quería destruir todo el universo?", "NO"},
                // === CUERPO HUMANO ===
                {"¿Los humanos tienen 206 huesos?", "SÍ"},
                {"¿La sangre es azul dentro del cuerpo?", "NO"},
                {"¿El estómago produce un ácido nuevo cada 3-4 días?", "SÍ"},
                {"¿Los bebés nacen con más huesos que los adultos?", "SÍ"},
                {"¿El hígado es el órgano interno más grande?", "SÍ"},
                {"¿Puedes estornudar con los ojos abiertos?", "SÍ"},
                {"¿Las uñas siguen creciendo después de morir?", "NO"},
                {"¿El cuerpo humano tiene más bacterias que células propias?", "SÍ"},
                {"¿Cortarte el pelo lo hace crecer más rápido?", "NO"},
                {"¿Los músculos más fuertes del cuerpo son los de la mandíbula?", "SÍ"},
                // === RANDOM / CURIOSIDADES ===
                {"¿Hay más estrellas en el universo que granos de arena en la Tierra?", "SÍ"},
                {"¿Un día en Venus dura más que un año en Venus?", "SÍ"},
                {"¿Las bananas son berries pero las fresas no?", "SÍ"},
                {"¿Oxford es más vieja que el imperio Azteca?", "SÍ"},
                {"¿Los rayos pueden caer dos veces en el mismo lugar?", "SÍ"},
                {"¿El papel se puede doblar más de 7 veces?", "SÍ"},
                {"¿Hay más gente viva hoy que muerta en toda la historia?", "NO"},
                {"¿Un segundo tiene 1000 milisegundos?", "SÍ"},
                {"¿El número Pi tiene fin?", "NO"},
                {"¿El color naranja se llama así por la fruta?", "SÍ"},
                {"¿Los semáforos existían antes que los coches?", "SÍ"},
                {"¿El WiFi usa ondas de radio?", "SÍ"},
        };

        List<String[]> available = new ArrayList<>(Arrays.asList(fallback));
        available.removeIf(q -> usedQuestions.contains(q[0]));
        if (available.isEmpty()) {
            available = new ArrayList<>(Arrays.asList(fallback));
        }
        Collections.shuffle(available);
        String[] chosen = available.get(0);
        return new QuestionData(chosen[0], chosen[1].contains("SÍ") || chosen[1].contains("SI"));
    }

    private static class QuestionData {
        final String question;
        final boolean answer;

        QuestionData(String question, boolean answer) {
            this.question = question;
            this.answer = answer;
        }
    }
}
