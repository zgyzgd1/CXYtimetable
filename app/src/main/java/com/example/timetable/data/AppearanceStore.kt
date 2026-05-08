package com.example.timetable.data

import android.content.Context
import android.content.SharedPreferences

enum class AppBackgroundMode(val storageValue: String) {
    BUNDLED_IMAGE("bundled_image"),
    CUSTOM_IMAGE("custom_image"),
    GRADIENT("gradient");

    companion object {
        fun fromStorageValue(value: String?): AppBackgroundMode {
            return entries.firstOrNull { it.storageValue == value } ?: BUNDLED_IMAGE
        }
    }
}

data class BackgroundAppearance(
    val mode: AppBackgroundMode,
    val revision: Long,
    val imageTransform: BackgroundImageTransform,
)

internal data class ResolvedBackgroundMode(
    val mode: AppBackgroundMode,
    val shouldPersist: Boolean,
)

internal data class ResolvedBackgroundImageTransform(
    val imageTransform: BackgroundImageTransform,
    val shouldPersist: Boolean,
)

internal fun resolvePersistedBackgroundMode(
    storedModeValue: String?,
    hasCustomBackground: Boolean,
): ResolvedBackgroundMode {
    val parsedMode = AppBackgroundMode.entries.firstOrNull { it.storageValue == storedModeValue }
    return when {
        storedModeValue == null -> ResolvedBackgroundMode(AppBackgroundMode.BUNDLED_IMAGE, shouldPersist = true)
        parsedMode == null -> ResolvedBackgroundMode(AppBackgroundMode.BUNDLED_IMAGE, shouldPersist = true)
        parsedMode == AppBackgroundMode.CUSTOM_IMAGE && !hasCustomBackground -> {
            ResolvedBackgroundMode(AppBackgroundMode.BUNDLED_IMAGE, shouldPersist = true)
        }
        else -> ResolvedBackgroundMode(parsedMode, shouldPersist = false)
    }
}

internal fun resolvePersistedBackgroundImageTransform(
    storedScale: Float?,
    storedHorizontalBias: Float?,
    storedVerticalBias: Float?,
): ResolvedBackgroundImageTransform {
    val defaults = BackgroundImageTransform()
    val rawTransform = BackgroundImageTransform(
        scale = storedScale ?: defaults.scale,
        horizontalBias = storedHorizontalBias ?: defaults.horizontalBias,
        verticalBias = storedVerticalBias ?: defaults.verticalBias,
    )
    val normalized = rawTransform.normalized()
    return ResolvedBackgroundImageTransform(
        imageTransform = normalized,
        shouldPersist = storedScale == null ||
            storedHorizontalBias == null ||
            storedVerticalBias == null ||
            normalized.scale != rawTransform.scale ||
            normalized.horizontalBias != rawTransform.horizontalBias ||
            normalized.verticalBias != rawTransform.verticalBias,
    )
}

internal fun sanitizeWeekTimeSlots(
    slots: List<WeekTimeSlot>,
    fallbackSlots: List<WeekTimeSlot>,
): List<WeekTimeSlot> {
    val normalized = normalizeWeekTimeSlots(
        slots
            .map {
                WeekTimeSlot(
                    startMinutes = it.startMinutes.coerceIn(0, 24 * 60 - 1),
                    endMinutes = it.endMinutes.coerceIn(1, 24 * 60),
                )
            }
            .filter { it.startMinutes < it.endMinutes },
    )
    return normalized.ifEmpty { normalizeWeekTimeSlots(fallbackSlots) }
}

object AppearanceStore {
    private const val PREFS_NAME = "appearance_prefs"
    private const val KEY_BACKGROUND_MODE = "background_mode"
    private const val KEY_BACKGROUND_REVISION = "background_revision"
    private const val KEY_BACKGROUND_IMAGE_SCALE = "background_image_scale"
    private const val KEY_BACKGROUND_IMAGE_HORIZONTAL_BIAS = "background_image_horizontal_bias"
    private const val KEY_BACKGROUND_IMAGE_VERTICAL_BIAS = "background_image_vertical_bias"
    private const val KEY_WEEK_CARD_ALPHA = "week_card_alpha"
    private const val KEY_WEEK_CARD_HUE = "week_card_hue"
    private const val KEY_WEEK_TIME_SLOTS = "week_time_slots"
    private const val DEFAULT_BACKGROUND_REVISION = 0L
    private const val DEFAULT_WEEK_CARD_ALPHA = 0.82f
    private const val DEFAULT_WEEK_CARD_HUE = 0f

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun SharedPreferences.getOptionalFloat(key: String): Float? {
        return if (contains(key)) getFloat(key, 0f) else null
    }

