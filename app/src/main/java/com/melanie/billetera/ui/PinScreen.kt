package com.melanie.billetera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.melanie.billetera.WalletViewModel
import com.melanie.billetera.ui.theme.*
import kotlinx.coroutines.delay

private const val PIN_LENGTH = 4

/** Crear PIN (si vm.needsPinSetup) o desbloquear con PIN. */
@Composable
fun PinScreen(vm: WalletViewModel) {
    if (vm.needsPinSetup) PinSetup(vm) else PinUnlock(vm)
}

@Composable
private fun PinUnlock(vm: WalletViewModel) {
    val name = vm.session?.name?.substringBefore(' ').orEmpty()
    PinFrame(
        title = "Hola, $name",
        subtitle = "Ingresa tu PIN para continuar",
        error = vm.pinError,
        onComplete = vm::unlockWithPin,
        footer = {
            TextButton({ vm.forgotPin() }) {
                Text("¿Olvidaste tu PIN? Entra con contraseña", color = Color.White.copy(alpha = .7f), fontSize = 13.sp)
            }
        },
    )
}

@Composable
private fun PinSetup(vm: WalletViewModel) {
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    PinFrame(
        title = if (first == null) "Crea tu PIN" else "Confírmalo",
        subtitle = if (first == null) "4 dígitos para entrar más rápido" else "Escríbelo otra vez",
        error = error,
        onComplete = { pin ->
            val f = first
            when {
                f == null -> {
                    val weak = pin.toSet().size == 1 || pin in listOf("1234", "4321", "0123", "3210")
                    if (weak) error = "Elige un PIN menos fácil de adivinar"
                    else { first = pin; error = null }
                }
                f == pin -> vm.setupPin(pin)
                else -> { first = null; error = "No coinciden, empieza de nuevo" }
            }
        },
        footer = {
            TextButton({ vm.skipPinSetup() }) {
                Text("Ahora no", color = Color.White.copy(alpha = .7f), fontSize = 14.sp)
            }
        },
    )
}

@Composable
private fun PinFrame(
    title: String, subtitle: String, error: String?,
    onComplete: (String) -> Unit, footer: @Composable () -> Unit,
) {
    var digits by remember { mutableStateOf("") }

    LaunchedEffect(digits) {
        if (digits.length == PIN_LENGTH) {
            delay(140)
            onComplete(digits)
            digits = ""
        }
    }

    Column(
        Modifier.fillMaxSize().background(BrandDark).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("mel", color = Color.White, fontSize = 40.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
            Text(".", color = Lime, fontSize = 40.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(28.dp))
        Text(title, color = Color.White, fontSize = 22.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = Color.White.copy(alpha = .7f), fontSize = 15.sp, textAlign = TextAlign.Center)

        Spacer(Modifier.height(30.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            repeat(PIN_LENGTH) { i ->
                val filled = i < digits.length
                Box(
                    Modifier.size(16.dp).clip(CircleShape)
                        .background(if (filled) Lime else Color.White.copy(alpha = .18f))
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            error.orEmpty(), color = Color(0xFFFF8A80), fontSize = 13.sp,
            textAlign = TextAlign.Center, modifier = Modifier.height(36.dp),
        )

        Spacer(Modifier.weight(1f))
        Keypad(
            onDigit = { if (digits.length < PIN_LENGTH) digits += it },
            onBackspace = { digits = digits.dropLast(1) },
        )
        Spacer(Modifier.height(8.dp))
        footer()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Keypad(onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "<"))
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(Modifier.size(72.dp))
                        "<" -> Box(
                            Modifier.size(72.dp).clip(CircleShape).clickable(onClick = onBackspace),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.AutoMirrored.Rounded.Backspace, "Borrar", tint = Color.White.copy(alpha = .8f)) }
                        else -> Box(
                            Modifier.size(72.dp).clip(CircleShape).background(Color.White.copy(alpha = .09f))
                                .clickable { onDigit(key) },
                            contentAlignment = Alignment.Center,
                        ) { Text(key, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium) }
                    }
                }
            }
        }
    }
}
