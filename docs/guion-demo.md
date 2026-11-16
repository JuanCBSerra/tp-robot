# Guion del video de demostración y evidencias

Duración objetivo: **5–7 minutos**. Velocidad de simulación: **x10** (se cambia en el panel 🧪).

Para grabar: el grabador del emulador de Android Studio (⋮ → Record and Playback) o

```bash
adb shell screenrecord /sdcard/demo.mp4
```

Antes de cada escenario: **🧪 Panel de simulación → elegir escenario** (reinicia el robot en el galpón) → volver → **Control → Iniciar**.

## 0. Arranque (30 s)
1. Abrir la app sin internet (modo avión) → selección de robot. Mostrar que **Ovejero-02** está en mantenimiento y **Ovejero-03** fuera de alcance (estado deshabilitado).
2. Conectar **Ovejero-01** → Panel de supervisión.

## 1. Misión normal (1 min)
- Escenario "1 · Misión normal", x10, **Arreo al corral**.
- Mostrar: orden *pendiente → confirmada*, el robot moviéndose en el mapa, progreso, animales contados, en el corral.
- Al final: **45/45** en el corral, misión *Completada*, vuelve al galpón. Ir a **Historial → Misiones**.
- 📸 Evidencia: captura del panel con 45/45 y del historial.

## 2. Batería baja (1 min)
- Escenario "2 · Batería baja" (arranca con 34 %).
- Mostrar: alerta *Batería baja* al 25 % (chip naranja), *Batería crítica* al 15 % (rojo), "Reanudar" deshabilitado con su motivo.
- Acción disponible: **Volver al galpón** → confirmada → el robot vuelve. (Si no se hace nada, al 10 % vuelve solo).
- 📸 Evidencia: alerta + botón con motivo + orden confirmada.

## 3. Desconexión (1 min)
- Escenario "3 · Desconexión". Al llegar al Potrero 3 se corta la señal ~12 s.
- Mostrar: banner rojo "Sin señal", datos en gris "dato viejo", "Última actualización hace X s", botones bloqueados.
- Mantener presionada la **detención** durante el corte → queda *pendiente* → a los 5 s *sin respuesta*.
- Al volver la señal: banner desaparece, datos al día, evento "Señal recuperada" en el historial.
- (Alternativa manual: 🧪 switch "Cortar el enlace").
- 📸 Evidencia: banner + orden sin respuesta + eventos de conexión.

## 4. Incidente: alambrado caído (1 min)
- Escenario "4 · Alambrado caído". En el Potrero 4 el robot se frena.
- Mostrar: tarjeta roja **"El robot espera tu decisión"**, "Rodear y seguir" deshabilitado hasta reconocer.
- Reconocer → elegir **Rodear y seguir** (o Volver / Cancelar).
- 📸 Evidencia: tarjeta de decisión antes y después de reconocer.

## 5. Escenario propio: se viene una tormenta (1 min)
- Escenario "5 · Se viene una tormenta". En el Potrero 3 llega la alerta y faltan 3 vacas en el monte.
- Decisión: **Volver al galpón** (el Go2 no es impermeable). La misión queda *Abortada* con 24/45 contados.
- Alternativa: "Seguir buscando" → termina con 42/45 y alerta *Faltan animales*.
- 📸 Evidencia: alerta + historial con la misión abortada.

## Extras (si sobra tiempo)
- 🧪 "Rechazar la próxima orden" → mandar Pausar → *rechazada* con motivo.
- 🧪 "Persona cerca del robot" → alerta + la velocidad baja a 0,4 m/s.
- 🧪 "Últimos mensajes JSON" → mostrar el contrato en vivo.
