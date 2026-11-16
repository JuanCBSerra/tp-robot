package com.tprobot.ovejero.domain.model

/**
 * Plano de la chacra. Las coordenadas están normalizadas (0..1):
 * x crece hacia la derecha (este) e y hacia abajo (sur).
 *
 * Es un dato fijo del caso de uso: lo usan el simulador (para mover al robot)
 * y la UI (para dibujar el mapa).
 */
data class FieldMap(
    val name: String,
    val widthMeters: Int,
    val heightMeters: Int,
    val zones: List<FieldZone>,
    val route: List<Checkpoint>
) {
    val base: Checkpoint get() = route.first()
    val totalHeads: Int get() = route.sumOf { it.expectedHeads }

    /** Nombre de la zona en la que cae un punto (la más específica primero). */
    fun zoneAt(x: Float, y: Float): String =
        zones.filter { it.kind != ZoneKind.ARROYO }
            .sortedBy { it.kind.priority }
            .firstOrNull { x in it.left..it.right && y in it.top..it.bottom }
            ?.name ?: "Camino interno"

    companion object {
        /** Chacra "La Colorada", Oberá, Misiones (caso inventado para el TP). */
        val LA_COLORADA = FieldMap(
            name = "Chacra La Colorada",
            widthMeters = 400,
            heightMeters = 400,
            zones = listOf(
                FieldZone("Monte nativo", 0.00f, 0.00f, 1.00f, 0.06f, ZoneKind.MONTE),
                FieldZone("Potrero 3", 0.00f, 0.06f, 0.34f, 0.42f, ZoneKind.POTRERO),
                FieldZone("Potrero 4", 0.34f, 0.06f, 0.67f, 0.42f, ZoneKind.POTRERO),
                FieldZone("Potrero 5", 0.67f, 0.06f, 1.00f, 0.42f, ZoneKind.POTRERO),
                FieldZone("Potrero 2", 0.00f, 0.42f, 0.34f, 0.80f, ZoneKind.POTRERO),
                FieldZone("Potrero 1", 0.34f, 0.42f, 0.67f, 0.80f, ZoneKind.POTRERO),
                FieldZone("Corral", 0.67f, 0.42f, 1.00f, 0.80f, ZoneKind.CORRAL),
                FieldZone("Arroyo", 0.00f, 0.31f, 0.34f, 0.34f, ZoneKind.ARROYO),
                FieldZone("Galpón", 0.40f, 0.82f, 0.60f, 0.94f, ZoneKind.GALPON),
                FieldZone("Casa", 0.05f, 0.84f, 0.22f, 0.97f, ZoneKind.CASA)
            ),
            route = listOf(
                Checkpoint("Galpón", 0.50f, 0.88f, expectedHeads = 0),
                Checkpoint("Potrero 1", 0.50f, 0.61f, expectedHeads = 8),
                Checkpoint("Potrero 2", 0.17f, 0.61f, expectedHeads = 10),
                Checkpoint("Potrero 3", 0.17f, 0.22f, expectedHeads = 9),
                Checkpoint("Potrero 4", 0.50f, 0.22f, expectedHeads = 10),
                Checkpoint("Potrero 5", 0.83f, 0.22f, expectedHeads = 8),
                Checkpoint("Corral", 0.83f, 0.61f, expectedHeads = 0, isCorral = true),
                Checkpoint("Galpón", 0.50f, 0.88f, expectedHeads = 0)
            )
        )
    }
}

data class FieldZone(
    val name: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val kind: ZoneKind
)

enum class ZoneKind(val priority: Int) {
    GALPON(0),
    CASA(1),
    CORRAL(2),
    POTRERO(3),
    MONTE(4),
    ARROYO(5)
}

data class Checkpoint(
    val name: String,
    val x: Float,
    val y: Float,
    val expectedHeads: Int,
    val isCorral: Boolean = false
)
