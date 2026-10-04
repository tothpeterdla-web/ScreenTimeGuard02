const COMMAND_TTL_MS = 2 * 60 * 1000;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") return cors(new Response(null, { status: 204 }));
    if (request.method === "GET" && (url.pathname === "/" || url.pathname === "/parent")) {
      return new Response(parentPage(), {
        headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" }
      });
    }

    try {
      if (url.pathname === "/v1/register" && request.method === "POST") {
        return cors(await registerDevice(request, env));
      }
      if (url.pathname === "/v1/unlock" && request.method === "POST") {
        return cors(await createUnlock(request, env));
      }
      if (url.pathname === "/v1/command" && request.method === "GET") {
        return cors(await getCommand(request, env, url));
      }
      if (url.pathname === "/v1/ack" && request.method === "POST") {
        return cors(await acknowledge(request, env));
      }
      if (url.pathname === "/v1/revoke" && request.method === "POST") {
        return cors(await revoke(request, env));
      }
      return cors(json({ error: "not_found" }, 404));
    } catch (error) {
      console.error(error);
      return cors(json({ error: "server_error" }, 500));
    }
  }
};

async function registerDevice(request, env) {
  const body = await readJson(request);
  const deviceId = validDeviceId(body.deviceId);
  const secret = bearer(request);
  if (!deviceId || !validSecret(secret)) return json({ error: "bad_request" }, 400);

  const hash = await sha256(secret);
  const existing = await env.DB.prepare(
    "SELECT secret_hash FROM devices WHERE device_id = ?"
  ).bind(deviceId).first();

  if (existing) {
    if (!timingSafeEqual(existing.secret_hash, hash)) return json({ error: "device_already_registered" }, 409);
    return json({ ok: true });
  }

  await env.DB.prepare(
    "INSERT INTO devices(device_id, secret_hash, created_at) VALUES (?, ?, ?)"
  ).bind(deviceId, hash, Date.now()).run();
  return json({ ok: true }, 201);
}

async function createUnlock(request, env) {
  const body = await readJson(request);
  const deviceId = validDeviceId(body.deviceId);
  const secret = bearer(request);
  if (!deviceId || !validSecret(secret)) return json({ error: "bad_request" }, 400);
  if (!(await authorized(env, deviceId, secret))) return json({ error: "unauthorized" }, 401);

  let action = String(body.action || "");
  let minutes = null;
  if (action === "unlock_for_minutes") {
    minutes = Number(body.minutes);
    if (![15, 30, 60].includes(minutes)) return json({ error: "invalid_duration" }, 400);
  } else if (action !== "unlock_until_midnight") {
    return json({ error: "invalid_action" }, 400);
  }

  const now = Date.now();
  const id = crypto.randomUUID();
  await env.DB.batch([
    env.DB.prepare(
      "UPDATE commands SET consumed_at = ? WHERE device_id = ? AND consumed_at IS NULL"
    ).bind(now, deviceId),
    env.DB.prepare(
      "INSERT INTO commands(id, device_id, action, minutes, created_at, expires_at, consumed_at) VALUES (?, ?, ?, ?, ?, ?, NULL)"
    ).bind(id, deviceId, action, minutes, now, now + COMMAND_TTL_MS)
  ]);

  return json({ ok: true, id });
}

async function getCommand(request, env, url) {
  const deviceId = validDeviceId(url.searchParams.get("deviceId"));
  const secret = bearer(request);
  if (!deviceId || !validSecret(secret)) return json({ error: "bad_request" }, 400);
  if (!(await authorized(env, deviceId, secret))) return json({ error: "unauthorized" }, 401);

  const now = Date.now();
  await env.DB.prepare(
    "UPDATE commands SET consumed_at = ? WHERE device_id = ? AND consumed_at IS NULL AND expires_at <= ?"
  ).bind(now, deviceId, now).run();

  const command = await env.DB.prepare(
    "SELECT id, action, minutes, created_at, expires_at FROM commands WHERE device_id = ? AND consumed_at IS NULL AND expires_at > ? ORDER BY created_at DESC LIMIT 1"
  ).bind(deviceId, now).first();

  if (!command) return new Response(null, { status: 204 });
  return json({
    id: command.id,
    action: command.action,
    minutes: command.minutes,
    createdAt: command.created_at,
    expiresAt: command.expires_at
  });
}

async function acknowledge(request, env) {
  const body = await readJson(request);
  const deviceId = validDeviceId(body.deviceId);
  const id = typeof body.id === "string" ? body.id : "";
  const secret = bearer(request);
  if (!deviceId || !id || !validSecret(secret)) return json({ error: "bad_request" }, 400);
  if (!(await authorized(env, deviceId, secret))) return json({ error: "unauthorized" }, 401);

  await env.DB.prepare(
    "UPDATE commands SET consumed_at = ? WHERE id = ? AND device_id = ?"
  ).bind(Date.now(), id, deviceId).run();
  return json({ ok: true });
}

async function revoke(request, env) {
  const body = await readJson(request);
  const deviceId = validDeviceId(body.deviceId);
  const secret = bearer(request);
  if (!deviceId || !validSecret(secret)) return json({ error: "bad_request" }, 400);
  if (!(await authorized(env, deviceId, secret))) return json({ error: "unauthorized" }, 401);

  await env.DB.batch([
    env.DB.prepare("DELETE FROM commands WHERE device_id = ?").bind(deviceId),
    env.DB.prepare("DELETE FROM devices WHERE device_id = ?").bind(deviceId)
  ]);
  return json({ ok: true });
}

