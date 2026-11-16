package com.tprobot.ovejero.core.navigation

/**
 * Rutas de navegación de El Ovejero.
 * - RobotSelection: pantalla inicial (selección del robot).
 * - Supervision / Control / Alerts / History: pestañas principales con el robot conectado.
 * - Simulation: panel del simulador (para la demo y las pruebas).
 */
sealed class Screen(val route: String) {
    data object RobotSelection : Screen("robot_selection")
    data object Supervision : Screen("supervision")
    data object Control : Screen("control")
    data object Alerts : Screen("alerts")
    data object History : Screen("history")
    data object Simulation : Screen("simulation")
}
