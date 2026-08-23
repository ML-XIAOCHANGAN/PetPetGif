package com.xiaochangan.petpetgif.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.xiaochangan.petpetgif.R

/**
 * 摸头杀生成器主题 —— 复刻网页版 style.css 配色（固定浅色奶油）：
 * 奶油黄底 + 金色按钮 + 棕褐文字（--bg:#fff5d3 / --btn-bg:#fedc7d / --txt:#572e23），
 * 标题用 Balsamiq Sans、正文用 Work Sans（网站同款 Google Fonts，已内置）。
 */
val BalsamiqSans = FontFamily(Font(R.font.balsamiq_bold))
val WorkSans = FontFamily(
    Font(R.font.worksans_regular, FontWeight.Normal),
    Font(R.font.worksans_semibold, FontWeight.SemiBold),
)

/** 明色（网页版 :root）——唯一主题，无暗色切换 */
private val WebLightColors = lightColorScheme(
    primary = Color(0xFFFEDC7D),          // --btn-bg 金色按钮
    onPrimary = Color(0xFF943D1A),        // --btn-color 按钮棕字
    primaryContainer = Color(0xFFFEE9A8), // 浅金（滑块活动轨等）
    onPrimaryContainer = Color(0xFF7C4231),
    secondary = Color(0xFFB24A1F),        // --link 橙棕
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF6B816), // --gold 金橙（滑块拇指/焦点）
    onSecondaryContainer = Color(0xFF572E23),
    tertiary = Color(0xFFF6B816),
    onTertiary = Color(0xFF572E23),
    background = Color(0xFFFFF5D3),       // --bg 奶油黄
    onBackground = Color(0xFF572E23),     // --txt 棕褐
    surface = Color(0xFFFFFFFF),          // --fg 卡片白
    onSurface = Color(0xFF572E23),
    surfaceVariant = Color(0xFFFFFBF3),   // --preview-bg 预览奶油白
    onSurfaceVariant = Color(0xFF5E3226), // --txt-light
    outline = Color(0xFFF6B816),          // --input-border 金边框
    outlineVariant = Color(0x80E5C270),   // --input-track-bg 滑块轨
    error = Color(0xFFFF6E3F),            // --error
    onError = Color(0xFFFFFFFF),
)

private val WebTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = BalsamiqSans, fontWeight = FontWeight.Bold, fontSize = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = BalsamiqSans, fontWeight = FontWeight.Bold, fontSize = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = BalsamiqSans, fontWeight = FontWeight.Bold, fontSize = 17.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = WorkSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
    ),
    bodyLarge = TextStyle(fontFamily = WorkSans, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = WorkSans, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = WorkSans, fontSize = 12.sp),
    labelLarge = TextStyle(
        fontFamily = WorkSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
    ),
    labelMedium = TextStyle(fontFamily = WorkSans, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = WorkSans, fontSize = 11.sp),
)

@Composable
fun PetPetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WebLightColors,
        typography = WebTypography,
        content = content,
    )
}
