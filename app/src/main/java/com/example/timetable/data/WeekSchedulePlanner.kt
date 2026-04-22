package com.example.timetable.data

import kotlin.math.floor
import kotlin.math.max

private const val DAY_END_MINUTES = 24 * 60

data class FixedWeekScheduleConfig(
    val firstStartMinutes: Int,
    val lessonDurationMinutes: Int,
    val breakDurationMinutes: Int,
    val slotCount: Int,
) {
    init {
        require(firstStartMinutes in 0 until DAY_END_MINUTES)
        require(lessonDurationMinutes in 1..12 * 60)
        require(breakDurationMinutes in 0..6 * 60)
        require(slotCount in 1..20)
    }
}

fun inferFixedWeekScheduleConfig(slots: List<WeekTimeSlot>): FixedWeekScheduleConfig {
    val sortedSlots = slots.sortedBy { it.startMinutes }
    val firstSlot = sortedSlots.firstOrNull()
        ?: return FixedWeekScheduleConfig(
            firstStartMinutes = 8 * 60,
            lessonDurationMinutes = 40,
            breakDurationMinutes = 5,
            slotCount = 8,
        )

    val firstGap = if (sortedSlots.size > 1) {
        (sortedSlots[1].startMinutes - firstSlot.endMinutes).coerceAtLeast(0)
    } else {
        5
    }

    return FixedWeekScheduleConfig(
        firstStartMinutes = firstSlot.startMinutes,
        lessonDurationMinutes = firstSlot.endMinutes - firstSlot.startMinutes,
        breakDurationMinutes = firstGap,
        slotCount = sortedSlots.size.coerceIn(1, 20),
    )
}

fun buildWeekTimeSlotsFromFixedSchedule(config: FixedWeekScheduleConfig): List<WeekTimeSlot> {
    val slots = mutableListOf<WeekTimeSlot>()
    var nextStart = config.firstStartMinutes

    repeat(config.slotCount) {
        val end = nextStart + config.lessonDurationMinutes
        if (end > DAY_END_MINUTES) {
            return slots
        }
        slots += WeekTimeSlot(
            startMinutes = nextStart,
            endMinutes = end,
        )
        nextStart = end + config.breakDurationMinutes
        if (nextStart >= DAY_END_MINUTES) {
            return slots
        }
    }

    return slots
}

fun syncWeekTimeSlotsWithEntryChange(
    currentSlots: List<WeekTimeSlot>,
    originalEntry: TimetableEntry?,
    updatedEntry: TimetableEntry,
): List<WeekTimeSlot> {
    val sortedSlots = currentSlots.sortedBy { it.startMinutes }
    if (sortedSlots.isEmpty()) {
        return listOf(
            WeekTimeSlot(
                startMinutes = updatedEntry.startMinutes,
                endMinutes = updatedEntry.endMinutes,
            ),
        )
    }

    val referenceEntry = originalEntry ?: updatedEntry
    val overlappingIndices = sortedSlots.indices.filter { index ->
        slotOverlapsRange(
            slot = sortedSlots[index],
            startMinutes = referenceEntry.startMinutes,
            endMinutes = referenceEntry.endMinutes,
        )
    }

    val syncedSlots = when {
        overlappingIndices.isNotEmpty() -> {
            replaceSlotRange(
                slots = sortedSlots,
                range = overlappingIndices.first()..overlappingIndices.last(),
                updatedStartMinutes = updatedEntry.startMinutes,
                updatedEndMinutes = updatedEntry.endMinutes,
            )
        }
        updatedEntry.endMinutes <= sortedSlots.first().startMinutes -> {
            listOf(
                WeekTimeSlot(
                    startMinutes = updatedEntry.startMinutes,
                    endMinutes = updatedEntry.endMinutes,
                ),
            ) + sortedSlots
        }
        updatedEntry.startMinutes >= sortedSlots.last().endMinutes -> {
            sortedSlots + WeekTimeSlot(
                startMinutes = updatedEntry.startMinutes,
                endMinutes = updatedEntry.endMinutes,
            )
        }
        else -> {
            val nearestIndex = sortedSlots.indices.minByOrNull { index ->
                val slotMidpoint = (sortedSlots[index].startMinutes + sortedSlots[index].endMinutes) / 2
                kotlin.math.abs(slotMidpoint - (updatedEntry.startMinutes + updatedEntry.endMinutes) / 2)
            } ?: 0
            replaceSlotRange(
                slots = sortedSlots,
                range = nearestIndex..nearestIndex,
                updatedStartMinutes = updatedEntry.startMinutes,
                updatedEndMinutes = updatedEntry.endMinutes,
            )
        }
    }

    return normalizeWeekTimeSlots(syncedSlots)
}

