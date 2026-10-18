package com.melanie.billetera.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.melanie.billetera.WalletViewModel
import com.melanie.billetera.data.local.TransactionEntity
import com.melanie.billetera.data.local.TxType
import com.melanie.billetera.money
import com.melanie.billetera.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Op(val title: String, val icon: ImageVector, val color: Color) {
    DEPOSIT("Depositar", Icons.Rounded.ArrowDownward, Color(0xFF12B76A)),
    WITHDRAW("Retirar", Icons.Rounded.ArrowUpward, Color(0xFFF79009)),
    TRANSFER("Transferir", Icons.Rounded.SwapHoriz, Color(0xFF13524A)),
    SERVICE("Pagar Servicios", Icons.Rounded.ReceiptLong, Color(0xFF0BA5EC)),
}

@Composable
fun WalletScreen(vm: WalletViewModel) {
    val balance by vm.balance.collectAsStateWithLifecycle()
    val connected by vm.connected.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    var op by remember { mutableStateOf<Op?>(null) }
    var showConfig by remember { mutableStateOf(false) }
    var lastOk by remember { mutableStateOf(true) }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect {
            lastOk = it.success
            snack.currentSnackbarData?.dismiss()
            snack.showSnackbar(it.text, duration = SnackbarDuration.Short)
        }
    }

    Scaffold(
        containerColor = Cream,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = {
            SnackbarHost(snack, modifier = Modifier.navigationBarsPadding()) {
                Snackbar(
                    snackbarData = it, shape = RoundedCornerShape(16.dp),
                    containerColor = if (lastOk) Ink else Expense, contentColor = Color.White,
                )
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
            Header(balance, connected, vm.session?.name.orEmpty(), vm.session?.account.orEmpty(), busy) { showConfig = true }

            Spacer(Modifier.height(18.dp))
            ActionPanel(enabled = !busy) { op = it }

            Spacer(Modifier.height(22.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Movimientos", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.weight(1f))
                Text("${history.size} registros", fontSize = 12.sp, color = Muted)
            }
            Spacer(Modifier.height(8.dp))

            if (history.isEmpty()) EmptyState()
            else LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(history, key = { it.id }) { TxRow(it) }
            }
        }
    }

    op?.let { current ->
        OperationDialog(
            op = current,
            onDismiss = { op = null },
            onConfirm = { target, amount ->
                when (current) {
                    Op.DEPOSIT -> vm.deposit(amount)
                    Op.WITHDRAW -> vm.withdraw(amount)
                    Op.TRANSFER -> vm.transfer(target, amount)
                    Op.SERVICE -> vm.payService(target, amount)
                }
                op = null
            },
        )
    }
    if (showConfig) AccountDialog(
        vm.session, vm.host, vm.pinEnabled, { showConfig = false }, { vm.saveHost(it) },
        onPin = { showConfig = false; if (vm.pinEnabled) vm.removePin() else vm.startPinSetup() },
    ) { showConfig = false; vm.logout() }
}

// ---------------- Cabecera ----------------
@Composable
private fun Header(balance: Double, connected: Boolean, name: String, account: String, busy: Boolean, onSettings: () -> Unit) {
    val animated by animateFloatAsState(balance.toFloat(), tween(700), label = "balance")
    Box(
        Modifier.fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(Brush.linearGradient(listOf(BrandDark, Brand, BrandLight)))
    ) {
        // círculos decorativos
        Box(Modifier.size(180.dp).offset(x = 230.dp, y = (-50).dp).clip(CircleShape).background(Color.White.copy(alpha = .07f)))
        Box(Modifier.size(120.dp).offset(x = (-30).dp, y = 110.dp).clip(CircleShape).background(Color.White.copy(alpha = .06f)))

        Column(Modifier.statusBarsPadding().padding(horizontal = 22.dp).padding(top = 14.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = .18f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.AccountBalanceWallet, null, tint = Color.White) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Hola, ${name.substringBefore(' ')}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Cuenta $account", color = Lime, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                ConnectionPill(connected)
                IconButton(onClick = onSettings) {
                    Icon(Icons.Rounded.Settings, "Configuración", tint = Color.White)
                }
            }
            Spacer(Modifier.height(26.dp))
            Text("Saldo actual", color = Color.White.copy(alpha = .75f), fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    money(animated.toDouble()), color = Color.White, fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (busy) {
                    Spacer(Modifier.width(12.dp))
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.5.dp)
                }
            }
        }
    }
}

@Composable
private fun ConnectionPill(connected: Boolean) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        1f, .35f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha",
    )
    val color = if (connected) Color(0xFF3DDC97) else Color(0xFFFF6B6B)
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .16f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).alpha(if (connected) pulse else 1f).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(if (connected) "En línea" else "Desconectado", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------------- Acciones ----------------
@Composable
private fun ActionPanel(enabled: Boolean, onClick: (Op) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Op.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { ActionTile(it, enabled, Modifier.weight(1f)) { onClick(it) } }
            }
        }
    }
}

