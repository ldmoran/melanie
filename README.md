# Mel Wallet – Billetera Móvil Distribuida (Android / Kotlin)

Cliente Android (Jetpack Compose) + backend (Node + SQLite embebido, **sin Docker**).

## Arquitectura
| Requisito | Implementación |
|---|---|
| Registro / Login | `ui/AuthScreen.kt` + `/api/auth/register` y `/api/auth/login`. Contraseñas con **scrypt + sal** (nunca en texto plano); sesión por token Bearer |
| Base de datos del servidor | **SQLite** (`backend/wallet.db`, se crea sola): usuarios, sesiones, movimientos |
| Room (SQLite local) | `data/local/Local.kt` – historial por usuario, sobrevive al reiniciar |
| REST | `data/remote/Remote.kt` – Retrofit + OkHttp (depósito, retiro, transferencia, pago de servicios) |
| WebSocket | `WalletSocket` – OkHttp WS autenticado por token, reconexión automática |
| Corrutinas | Red y Room en `Dispatchers.IO`; la UI se actualiza vía `StateFlow` en `Main` |

## 1. Levantar el backend (Node ≥ 22.13)
```bash
cd backend
npm install
npm start      # http://localhost:3000  (crea backend/wallet.db)
```

## 2. Ejecutar la app
Abrir la carpeta en Android Studio → Run ▶ en el emulador (usa `10.0.2.2:3000`).
En un celular real: en la pantalla de login, tocar **Servidor** y poner `IP-de-tu-PC:3000` (misma WiFi).
APK listo: `apk/mel-wallet.apk`.

## 3. Demo con Postman (defensa)
1. Crear una cuenta en la app (se le asigna un número, p. ej. `ACC-4821`, visible en la cabecera).
2. Importar `backend/postman_collection.json`.
3. Ejecutar *1. Login como Cliente Demo* (`demo@mel.com` / `demo1234`, tiene $1000).
4. Poner la cuenta de la app en la variable `destino` y ejecutar *2. Transferir*.
   El saldo y el historial de la app cambian al instante por WebSocket.

Pago de servicios: contrato `LUZ-123456`, `AGUA-123456` o `NET-123456`.
