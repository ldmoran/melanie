// Backend simulado: REST (Express) + WebSocket (ws) en el mismo puerto.
const express = require('express');
const http = require('http');
const { WebSocketServer } = require('ws');

const PORT = process.env.PORT || 3000;
const app = express();
app.use(express.json());

// cuentas en memoria: id -> saldo
const accounts = new Map([['ACC-1001', 100.0], ['ACC-2002', 500.0]]);
const sockets = new Map(); // id -> Set<WebSocket>

const round = (n) => Math.round(n * 100) / 100;
const bal = (id) => { if (!accounts.has(id)) accounts.set(id, 500.0); return accounts.get(id); };
const fail = (res, code, message) => res.status(code).json({ ok: false, message });
const validAmount = (a) => typeof a === 'number' && isFinite(a) && a > 0;

function push(account, payload) {
  (sockets.get(account) || new Set()).forEach((ws) => {
    if (ws.readyState === 1) ws.send(JSON.stringify(payload));
  });
}

app.use((req, _res, next) => { console.log(`${req.method} ${req.url}`, req.body || ''); next(); });

app.get('/api/balance/:account', (req, res) =>
  res.json({ ok: true, account: req.params.account, balance: bal(req.params.account) }));

app.post('/api/deposit', (req, res) => {
  const { account, amount } = req.body;
  if (!account || !validAmount(amount)) return fail(res, 400, 'Datos inválidos');
  accounts.set(account, round(bal(account) + amount));
  res.json({ ok: true, message: 'Depósito exitoso', balance: accounts.get(account), reference: ref('DEP') });
});

app.post('/api/withdraw', (req, res) => {
  const { account, amount } = req.body;
  if (!account || !validAmount(amount)) return fail(res, 400, 'Datos inválidos');
  if (bal(account) < amount) return fail(res, 409, 'Saldo insuficiente');
  accounts.set(account, round(bal(account) - amount));
  res.json({ ok: true, message: 'Retiro exitoso', balance: accounts.get(account), reference: ref('RET') });
});

app.post('/api/transfer', (req, res) => {
  const { from, to, amount } = req.body;
  if (!from || !to || !validAmount(amount)) return fail(res, 400, 'Datos inválidos');
  if (from === to) return fail(res, 400, 'No puedes transferirte a ti mismo');
  if (bal(from) < amount) return fail(res, 409, 'Saldo insuficiente');
  accounts.set(from, round(bal(from) - amount));
  accounts.set(to, round(bal(to) + amount));
  // notificación en tiempo real al receptor
  push(to, {
    type: 'TRANSFER_IN', from, amount,
    balance: accounts.get(to), timestamp: Date.now(), reference: ref('TRF'),
  });
  res.json({ ok: true, message: 'Transferencia enviada', balance: accounts.get(from), reference: ref('TRF') });
});

// Pago de servicios. Prefijos: LUZ-, AGUA-, NET-
app.post('/api/services/pay', (req, res) => {
  const { account, contract, amount } = req.body;
  if (!account || !contract || !validAmount(amount)) return fail(res, 400, 'Datos inválidos');
  const m = /^(LUZ|AGUA|NET)-\d{4,10}$/i.exec(String(contract).trim());
  if (!m) return fail(res, 404, 'Código de contrato no encontrado (ej. LUZ-123456)');
  if (bal(account) < amount) return fail(res, 409, 'Saldo insuficiente');
  accounts.set(account, round(bal(account) - amount));
  const names = { LUZ: 'Luz', AGUA: 'Agua', NET: 'Internet' };
  res.json({
    ok: true, message: 'Servicio pagado', balance: accounts.get(account),
    service: names[m[1].toUpperCase()], reference: ref('SRV'),
  });
});

let seq = 0;
function ref(p) { return `${p}-${Date.now().toString(36).toUpperCase()}${(seq++ % 100).toString().padStart(2, '0')}`; }

const server = http.createServer(app);
const wss = new WebSocketServer({ server, path: '/ws' });

wss.on('connection', (ws, req) => {
  const account = new URL(req.url, 'http://x').searchParams.get('account');
  if (!account) return ws.close(1008, 'account requerido');
  if (!sockets.has(account)) sockets.set(account, new Set());
  sockets.get(account).add(ws);
  console.log(`WS conectado: ${account}`);
  ws.send(JSON.stringify({ type: 'CONNECTED', account, balance: bal(account) }));
  ws.on('close', () => { sockets.get(account)?.delete(ws); console.log(`WS cerrado: ${account}`); });
});

server.listen(PORT, '0.0.0.0', () => console.log(`Backend en http://0.0.0.0:${PORT}`));
