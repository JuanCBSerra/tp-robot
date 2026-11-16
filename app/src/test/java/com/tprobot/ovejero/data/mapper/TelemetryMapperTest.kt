package com.tprobot.ovejero.data.mapper

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tprobot.ovejero.data.remote.dto.TelemetryDto
import com.tprobot.ovejero.domain.model.MissionStatus
import com.tprobot.ovejero.domain.model.MissionType
import com.tprobot.ovejero.domain.model.MotionMode
import com.tprobot.ovejero.domain.model.OperationalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Parsea el ejemplo de telemetría de docs/contrato-json.md y lo mapea al dominio. */
class TelemetryMapperTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val json = """
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
          "service": { "heads_counted": 27, "heads_expected": 45, "heads_in_corral": 0, "temperature_c": 31.8 },
          "link": { "signal_percent": 54 }
        }
    """.trimIndent()

    @Test
    fun `telemetria de ejemplo se mapea al dominio`() {
        val dto = moshi.adapter(TelemetryDto::class.java).fromJson(json)
        assertNotNull(dto)
        val telemetry = TelemetryMapper.toDomain(dto!!)

        assertEquals(63, telemetry.batteryPercent)
        assertEquals(OperationalState.EN_MISION, telemetry.operationalState)
        assertEquals(MotionMode.ARREANDO, telemetry.motion.mode)
        assertEquals("Potrero 4", telemetry.position.zoneName)
        assertEquals(27, telemetry.herd.counted)
        assertEquals(18, telemetry.herd.missing)

        val mission = telemetry.mission!!
        assertEquals(MissionType.ARREO, mission.type)
        assertEquals(MissionStatus.EN_CURSO, mission.status)
        assertEquals(42, mission.progressPercent) // 0.415 -> 42 %
    }

    @Test
    fun `estado desconocido no rompe el mapeo`() {
        assertEquals(OperationalState.DESCONOCIDO, TelemetryMapper.toOperationalState("DANCING"))
        assertEquals(MissionStatus.ABORTADA, TelemetryMapper.toMissionStatus("???"))
    }
}
