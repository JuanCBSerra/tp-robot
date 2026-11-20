# Registro de decisiones y limitaciones

## Decisiones

| # | Decisión | Por qué |
|---|---|---|
| D1 | **Go2 EDU** (cuadrúpedo) y no G1 | Terreno con barro, pendiente y pasto alto; 2–4 h de batería; la presencia física sirve para arrear. El G1 es más lento, menos estable en el campo y más caro, y sus manos no aportan a la tarea. |
| D2 | **Control por órdenes discretas**, sin joystick | El operador (productor rural) supervisa desde la casa; teleoperar en tiempo real por 4G no es realista ni necesario. La consigna lo permite. |
| D3 | Contrato **JSON** propio + **Moshi** | Mismo stack de la cátedra (DTO + `@Json` + mapper). El simulador produce y consume JSON real, así el contrato no es solo un documento: se ejerce en cada mensaje. |
| D4 | Simulador **dentro del APK** detrás de `RobotDataSource` | La app tiene que andar sin robot, servidor ni internet. Cambiar al robot real = otra implementación de la interfaz. |
| D5 | **Room** para el historial | Lo sugiere la consigna; devuelve `Flow` (la pantalla se actualiza sola) y valida las consultas al compilar. Guardamos eventos y un resumen por misión, no video (privacidad). |
| D6 | Telemetría "vieja" a los **3 s** sin datos | El robot manda cada 0,5 s: 6 mensajes perdidos seguidos ya es pérdida de enlace. |
| D7 | Timeout de órdenes de **5 s** → "sin respuesta" | Distingue "rechazada" (el robot dijo que no) de "sin respuesta" (no sabemos si se ejecutó). |
| D8 | Condiciones de habilitación en el **dominio** (`GetCommandAvailabilityUseCase`) | La UI las usa para deshabilitar botones y mostrar el motivo; los casos de uso las vuelven a chequear antes de mandar. Son funciones puras y tienen tests. |
| D9 | El **robot también valida** cada orden | Doble control: aunque la app se equivoque (o esté desactualizada), el robot puede rechazar. |
| D10 | Detención operativa **mantener presionado 1,5 s** y permitida **aun sin señal** | Evita toques accidentales (riesgo 2) y, ante una pérdida de señal, el operador puede al menos intentar frenar (puede quedar "sin respuesta"). |
| D11 | Para seguir después de un incidente hay que **reconocer la alerta primero** | Obliga a leer qué pasó antes de decidir (HRI). |
| D12 | Robot vuelve solo con **10 % de batería** o **+2 min sin enlace** | Medidas de seguridad autónomas para cuando el operador no puede intervenir. |
| D13 | Reloj de simulación **x1 / x5 / x10** | A x1 una misión de arreo dura ~16 min; a x10 ~1,5 min, ideal para el video. |
| D14 | `ApplicationScope` (CoroutineScope de la app) en data | El simulador y el repositorio tienen que seguir escuchando aunque cambie la pantalla. Los ViewModels siguen usando solo `viewModelScope`. Es lo único que no estaba en el proyecto de la cátedra. |
| D15 | Mapa dibujado con `Canvas` | Offline y sin API keys (Google Maps necesita internet). |
| D16 | **Batería siempre visible** (barra superior) y frente al mínimo en Control | Si iniciar exige ≥ 30 %, el operador tiene que ver ese dato en todo momento y no solo en una pantalla (visibilidad del estado del sistema). |
| D17 | **Confirmación** al cancelar la misión y al volver al galpón | Evita órdenes accidentales (riesgo 2) sin frenar las acciones urgentes: la detención sigue siendo "mantener presionado". |
| D18 | **Aviso de misión completada** | La misión termina sin que el operador esté mirando; la app avisa con el resultado (contados / esperados). |
| D19 | **La pantalla solo dibuja**: textos, tonos y umbrales de la UI los resuelve `StatusPresenter` desde el ViewModel | Es la regla de MVVM: los Composables no consultan reglas de negocio (`OperationRules`, `HeatStress`). Así esa lógica se prueba con tests JVM, sin emulador. |
| D20 | Tres umbrales de batería distintos | 30 % mínimo para iniciar, 25 % aviso de batería baja, 15 % mínimo para reanudar y alerta crítica. Se dejaron separados porque cumplen funciones distintas, pero hay que explicarlos en la demo. |

## Limitaciones (fuera del alcance)

- **No hay visión por computadora.** El conteo de animales sale de una tabla fija por potrero; en un robot real requeriría un modelo de detección (y probablemente una cámara térmica adicional).
- **No hay algoritmo de arreo.** El robot recorre una ruta fija; los animales "lo siguen" por regla del simulador.
- **No hay navegación autónoma real** ni mapeo: la ruta está precargada en `FieldMap.LA_COLORADA`.
- **No se integra el SDK de Unitree.** El contrato JSON es una propuesta nuestra, no el protocolo oficial.
- **Conectividad**: se asume WiFi/4G en el campo con cortes; no se modela la red real.
- La **detención** de la app es un comando de software simulado: **no reemplaza la parada de emergencia física**.
- Un solo robot operable a la vez (los otros de la flota se muestran pero no están disponibles).
- El historial guarda hasta los últimos 300 eventos en pantalla (en la base quedan todos hasta que se borra).

## Riesgos y medidas implementadas

| # | Riesgo | Medida en la app |
|---|---|---|
| 1 | Persona cerca del robot | Alerta `PERSON_NEARBY`; el robot baja a 0,4 m/s (se demuestra desde el panel de simulación). |
| 2 | Orden accidental | Confirmación para cancelar misión y desconectar; detención con "mantener presionado". |
| 3 | Pérdida de conexión | Banner rojo, datos en gris "dato viejo", órdenes bloqueadas (salvo detención), "sin respuesta" a los 5 s, regreso autónomo a los 2 min. |
| 4 | Estrés de la hacienda | Velocidad de arreo limitada a 1 m/s. |
| 5 | Privacidad | La app no guarda imágenes ni video; el historial solo tiene números y eventos. |
