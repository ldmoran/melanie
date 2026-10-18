// Backend: REST (Express) + WebSocket (ws) + SQLite embebido (node:sqlite, sin Docker).
// Requiere Node >= 22.13. La base se crea sola en backend/wallet.db
const express = require('express');
const http = require('http');
const path = require('path');
const crypto = require('crypto');
const { DatabaseSync } = require('node:sqlite');
const { WebSocketServer } = require('ws');

const PORT = process.env.PORT || 3000;
const db = new DatabaseSync(path.join(__dirname, 'wallet.db'));

db.exec(`
  PRAGMA journal_mode = WAL;
  CREATE TABLE IF NOT EXISTS users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          TEXT    NOT NULL,
    email         TEXT    NOT NULL UNIQUE,
    salt          TEXT    NOT NULL,
    password_hash TEXT    NOT NULL,
    account       TEXT    NOT NULL UNIQUE,
    balance       REAL    NOT NULL DEFAULT 0,
    created_at    INTEGER NOT NULL
  );
  CREATE TABLE IF NOT EXISTS sessions (
    token_hash TEXT PRIMARY KEY,
    user_id    INTEGER NOT NULL REFERENCES users(id),
    created_at INTEGER NOT NULL
  );
  CREATE TABLE IF NOT EXISTS movements (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER NOT NULL REFERENCES users(id),
    type        TEXT    NOT NULL,
    amount      REAL    NOT NULL,
    counterpart TEXT,
    reference   TEXT,
    created_at  INTEGER NOT NULL
  );
`);

// ---------- utilidades ----------
const round = (n) => Math.round(n * 100) / 100;
const sha256 = (s) => crypto.createHash('sha256').update(s).digest('hex');
const hashPassword = (password, salt) => crypto.scryptSync(password, salt, 64).toString('hex');
const validAmount = (a) => typeof a === 'number' && isFinite(a) && a > 0 && a <= 1_000_000;
const fail = (res, code, message) => res.status(code).json({ ok: false, message });
let seq = 0;
const ref = (p) => `${p}-${Date.now().toString(36).toUpperCase()}${(seq++ % 100).toString().padStart(2, '0')}`;

const q = {
  userByEmail: db.prepare('SELECT * FROM users WHERE email = ?'),
  userByAccount: db.prepare('SELECT * FROM users WHERE account = ?'),
  userById: db.prepare('SELECT * FROM users WHERE id = ?'),
  insertUser: db.prepare('INSERT INTO users (name,email,salt,password_hash,account,balance,created_at) VALUES (?,?,?,?,?,?,?)'),
  setBalance: db.prepare('UPDATE users SET balance = ? WHERE id = ?'),
  insertSession: db.prepare('INSERT INTO sessions (token_hash,user_id,created_at) VALUES (?,?,?)'),
  userByToken: db.prepare('SELECT u.* FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token_hash = ?'),
  insertMovement: db.prepare('INSERT INTO movements (user_id,type,amount,counterpart,reference,created_at) VALUES (?,?,?,?,?,?)'),
};

function tx(fn) {
  db.exec('BEGIN');
  try { const r = fn(); db.exec('COMMIT'); return r; }
  catch (e) { db.exec('ROLLBACK'); throw e; }
}

function newAccount() {
  for (;;) {
    const acc = 'ACC-' + (1000 + crypto.randomInt(9000));
    if (!q.userByAccount.get(acc)) return acc;
  }
}

function createUser(name, email, password, balance = 0, account = newAccount()) {
  const salt = crypto.randomBytes(16).toString('hex');
  q.insertUser.run(name, email, salt, hashPassword(password, salt), account, balance, Date.now());
  return q.userByEmail.get(email);
}

function openSession(user) {
  const token = crypto.randomBytes(32).toString('hex');
  q.insertSession.run(sha256(token), user.id, Date.now());
  return token;
}

const publicUser = (u) => ({ name: u.name, email: u.email, account: u.account, balance: u.balance });

// ---------- WebSocket: sockets por cuenta ----------
const sockets = new Map();
function push(account, payload) {
  (sockets.get(account) || new Set()).forEach((ws) => {
    if (ws.readyState === 1) ws.send(JSON.stringify(payload));
  });
}

// ---------- REST ----------
const app = express();
app.use(express.json());
app.use((req, _res, next) => {
  const safe = { ...req.body }; delete safe.password;
  console.log(`${req.method} ${req.url}`, safe);
  next();
});

app.post('/api/auth/register', (req, res) => {
  const name = String(req.body.name || '').trim();
  const email = String(req.body.email || '').trim().toLowerCase();
  const password = String(req.body.password || '');
  if (name.length < 2) return fail(res, 400, 'Ingresa tu nombre');
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return fail(res, 400, 'Correo no válido');
  if (password.length < 6) return fail(res, 400, 'La contraseña debe tener al menos 6 caracteres');
  if (q.userByEmail.get(email)) return fail(res, 409, 'Ese correo ya está registrado');
  const user = createUser(name, email, password);
  res.status(201).json({ ok: true, message: 'Cuenta creada', token: openSession(user), user: publicUser(user) });
});

