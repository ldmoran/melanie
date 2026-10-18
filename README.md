# Mel Wallet – Billetera Móvil Distribuida (Android / Kotlin)

Cliente Android (Jetpack Compose) + backend simulado (Node) para el Proyecto Final UC 1.

## Arquitectura
| Requisito | Implementación |
|---|---|
| Room (SQLite) | `data/local/Local.kt` – entidad, DAO (`Flow`), base de datos |
| REST | `data/remote/Remote.kt` – Retrofit + OkHttp (`/api/deposit`, `withdraw`, `transfer`, `services/pay`) |
| WebSocket | `WalletSocket` – OkHttp WS con reconexión automática, `TRANSFER_IN` actualiza saldo e historial |
| Corrutinas | Red y Room en `Dispatchers.IO`; la UI se actualiza vía `StateFlow` en `Main` (`WalletViewModel.kt`) |
| UI | `ui/WalletScreen.kt` – saldo, indicador En línea/Desconectado, 4 acciones con diálogos, historial verde/rojo, icono distinto para servicios |

## 1. Levantar el backend
```bash
cd backend
npm install
npm start      # http://localhost:3000
```

## 2. Compilar / ejecutar la app
Abrir la carpeta `melanie` en Android Studio y ejecutar en el emulador (usa `10.0.2.2:3000` por defecto).
En un celular real: ⚙ dentro de la app → poner `IP-de-tu-PC:3000` (misma red WiFi).
APK: `app/build/outputs/apk/debug/app-debug.apk` (`./gradlew assembleDebug`, JDK 17+).

## 3. Demo con Postman (defensa)
Cuenta de la app: `ACC-1001`. Enviar dinero "desde otro usuario":

`POST http://localhost:3000/api/transfer`
```json
{ "from": "ACC-2002", "to": "ACC-1001", "amount": 50 }
```
El saldo y el historial de la app cambian al instante por WebSocket.

Pago de servicios: contrato `LUZ-123456`, `AGUA-123456` o `NET-123456`. Error de saldo insuficiente responde HTTP 409 y la app muestra el mensaje.
