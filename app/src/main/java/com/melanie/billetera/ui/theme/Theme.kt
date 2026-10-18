package com.melanie.billetera.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Violet = Color(0xFF5B3FD9)
val VioletDark = Color(0xFF2E1A87)
val VioletLight = Color(0xFF8B6CFF)
val Income = Color(0xFF12B76A)
val Expense = Color(0xFFE5484D)
val Surface0 = Color(0xFFF4F3FA)
val Ink = Color(0xFF16142B)
val Muted = Color(0xFF7A7892)

private val scheme = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    secondary = Income,
    background = Surface0,
    surface = Color.White,
    onSurface = Ink,
    error = Expense,
)

@Composable
fun WalletTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