app.post('/api/auth/login', (req, res) => {
  const email = String(req.body.email || '').trim().toLowerCase();
  const password = String(req.body.password || '');
  const user = q.userByEmail.get(email);
  const good = user && crypto.timingSafeEqual(
    Buffer.from(hashPassword(password, user.salt), 'hex'), Buffer.from(user.password_hash, 'hex'));
  if (!good) return fail(res, 401, 'Correo o contraseña incorrectos');
  res.json({ ok: true, message: 'Bienvenido', token: openSession(user), user: publicUser(user) });
});

// Todo lo siguiente requiere "Authorization: Bearer <token>"
app.use('/api', (req, res, next) => {
  const m = /^Bearer (.+)$/.exec(req.headers.authorization || '');
  const user = m && q.userByToken.get(sha256(m[1]));
  if (!user) return fail(res, 401, 'Sesión expirada, inicia sesión de nuevo');
  req.user = user;
  next();
});

app.get('/api/me', (req, res) => res.json({ ok: true, balance: req.user.balance, user: publicUser(req.user) }));

app.post('/api/deposit', (req, res) => {
  const { amount } = req.body;
  if (!validAmount(amount)) return fail(res, 400, 'Monto inválido');
  const balance = round(req.user.balance + amount);
  const reference = ref('DEP');
  tx(() => { q.setBalance.run(balance, req.user.id); q.insertMovement.run(req.user.id, 'DEPOSIT', amount, null, reference, Date.now()); });
  res.json({ ok: true, message: 'Depósito exitoso', balance, reference });
});

app.post('/api/withdraw', (req, res) => {
  const { amount } = req.body;
  if (!validAmount(amount)) return fail(res, 400, 'Monto inválido');
  if (req.user.balance < amount) return fail(res, 409, 'Saldo insuficiente');
  const balance = round(req.user.balance - amount);
  const reference = ref('RET');
  tx(() => { q.setBalance.run(balance, req.user.id); q.insertMovement.run(req.user.id, 'WITHDRAW', amount, null, reference, Date.now()); });
  res.json({ ok: true, message: 'Retiro exitoso', balance, reference });
});

app.post('/api/transfer', (req, res) => {
  const to = String(req.body.to || '').trim().toUpperCase();
  const { amount } = req.body;
  if (!to || !validAmount(amount)) return fail(res, 400, 'Datos inválidos');
  if (to === req.user.account) return fail(res, 400, 'No puedes transferirte a ti mismo');
  const dest = q.userByAccount.get(to);
  if (!dest) return fail(res, 404, 'La cuenta destino no existe');
  if (req.user.balance < amount) return fail(res, 409, 'Saldo insuficiente');
  const mine = round(req.user.balance - amount);
  const theirs = round(dest.balance + amount);
  const reference = ref('TRF');
  const now = Date.now();
  tx(() => {
    q.setBalance.run(mine, req.user.id);
    q.setBalance.run(theirs, dest.id);
    q.insertMovement.run(req.user.id, 'TRANSFER_OUT', amount, to, reference, now);
    q.insertMovement.run(dest.id, 'TRANSFER_IN', amount, req.user.account, reference, now);
  });
  // notificación en tiempo real al receptor
  push(dest.account, {
    type: 'TRANSFER_IN', from: req.user.account, amount, balance: theirs, timestamp: now, reference,
  });
  res.json({ ok: true, message: 'Transferencia enviada', balance: mine, reference });
});

// Pago de servicios. Prefijos: LUZ-, AGUA-, NET-
app.post('/api/services/pay', (req, res) => {
  const { contract, amount } = req.body;
  if (!contract || !validAmount(amount)) return fail(res, 400, 'Datos inválidos');
  const m = /^(LUZ|AGUA|NET)-\d{4,10}$/i.exec(String(contract).trim());
  if (!m) return fail(res, 404, 'Código de contrato no encontrado (ej. LUZ-123456)');
  if (req.user.balance < amount) return fail(res, 409, 'Saldo insuficiente');
  const balance = round(req.user.balance - amount);
  const reference = ref('SRV');
  const names = { LUZ: 'Luz', AGUA: 'Agua', NET: 'Internet' };
  tx(() => { q.setBalance.run(balance, req.user.id); q.insertMovement.run(req.user.id, 'SERVICE', amount, String(contract).toUpperCase(), reference, Date.now()); });
  res.json({ ok: true, message: 'Servicio pagado', balance, service: names[m[1].toUpperCase()], reference });
});

app.use((err, _req, res, _next) => {
  console.error(err);
  fail(res, err.status || 500, err.status === 400 ? 'Petición mal formada' : 'Error interno del servidor');
});

// ---------- servidor + WebSocket ----------
const server = http.createServer(app);
const wss = new WebSocketServer({ server, path: '/ws' });

wss.on('connection', (ws, req) => {
  const token = new URL(req.url, 'http://x').searchParams.get('token') || '';
  const user = q.userByToken.get(sha256(token));
  if (!user) return ws.close(1008, 'no autorizado');
  if (!sockets.has(user.account)) sockets.set(user.account, new Set());
  sockets.get(user.account).add(ws);
  console.log(`WS conectado: ${user.account}`);
  ws.send(JSON.stringify({ type: 'CONNECTED', account: user.account, balance: q.userById.get(user.id).balance }));
  ws.on('close', () => { sockets.get(user.account)?.delete(ws); console.log(`WS cerrado: ${user.account}`); });
});

server.listen(PORT, '0.0.0.0', () => console.log(`Backend en http://0.0.0.0:${PORT}  (SQLite: backend/wallet.db)`));
