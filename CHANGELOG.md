# CoreProtect - Changelog v2.0

## 🏰 MEGA UPDATE: Sistema de Núcleos v2.0

---

### 🖱️ Panel de Gestión (Click Derecho al Núcleo)
- Click derecho al núcleo abre un **panel interactivo** completo
- Vista de información del núcleo: nivel, área, miembros, dueño, mejoras activas
- Acceso directo a mejoras, miembros y tienda de upgrades
- Botón para visualizar/ocultar los bordes de tu zona con partículas
- Botón de cierre

### ⬆️ Mejora de Nivel desde el Panel
- Mejora tu núcleo directamente desde el GUI sin comprar uno nuevo
- El coste es la **diferencia** entre el nivel actual y el siguiente
- La zona se expande **inmediatamente** al pagar

### 🛒 Tienda de Mejoras de Zona (8 upgrades)
| Mejora | Precio | Efecto |
|--------|--------|--------|
| **Anti-Explosión** | $50,000 | Sin explosiones en tu zona |
| **Anti-PvP** | $75,000 | Zona de paz total |
| **Anti-Mobs** | $60,000 | Sin spawn de mobs hostiles |
| **Sin Caída** | $30,000 | Sin daño por caída |
| **Velocidad** | $40,000 | Velocidad de movimiento extra |
| **Auto-Curación** | $80,000 | Regeneración pasiva lenta |
| **Boost de Daño** | $100K/nv | +5% daño por nivel (máx 5) |
| **Boost de Vida** | $100K/nv | +2 corazones por nivel (máx 5) |

### 👥 Gestión de Miembros desde GUI
- Panel visual con cabezas de jugadores
- Indicador online/offline para cada miembro
- Invitar jugadores online con un click
- Expulsar miembros con un click
- Notificación automática al jugador invitado

### 🏠 Sistema de Home
- `/cores sethome` — Establece tu home en la zona donde estés (dueño o miembro)
- `/cores home` — Teletransportarte a tu núcleo
- Solo 1 home por jugador
- Se persiste entre reinicios del servidor

### 👁 Visualización de Zona
- Activa/desactiva la visualización de los bordes de tu zona
- Toggle desde el panel de gestión del núcleo

### 💾 Persistencia
- Todas las mejoras se guardan y cargan automáticamente
- Homes de jugadores persistentes
- Auto-guardado configurable

### 📋 Comandos Nuevos
| Comando | Descripción |
|---------|-------------|
| `/cores home` | Ir a tu núcleo |
| `/cores sethome` | Establecer home en tu zona |

### 🔧 Mejoras Técnicas
- `ProtectedRegion` ahora tiene nivel y tamaño mutables
- 8 flags de mejora con getters/setters
- DataManager guarda/carga upgrades y homes
- Mensajes en español con tildes y ñ correctas
- Todos los textos de GUI con acentos corregidos