    private fun nextBackgroundRevision(store: SharedPreferences): Long {
        return maxOf(
            System.currentTimeMillis(),
            store.getLong(KEY_BACKGROUND_REVISION, DEFAULT_BACKGROUND_REVISION) + 1L,
        )
    }

    /**
     * 确保 SharedPreferences 中存在合理的默认值。
     *
     * 此方法执行一次性数据迁移：当存储值缺失或无效时写入合理默认值。
     * 应在应用启动时（ViewModel 初始化时）调用一次，之后的 get 方法均为纯读取。
     */
    fun ensureDefaults(context: Context) {
        val store = prefs(context)
        val resolvedMode = resolvePersistedBackgroundMode(
            storedModeValue = store.getString(KEY_BACKGROUND_MODE, null),
            hasCustomBackground = BackgroundImageManager.hasCustomBackground(context),
        )
        val resolvedTransform = resolvePersistedBackgroundImageTransform(
            storedScale = store.getOptionalFloat(KEY_BACKGROUND_IMAGE_SCALE),
            storedHorizontalBias = store.getOptionalFloat(KEY_BACKGROUND_IMAGE_HORIZONTAL_BIAS),
            storedVerticalBias = store.getOptionalFloat(KEY_BACKGROUND_IMAGE_VERTICAL_BIAS),
        )
        val hasWork = resolvedMode.shouldPersist || resolvedTransform.shouldPersist
        if (!hasWork) return

        val revision = if (resolvedMode.shouldPersist) {
            nextBackgroundRevision(store)
        } else {
            store.getLong(KEY_BACKGROUND_REVISION, DEFAULT_BACKGROUND_REVISION)
        }
        store.edit()
            .apply {
                if (resolvedMode.shouldPersist) {
                    putString(KEY_BACKGROUND_MODE, resolvedMode.mode.storageValue)
                    putLong(KEY_BACKGROUND_REVISION, revision)
                }
                if (resolvedTransform.shouldPersist) {
                    putFloat(KEY_BACKGROUND_IMAGE_SCALE, resolvedTransform.imageTransform.scale)
                    putFloat(KEY_BACKGROUND_IMAGE_HORIZONTAL_BIAS, resolvedTransform.imageTransform.horizontalBias)
                    putFloat(KEY_BACKGROUND_IMAGE_VERTICAL_BIAS, resolvedTransform.imageTransform.verticalBias)
                }
            }
            .apply()

        // Sanitize week time slots if corrupt
        val rawSlots = rawWeekTimeSlots(context)
        val sanitized = sanitizeWeekTimeSlots(rawSlots, defaultWeekTimeSlots())
        if (sanitized != rawSlots) {
            store.edit().putString(KEY_WEEK_TIME_SLOTS, serializedWeekTimeSlots(sanitized)).apply()
        }
    }

    private fun rawWeekTimeSlots(context: Context): List<WeekTimeSlot> {
        val raw = prefs(context).getString(KEY_WEEK_TIME_SLOTS, null).orEmpty()
        return raw.split(';')
            .mapNotNull { token ->
                val parts = token.split('-')
                if (parts.size != 2) return@mapNotNull null
                val start = parts[0].toIntOrNull() ?: return@mapNotNull null
                val end = parts[1].toIntOrNull() ?: return@mapNotNull null
                WeekTimeSlot(start, end).takeIf { start in 0 until 24 * 60 && end in 1..24 * 60 && start < end }
            }
    }

    fun getBackgroundAppearance(context: Context): BackgroundAppearance {
        val store = prefs(context)
        val resolvedMode = resolvePersistedBackgroundMode(
            storedModeValue = store.getString(KEY_BACKGROUND_MODE, null),
            hasCustomBackground = BackgroundImageManager.hasCustomBackground(context),
        )
        val resolvedTransform = resolvePersistedBackgroundImageTransform(
            storedScale = store.getOptionalFloat(KEY_BACKGROUND_IMAGE_SCALE),
            storedHorizontalBias = store.getOptionalFloat(KEY_BACKGROUND_IMAGE_HORIZONTAL_BIAS),
            storedVerticalBias = store.getOptionalFloat(KEY_BACKGROUND_IMAGE_VERTICAL_BIAS),
        )
        val revision = store.getLong(KEY_BACKGROUND_REVISION, DEFAULT_BACKGROUND_REVISION)

        return BackgroundAppearance(
            mode = resolvedMode.mode,
            revision = revision,
            imageTransform = resolvedTransform.imageTransform,
        )
    }

    fun setBackgroundMode(context: Context, mode: AppBackgroundMode) {
        val store = prefs(context)
        store.edit()
            .putString(KEY_BACKGROUND_MODE, mode.storageValue)
            .putLong(KEY_BACKGROUND_REVISION, nextBackgroundRevision(store))
            .apply()
    }

