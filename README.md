# 🐕‍🦺 El Ovejero — HMI para un Unitree Go2 simulado

Trabajo práctico grupal · **Desarrollo de Aplicaciones I** · UADE.
Interfaz de operación y supervisión (HMI/HRI) para un **Unitree Go2 EDU** que recorre los potreros de una chacra
en Misiones, **cuenta la hacienda y la lleva al corral**. El robot está **simulado dentro del APK**:
no hace falta hardware, servidor ni internet.

- 📄 Consigna: [docs/TP-HMI-Ottoman.pdf](docs/TP-HMI-Ottoman.pdf)
- 🐄 Caso de uso, actores, recorrido, riesgos: [docs/caso-de-uso.md](docs/caso-de-uso.md)
- 🏛️ Arquitectura (diagramas): [docs/arquitectura.md](docs/arquitectura.md)
- 📨 Contrato JSON (telemetría, alertas, órdenes, respuestas): [docs/contrato-json.md](docs/contrato-json.md)
- 🧭 Decisiones y limitaciones: [docs/decisiones-y-limitaciones.md](docs/decisiones-y-limitaciones.md)
- 🎬 Guion del video y evidencias de los 5 escenarios: [docs/guion-demo.md](docs/guion-demo.md)

## Stack

Kotlin · Jetpack Compose (Material 3) · MVVM · ViewModel + **StateFlow** / SharedFlow · Clean Architecture ·
Hilt (KSP) · Moshi (contrato JSON) · **Room** (historial) · Coroutines · Navigation Compose.
Mismas convenciones y versiones que el proyecto de la cátedra (PokeDex Academy).

## Pantallas

| Pantalla | Qué tiene |
|---|---|
| Selección de robot | Flota con estados disponible / mantenimiento / fuera de alcance |
| Panel (supervisión) | Mapa de la chacra, misión y progreso, telemetría completa + 3 variables del servicio, decisión pendiente, acciones rápidas |
| Control | Tipo de misión, las 7 órdenes con su condición de habilitación, registro de órdenes con estado |
| Alertas | Pendientes y reconocidas, reconocer, ir a decidir |
| Historial | Misiones y eventos guardados en Room |
| Desconexión | Banner de "sin señal" + datos marcados como viejos; desconectar con confirmación |
| 🧪 Simulación | Escenarios 1–6 (el 6 es una ola de calor), velocidad x1/x5/x10, forzar desconexión / rechazos / batería / obstáculo / tormenta / ola de calor / persona cerca, últimos JSON |

## Cómo compilar y correr

Requisitos: **Android Studio** (Ladybug o posterior) con **JDK 17** y **Android SDK 35**.

1. `File → Open…` y elegir la carpeta del repo. Esperar el *Gradle sync*.
2. Correr la configuración `app` en un emulador o celular (Android 7.0+).

Por línea de comandos (con el wrapper generado por Android Studio):

```bash
./gradlew assembleDebug        # APK en app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # tests unitarios
./gradlew assembleRelease      # APK de entrega (firmado con la clave debug)
```

## Estructura

```
app/src/main/java/com/tprobot/ovejero/
 ├── core/            Resource, UiState, Screen (rutas), tema, ApplicationScope
 ├── domain/          modelos, interfaces de repositorio, casos de uso (Kotlin puro)
 ├── data/            contrato JSON (dto), simulador, Room, mappers, repositorios
 ├── presentation/    pantallas Compose + ViewModels por feature
 └── di/              módulos de Hilt
```

> El contrato JSON es una propuesta del grupo y **no es el protocolo oficial de Unitree**.
> La detención de la app es un comando de software simulado y **no reemplaza una parada de emergencia física**.