@Composable
private fun ActionTile(op: Op, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier.height(76.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(22.dp), color = Color.White, shadowElevation = 3.dp,
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(op.color.copy(alpha = .14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(op.icon, null, tint = op.color, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.width(12.dp))
            Text(op.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink, maxLines = 2)
        }
    }
}

// ---------------- Historial ----------------
private val dateFmt = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale("es", "EC"))

@Composable
private fun TxRow(tx: TransactionEntity) {
    val (icon, tint, title) = when (tx.txType) {
        TxType.DEPOSIT -> Triple(Icons.Rounded.ArrowDownward, Income, "Depósito")
        TxType.WITHDRAW -> Triple(Icons.Rounded.ArrowUpward, Expense, "Retiro")
        TxType.TRANSFER_OUT -> Triple(Icons.Rounded.CallMade, Expense, "Transferencia enviada")
        TxType.TRANSFER_IN -> Triple(Icons.Rounded.CallReceived, Income, "Transferencia recibida")
        TxType.SERVICE -> Triple(
            when (tx.detail) {
                "Luz" -> Icons.Rounded.Bolt
                "Agua" -> Icons.Rounded.WaterDrop
                "Internet" -> Icons.Rounded.Wifi
                else -> Icons.Rounded.ReceiptLong
            }, Expense, "Servicio · ${tx.detail ?: "Básico"}"
        )
    }
    val sub = when (tx.txType) {
        TxType.TRANSFER_OUT -> "Para ${tx.counterpart}"
        TxType.TRANSFER_IN -> "De ${tx.counterpart}"
        TxType.SERVICE -> "${tx.category} · ${tx.counterpart}"
        else -> null
    }
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(tint.copy(alpha = .12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = tint) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub != null) Text(sub, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dateFmt.format(Date(tx.timestamp)), fontSize = 12.sp, color = Muted)
            }
            Text(
                (if (tx.isIncome) "+" else "−") + money(tx.amount),
                color = tint, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.ReceiptLong, null, tint = Muted.copy(alpha = .5f), modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(8.dp))
        Text("Aún no hay movimientos", color = Muted)
    }
}

// ---------------- Diálogos ----------------
@Composable
private fun OperationDialog(op: Op, onDismiss: () -> Unit, onConfirm: (String, Double) -> Unit) {
    var target by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }

    val amount = amountText.replace(',', '.').toDoubleOrNull()
    val amountErr = tried && (amount == null || amount <= 0)
    val needsTarget = op == Op.TRANSFER || op == Op.SERVICE
    val targetErr = tried && needsTarget && target.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White,
        icon = {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(op.color.copy(alpha = .14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(op.icon, null, tint = op.color, modifier = Modifier.size(28.dp)) }
        },
        title = { Text(op.title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (needsTarget) OutlinedTextField(
                    value = target, onValueChange = { target = it }, singleLine = true,
                    label = { Text(if (op == Op.TRANSFER) "Cuenta destino" else "Código de contrato") },
                    placeholder = { Text(if (op == Op.TRANSFER) "ACC-2002" else "LUZ-123456") },
                    supportingText = if (op == Op.SERVICE) ({ Text("Prefijos: LUZ-, AGUA-, NET-") }) else null,
                    isError = targetErr, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it }, singleLine = true,
                    label = { Text("Monto") }, prefix = { Text("$ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountErr,
                    supportingText = if (amountErr) ({ Text("Ingresa un monto mayor a 0") }) else null,
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    tried = true
                    if (amount != null && amount > 0 && (!needsTarget || target.isNotBlank()))
                        onConfirm(target.trim(), amount)
                },
                colors = ButtonDefaults.buttonColors(containerColor = op.color),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Confirmar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Muted) } },
    )
}

@Composable
private fun AccountDialog(
    session: com.melanie.billetera.Session?, host: String, pinEnabled: Boolean,
    onDismiss: () -> Unit, onSaveHost: (String) -> Unit, onPin: () -> Unit, onLogout: () -> Unit,
) {
    var h by remember { mutableStateOf(host) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White,
        title = { Text(session?.name ?: "Mi cuenta", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(session?.email.orEmpty(), color = Muted, fontSize = 14.sp)
                Text("Cuenta ${session?.account.orEmpty()}", color = Brand, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(h, { h = it }, singleLine = true, label = { Text("Servidor (host:puerto)") },
                    supportingText = { Text("Emulador: 10.0.2.2:3000") },
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                TextButton(onPin) {
                    Icon(Icons.Rounded.Lock, null, tint = Brand, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (pinEnabled) "Quitar PIN" else "Crear PIN de acceso", color = Brand, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onLogout) {
                    Icon(Icons.Rounded.Logout, null, tint = Expense, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cerrar sesión", color = Expense, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = { Button({ onSaveHost(h); onDismiss() }, shape = RoundedCornerShape(14.dp)) { Text("Guardar") } },
        dismissButton = { TextButton(onDismiss) { Text("Cerrar", color = Muted) } },
    )
}
