package com.tprobot.ovejero.presentation.common

import com.tprobot.ovejero.domain.model.ConnectionState
import com.tprobot.ovejero.domain.model.HerdStatus
import com.tprobot.ovejero.domain.model.MissionStatus
import com.tprobot.ovejero.domain.model.MissionType
import com.tprobot.ovejero.domain.model.Motion
import com.tprobot.ovejero.domain.model.MotionMode
import com.tprobot.ovejero.presentation.common.components.Tone
import com.tprobot.ovejero.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusPresenterTest {

    // --- Tono de la batería ---

    @Test
    fun `tono de bateria segun los umbrales`() {
        assertEquals(Tone.OK, StatusPresenter.batteryTone(100))
        assertEquals(Tone.OK, StatusPresenter.batteryTone(26))
        assertEquals(Tone.ADVERTENCIA, StatusPresenter.batteryTone(25))
        assertEquals(Tone.ADVERTENCIA, StatusPresenter.batteryTone(16))
        assertEquals(Tone.ERROR, StatusPresenter.batteryTone(15))
        assertEquals(Tone.ERROR, StatusPresenter.batteryTone(0))
    }

    @Test
    fun `UiFormat delega el tono de bateria en el presentador`() {
        assertEquals(StatusPresenter.batteryTone(20), UiFormat.batteryTone(20))
    }

    // --- Batería en la barra superior ---

    @Test
    fun `sin telemetria no hay texto de bateria`() {
        assertEquals("", StatusPresenter.topBarBatterySuffix(Fixtures.status(telemetry = null)))
    }

    @Test
    fun `bateria normal se muestra sin marca`() {
        val status = Fixtures.status(Fixtures.telemetry(battery = 78))
        assertEquals(" · 🔋 78 %", StatusPresenter.topBarBatterySuffix(status))
    }

    @Test
    fun `bateria baja se marca con advertencia desde el umbral`() {
        val status = Fixtures.status(Fixtures.telemetry(battery = 25))
        assertEquals(" · 🔋 25 % ⚠️", StatusPresenter.topBarBatterySuffix(status))
        val justAbove = Fixtures.status(Fixtures.telemetry(battery = 26))
        assertEquals(" · 🔋 26 %", StatusPresenter.topBarBatterySuffix(justAbove))
    }

    @Test
    fun `sin senial la bateria se marca como dato viejo aunque sea baja`() {
        val normal = Fixtures.status(Fixtures.telemetry(battery = 80), connection = ConnectionState.SIN_SENIAL)
        assertEquals(" · 🔋 80 % (dato viejo)", StatusPresenter.topBarBatterySuffix(normal))
        val low = Fixtures.status(Fixtures.telemetry(battery = 10), connection = ConnectionState.SIN_SENIAL)
        assertEquals(" · 🔋 10 % (dato viejo)", StatusPresenter.topBarBatterySuffix(low))
    }

    // --- Batería frente al mínimo para iniciar ---

    @Test
    fun `requisito de bateria sin datos`() {
        val ui = StatusPresenter.batteryRequirement(Fixtures.status(telemetry = null))
        assertEquals("Batería: sin datos (mínimo 30 %)", ui.text)
        assertNull(ui.tone)
    }

    @Test
    fun `requisito de bateria con dato viejo`() {
        val ui = StatusPresenter.batteryRequirement(
            Fixtures.status(Fixtures.telemetry(battery = 80), connection = ConnectionState.SIN_SENIAL)
        )
        assertEquals("Batería: 80 % · dato viejo (mínimo 30 %)", ui.text)
        assertNull(ui.tone)
    }

    @Test
    fun `requisito de bateria cumplido en el limite`() {
        val ui = StatusPresenter.batteryRequirement(Fixtures.status(Fixtures.telemetry(battery = 30)))
        assertEquals("Batería: 30 % (mínimo 30 %)", ui.text)
        assertEquals(Tone.OK, ui.tone)
    }

    @Test
    fun `requisito de bateria insuficiente`() {
        val ui = StatusPresenter.batteryRequirement(Fixtures.status(Fixtures.telemetry(battery = 29)))
        assertEquals("Batería: 29 % · insuficiente para iniciar (mínimo 30 %)", ui.text)
        assertEquals(Tone.ERROR, ui.tone)
    }

    // --- Temperatura interna del robot ---

    @Test
    fun `temperatura normal no avisa`() {
        val tile = StatusPresenter.temperatureTile(Fixtures.telemetry(temperature = 33.9))
        assertEquals("%.1f °C".format(33.9), tile.value)
        assertNull(tile.caption)
        assertEquals(Tone.OK, tile.tone)
    }

    @Test
    fun `temperatura alta avisa sobrecalentamiento`() {
        val tile = StatusPresenter.temperatureTile(Fixtures.telemetry(temperature = 34.0))
        assertEquals("sobrecalentamiento", tile.caption)
        assertEquals(Tone.ADVERTENCIA, tile.tone)
    }

    @Test
    fun `temperatura muy alta es peligro`() {
        val tile = StatusPresenter.temperatureTile(Fixtures.telemetry(temperature = 38.0))
        assertEquals("sobrecalentamiento", tile.caption)
        assertEquals(Tone.ERROR, tile.tone)
    }

    // --- Panel completo ---

    @Test
    fun `panel con robot en el galpon cargando y sin mision`() {
        val telemetry = Fixtures.telemetry(battery = 90, charging = true, signal = 85, zone = "Galpón")
        val status = Fixtures.status(telemetry)
        val panel = StatusPresenter.telemetryPanel(status, telemetry)

        assertEquals(TileUi("90 %", "cargando en el galpón", Tone.OK), panel.battery)
        assertEquals(TileUi("85 %", "Conectado", Tone.OK), panel.signal)
        assertEquals(TileUi("Galpón", null, Tone.NORMAL), panel.location)
        assertEquals(TileUi("—", "solo en arreo", Tone.NORMAL), panel.corral)
    }

    @Test
    fun `panel con bateria baja y senial debil`() {
        val telemetry = Fixtures.telemetry(battery = 20, signal = 39)
        val panel = StatusPresenter.telemetryPanel(Fixtures.status(telemetry), telemetry)
        assertEquals(Tone.ADVERTENCIA, panel.battery.tone)
        assertNull(panel.battery.caption)
        assertEquals(Tone.ADVERTENCIA, panel.signal.tone)
        assertEquals(Tone.OK, StatusPresenter.telemetryPanel(
            Fixtures.status(Fixtures.telemetry(signal = 40)),
            Fixtures.telemetry(signal = 40)
        ).signal.tone)
    }

    @Test
    fun `panel muestra el movimiento`() {
        val telemetry = Fixtures.telemetry(motion = Motion(MotionMode.ARREANDO, 1.0))
        val panel = StatusPresenter.telemetryPanel(Fixtures.status(telemetry), telemetry)
        assertEquals(TileUi("Arreando", "%.1f m/s".format(1.0), Tone.NORMAL), panel.motion)
    }

    @Test
    fun `panel durante un arreo muestra animales en el corral`() {
        val telemetry = Fixtures.telemetry(
            mission = Fixtures.mission(type = MissionType.ARREO),
            herd = HerdStatus(counted = 30, expected = 45, inCorral = 12)
        )
        val panel = StatusPresenter.telemetryPanel(Fixtures.status(telemetry), telemetry)
        assertEquals(TileUi("12 / 45", null, Tone.NORMAL), panel.corral)
        // La misión sigue activa: los faltantes todavía no son una advertencia.
        assertEquals(TileUi("30 / 45", "faltan 15", Tone.NORMAL), panel.counted)
    }

    @Test
    fun `panel en un recuento no muestra el corral`() {
        val telemetry = Fixtures.telemetry(mission = Fixtures.mission(type = MissionType.RECUENTO))
        val panel = StatusPresenter.telemetryPanel(Fixtures.status(telemetry), telemetry)
        assertEquals(TileUi("—", "solo en arreo", Tone.NORMAL), panel.corral)
    }

    @Test
    fun `mision terminada con faltantes marca los animales contados en advertencia`() {
        val telemetry = Fixtures.telemetry(
            mission = Fixtures.mission(status = MissionStatus.ABORTADA),
            herd = HerdStatus(counted = 42, expected = 45, inCorral = 0)
        )
        val panel = StatusPresenter.telemetryPanel(Fixtures.status(telemetry), telemetry)
        assertEquals(TileUi("42 / 45", "faltan 3", Tone.ADVERTENCIA), panel.counted)
    }

    @Test
    fun `mision terminada con todos los animales no advierte`() {
        val telemetry = Fixtures.telemetry(
            mission = Fixtures.mission(status = MissionStatus.COMPLETADA),
            herd = HerdStatus(counted = 45, expected = 45, inCorral = 45)
        )
        val panel = StatusPresenter.telemetryPanel(Fixtures.status(telemetry), telemetry)
        assertEquals(TileUi("45 / 45", "están todos", Tone.NORMAL), panel.counted)
    }

    @Test
    fun `sin mision los faltantes no advierten`() {
        val telemetry = Fixtures.telemetry(mission = null, herd = HerdStatus(0, 45, 0))
        val panel = StatusPresenter.telemetryPanel(Fixtures.status(telemetry), telemetry)
        assertEquals(TileUi("0 / 45", "faltan 45", Tone.NORMAL), panel.counted)
    }

    @Test
    fun `panel incluye la tarjeta de temperatura`() {
        val telemetry = Fixtures.telemetry(temperature = 36.0)
        val panel = StatusPresenter.telemetryPanel(Fixtures.status(telemetry), telemetry)
        assertEquals(StatusPresenter.temperatureTile(telemetry), panel.temperature)
    }
}
