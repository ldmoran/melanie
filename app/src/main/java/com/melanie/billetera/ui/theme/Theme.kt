package com.melanie.billetera.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val BrandDark = Color(0xFF0A2B27)
val Brand = Color(0xFF13524A)
val BrandLight = Color(0xFF1E7568)
val Lime = Color(0xFFD6F26B)
val Income = Color(0xFF17995F)
val Expense = Color(0xFFD64541)
val Cream = Color(0xFFF5F2EA)
val Ink = Color(0xFF14201E)
val Muted = Color(0xFF6C7875)

private val scheme = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    secondary = Income,
    background = Cream,
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
