package com.tprobot.ovejero.data.mapper

import com.tprobot.ovejero.data.local.entity.EventEntity
import com.tprobot.ovejero.data.local.entity.MissionEntity
import com.tprobot.ovejero.domain.model.EventCategory
import com.tprobot.ovejero.domain.model.HistoryEvent
import com.tprobot.ovejero.domain.model.MissionRecord
import com.tprobot.ovejero.domain.model.MissionStatus
import com.tprobot.ovejero.domain.model.MissionType

/** Entidades de Room <-> modelos de dominio. Los enums se guardan por nombre. */
object HistoryMapper {

    fun toDomain(entity: EventEntity): HistoryEvent = HistoryEvent(
        id = entity.id,
        timestampMillis = entity.timestamp,
        category = enumValueOrDefault(entity.category, EventCategory.SISTEMA),
        title = entity.title,
        detail = entity.detail
    )

    fun toEntity(timestamp: Long, category: EventCategory, title: String, detail: String) = EventEntity(
        timestamp = timestamp,
        category = category.name,
        title = title,
        detail = detail
    )

    fun toDomain(entity: MissionEntity): MissionRecord = MissionRecord(
        missionId = entity.missionId,
        robotName = entity.robotName,
        type = enumValueOrDefault(entity.type, MissionType.RECUENTO),
        result = enumValueOrDefault(entity.result, MissionStatus.ABORTADA),
        startedAtMillis = entity.startedAt,
        endedAtMillis = entity.endedAt,
        headsCounted = entity.headsCounted,
        headsExpected = entity.headsExpected,
        headsInCorral = entity.headsInCorral,
        batteryUsedPercent = entity.batteryUsedPercent
    )

    fun toEntity(record: MissionRecord) = MissionEntity(
        missionId = record.missionId,
        robotName = record.robotName,
        type = record.type.name,
        result = record.result.name,
        startedAt = record.startedAtMillis,
        endedAt = record.endedAtMillis,
        headsCounted = record.headsCounted,
        headsExpected = record.headsExpected,
        headsInCorral = record.headsInCorral,
        batteryUsedPercent = record.batteryUsedPercent
    )

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}
