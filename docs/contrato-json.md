# Contrato de mensajes (JSON) — propuesta del grupo

> ⚠️ **Este contrato es una propuesta nuestra para el TP. NO es el protocolo oficial de Unitree.**
> El Go2 real se programa con su SDK (DDS / ROS 2); acá definimos qué intercambiaría *nuestra app*
> con un "puente" del robot. En el APK lo implementa un robot **simulado** (`RobotSimulator`).

- Versión: `1.0` (campo `protocol_version` en la telemetría).
- Transporte: no se define (en un caso real: WebSocket sobre WiFi/4G). En la app es un `Flow<String>` en memoria.
- Tiempos: `timestamp` en **epoch milisegundos**.
- Posición: `x`, `y` **normalizadas 0..1** sobre el plano de la chacra (x → este, y → sur).
- Códigos en inglés (`ON_MISSION`, `HERDING`…); la app los traduce a su dominio en castellano (mappers).

| Mensaje | Dirección | Cuándo |
|---|---|---|
| `telemetry` | robot → app | cada ~500 ms mientras hay enlace |
| `alert` | robot → app | cuando pasa algo (se encolan si no hay enlace y se mandan al volver) |
| `command` | app → robot | cuando el operador da una orden |
| `command_ack` | robot → app | respuesta a cada orden (`ACCEPTED` / `REJECTED`) |

Si una orden no recibe `command_ack` en **5 s**, la app la marca **sin respuesta**: no sabemos si se ejecutó.

---

## 1. Telemetría

```json
{
  "type": "telemetry",
  "protocol_version": "1.0",
  "robot_id": "OVJ-01",
  "seq": 812,
  "timestamp": 1763320000000,
  "state": "ON_MISSION",
  "battery": { "percent": 63.4, "charging": false },
  "mission": {
    "mission_id": "MIS-1611-183012",
    "kind": "HERDING",
    "status": "RUNNING",
    "progress": 0.415,
    "checkpoints_reached": 3,
    "checkpoints_total": 7,
    "started_at": 1763319400000
  },
  "position": { "x": 0.312, "y": 0.22, "zone": "Potrero 4", "heading_deg": 90 },
  "motion": { "mode": "HERDING", "speed_mps": 1.0 },
  "service": {
    "heads_counted": 27,
    "heads_expected": 45,
    "heads_in_corral": 0,
    "temperature_c": 31.8
  },
  "link": { "signal_percent": 54 }
}
```

| Campo | Valores |
|---|---|
| `state` | `DOCKED` (en el galpón), `ON_MISSION`, `PAUSED`, `AWAITING_DECISION`, `RETURNING`, `STOPPED` |
| `mission.kind` | `HEAD_COUNT` (recuento), `HERDING` (arreo al corral) |
| `mission.status` | `RUNNING`, `PAUSED`, `COMPLETED`, `CANCELLED`, `ABORTED` |
| `mission.progress` | fracción 0.0 – 1.0 del recorrido |
| `motion.mode` | `IDLE`, `WALKING`, `HERDING` |
| `service.*` | variables propias del servicio: animales contados, esperados, en el corral y temperatura |
| `mission` | `null` si nunca hubo misión; si terminó, queda con su estado final hasta la próxima |

## 2. Alerta

```json
{
  "type": "alert",
  "alert_id": "AL-004",
  "robot_id": "OVJ-01",
  "timestamp": 1763320100000,
  "code": "OBSTACLE",
  "severity": "CRITICAL",
  "message": "Alambrado caído entre el Potrero 4 y el monte. El robot se detuvo: ¿rodear, esperar o volver?",
  "zone": "Potrero 4",
  "requires_decision": true
}
```