async function authorized(env, deviceId, secret) {
  const row = await env.DB.prepare(
    "SELECT secret_hash FROM devices WHERE device_id = ?"
  ).bind(deviceId).first();
  if (!row) return false;
  return timingSafeEqual(row.secret_hash, await sha256(secret));
}

function bearer(request) {
  const value = request.headers.get("authorization") || "";
  return value.startsWith("Bearer ") ? value.slice(7).trim() : "";
}

function validDeviceId(value) {
  if (typeof value !== "string") return "";
  const v = value.trim();
  return /^[a-zA-Z0-9_-]{16,80}$/.test(v) ? v : "";
}

function validSecret(value) {
  return typeof value === "string" && /^[a-zA-Z0-9_-]{32,128}$/.test(value);
}

async function sha256(value) {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)].map(b => b.toString(16).padStart(2, "0")).join("");
}

function timingSafeEqual(a, b) {
  if (typeof a !== "string" || typeof b !== "string" || a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

async function readJson(request) {
  try { return await request.json(); } catch { return {}; }
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" }
  });
}

function cors(response) {
  const h = new Headers(response.headers);
  h.set("access-control-allow-origin", "*");
  h.set("access-control-allow-headers", "authorization, content-type");
  h.set("access-control-allow-methods", "GET, POST, OPTIONS");
  return new Response(response.body, { status: response.status, statusText: response.statusText, headers: h });
}

function parentPage() {
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Screen Time Guard Parent</title>
<style>
:root{color-scheme:dark}*{box-sizing:border-box}body{margin:0;background:#05090f;color:#eff6ff;font-family:system-ui,-apple-system,sans-serif;min-height:100vh;display:grid;place-items:center;padding:20px}.card{width:min(480px,100%);background:#142233;border:1px solid #52718c;border-radius:22px;padding:22px}h1{font-size:26px;margin:0 0 6px}p{color:#9bb1c9;line-height:1.45}button,input{width:100%;min-height:52px;border-radius:14px;border:1px solid #52718c;background:#0b1520;color:#eff6ff;padding:12px 14px;font-size:16px;margin-top:10px}button{cursor:pointer;font-weight:650}button.primary{border-color:#24b8ff}.grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}.grid button{margin-top:0}.status{margin:14px 0;padding:12px;border-radius:14px;background:#0b1520;color:#9bb1c9}.ok{color:#49e78c}.bad{color:#ff6778}.small{font-size:12px;word-break:break-all}.hidden{display:none}</style>
</head>
<body><main class="card">
<h1>Parent remote unlock</h1>
<p>Only temporary screen-time overrides are available here. Device Owner, uninstall protection and the guardian PIN cannot be disabled remotely.</p>
<div id="setup">
<input id="device" placeholder="Device ID" autocomplete="off">
<input id="secret" placeholder="Pairing secret" autocomplete="off">
<button class="primary" onclick="pair()">Pair this phone</button>
</div>
<div id="controls" class="hidden">
<div class="status">Paired device<br><span id="deviceLabel" class="small"></span></div>
<div class="grid">
<button onclick="unlockMinutes(15)">Unlock 15 min</button>
<button onclick="unlockMinutes(30)">Unlock 30 min</button>
<button onclick="unlockMinutes(60)">Unlock 1 hour</button>
<button class="primary" onclick="unlockMidnight()">Until midnight</button>
</div>
<div id="result" class="status">Ready.</div>
<button onclick="forget()">Forget this phone</button>
</div>
<script>
const key='stg_parent_pairing_v1';
let pairing=null;
function parseHash(){const p=new URLSearchParams(location.hash.slice(1));const d=p.get('device'),s=p.get('secret');if(d&&s){localStorage.setItem(key,JSON.stringify({device:d,secret:s}));history.replaceState(null,'',location.pathname);}}
function load(){parseHash();try{pairing=JSON.parse(localStorage.getItem(key)||'null')}catch{};render();}
function pair(){const device=document.getElementById('device').value.trim(),secret=document.getElementById('secret').value.trim();if(!device||!secret)return;pairing={device,secret};localStorage.setItem(key,JSON.stringify(pairing));render();}
function forget(){localStorage.removeItem(key);pairing=null;render();}
function render(){document.getElementById('setup').classList.toggle('hidden',!!pairing);document.getElementById('controls').classList.toggle('hidden',!pairing);if(pairing)document.getElementById('deviceLabel').textContent=pairing.device;}
async function send(body){const out=document.getElementById('result');out.className='status';out.textContent='Sending…';try{const r=await fetch('/v1/unlock',{method:'POST',headers:{'content-type':'application/json','authorization':'Bearer '+pairing.secret},body:JSON.stringify({deviceId:pairing.device,...body})});if(!r.ok)throw new Error('Request failed ('+r.status+')');out.className='status ok';out.textContent='Unlock command sent.';}catch(e){out.className='status bad';out.textContent=e.message;}}
function unlockMinutes(minutes){send({action:'unlock_for_minutes',minutes});}
function unlockMidnight(){send({action:'unlock_until_midnight'});}
load();
</script></main></body></html>`;
}
