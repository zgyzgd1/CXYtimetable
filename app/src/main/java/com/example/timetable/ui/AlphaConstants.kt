package com.example.timetable.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// ─── Surface 透明度 ──────────────────────────────────────────────────
/** 卡片、容器等表面层的透明度控制 */
@Immutable
object SurfaceAlpha {
    /** 卡片默认背景透明度 (0.80f) */
    val card = 0.80f
    /** 卡片轻微透明 (0.82f) */
    val cardLight = 0.82f
    /** 卡片更透明 (0.84f) */
    val cardLighter = 0.84f
    /** 导航栏背景透明度 (0.80f) */
    val navigationBar = 0.80f
    /** 滚动容器背景透明度 (0.68f) */
    val scrolledContainer = 0.68f
    /** 固定顶部背景透明度 (0.86f) */
    val stickyHeader = 0.86f
    /** 周视图面板背景透明度 (0.25f) */
    val weekBoard = 0.25f
}

// ─── Border 边框透明度 ──────────────────────────────────────────────
@Immutable
object BorderAlpha {
    /** 卡片边框透明度 (0.14f) */
    val card = 0.14f
}

// ─── Content 内容文字透明度（精简语义层级）────────────────────────────
/**
 * 精简后的内容文字透明度层级。
 *
 * | 层级          | 值     | 用途           |
 * |--------------|--------|---------------|
 * | primary      | 0.92f  | 主要内容文字    |
 * | secondary    | 0.78f  | 次要内容文字    |
 * | hint         | 0.60f  | 提示 / hint    |
 * | disabled     | 0.38f  | 禁用状态       |
 */
@Immutable
object ContentAlpha {
    val primary = 0.92f
    val secondary = 0.78f
    val hint = 0.60f
    val disabled = 0.38f
}

// ─── Overlay 遮罩层透明度（精简语义层级）─────────────────────────────
/**
 * 精简后的遮罩层透明度层级。
 *
 * | 层级     | 值     | 用途             |
 * |---------|--------|-----------------|
 * | heavy   | 0.30f  | 选中 / 强调遮罩   |
 * | medium  | 0.20f  | hover / active   |
 * | light   | 0.10f  | 最轻遮罩          |
 * | subtle  | 0.05f  | 极轻点缀          |
 */
@Immutable
object OverlayAlpha {
    val heavy = 0.30f
    val medium = 0.20f
    val light = 0.10f
    val subtle = 0.05f
}

// ─── Accent 强调色透明度 ────────────────────────────────────────────
@Immutable
object AccentAlpha {
    /** 强调色最高透明度 (0.98f) */
    val highest = 0.98f
    /** 强调色高透明度 (0.72f) */
    val high = 0.72f
    /** 强调色中等透明度 (0.14f) */
    val medium = 0.14f
}

// ═══════════════════════════════════════════════════════════════════
// 便捷扩展函数
// ═══════════════════════════════════════════════════════════════════

// ── Surface ──────────────────────────────────────────────────────────
fun Color.surfaceCard(): Color = this.copy(alpha = SurfaceAlpha.card)
fun Color.surfaceCardLight(): Color = this.copy(alpha = SurfaceAlpha.cardLight)
fun Color.surfaceCardLighter(): Color = this.copy(alpha = SurfaceAlpha.cardLighter)
fun Color.navigationBar(): Color = this.copy(alpha = SurfaceAlpha.navigationBar)
fun Color.scrolledContainer(): Color = this.copy(alpha = SurfaceAlpha.scrolledContainer)
fun Color.stickyHeader(): Color = this.copy(alpha = SurfaceAlpha.stickyHeader)
fun Color.weekBoard(): Color = this.copy(alpha = SurfaceAlpha.weekBoard)

// ── Border ───────────────────────────────────────────────────────────
fun Color.borderCard(): Color = this.copy(alpha = BorderAlpha.card)

// ── Content（语义化命名）──────────────────────────────────────────────
fun Color.primaryContent(): Color = this.copy(alpha = ContentAlpha.primary)
fun Color.secondaryContent(): Color = this.copy(alpha = ContentAlpha.secondary)
fun Color.hintContent(): Color = this.copy(alpha = ContentAlpha.hint)
fun Color.disabledContent(): Color = this.copy(alpha = ContentAlpha.disabled)

// ── Overlay（语义化命名）──────────────────────────────────────────────
fun Color.overlayHeavy(): Color = this.copy(alpha = OverlayAlpha.heavy)
fun Color.overlayMedium(): Color = this.copy(alpha = OverlayAlpha.medium)
fun Color.overlayLight(): Color = this.copy(alpha = OverlayAlpha.light)
fun Color.overlaySubtle(): Color = this.copy(alpha = OverlayAlpha.subtle)

// ── Accent ───────────────────────────────────────────────────────────
fun Color.accentHighest(): Color = this.copy(alpha = AccentAlpha.highest)
fun Color.accentHigh(): Color = this.copy(alpha = AccentAlpha.high)
fun Color.accentMedium(): Color = this.copy(alpha = AccentAlpha.medium)