| `code` | `severity` | ¿Pide decisión? | Qué hace el robot |
|---|---|---|---|
| `LOW_BATTERY` | `WARNING` | no | avisa al 25 % |
| `CRITICAL_BATTERY` | `CRITICAL` | no | avisa al 15 % |
| `AUTO_RETURN` | `CRITICAL` / `WARNING` | no | vuelve solo al galpón (10 % de batería o +2 min sin enlace) |
| `OBSTACLE` | `CRITICAL` | **sí** | se detiene y espera (`AWAITING_DECISION`) |
| `STORM` | `CRITICAL` | **sí** | se detiene y espera (`AWAITING_DECISION`) |
| `PERSON_NEARBY` | `WARNING` | no | baja la velocidad a 0,4 m/s por 20 s |
| `MISSING_ANIMALS` | `WARNING` | no | aviso al terminar si contó menos de lo esperado |

## 3. Orden (comando)

```json
{
  "type": "command",
  "command_id": "CMD-0007",
  "robot_id": "OVJ-01",
  "timestamp": 1763320105000,
  "command": "START_MISSION",
  "params": { "mission_kind": "HERDING" }
}
```

```json
{
  "type": "command",
  "command_id": "CMD-0008",
  "robot_id": "OVJ-01",
  "timestamp": 1763320110000,
  "command": "ACK_ALERT",
  "params": { "alert_id": "AL-004" }
}
```

| `command` | `params` | La app lo habilita cuando… | El robot lo rechaza si… |
|---|---|---|---|
| `START_MISSION` | `mission_kind` | conectado, en el galpón, sin misión activa, batería ≥ 30 % | misma regla (valida de nuevo) |
| `PAUSE_MISSION` | — | misión en curso | no hay misión en marcha |
| `RESUME_MISSION` | — | misión pausada, alertas de decisión reconocidas, batería ≥ 15 % | ídem |
| `CANCEL_MISSION` | — | misión en curso o pausada | no hay misión |
| `RETURN_TO_BASE` | — | fuera del galpón y no volviendo | ya está en el galpón o volviendo |
| `OPERATIONAL_STOP` | — | robot en movimiento (**se permite aun sin señal**) | ya está quieto en el galpón |
| `ACK_ALERT` | `alert_id` | hay alertas sin reconocer | la alerta no existe o ya estaba reconocida |

## 4. Respuesta a una orden

Confirmada:

```json
{
  "type": "command_ack",
  "command_id": "CMD-0007",
  "robot_id": "OVJ-01",
  "timestamp": 1763320105800,
  "status": "ACCEPTED",
  "reason": null
}
```

Rechazada:

```json
{
  "type": "command_ack",
  "command_id": "CMD-0009",
  "robot_id": "OVJ-01",
  "timestamp": 1763320200700,
  "status": "REJECTED",
  "reason": "Batería insuficiente (24 %). Mínimo 30 %"
}
```

Estados de una orden en la app:

```
PENDIENTE ──ack ACCEPTED──▶ CONFIRMADA
    │
    ├────ack REJECTED──▶ RECHAZADA (con motivo)
    │
    └────5 s sin ack──▶ SIN RESPUESTA  (si después llega un ack, se registra como "respuesta tardía")
```

## 5. Flota (selección de robot)

```json
{
  "robots": [
    { "robot_id": "OVJ-01", "name": "Ovejero-01", "model": "Unitree Go2 EDU",
      "location": "Chacra La Colorada · Galpón", "status": "AVAILABLE", "battery_percent": 86.0, "note": null },
    { "robot_id": "OVJ-02", "name": "Ovejero-02", "model": "Unitree Go2 EDU",
      "location": "Taller en Oberá", "status": "MAINTENANCE", "battery_percent": null,
      "note": "Cambio del motor de la pata trasera izquierda" },
    { "robot_id": "OVJ-03", "name": "Ovejero-03", "model": "Unitree Go2 Air",
      "location": "Chacra del vecino (préstamo)", "status": "OUT_OF_RANGE", "battery_percent": 41.0,
      "note": "Sin señal desde hace 2 días" }
  ]
}
```

> En la app, el **Panel de simulación** muestra en vivo el último `command`, `command_ack`, `alert` y `telemetry`
> (sirve como evidencia para la entrega).
