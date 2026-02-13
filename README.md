# 🛡️ WardStone - Sistema de Núcleos de Protección

Plugin de Minecraft (Spigot/Paper 1.21.8+) que permite a los jugadores proteger sus construcciones mediante **núcleos** con 20 niveles de progresión, mejoras comprables, gestión de miembros desde GUI, animaciones épicas y mucho más.

---

## ✨ Características Principales

### 🛡️ Sistema de Núcleos
- **20 niveles de núcleos** — desde Roble ($1K) hasta Estrella del Nether ($2.5M), cada uno con mayor área de protección
- **Panel de gestión interactivo** — click derecho al núcleo para abrir un GUI completo
- **Mejora de nivel desde el panel** — pagas solo la diferencia entre niveles
- **Animación épica de mejora** — 20 segundos de espectáculo con partículas, levitación y giros
- **8 mejoras comprables** — anti-explosión, anti-PvP, velocidad, regeneración, vida extra, anti-mobs, anti-caída, boost de daño
- **Gestión de miembros por GUI** — invita/expulsa jugadores con un click, indicador online/offline
- **Sistema de home** — `/cores sethome` y `/cores home` para teletransportarte a tu núcleo
- **Visualización de zona** — activa/desactiva partículas que muestran los bordes de tu zona
- **Protección completa** — bloques, contenedores, explosiones, PvP, todo protegido desde Y mínima hasta Y máxima
- **Las mejoras se conservan al mover el núcleo** — rompe y recoloca sin perder nada
- **Persistencia total** — datos guardados en YAML con auto-guardado configurable

### ⚔️ Sistema RPG Completo
- **5 Clases RPG** — Guerrero, Mago, Arquero, Pícaro, Paladín
- **15 Subclases** — 3 por clase con habilidades únicas
- **45 Habilidades Activas** — efectos visuales, sonidos, cooldowns, coste de maná
- **Sistema de Stats** — STR/DEX/INT/VIT/WIS/LUK que afectan combate y progresión
- **100 Niveles** — XP por mobs, level-ups con efectos especiales
- **World Bosses Semanales** — Dragon Lord, Lich King, Demon Lord con loot legendario
- **GUIs RPG** — selección de clase, asignación de stats, árbol de habilidades
- **HUD de Maná/XP** — display en pantalla con barras visuales
- **Sistema de Oleadas** — defensa de núcleos contra mobs cada 45 minutos
- **Web Dashboard** — Next.js con vinculación de cuenta por código de 6 dígitos vía RCON

---

## 📋 Requisitos

- **Minecraft** 1.21+ (Spigot o Paper)
- **Java** 21+
- **Vault** (plugin de economía)
- Un plugin de economía compatible con Vault (EssentialsX, CMI, etc.)

---

## 🎮 Comandos

### 🛡️ Comandos de Núcleos
| Comando | Alias | Descripción |
|---------|-------|-------------|
| `/cores` | `/core`, `/nucleo` | Abrir la tienda de núcleos |
| `/cores info` | | Ver tus protecciones activas |
| `/cores remove` | `/cores eliminar` | Eliminar la protección donde estás |
| `/cores add <jugador>` | `/cores agregar` | Agregar miembro a la protección |
| `/cores kick <jugador>` | `/cores expulsar` | Eliminar miembro de la protección |
| `/cores sethome` | `/cores setcasa` | Establecer home en la zona donde estás |
| `/cores home` | `/cores casa` | Teletransportarte a tu núcleo |
| `/cores help` | `/cores ayuda` | Ver ayuda de comandos |
| `/admincore deleteprotection` | | Eliminar cualquier protección (admin) |

### ⚔️ Comandos RPG
| Comando | Descripción |
|---------|-------------|
| `/rpg` | Ver tu perfil RPG (clase, nivel, stats) |
| `/class` | Abrir GUI para seleccionar/clase y subclase |
| `/stats` | Abrir GUI para asignar puntos de stats |
| `/skills` | Abrir GUI del árbol de habilidades |
| `/skill <nombre>` | Usar una habilidad activa |
| `/linkmc` | Generar código de 6 dígitos para vincular cuenta web |

---

## 🔑 Permisos

| Permiso | Descripción | Default |
|---------|-------------|---------|
| `coreprotect.use` | Usar el sistema de núcleos | `true` |
| `coreprotect.admin` | Bypass de protecciones + comandos admin | `op` |

---

## 🏗️ Niveles de Núcleos