private fun replaceSlotRange(
    slots: List<WeekTimeSlot>,
    range: IntRange,
    updatedStartMinutes: Int,
    updatedEndMinutes: Int,
): List<WeekTimeSlot> {
    val firstIndex = range.first
    val lastIndex = range.last
    val existingRange = slots.subList(firstIndex, lastIndex + 1)

    val effectiveStartMinutes = if (firstIndex == 0) {
        updatedStartMinutes
    } else {
        val previousSlot = slots[firstIndex - 1]
        val originalGap = (existingRange.first().startMinutes - previousSlot.endMinutes).coerceAtLeast(0)
        max(updatedStartMinutes, previousSlot.endMinutes + originalGap)
    }
    val requestedDuration = (updatedEndMinutes - updatedStartMinutes).coerceAtLeast(1)
    val effectiveEndMinutes = (effectiveStartMinutes + requestedDuration).coerceAtMost(DAY_END_MINUTES)

    val replacementSlots = if (existingRange.size == 1) {
        listOf(
            WeekTimeSlot(
                startMinutes = effectiveStartMinutes,
                endMinutes = effectiveEndMinutes,
            ),
        )
    } else {
        rescaleWeekTimeSlotPattern(
            baseSlots = existingRange,
            newStartMinutes = effectiveStartMinutes,
            newEndMinutes = effectiveEndMinutes,
        )
    }

    val deltaEndMinutes = replacementSlots.last().endMinutes - existingRange.last().endMinutes
    val shiftedAfterSlots = slots
        .drop(lastIndex + 1)
        .mapNotNull { slot -> shiftWeekTimeSlot(slot, deltaEndMinutes) }

    return buildList {
        addAll(slots.take(firstIndex))
        addAll(replacementSlots)
        addAll(shiftedAfterSlots)
    }
}

private fun rescaleWeekTimeSlotPattern(
    baseSlots: List<WeekTimeSlot>,
    newStartMinutes: Int,
    newEndMinutes: Int,
): List<WeekTimeSlot> {
    val totalMinutes = (newEndMinutes - newStartMinutes).coerceAtLeast(1)
    if (baseSlots.size <= 1) {
        return listOf(
            WeekTimeSlot(
                startMinutes = newStartMinutes,
                endMinutes = newEndMinutes,
            ),
        )
    }

    val segments = mutableListOf<Int>()
    baseSlots.forEachIndexed { index, slot ->
        segments += (slot.endMinutes - slot.startMinutes).coerceAtLeast(1)
        if (index < baseSlots.lastIndex) {
            val nextSlot = baseSlots[index + 1]
            segments += (nextSlot.startMinutes - slot.endMinutes).coerceAtLeast(0)
        }
    }

    val minimumSegments = segments.mapIndexed { index, _ -> if (index % 2 == 0) 1 else 0 }
    val minimumTotal = minimumSegments.sum()
    if (totalMinutes < minimumTotal) {
        return listOf(
            WeekTimeSlot(
                startMinutes = newStartMinutes,
                endMinutes = newEndMinutes,
            ),
        )
    }

    val flexibleMinutes = totalMinutes - minimumTotal
    val weights = segments.mapIndexed { index, value ->
        (value - minimumSegments[index]).coerceAtLeast(0)
    }
    val distributedFlexibleMinutes = distributeProportionally(flexibleMinutes, weights)
    val scaledSegments = minimumSegments.mapIndexed { index, minimum ->
        minimum + distributedFlexibleMinutes[index]
    }

    val scaledSlots = mutableListOf<WeekTimeSlot>()
    var cursor = newStartMinutes
    baseSlots.indices.forEach { slotIndex ->
        val duration = scaledSegments[slotIndex * 2]
        val end = (cursor + duration).coerceAtMost(DAY_END_MINUTES)
        if (end <= cursor) {
            return scaledSlots
        }
        scaledSlots += WeekTimeSlot(
            startMinutes = cursor,
            endMinutes = end,
        )
        if (slotIndex < baseSlots.lastIndex) {
            cursor = end + scaledSegments[slotIndex * 2 + 1]
        }
    }

    return scaledSlots
}

