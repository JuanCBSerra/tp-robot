package com.tprobot.ovejero.presentation.common.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tprobot.ovejero.core.theme.CardBorder
import com.tprobot.ovejero.core.theme.EstadoAdvertencia
import com.tprobot.ovejero.core.theme.EstadoAdvertenciaFondo
import com.tprobot.ovejero.core.theme.EstadoDeshabilitado
import com.tprobot.ovejero.core.theme.EstadoDeshabilitadoFondo
import com.tprobot.ovejero.core.theme.EstadoError
import com.tprobot.ovejero.core.theme.EstadoErrorFondo
import com.tprobot.ovejero.core.theme.EstadoNormal
import com.tprobot.ovejero.core.theme.EstadoNormalFondo
import com.tprobot.ovejero.core.theme.EstadoOk
import com.tprobot.ovejero.core.theme.EstadoOkFondo
import com.tprobot.ovejero.domain.model.ConnectionState
import com.tprobot.ovejero.domain.model.RobotStatus
import com.tprobot.ovejero.presentation.common.UiFormat

/**
 * Estados visuales que pide la consigna (Figma y app usan los mismos):
 * normal, ok, advertencia, error y deshabilitado.
 */
enum class Tone(val content: Color, val container: Color) {
    NORMAL(EstadoNormal, EstadoNormalFondo),
    OK(EstadoOk, EstadoOkFondo),
    ADVERTENCIA(EstadoAdvertencia, EstadoAdvertenciaFondo),
    ERROR(EstadoError, EstadoErrorFondo),
    DESHABILITADO(EstadoDeshabilitado, EstadoDeshabilitadoFondo)
}

@Composable
fun LoadingStateView(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun ErrorStateView(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = EstadoError, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Reintentar") }
    }
}

@Composable
fun EmptyStateView(message: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = EstadoDeshabilitado, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Tarjeta de sección reutilizable (título + contenido). */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** Chip de estado con color según el tono. */
@Composable
fun StatusChip(text: String, tone: Tone, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(tone.container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = tone.content, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Celda de telemetría. Si el dato está desactualizado (sin señal) se muestra
 * en gris con la leyenda "dato viejo".
 */
@Composable
fun TelemetryTile(
    label: String,
    value: String,
    icon: ImageVector,
    tone: Tone,
    stale: Boolean,
    modifier: Modifier = Modifier,
    caption: String? = null
) {
    val effectiveTone = if (stale) Tone.DESHABILITADO else tone
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = effectiveTone.container.copy(alpha = 0.55f)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = effectiveTone.content, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (stale) EstadoDeshabilitado else MaterialTheme.colorScheme.onSurface
            )
            val bottom = if (stale) "dato viejo" else caption
            if (bottom != null) {
                Text(bottom, style = MaterialTheme.typography.bodyMedium, color = effectiveTone.content)
            }
        }
    }
}

/** Banner global cuando no hay señal o se está conectando. */
@Composable
fun ConnectionBanner(status: RobotStatus, modifier: Modifier = Modifier) {
    val (text, tone, icon) = when (status.connection) {
        ConnectionState.SIN_SENIAL -> Triple(
            "Sin señal con ${status.robot?.name ?: "el robot"} · última telemetría ${UiFormat.secondsAgo(status.secondsSinceUpdate)}. " +
                "Los datos están desactualizados y las órdenes pueden no llegar.",
            Tone.ERROR,
            Icons.Filled.CloudOff
        )
        ConnectionState.CONECTANDO -> Triple("Conectando con ${status.robot?.name ?: "el robot"}…", Tone.NORMAL, Icons.Filled.Sync)
        else -> return
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(tone.container)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tone.content)
            Spacer(Modifier.width(10.dp))
            Text(text, color = tone.content, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
