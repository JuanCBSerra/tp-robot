# Caso de uso — "El Ovejero": Go2 para recorrida y arreo en una chacra de Misiones

> Caso ficticio armado para el TP. Los nombres, números y el lugar son inventados.

## 1. Problema

**Chacra "La Colorada"**, a 12 km de Oberá (Misiones): **40 ha** divididas en **5 potreros**, con monte nativo, una pendiente fuerte hacia el arroyo y tierra colorada que se vuelve barro con cada lluvia. Tiene **45 vacas Braford** (la raza más común en la zona por su tolerancia al calor).

**Cómo se hace hoy:** todos los días, al atardecer, Don Ramón (el productor, 63 años) o un peón recorre los potreros a caballo para **contar la hacienda** y **llevarla al corral**. Lleva **unas 2 h**. Los problemas:

- Con el calor del verano (35–40 °C) y en días de lluvia, la recorrida se posterga o se hace mal.
- Si falta un animal, buscarlo en el monte lleva horas más.
- El peón trabaja solo medio día, y Don Ramón ya no quiere hacer la recorrida completa.

## 2. Solución propuesta

Un **Unitree Go2 EDU** hace la **recorrida diaria**. Recorre los potreros por un camino fijo, informa cuántos animales vio y los lleva hacia el corral caminando detrás de ellos. Don Ramón **supervisa desde el celular** en la casa y solo interviene cuando la app le pide una decisión.

### ¿Por qué Go2 y no otra cosa?

| Alternativa | Por qué no alcanza |
|---|---|
| Seguir como hoy (persona a caballo) | Depende de que haya alguien, del clima y del cansancio. Es la línea de base. |
| Robot con ruedas / cuatriciclo autónomo | Se encaja en el barro y no sube la pendiente al arroyo. |
| Dron | 20–30 min de batería, no arrea (solo mira) y molesta al ganado por el ruido. |
| Collares GPS en cada vaca | Dicen dónde está cada animal, pero no lo llevan al corral. Se pueden combinar a futuro. |
| **Go2 (cuadrúpedo)** | **Camina en barro, pasto alto y pendiente. Tiene 2–4 h de batería, alcanza para la recorrida. Su presencia física sirve para arrear.** |

¿Por qué no G1? Un humanoide es más lento, menos estable en terreno irregular y bastante más caro. Las manos no aportan nada en esta tarea.

## 3. Actores

| Actor | Rol | Qué necesita |
|---|---|---|
| **Don Ramón** (operador) | Supervisa desde la app, decide ante incidentes | Información clara, letra grande, pocas acciones, alertas que no se pierdan |
| **Peón** (persona en el campo) | A veces está en los potreros | Que el robot lo vea y frene; saber qué está haciendo el robot |
| **Vecinos / chicos** | Ocasionales | Que el robot no sea peligroso; privacidad |
| **Hacienda** | — | No ser estresada (velocidad baja cerca de los animales) |

## 4. Recorrido (esquema)

```
            [Monte nativo]
   ┌──────────┬──────────┬──────────┐
   │ Potrero 3│ Potrero 4│ Potrero 5│
   │ (arroyo, │          │          │
   │ pendiente)          │          │
   ├──────────┼──────────┼──────────┤
   │ Potrero 2│ Potrero 1│  CORRAL  │
   │          │          │    🐄    │
   └──────────┴────┬─────┴──────────┘
                   │
           [GALPÓN / BASE DE CARGA]  ← punto seguro
                   │
               [CASA] 📱 Don Ramón
```

**Ruta de la misión:** Galpón → P1 → P2 → P3 → P4 → P5 → Corral → Galpón.
Cada potrero es un **punto de control**: el robot llega, "cuenta" y sigue.

## 5. Misiones

| Misión | Qué hace | Duración simulada x1 | x10 (demo) |
|---|---|---|---|
| **Recuento** | Recorre los potreros a 1,5 m/s y reporta cuántos animales vio en cada uno | ~11 min | ~1 min |
| **Arreo al corral** | Recorre y además lleva la hacienda al corral (a 1 m/s mientras arrea) | ~16 min | ~1,5 min |