| Nivel | Material | Precio | Área |
|:-----:|----------|-------:|:----:|
| 1 | Roble | $1,000 | 10×10 |
| 2 | Abedul | $2,500 | 12×12 |
| 3 | Piedra | $5,000 | 14×14 |
| 4 | Piedra Lisa | $8,000 | 16×16 |
| 5 | Carbón | $12,000 | 18×18 |
| 6 | Hierro | $20,000 | 22×22 |
| 7 | Cobre | $30,000 | 26×26 |
| 8 | Oro | $45,000 | 30×30 |
| 9 | Lapislázuli | $65,000 | 35×35 |
| 10 | Redstone | $90,000 | 40×40 |
| 11 | Amatista | $120,000 | 46×46 |
| 12 | Esmeralda | $160,000 | 52×52 |
| 13 | Diamante | $220,000 | 60×60 |
| 14 | Obsidiana | $300,000 | 70×70 |
| 15 | Cuarzo | $400,000 | 80×80 |
| 16 | Prismarina | $550,000 | 92×92 |
| 17 | Purpur | $750,000 | 106×106 |
| 18 | Obsidiana Llorosa | $1,000,000 | 120×120 |
| 19 | Netherite | $1,500,000 | 140×140 |
| 20 | Núcleo Estelar | $2,500,000 | 160×160 |

> Al mejorar de nivel, solo pagas la **diferencia** entre el precio actual y el siguiente.

---

## 🛒 Mejoras de Zona

Comprables desde el panel de gestión del núcleo (click derecho al bloque del núcleo → Tienda de Mejoras):

| Mejora | Precio | Efecto |
|--------|-------:|--------|
| Anti-Explosión | $50,000 | Las explosiones no destruyen bloques ni hacen daño en tu zona |
| Anti-PvP | $75,000 | Zona de paz: nadie puede atacar a otros jugadores |
| Anti-Mobs | $60,000 | No spawnean mobs hostiles en tu zona |
| Sin Caída | $30,000 | Sin daño por caída dentro de la zona |
| Velocidad | $40,000 | Boost de velocidad al estar en tu zona |
| Auto-Curación | $80,000 | Regeneración pasiva para miembros en la zona |
| Boost de Daño | $100,000/nv | +5% de daño por nivel (máximo 5 niveles) |
| Boost de Vida | $100,000/nv | +2 corazones por nivel (máximo 5 niveles) |

> Las mejoras se **conservan** aunque rompas y recoloques el núcleo.

---

## 🖱️ Panel de Gestión

Al hacer **click derecho** en tu núcleo se abre un panel con:

- **Información del núcleo** — nivel, área, dueño, miembros, mejoras activas
- **Botón de mejora** — sube de nivel pagando solo la diferencia
- **Gestión de miembros** — ve quién está online/offline, invita o expulsa con un click
- **Tienda de mejoras** — compra las 8 mejoras disponibles
- **Visualizar zona** — activa/desactiva partículas rojas en los bordes

---

## 🔧 Compilación

### Opción 1: Con Maven
```bash
mvn clean package
```

### Opción 2: Script de compilación (sin Maven)