    fun setCustomBackground(
        context: Context,
        imageTransform: BackgroundImageTransform = BackgroundImageTransform(),
    ) {
        val store = prefs(context)
        val normalized = imageTransform.normalized()
        store.edit()
            .putString(KEY_BACKGROUND_MODE, AppBackgroundMode.CUSTOM_IMAGE.storageValue)
            .putFloat(KEY_BACKGROUND_IMAGE_SCALE, normalized.scale)
            .putFloat(KEY_BACKGROUND_IMAGE_HORIZONTAL_BIAS, normalized.horizontalBias)
            .putFloat(KEY_BACKGROUND_IMAGE_VERTICAL_BIAS, normalized.verticalBias)
            .putLong(KEY_BACKGROUND_REVISION, nextBackgroundRevision(store))
            .apply()
    }

    fun getBackgroundImageTransform(context: Context): BackgroundImageTransform {
        val store = prefs(context)
        val resolved = resolvePersistedBackgroundImageTransform(
            storedScale = store.getOptionalFloat(KEY_BACKGROUND_IMAGE_SCALE),
            storedHorizontalBias = store.getOptionalFloat(KEY_BACKGROUND_IMAGE_HORIZONTAL_BIAS),
            storedVerticalBias = store.getOptionalFloat(KEY_BACKGROUND_IMAGE_VERTICAL_BIAS),
        )
        return resolved.imageTransform
    }

    fun setBackgroundImageTransform(context: Context, imageTransform: BackgroundImageTransform) {
        val store = prefs(context)
        val normalized = imageTransform.normalized()
        store.edit()
            .putFloat(KEY_BACKGROUND_IMAGE_SCALE, normalized.scale)
            .putFloat(KEY_BACKGROUND_IMAGE_HORIZONTAL_BIAS, normalized.horizontalBias)
            .putFloat(KEY_BACKGROUND_IMAGE_VERTICAL_BIAS, normalized.verticalBias)
            .putLong(KEY_BACKGROUND_REVISION, nextBackgroundRevision(store))
            .apply()
    }

    fun getWeekCardAlpha(context: Context): Float {
        return prefs(context).getFloat(KEY_WEEK_CARD_ALPHA, DEFAULT_WEEK_CARD_ALPHA)
            .coerceIn(0.35f, 1.0f)
    }

    fun setWeekCardAlpha(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_WEEK_CARD_ALPHA, value.coerceIn(0.35f, 1.0f)).apply()
    }

    fun getWeekCardHue(context: Context): Float {
        return prefs(context).getFloat(KEY_WEEK_CARD_HUE, DEFAULT_WEEK_CARD_HUE)
            .coerceIn(-180f, 180f)
    }

    fun setWeekCardHue(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_WEEK_CARD_HUE, value.coerceIn(-180f, 180f)).apply()
    }

    fun getWeekTimeSlots(context: Context): List<WeekTimeSlot> {
        val raw = prefs(context).getString(KEY_WEEK_TIME_SLOTS, null).orEmpty()
        val parsed = raw.split(';')
            .mapNotNull { token ->
                val parts = token.split('-')
                if (parts.size != 2) return@mapNotNull null
                val start = parts[0].toIntOrNull() ?: return@mapNotNull null
                val end = parts[1].toIntOrNull() ?: return@mapNotNull null
                WeekTimeSlot(start, end).takeIf { start in 0 until 24 * 60 && end in 1..24 * 60 && start < end }
            }
        if (raw.isBlank()) {
            return defaultWeekTimeSlots()
        }
        return sanitizeWeekTimeSlots(parsed, defaultWeekTimeSlots())
    }

    fun setWeekTimeSlots(context: Context, slots: List<WeekTimeSlot>): List<WeekTimeSlot> {
        val sanitized = sanitizeWeekTimeSlots(slots, defaultWeekTimeSlots())
        prefs(context).edit().putString(KEY_WEEK_TIME_SLOTS, serializedWeekTimeSlots(sanitized)).apply()
        return sanitized
    }

    fun defaultWeekTimeSlots(): List<WeekTimeSlot> {
        val slots = mutableListOf<WeekTimeSlot>()
        var start = 8 * 60
        repeat(20) {
            val end = (start + 40).coerceAtMost(24 * 60)
            if (start < end) {
                slots += WeekTimeSlot(start, end)
            }
            start += 45
        }
        return slots
    }

    private fun serializedWeekTimeSlots(slots: List<WeekTimeSlot>): String {
        return slots.joinToString(";") { "${it.startMinutes}-${it.endMinutes}" }
    }
}

data class WeekTimeSlot(
    val startMinutes: Int,
    val endMinutes: Int,
)
