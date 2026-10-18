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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Session(val token: String, val name: String, val email: String, val account: String)

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("wallet", Context.MODE_PRIVATE)
    private val dao = AppDatabase.get(app).transactionDao()
    private val socket = WalletSocket(viewModelScope)

    var host by mutableStateOf(prefs.getString("host", "10.0.2.2:3000")!!)
        private set

    @Volatile private var token: String? = prefs.getString("token", null)
    var session by mutableStateOf(loadSession())
        private set

    private var api: WalletApi = buildApi(host) { token }

    private val accountFlow = MutableStateFlow(session?.account ?: "")

    private val _balance = MutableStateFlow(prefs.getFloat("balance", 0f).toDouble())
    val balance: StateFlow<Double> = _balance
    val connected: StateFlow<Boolean> = socket.connected

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages

    // Historial reactivo: Room -> Flow (en IO) -> UI (Main). Solo del usuario con sesión.
    val history: StateFlow<List<TransactionEntity>> = accountFlow
        .flatMapLatest { dao.observeAll(it) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Eventos del WebSocket: se procesan en IO y el StateFlow actualiza la UI en Main.
        viewModelScope.launch(Dispatchers.IO) {
            socket.events.collect { ev ->
                when (ev.type) {
                    "TRANSFER_IN" -> {
                        val owner = session?.account ?: return@collect
                        dao.insert(
                            TransactionEntity(
                                owner = owner, type = TxType.TRANSFER_IN.name, amount = ev.amount,
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
        if (session != null) connectSession()
    }

    private fun loadSession(): Session? {
        val t = prefs.getString("token", null) ?: return null
        return Session(
            t, prefs.getString("name", "")!!, prefs.getString("email", "")!!,
            prefs.getString("account", "")!!,
        )
    }

    private fun setBalance(value: Double) {
        _balance.value = value
        prefs.edit().putFloat("balance", value.toFloat()).apply()
    }

    private fun connectSession() {
        val t = token ?: return
        socket.start(host, t)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                api.me().let { r -> r.user?.let { setBalance(it.balance) } }
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) withContext(Dispatchers.Main) { logout("Tu sesión expiró") }
            } catch (_: Exception) { /* sin red: se usa el saldo guardado */ }
        }
    }

    fun saveHost(newHost: String) {
        host = newHost.trim().ifBlank { host }
        prefs.edit().putString("host", host).apply()
        api = buildApi(host) { token }
        if (session != null) connectSession()
    }

    // ---------- Autenticación ----------
    fun authenticate(register: Boolean, name: String, email: String, password: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val res = withContext(Dispatchers.IO) {
                    if (register) api.register(AuthReq(name.trim(), email.trim(), password))
                    else api.login(AuthReq(null, email.trim(), password))
                }
                val u = res.user!!
                token = res.token
                prefs.edit().putString("token", res.token).putString("name", u.name)
                    .putString("email", u.email).putString("account", u.account).apply()
                setBalance(u.balance)
                accountFlow.value = u.account
                session = Session(res.token!!, u.name, u.email, u.account)
                connectSession()
            } catch (e: Exception) {
                _messages.emit(UiMessage(e.friendlyMessage(), false))
            } finally {
                _busy.value = false
            }
        }
    }

    fun logout(reason: String? = null) {
        socket.stop()
        token = null
        prefs.edit().remove("token").remove("name").remove("email").remove("account")
            .remove("balance").apply()
        _balance.value = 0.0
        accountFlow.value = ""
        session = null
        if (reason != null) _messages.tryEmit(UiMessage(reason, false))
    }

    // ---------- Operaciones (REST en IO, persistencia en IO, resultado a Main) ----------
    private fun run(
        onOk: String,
        call: suspend () -> OpResponse,
        toTx: (OpResponse, String) -> TransactionEntity,
    ) {
        if (_busy.value) return
        val owner = session?.account ?: return
        viewModelScope.launch {
            _busy.value = true
            try {
                val res = withContext(Dispatchers.IO) {
                    val r = call()
                    dao.insert(toTx(r, owner))
                    r
                }
                setBalance(res.balance)
                _messages.emit(UiMessage(onOk, true))
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) logout(e.friendlyMessage())
                else _messages.emit(UiMessage(e.friendlyMessage(), false))
            } catch (e: Exception) {
                _messages.emit(UiMessage(e.friendlyMessage(), false))
            } finally {
                _busy.value = false
            }
        }
    }

    private fun tx(owner: String, type: TxType, amount: Double, counterpart: String? = null,
                   category: String? = null, detail: String? = null, ref: String? = null) =
        TransactionEntity(
            owner = owner, type = type.name, amount = amount, counterpart = counterpart,
            category = category, detail = detail, reference = ref,
            timestamp = System.currentTimeMillis(),
        )

    fun deposit(amount: Double) = run("Depósito de ${money(amount)} exitoso",
        { api.deposit(AmountReq(amount)) }) { r, o -> tx(o, TxType.DEPOSIT, amount, ref = r.reference) }

    fun withdraw(amount: Double) = run("Retiro de ${money(amount)} exitoso",
        { api.withdraw(AmountReq(amount)) }) { r, o -> tx(o, TxType.WITHDRAW, amount, ref = r.reference) }

    fun transfer(to: String, amount: Double) = run("Enviaste ${money(amount)} a ${to.uppercase()}",
        { api.transfer(TransferReq(to, amount)) }) { r, o ->
        tx(o, TxType.TRANSFER_OUT, amount, counterpart = to.uppercase(), ref = r.reference)
    }

    fun payService(contract: String, amount: Double) = run("Servicio pagado: ${money(amount)}",
        { api.payService(ServiceReq(contract.trim().uppercase(), amount)) }) { r, o ->
        tx(o, TxType.SERVICE, amount, counterpart = contract.trim().uppercase(),
            category = "Servicio Básico", detail = r.service, ref = r.reference)
    }
}

data class UiMessage(val text: String, val success: Boolean)

fun money(v: Double): String = "$" + String.format(java.util.Locale.US, "%,.2f", v)