1. Descarga las dependencias en la carpeta `libs/`:
   - **Spigot API 1.21**: descarga desde [SpigotMC Nexus](https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/) → guardar como `libs/paper-api.jar`
   - **Vault API**: descarga desde [JitPack](https://jitpack.io/com/github/MilkBowl/VaultAPI/1.7.1/VaultAPI-1.7.1.jar) → guardar como `libs/vault-api.jar`

2. Ejecuta:
```powershell
powershell -ExecutionPolicy Bypass -File build.ps1
```

3. El JAR se genera en `build/CoreProtect-1.0.0.jar`

---

## 🌐 Web Dashboard

El plugin incluye un **dashboard web moderno** desarrollado en Next.js 14:

### Características
- **Landing page** con animaciones Framer Motion, partículas y gradientes
- **Sistema de vinculación** por código de 6 dígitos con validación RCON
- **Diseño responsive** con TailwindCSS y tema oscuro
- **Animaciones ultra-mega guapas** como solicitaste ✨

### Configuración
1. Ve a la carpeta `web/`
2. Copia `.env.local.example` a `.env.local` y configura:
   ```env
   RCON_HOST=54.36.173.237
   RCON_PORT=25575
   RCON_PASSWORD=D34X5X75lum9GHd
   ```
3. Instala dependencias: `npm install`
4. Construye: `npm run build`
5. Inicia: `npm start` (o despliega en Vercel/Netlify)

### Flujo de Vinculación
1. Jugador ejecuta `/linkmc` en el servidor
2. Copia el código de 6 dígitos
3. Pega el código en la web
4. La web valida via RCON y confirma la vinculación

---

## 📦 Instalación

### Plugin
1. Coloca `CoreProtect-1.0.0.jar` en la carpeta `plugins/` de tu servidor
2. Asegúrate de tener **Vault** y un plugin de economía instalados
3. Reinicia el servidor
4. Los archivos `config.yml` y `messages.yml` se generan automáticamente

### Web Dashboard (Opcional)
1. Ve a la carpeta `web/`
2. Configura `.env.local` con tus credenciales RCON
3. Ejecuta `npm install && npm run build`
4. Despliega donde prefieras (Vercel, Netlify, o servidor propio)

---

## ⚙️ Configuración

### `config.yml`
- **Ajustes generales**: duración de animación, permitir romper núcleos, intervalo de auto-guardado
- **Niveles**: material, nombre, precio y tamaño de cada nivel (totalmente personalizable)

### `messages.yml`
- Todos los mensajes del plugin en español, con soporte de colores (`&a`, `&c`, etc.) y placeholders (`{player}`, `{level}`, etc.)

---

## 📁 Estructura del Proyecto

```
src/main/java/com/moonlight/coreprotect/
├── CoreProtectPlugin.java          # Clase principal del plugin
├── commands/
│   ├── CoreCommand.java            # /cores (tienda, info, home, miembros...)
│   ├── AdminCommand.java           # /admincore (administración)
│   ├── RPGCommand.java             # Comandos RPG (/rpg, /class, /stats, /skills, /skill, /linkmc)
│   └── LinkValidateCommand.java    # Validación RCON para web
├── core/
│   ├── CoreLevel.java              # Definición de niveles de núcleo
│   └── ProtectedRegion.java        # Región protegida con mejoras
├── data/
│   └── DataManager.java            # Persistencia en data.yml
├── effects/
│   ├── CoreAnimation.java          # Animaciones (colocación, mejora, rotura)
│   └── SoundManager.java           # Efectos de sonido
├── gui/
│   ├── ShopGUI.java                # Tienda de núcleos
│   ├── CoreManagementGUI.java      # Panel de gestión del núcleo
│   ├── CoreUpgradesShopGUI.java    # Tienda de mejoras de zona
│   ├── CoreMembersGUI.java         # Gestión de miembros
│   └── GUIListener.java            # Eventos de todos los GUIs
├── protection/
│   ├── ProtectionManager.java      # Gestión de regiones y tareas
│   ├── ProtectionListener.java     # Protección de bloques y eventos
│   └── CorePlaceListener.java      # Colocación de núcleos
├── rpg/
│   ├── RPGClass.java               # Enum de 5 clases base
│   ├── RPGSubclass.java            # Enum de 15 subclases
│   ├── RPGStats.java               # Sistema de stats (STR/DEX/INT/VIT/WIS/LUK)
│   ├── RPGPlayer.java              # Datos RPG del jugador
│   ├── RPGManager.java             # Gestión de datos, XP, linking
│   ├── RPGCombatListener.java      # Integración combate + stats
│   ├── RPGHudListener.java         # HUD de maná/XP
│   ├── abilities/
│   │   ├── Ability.java            # Clase base de habilidades
│   │   └── AbilityRegistry.java    # Registro de 45 habilidades
│   ├── gui/
│   │   ├── ClassSelectionGUI.java  # GUI selección clase/subclase
│   │   ├── StatsGUI.java           # GUI asignación stats
│   │   ├── SkillTreeGUI.java       # GUI árbol de habilidades
│   │   └── RPGGUIListener.java     # Eventos GUIs RPG
│   └── bosses/
│       └── BossManager.java        # Sistema de world bosses semanales
├── raids/
│   └── RaidManager.java            # Sistema de oleadas de mobs
└── utils/
    └── MessageManager.java         # Sistema de mensajes

web/                                 # Dashboard Next.js
├── pages/
│   ├── index.js                    # Landing page con animaciones
│   ├── link.js                     # Página de vinculación 6 dígitos
│   └── api/
│       └── link.js                 # API RCON para validación
├── styles/
│   └── globals.css                 # CSS con animaciones y tema
├── package.json                    # Dependencias Next.js + Tailwind
└── .env.local                      # Credenciales RCON (gitignore)
```

---

## 🤝 Créditos

Desarrollado por **MoonlightMC** — [moonlightmc.xyz](https://moonlightmc.xyz)

---

## 📄 Licencia

Este proyecto es de uso privado para el servidor MoonlightMC. Todos los derechos reservados.
