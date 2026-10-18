package com.melanie.billetera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.melanie.billetera.ui.AuthScreen
import com.melanie.billetera.ui.PinScreen
import com.melanie.billetera.ui.WalletScreen
import com.melanie.billetera.ui.theme.WalletTheme

class MainActivity : ComponentActivity() {
    private val vm: WalletViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WalletTheme {
                when {
                    vm.session == null -> AuthScreen(vm)
                    vm.needsPinSetup || vm.locked -> PinScreen(vm)
                    else -> WalletScreen(vm)
                }
            }
        }
    }
}