El recorrido mide ~960 m (plano de 400 × 400 m). En cada potrero el robot se detiene 8 s a "contar".
En la app, el reloj de la simulación se puede poner en x1, x5 o x10 desde el panel de simulación.

## 6. Telemetría

**Obligatoria (consigna):** conectividad, batería, estado operativo, misión/progreso, ubicación (potrero actual en el mapa), movimiento (velocidad y si está caminando, detenido o arreando), alertas y última actualización.

**Propias del servicio:**
1. 🐄 **Animales contados / esperados** (ej. `42 / 45`).
2. 🐄 **Animales en el corral** (ej. `30 / 45`), el avance real del arreo.
3. 🌡️ **Temperatura ambiente**. Se marca en naranja desde 34 °C y en rojo desde 38 °C (estrés térmico de la hacienda y del robot).

## 7. Comandos

| Comando | Habilitado cuando… | Confirmación extra |
|---|---|---|
| Iniciar misión | Conectado, sin misión activa, batería ≥ 30 % | — |
| Pausar | Misión en curso | — |
| Reanudar | Misión pausada y batería ≥ 15 % | — |
| Cancelar misión | Misión en curso o pausada | Sí (diálogo) |
| Volver al galpón | Robot fuera del galpón | — |
| Detención operativa | Robot en movimiento | Sí (mantener presionado) |
| Reconocer alerta | Hay alertas sin reconocer | — |

Cada comando muestra su estado: **pendiente → confirmada / rechazada / sin respuesta** (timeout de 5 s).
Sin conexión, todos los comandos quedan deshabilitados salvo la detención operativa, que se intenta igual (puede quedar "sin respuesta").

> La detención de la app es un comando de software simulado y **no reemplaza una parada de emergencia física**. Una orden enviada no equivale a una orden ejecutada.

## 8. Escenarios a demostrar

1. **Misión normal:** arreo completo, 45/45 en el corral, vuelta al galpón.
2. **Batería baja:** cae al 20 %, aparece una alerta y se ofrece "Volver al galpón".
3. **Desconexión:** se corta el WiFi en el monte (P3); los datos aparecen en gris como "desactualizados hace X s", los comandos quedan sin respuesta, y al reconectar se resincroniza.
4. **Incidente:** **alambrado caído** en el P4. El robot se detiene y pide decisión: rodear, esperar o volver.
5. **Escenario propio — se viene una tormenta ⛈️:** alerta de lluvia y quedan 3 animales sin encontrar. El Go2 no es impermeable: el operador elige entre terminar rápido o volver ya.

## 9. Riesgos y medidas

| # | Riesgo | Medida |
|---|---|---|
| 1 | Persona cerca del robot (peón, chicos) | El robot reporta "persona cerca" y baja la velocidad; alerta en la app |
| 2 | Orden accidental desde el celular | Confirmación en Cancelar; mantener presionado en Detención |
| 3 | Pérdida de conexión en el monte | Datos marcados como viejos, comandos bloqueados, el robot sigue la misión o vuelve solo si pasan más de 2 min |
| 4 | Estrés de la hacienda | Velocidad máxima limitada mientras arrea (1 m/s); aviso de estrés térmico con más de 34 °C |
| 5 | Privacidad (la cámara filma lindes con vecinos) | La app no guarda video; el historial solo guarda eventos y números |

## 10. Alcance

**Dentro del alcance (lo que hacemos):**
- App Android de supervisión y control (HMI) con un **robot simulado**.
- El simulador **inventa** los datos: conteos, posiciones, alertas, desconexiones y rechazos, con reglas simples y escenarios guionados.
- Historial local de misiones y eventos.

**Fuera del alcance (se menciona como trabajo futuro):**
- **Visión por computadora** para detectar y contar animales, personas o alambrados.
- **Algoritmo de arreo** (cómo moverse para empujar al rodeo).
- **Navegación autónoma real** y mapeo del campo.
- Integración con el SDK real de Unitree (el contrato JSON es una propuesta nuestra, **no es el protocolo oficial de Unitree**).
- Cobertura de red en el campo (se asume WiFi/4G con cortes).

Estas capacidades **no vienen** con el Go2 de fábrica y requerirían desarrollo adicional. La app solo **muestra lo que el robot reporta** y **envía órdenes**.
