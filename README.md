# Mel Wallet â€“ Billetera MÃ³vil Distribuida (Android / Kotlin)

Cliente Android (Jetpack Compose) + backend (Node + SQLite embebido, **sin Docker**).

## Arquitectura
| Requisito | ImplementaciÃ³n |
|---|---|
| Registro / Login | `ui/AuthScreen.kt` + `/api/auth/register` y `/api/auth/login`. ContraseÃ±as con **scrypt + sal** (nunca en texto plano); sesiÃ³n por token Bearer |
| Base de datos del servidor | **SQLite** (`backend/wallet.db`, se crea sola): usuarios, sesiones, movimientos |
| Room (SQLite local) | `data/local/Local.kt` â€“ historial por usuario, sobrevive al reiniciar |
| REST | `data/remote/Remote.kt` â€“ Retrofit + OkHttp (depÃ³sito, retiro, transferencia, pago de servicios) |
| WebSocket | `WalletSocket` â€“ OkHttp WS autenticado por token, reconexiÃ³n automÃ¡tica |
| Corrutinas | Red y Room en `Dispatchers.IO`; la UI se actualiza vÃ­a `StateFlow` en `Main` |

## 1. Levantar el backend (Node â‰¥ 22.13)
```bash
cd backend
npm install
npm start      # http://localhost:3000  (crea backend/wallet.db)
```

## 2. Ejecutar la app
Abrir la carpeta en Android Studio â†’ Run â–¶ en el emulador (usa `10.0.2.2:3000`).
En un celular real: en la pantalla de login, tocar **Servidor** y poner `IP-de-tu-PC:3000` (misma WiFi).
APK listo: `apk/mel-wallet.apk`.

## 3. Demo con Postman (defensa)
1. Crear una cuenta en la app (se le asigna un nÃºmero, p. ej. `ACC-4821`, visible en la cabecera).
2. Importar `backend/postman_collection.json`.
3. Ejecutar *1. Registrar otro usuario* y *2. Depositar fondos* (la base arranca vacÃ­a, sin datos precargados).
4. Poner la cuenta de la app en la variable `destino` y ejecutar *3. Transferir*.
   El saldo y el historial de la app cambian al instante por WebSocket.

Pago de servicios: contrato `LUZ-123456`, `AGUA-123456` o `NET-123456`.

