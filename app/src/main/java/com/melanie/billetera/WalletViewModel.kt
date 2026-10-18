package com.melanie.billetera

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.melanie.billetera.data.local.AppDatabase
import com.melanie.billetera.data.local.TransactionEntity
import com.melanie.billetera.data.local.TxType
import com.melanie.billetera.data.remote.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WalletViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("wallet", Context.MODE_PRIVATE)
    private val dao = AppDatabase.get(app).transactionDao()
    private val socket = WalletSocket(viewModelScope)

    var host by mutableStateOf(prefs.getString("host", "10.0.2.2:3000")!!)
        private set
    var account by mutableStateOf(prefs.getString("account", "ACC-1001")!!)
        private set

    private var api: WalletApi = buildApi(host)

    private val _balance = MutableStateFlow(prefs.getFloat("balance", 0f).toDouble())
    val balance: StateFlow<Double> = _balance
    val connected: StateFlow<Boolean> = socket.connected

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages

    // Historial reactivo: Room -> Flow (en IO) -> UI (Main)
    val history: StateFlow<List<TransactionEntity>> = dao.observeAll()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Eventos del WebSocket: se procesan en IO y el StateFlow actualiza la UI en Main.
        viewModelScope.launch(Dispatchers.IO) {
            socket.events.collect { ev ->
                when (ev.type) {
                    "TRANSFER_IN" -> {
                        dao.insert(
                            TransactionEntity(
                                type = TxType.TRANSFER_IN.name, amount = ev.amount,
                                counterpart = ev.from, category = null, detail = null,
                                reference = ev.reference,
                                timestamp = if (ev.timestamp > 0) ev.timestamp else System.currentTimeMillis(),
                            )
                        )
                        setBalance(ev.balance)
                        _messages.emit(UiMessage("Recibiste ${money(ev.amount)} de ${ev.from}", true))
                    }
                    "CONNECTED" -> setBalance(ev.balance)
                }
            }
        }
        reconnect()
    }

    private fun setBalance(value: Double) {
        _balance.value = value
        prefs.edit().putFloat("balance", value.toFloat()).apply()
    }

    private fun reconnect() {
        socket.start(host, account)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.balance(account) }.onSuccess { setBalance(it.balance) }
        }
    }

    fun saveConfig(newHost: String, newAccount: String) {
        host = newHost.trim().ifBlank { host }
        account = newAccount.trim().ifBlank { account }
        prefs.edit().putString("host", host).putString("account", account).apply()
        api = buildApi(host)
        reconnect()
    }

    // ---------- Operaciones (REST en IO, persistencia en IO, resultado a Main) ----------
    private fun run(
        onOk: String,
        call: suspend () -> OpResponse,
        toTx: (OpResponse) -> TransactionEntity,
    ) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val res = withContext(Dispatchers.IO) {
                    val r = call()
                    dao.insert(toTx(r))
                    r
                }
                setBalance(res.balance)
                _messages.emit(UiMessage(onOk, true))
            } catch (e: Exception) {
                _messages.emit(UiMessage(e.friendlyMessage(), false))
            } finally {
                _busy.value = false
            }
        }
    }

    private fun tx(type: TxType, amount: Double, counterpart: String? = null,
                   category: String? = null, detail: String? = null, ref: String? = null) =
        TransactionEntity(
            type = type.name, amount = amount, counterpart = counterpart, category = category,
            detail = detail, reference = ref, timestamp = System.currentTimeMillis(),
        )

    fun deposit(amount: Double) = run("Depósito de ${money(amount)} exitoso",
        { api.deposit(DepositReq(account, amount)) }) { tx(TxType.DEPOSIT, amount, ref = it.reference) }

    fun withdraw(amount: Double) = run("Retiro de ${money(amount)} exitoso",
        { api.withdraw(DepositReq(account, amount)) }) { tx(TxType.WITHDRAW, amount, ref = it.reference) }

    fun transfer(to: String, amount: Double) = run("Enviaste ${money(amount)} a $to",
        { api.transfer(TransferReq(account, to, amount)) }) {
        tx(TxType.TRANSFER_OUT, amount, counterpart = to, ref = it.reference)
    }

    fun payService(contract: String, amount: Double) = run("Servicio pagado: ${money(amount)}",
        { api.payService(ServiceReq(account, contract.trim().uppercase(), amount)) }) {
        tx(TxType.SERVICE, amount, counterpart = contract.trim().uppercase(),
            category = "Servicio Básico", detail = it.service, ref = it.reference)
    }
}

data class UiMessage(val text: String, val success: Boolean)

fun money(v: Double): String = "$" + String.format(java.util.Locale.US, "%,.2f", v)