private fun distributeProportionally(
    total: Int,
    weights: List<Int>,
): List<Int> {
    if (total <= 0 || weights.isEmpty()) {
        return List(weights.size) { 0 }
    }

    val effectiveWeights = if (weights.sum() > 0) {
        weights
    } else {
        weights.mapIndexed { index, _ -> if (index % 2 == 0) 1 else 0 }
    }

    val weightSum = effectiveWeights.sum().coerceAtLeast(1)
    val rawShares = effectiveWeights.map { weight ->
        total.toDouble() * weight.toDouble() / weightSum.toDouble()
    }
    val floorShares = rawShares.map { floor(it).toInt() }.toMutableList()
    var remainder = total - floorShares.sum()

    rawShares.indices
        .sortedByDescending { index -> rawShares[index] - floorShares[index].toDouble() }
        .forEach { index ->
            if (remainder <= 0) return@forEach
            floorShares[index] += 1
            remainder -= 1
        }

    return floorShares
}

private fun shiftWeekTimeSlot(
    slot: WeekTimeSlot,
    deltaMinutes: Int,
): WeekTimeSlot? {
    val shiftedStart = slot.startMinutes + deltaMinutes
    val shiftedEnd = slot.endMinutes + deltaMinutes
    if (shiftedEnd <= 0 || shiftedStart >= DAY_END_MINUTES) {
        return null
    }

    val normalizedStart = shiftedStart.coerceIn(0, DAY_END_MINUTES - 1)
    val normalizedEnd = shiftedEnd.coerceIn(1, DAY_END_MINUTES)
    return WeekTimeSlot(
        startMinutes = normalizedStart,
        endMinutes = normalizedEnd,
    ).takeIf { it.startMinutes < it.endMinutes }
}

private fun normalizeWeekTimeSlots(slots: List<WeekTimeSlot>): List<WeekTimeSlot> {
    val normalized = mutableListOf<WeekTimeSlot>()
    slots.sortedBy { it.startMinutes }.forEach { slot ->
        val adjustedStart = if (normalized.isEmpty()) {
            slot.startMinutes.coerceIn(0, DAY_END_MINUTES - 1)
        } else {
            max(slot.startMinutes, normalized.last().endMinutes).coerceAtMost(DAY_END_MINUTES - 1)
        }
        val adjustedEnd = slot.endMinutes
            .coerceAtLeast(adjustedStart + 1)
            .coerceAtMost(DAY_END_MINUTES)
        if (adjustedStart < adjustedEnd) {
            normalized += WeekTimeSlot(
                startMinutes = adjustedStart,
                endMinutes = adjustedEnd,
            )
        }
    }
    return normalized
}

private fun slotOverlapsRange(
    slot: WeekTimeSlot,
    startMinutes: Int,
    endMinutes: Int,
): Boolean {
    return slot.endMinutes > startMinutes && slot.startMinutes < endMinutes
}
