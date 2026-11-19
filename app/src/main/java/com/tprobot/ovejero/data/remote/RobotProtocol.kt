package com.tprobot.ovejero.data.remote

/**
 * Códigos que usa el contrato JSON (propuesta propia, no es el protocolo de Unitree).
 * Los mappers traducen estos códigos a los enums del dominio.
 */
object RobotProtocol {
    const val VERSION = "1.0"

    object MessageType {
        const val TELEMETRY = "telemetry"
        const val ALERT = "alert"
        const val COMMAND = "command"
        const val COMMAND_ACK = "command_ack"
    }

    object State {
        const val DOCKED = "DOCKED"
        const val ON_MISSION = "ON_MISSION"
        const val PAUSED = "PAUSED"
        const val AWAITING_DECISION = "AWAITING_DECISION"
        const val RETURNING = "RETURNING"
        const val STOPPED = "STOPPED"
    }

    object MissionKind {
        const val HEAD_COUNT = "HEAD_COUNT"
        const val HERDING = "HERDING"
    }

    object MissionStatus {
        const val RUNNING = "RUNNING"
        const val PAUSED = "PAUSED"
        const val COMPLETED = "COMPLETED"
        const val CANCELLED = "CANCELLED"
        const val ABORTED = "ABORTED"
    }

    object Motion {
        const val IDLE = "IDLE"
        const val WALKING = "WALKING"
        const val HERDING = "HERDING"
    }

    object Command {
        const val START_MISSION = "START_MISSION"
        const val PAUSE_MISSION = "PAUSE_MISSION"
        const val RESUME_MISSION = "RESUME_MISSION"
        const val CANCEL_MISSION = "CANCEL_MISSION"
        const val RETURN_TO_BASE = "RETURN_TO_BASE"
        const val OPERATIONAL_STOP = "OPERATIONAL_STOP"
        const val ACK_ALERT = "ACK_ALERT"
    }

    object AckStatus {
        const val ACCEPTED = "ACCEPTED"
        const val REJECTED = "REJECTED"
    }

    object AlertCode {
        const val LOW_BATTERY = "LOW_BATTERY"
        const val CRITICAL_BATTERY = "CRITICAL_BATTERY"
        const val AUTO_RETURN = "AUTO_RETURN"
        const val OBSTACLE = "OBSTACLE"
        const val STORM = "STORM"
        const val PERSON_NEARBY = "PERSON_NEARBY"
        const val MISSING_ANIMALS = "MISSING_ANIMALS"
        const val HEAT_STRESS = "HEAT_STRESS"
    }

    object Severity {
        const val INFO = "INFO"
        const val WARNING = "WARNING"
        const val CRITICAL = "CRITICAL"
    }

    object RobotStatus {
        const val AVAILABLE = "AVAILABLE"
        const val MAINTENANCE = "MAINTENANCE"
        const val OUT_OF_RANGE = "OUT_OF_RANGE"
    }
}
