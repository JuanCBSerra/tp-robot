package com.tprobot.ovejero.presentation.common.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tprobot.ovejero.core.theme.EstadoDeshabilitado
import com.tprobot.ovejero.core.theme.EstadoError
import com.tprobot.ovejero.core.theme.MapaArroyo
import com.tprobot.ovejero.core.theme.MapaCasa
import com.tprobot.ovejero.core.theme.MapaCorral
import com.tprobot.ovejero.core.theme.MapaGalpon
import com.tprobot.ovejero.core.theme.MapaMonte
import com.tprobot.ovejero.core.theme.MapaPotrero
import com.tprobot.ovejero.core.theme.MapaPotreroBorde
import com.tprobot.ovejero.core.theme.MapaRuta
import com.tprobot.ovejero.core.theme.TierraColorada
import com.tprobot.ovejero.domain.model.FieldMap
import com.tprobot.ovejero.domain.model.Position
import com.tprobot.ovejero.domain.model.ZoneKind

/**
 * Plano de la chacra dibujado con Canvas (sin Google Maps: todo offline).
 * Muestra potreros, ruta de la misión, puntos de control ya visitados y la posición del robot.
 * Si el dato está desactualizado, el robot se dibuja en gris.
 */
@Composable
fun FieldMapView(
    map: FieldMap,
    robotPosition: Position?,
    checkpointsReached: Int,
    stale: Boolean,
    alertActive: Boolean,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF33402F))

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
    ) {
        drawRect(Color(0xFFEDE3D3))

        // Zonas
        map.zones.forEach { zone ->
            val topLeft = Offset(zone.left * size.width, zone.top * size.height)
            val zoneSize = Size((zone.right - zone.left) * size.width, (zone.bottom - zone.top) * size.height)
            when (zone.kind) {
                ZoneKind.POTRERO -> {
                    drawRect(MapaPotrero, topLeft, zoneSize)
                    drawRect(MapaPotreroBorde, topLeft, zoneSize, style = Stroke(width = 2f))
                }
                ZoneKind.MONTE -> drawRect(MapaMonte, topLeft, zoneSize)
                ZoneKind.CORRAL -> {
                    drawRect(MapaCorral, topLeft, zoneSize)
                    drawRect(Color(0xFF8D6E63), topLeft, zoneSize, style = Stroke(width = 3f))
                }
                ZoneKind.ARROYO -> drawArroyo(topLeft, zoneSize)
                ZoneKind.GALPON -> drawRoundRect(MapaGalpon, topLeft, zoneSize, CornerRadius(8f, 8f))
                ZoneKind.CASA -> drawRoundRect(MapaCasa, topLeft, zoneSize, CornerRadius(8f, 8f))
            }
            if (zone.kind != ZoneKind.ARROYO) {
                val label = textMeasurer.measure(zone.name, labelStyle)
                val labelColor = if (zone.kind == ZoneKind.MONTE) Color.White else Color(0xFF33402F)
                drawText(
                    textMeasurer = textMeasurer,
                    text = zone.name,
                    style = labelStyle.copy(color = labelColor),
                    topLeft = Offset(
                        topLeft.x + 6f,
                        topLeft.y + 4f
                    ).let { if (label.size.width > zoneSize.width) it.copy(x = topLeft.x + 2f) else it }
                )
            }
        }

        // Ruta de la misión (punteada)
        val routePath = Path().apply {
            map.route.forEachIndexed { index, cp ->
                val p = Offset(cp.x * size.width, cp.y * size.height)
                if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
        }
        drawPath(
            routePath,
            color = MapaRuta.copy(alpha = 0.6f),
            style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
        )

        // Puntos de control (rellenos = ya visitados)
        map.route.forEachIndexed { index, cp ->
            if (index == 0 || index == map.route.lastIndex) return@forEachIndexed
            val center = Offset(cp.x * size.width, cp.y * size.height)
            val visited = index <= checkpointsReached
            drawCircle(Color.White, radius = 11f, center = center)
            drawCircle(
                if (visited) MapaRuta else MapaRuta.copy(alpha = 0.4f),
                radius = 9f,
                center = center,
                style = if (visited) androidx.compose.ui.graphics.drawscope.Fill else Stroke(width = 3f)
            )
        }

        // Robot
        if (robotPosition != null) {
            val center = Offset(robotPosition.x * size.width, robotPosition.y * size.height)
            val bodyColor = when {
                stale -> EstadoDeshabilitado
                alertActive -> EstadoError
                else -> TierraColorada
            }
            drawCircle(bodyColor.copy(alpha = 0.25f), radius = 34f, center = center)
            rotate(degrees = robotPosition.headingDegrees.toFloat(), pivot = center) {
                val arrow = Path().apply {
                    moveTo(center.x, center.y - 22f)
                    lineTo(center.x + 13f, center.y + 12f)
                    lineTo(center.x, center.y + 5f)
                    lineTo(center.x - 13f, center.y + 12f)
                    close()
                }
                drawPath(arrow, bodyColor)
                drawPath(arrow, Color.White, style = Stroke(width = 3f))
            }
        }
    }
}

private fun DrawScope.drawArroyo(topLeft: Offset, zoneSize: Size) {
    val path = Path()
    val steps = 12
    val midY = topLeft.y + zoneSize.height / 2
    path.moveTo(topLeft.x, midY)
    for (i in 1..steps) {
        val x = topLeft.x + zoneSize.width * i / steps
        val y = midY + if (i % 2 == 0) -zoneSize.height else zoneSize.height
        path.lineTo(x, y)
    }
    drawPath(path, MapaArroyo, style = Stroke(width = 7f))
}
