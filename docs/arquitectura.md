# Arquitectura

Clean Architecture + MVVM, igual que el proyecto de la cátedra (PokeDex Academy), con una diferencia:
en vez de una API REST con Retrofit, del otro lado hay un **robot simulado** que habla en JSON.

```mermaid
flowchart TB
    subgraph presentation["presentation (Jetpack Compose + ViewModel)"]
        UI["Screens Compose<br/>RobotSelection · Supervision · Control<br/>Alerts · History · Simulation"]
        VM["ViewModels<br/>StateFlow&lt;UiState&gt; + SharedFlow (efectos)"]
        UI -- "eventos (clicks)" --> VM
        VM -- "estado (collectAsStateWithLifecycle)" --> UI
    end

    subgraph domain["domain (Kotlin puro)"]
        UC["Casos de uso<br/>StartMission · Pause · Resume · Cancel<br/>ReturnToBase · OperationalStop · AcknowledgeAlert<br/>GetCommandAvailability · Observe*"]
        REPO_I["interfaces<br/>RobotRepository · SimulationRepository"]
        MODEL["modelos<br/>Telemetry · Alert · CommandRecord · MissionRecord · FieldMap"]
        UC --> REPO_I
    end

    subgraph data["data"]
        REPO["RobotRepositoryImpl<br/>(conexión, timeout de órdenes, historial)"]
        SIMREPO["SimulationRepositoryImpl"]
        MAP["Mappers<br/>DTO ⇄ dominio · Entity ⇄ dominio"]
        DS["RobotDataSource (interfaz)<br/>Flow&lt;String&gt; JSON in · send(JSON)"]
        SIM["RobotSimulator<br/>(Go2 simulado)"]
        ROOM[("Room<br/>events · missions")]
        REPO --> MAP
        REPO --> DS
        REPO --> ROOM
        SIMREPO --> SIM
        DS -. "implementada por" .-> SIM
    end

    VM --> UC
    REPO -. "implementa" .-> REPO_I
    SIMREPO -. "implementa" .-> REPO_I

    DI["di (Hilt)<br/>AppModule · DatabaseModule<br/>DataSourceModule · RepositoryModule"] -. "conecta interfaces con implementaciones" .-> data
```

## Reglas que respetamos

| Regla | Cómo se cumple |
|---|---|
| La UI observa estado y manda eventos, **sin tocar el simulador** | Las pantallas solo conocen ViewModels; los ViewModels solo casos de uso. Ni siquiera el panel de simulación ve `RobotSimulator`: pasa por `SimulationRepository`. |
| Un **Repository** desacopla telemetría, comandos y eventos de la implementación simulada | `RobotRepository` (dominio) ← `RobotRepositoryImpl` (data) → `RobotDataSource` ← `RobotSimulator`. Cambiar a un robot real = otra implementación de `RobotDataSource` + 1 línea en `DataSourceModule`. |
| Separar modelos, Repository, ViewModel y Compose | Paquetes `domain/model`, `domain/repository`, `data/repository`, `presentation/<feature>/*ViewModel.kt` y `*Screen.kt`. |
| StateFlow | Cada ViewModel expone `StateFlow<UiState<T>>` (backing property privada) y `SharedFlow` para snackbars/navegación. |
| Persistencia local | Room: tabla `events` (todo lo que pasa) y `missions` (resumen al terminar cada misión). |
| Funciona sin robot, servidor ni internet | El manifest no pide permiso de INTERNET. |

## Flujo de una orden

```mermaid
sequenceDiagram
    actor Op as Operador
    participant S as ControlScreen
    participant VM as ControlViewModel
    participant UC as PauseMissionUseCase
    participant R as RobotRepositoryImpl
    participant Sim as RobotSimulator

    Op->>S: toca "Pausar"
    S->>VM: pause()
    VM->>UC: invoke()
    UC->>UC: GetCommandAvailability (¿está habilitada?)
    UC->>R: sendCommand(PAUSAR)
    R->>R: registro PENDIENTE + timer 5 s
    R->>Sim: send({"type":"command","command":"PAUSE_MISSION",...})
    R-->>VM: Resource.Success(registro)
    Sim-->>R: {"type":"command_ack","status":"ACCEPTED"} (0,4–1,2 s después)
    R->>R: PENDIENTE → CONFIRMADA, evento a Room
    R-->>VM: Flow<List<CommandRecord>> emite
    VM-->>S: StateFlow emite → chip "Confirmada"
```

## Estructura de paquetes

```
app/src/main/java/com/tprobot/ovejero/
 ├── core/            model (Resource, UiState), navigation (Screen), theme, coroutines (ApplicationScope)
 ├── domain/          model, repository (interfaces), usecase (1 clase = 1 acción)
 ├── data/
 │    ├── remote/     RobotDataSource, RobotProtocol, dto/ (contrato JSON)
 │    ├── simulator/  RobotSimulator (el Go2 simulado)
 │    ├── local/      Room: OvejeroDatabase, dao/, entity/
 │    ├── mapper/     Telemetry, Alert, Command, Robot, History
 │    └── repository/ RobotRepositoryImpl, SimulationRepositoryImpl
 ├── presentation/    robotselection, supervision, control, alerts, history, simulation, shell, common/
 └── di/              AppModule, DatabaseModule, DataSourceModule, RepositoryModule
```
