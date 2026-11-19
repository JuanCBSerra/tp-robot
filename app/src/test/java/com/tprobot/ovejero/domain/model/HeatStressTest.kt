package com.tprobot.ovejero.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatStressTest {

    @Test
    fun `formula del ITH da los valores esperados`() {
        // 26,5 °C y 55 % de humedad -> ITH ~74,3 (clima de base más caluroso del simulador)
        assertEquals(74.33, HeatStress.thi(26.5, 55), 0.05)
        // 33 °C y 62 % -> ITH ~84,4 (ola de calor forzada)
        assertEquals(84.42, HeatStress.thi(33.0, 62), 0.05)
    }

    @Test
    fun `el clima de base del simulador no dispara alertas`() {
        // Pico de la oscilación de base (25,5 + 1,0 °C) con la humedad de base (55 %).
        assertTrue(HeatStress.thi(26.5, 55) < OperationRules.HEAT_THI_WARNING)
        assertEquals(HeatStress.Level.SIN_ESTRES, HeatStress.level(HeatStress.thi(26.5, 55)))
    }

    @Test
    fun `niveles segun los umbrales de bovinos de carne`() {
        assertEquals(HeatStress.Level.SIN_ESTRES, HeatStress.level(74.9))
        assertEquals(HeatStress.Level.ALERTA, HeatStress.level(75.0))
        assertEquals(HeatStress.Level.ALERTA, HeatStress.level(78.9))
        assertEquals(HeatStress.Level.PELIGRO, HeatStress.level(79.0))
        assertEquals(HeatStress.Level.PELIGRO, HeatStress.level(HeatStress.thi(35.0, 65)))
    }
}
