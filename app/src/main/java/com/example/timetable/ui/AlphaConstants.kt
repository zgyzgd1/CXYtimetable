package com.example.timetable.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Surface 透明度常量
 * 用于卡片、容器等表面层的透明度控制
 */
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

/**
 * Border 边框透明度常量
 */
@Immutable
object BorderAlpha {
    /** 卡片边框透明度 (0.14f) */
    val card = 0.14f
}

/**
 * Overlay 覆盖层透明度常量
 * 用于渐变、遮罩等效果
 */
@Immutable
object OverlayAlpha {
    /** 主文本高透明度 (0.98f, 0.92f) */
    val primaryHigh = 0.98f
    
    /** 主文本中等透明度 (0.88f, 0.87f) */
    val primaryMedium = 0.88f
    
    /** 次要文本透明度 (0.82f, 0.80f) */
    val secondary = 0.82f
    
    /** 提示文本透明度 (0.78f, 0.72f) */
    val hint = 0.78f
    
    /** 装饰性低透明度 (0.72f) */
    val decorative = 0.72f
    
    /** 禁用/占位透明度 (0.70f, 0.7f) */
    val disabled = 0.70f
    
    /** 微弱透明度 (0.60f, 0.6f) */
    val faint = 0.60f
    
    /** 极弱透明度 (0.50f, 0.5f) */
    val veryFaint = 0.50f
    
    /** 背景叠加透明度 (0.34f) */
    val backgroundOverlay = 0.34f
    
    /** 选中状态透明度 (0.30f, 0.3f) */
    val selected = 0.30f
    
    /** 悬停状态透明度 (0.25f, 0.22f, 0.2f) */
    val hover = 0.25f
    
    /** 激活状态透明度 (0.18f) */
    val active = 0.18f
    
    /** 轻微激活透明度 (0.16f) */
    val activeLight = 0.16f
    
    /** 选中项浅色透明度 (0.14f, 0.12f) */
    val selectedLight = 0.14f
    
    /** 最浅透明度 (0.10f, 0.08f) */
    val lightest = 0.10f
    
    /** 中等偏上透明度 (0.56f) - 用于特殊叠加效果 */
    val mediumHigh = 0.56f
    
    /** 中等偏高透明度 (0.65f) - 用于对话框占位符 */
    val mediumHigher = 0.65f
}

/**
 * Accent 强调色透明度常量
 * 用于课程卡片的强调色变体
 */
@Immutable
object AccentAlpha {
    /** 强调色最高透明度 (0.98f) */
    val highest = 0.98f
    
    /** 强调色高透明度 (0.72f) */
    val high = 0.72f
    
    /** 强调色中等透明度 (0.14f) */
    val medium = 0.14f
}

/**
 * 便捷扩展函数：应用预定义的透明度
 */
fun Color.surfaceCard(): Color = this.copy(alpha = SurfaceAlpha.card)
fun Color.surfaceCardLight(): Color = this.copy(alpha = SurfaceAlpha.cardLight)
fun Color.surfaceCardLighter(): Color = this.copy(alpha = SurfaceAlpha.cardLighter)
fun Color.navigationBar(): Color = this.copy(alpha = SurfaceAlpha.navigationBar)
fun Color.scrolledContainer(): Color = this.copy(alpha = SurfaceAlpha.scrolledContainer)
fun Color.stickyHeader(): Color = this.copy(alpha = SurfaceAlpha.stickyHeader)
fun Color.weekBoard(): Color = this.copy(alpha = SurfaceAlpha.weekBoard)

fun Color.borderCard(): Color = this.copy(alpha = BorderAlpha.card)

fun Color.overlayPrimaryHigh(): Color = this.copy(alpha = OverlayAlpha.primaryHigh)
fun Color.overlayPrimaryMedium(): Color = this.copy(alpha = OverlayAlpha.primaryMedium)
fun Color.overlaySecondary(): Color = this.copy(alpha = OverlayAlpha.secondary)
fun Color.overlayHint(): Color = this.copy(alpha = OverlayAlpha.hint)
fun Color.overlayDecorative(): Color = this.copy(alpha = OverlayAlpha.decorative)
fun Color.overlayDisabled(): Color = this.copy(alpha = OverlayAlpha.disabled)
fun Color.overlayFaint(): Color = this.copy(alpha = OverlayAlpha.faint)
fun Color.overlayVeryFaint(): Color = this.copy(alpha = OverlayAlpha.veryFaint)
fun Color.overlayBackgroundOverlay(): Color = this.copy(alpha = OverlayAlpha.backgroundOverlay)
fun Color.overlaySelected(): Color = this.copy(alpha = OverlayAlpha.selected)
fun Color.overlayHover(): Color = this.copy(alpha = OverlayAlpha.hover)
fun Color.overlayActive(): Color = this.copy(alpha = OverlayAlpha.active)
fun Color.overlayActiveLight(): Color = this.copy(alpha = OverlayAlpha.activeLight)
fun Color.accentMedium(): Color = this.copy(alpha = AccentAlpha.medium)
fun Color.overlayLightest(): Color = this.copy(alpha = OverlayAlpha.lightest)
fun Color.overlayMediumHigh(): Color = this.copy(alpha = OverlayAlpha.mediumHigh)
fun Color.overlayMediumHigher(): Color = this.copy(alpha = OverlayAlpha.mediumHigher)

fun Color.accentHighest(): Color = this.copy(alpha = AccentAlpha.highest)
fun Color.accentHigh(): Color = this.copy(alpha = AccentAlpha.high)
