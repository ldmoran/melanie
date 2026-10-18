package com.melanie.billetera.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.melanie.billetera.WalletViewModel
import com.melanie.billetera.ui.theme.*

/** Requisitos de contraseña (los mismos que valida el servidor). */
private val passwordRules: List<Pair<String, (String) -> Boolean>> = listOf(
    "Mínimo 8 caracteres" to { p -> p.length >= 8 },
    "Una letra mayúscula" to { p -> p.any { it.isUpperCase() } },
    "Una letra minúscula" to { p -> p.any { it.isLowerCase() } },
    "Un número" to { p -> p.any { it.isDigit() } },
)

@Composable
fun AuthScreen(vm: WalletViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var showServer by remember { mutableStateOf(false) }

    var register by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var tried by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.messages.collect {
            snack.currentSnackbarData?.dismiss()
            snack.showSnackbar(it.text)
        }
    }

    val emailOk = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim())
    val nameErr = tried && register && name.trim().length < 2
    val emailErr = tried && !emailOk
    val passOk = passwordRules.all { it.second(pass) }
    val passErr = tried && (if (register) !passOk else pass.isEmpty())
    val pass2Err = tried && register && pass2 != pass

    fun submit() {
        tried = true
        val valid = emailOk && !nameErr && (if (register) passOk && pass2 == pass else pass.isNotEmpty())
            && (!register || name.trim().length >= 2)
        if (valid) vm.authenticate(register, name, email, pass)
    }

    Scaffold(
        containerColor = BrandDark,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = {
            SnackbarHost(snack, Modifier.imePadding().navigationBarsPadding()) {
                Snackbar(it, shape = RoundedCornerShape(14.dp), containerColor = Lime, contentColor = BrandDark)
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
            // ---- parte superior: marca ----
            Box(Modifier.fillMaxWidth().height(if (register) 150.dp else 220.dp)) {
                Canvas(Modifier.matchParentSize()) {
                    val stroke = Stroke(width = 2f)
                    val c = Color.White.copy(alpha = .07f)
                    drawCircle(c, radius = size.width * .55f, center = Offset(size.width * .95f, size.height * .15f), style = stroke)
                    drawCircle(c, radius = size.width * .38f, center = Offset(size.width * .95f, size.height * .15f), style = stroke)
                    drawCircle(Lime.copy(alpha = .9f), radius = 9f, center = Offset(size.width * .78f, size.height * .62f))
                    drawRoundRect(
                        Color.White.copy(alpha = .05f), topLeft = Offset(-40f, size.height * .72f),
                        size = Size(size.width * .5f, 90f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(45f),
                    )
                }
                Column(
                    Modifier.statusBarsPadding().padding(horizontal = 28.dp).padding(top = 28.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("mel", color = Color.White, fontSize = 54.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                        Text(".", color = Lime, fontSize = 54.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                    }
                    AnimatedVisibility(!register) {
                        Text(
                            "Tu plata, a la mano\ny sin tanta vuelta.",
                            color = Color.White.copy(alpha = .72f), fontSize = 16.sp, lineHeight = 22.sp,
                        )
                    }
                }
            }

            // ---- hoja del formulario ----
            Column(
                Modifier.fillMaxWidth().weight(1f)
                    .clip(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
                    .background(Cream)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 26.dp).padding(top = 26.dp, bottom = 20.dp)
                    .navigationBarsPadding(),
            ) {
                // pestañas
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    AuthTab("Ingresar", !register) { register = false; tried = false }
                    AuthTab("Crear cuenta", register) { register = true; tried = false }
                }
                Spacer(Modifier.height(22.dp))

                AnimatedVisibility(register) {
                    Column {
                        Field(name, { name = it }, "Nombre completo", Icons.Rounded.Person, nameErr, "Escribe tu nombre",
                            KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words, imeAction = ImeAction.Next))
                        Spacer(Modifier.height(12.dp))
                    }
                }
                Field(email, { email = it }, "Correo", Icons.Rounded.Mail, emailErr, "Ingresa un correo válido",
                    KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
                Spacer(Modifier.height(12.dp))
                Field(pass, { pass = it }, "Contraseña", Icons.Rounded.Lock, passErr,
                    if (register) "Revisa los requisitos de abajo" else "Ingresa tu contraseña",
                    KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (register) ImeAction.Next else ImeAction.Done),
                    password = true, visible = showPass, onToggle = { showPass = !showPass }, onDone = ::submit)
                AnimatedVisibility(register) {
                    Column(Modifier.padding(start = 6.dp, top = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        passwordRules.forEach { (label, check) ->
                            val ok = check(pass)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                                    tint = if (ok) Income else Muted.copy(alpha = .6f), modifier = Modifier.size(15.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(label, fontSize = 12.sp, color = if (ok) Income else Muted)
                            }
                        }
                    }
                }
                AnimatedVisibility(register) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Field(pass2, { pass2 = it }, "Repite la contraseña", Icons.Rounded.Lock, pass2Err, "No coinciden",
                            KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            password = true, visible = showPass, onDone = ::submit)
                    }
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = ::submit, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandDark, contentColor = Color.White),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Lime, strokeWidth = 2.5.dp)
                    else Text(if (register) "Crear mi cuenta" else "Entrar", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { showServer = true }.padding(8.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Dns, null, tint = Muted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Servidor: ${vm.host}", color = Muted, fontSize = 13.sp)
                }
            }
        }
    }

    if (showServer) HostDialog(vm.host, { showServer = false }) { vm.saveHost(it); showServer = false }
}

@Composable
private fun AuthTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick)) {
        Text(
            text, fontSize = 20.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            color = if (selected) Ink else Muted.copy(alpha = .7f),
        )
        Spacer(Modifier.height(5.dp))
        Box(Modifier.height(3.dp).width(if (selected) 34.dp else 0.dp).clip(RoundedCornerShape(2.dp)).background(Brand))
    }
}

@Composable
private fun Field(
    value: String, onChange: (String) -> Unit, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    error: Boolean, errorText: String, options: KeyboardOptions,
    password: Boolean = false, visible: Boolean = false, onToggle: (() -> Unit)? = null, onDone: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, tint = if (error) Expense else Brand) },
        trailingIcon = if (password && onToggle != null) ({
            IconButton(onToggle) {
                Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Mostrar contraseña", tint = Muted)
            }
        }) else null,
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = options,
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone?.invoke() }),
        isError = error,
        supportingText = if (error) ({ Text(errorText) }) else null,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
            focusedBorderColor = Brand, unfocusedBorderColor = Color(0xFFDAD5C8),
            focusedLabelColor = Brand, cursorColor = Brand,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun HostDialog(host: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var h by remember { mutableStateOf(host) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White,
        title = { Text("Servidor", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                h, { h = it }, singleLine = true, label = { Text("host:puerto") },
                supportingText = { Text("Emulador: 10.0.2.2:3000 · Celular: IP de tu PC") },
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { Button({ onSave(h) }, shape = RoundedCornerShape(14.dp)) { Text("Guardar") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar", color = Muted) } },
    )
}
