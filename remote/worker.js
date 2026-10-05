const COMMAND_TTL_MS = 2 * 60 * 1000;
const MAX_PIN_ATTEMPTS_PER_DAY = 3;
const PIN_ITERATIONS = 120000;
let schemaReady = null;

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
      if (url.pathname.startsWith("/v1/")) await ensureSchema(env);

      if (url.pathname === "/v1/register" && request.method === "POST") {
        return cors(await registerDevice(request, env));
      }
      if (url.pathname === "/v1/pin-info" && request.method === "GET") {
        return cors(await getPinInfo(request, env, url));
      }
      if (url.pathname === "/v1/verify-pin" && request.method === "POST") {
        return cors(await verifyPin(request, env));
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

async function ensureSchema(env) {
  if (!schemaReady) {
    schemaReady = (async () => {
      await env.DB.batch([
        env.DB.prepare(
          "CREATE TABLE IF NOT EXISTS devices (device_id TEXT PRIMARY KEY, secret_hash TEXT NOT NULL, admin_secret_hash TEXT, guardian_proof TEXT, pin_salt TEXT, pin_iterations INTEGER, pin_attempt_day TEXT, pin_failed_attempts INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)"
        ),
        env.DB.prepare(
          "CREATE TABLE IF NOT EXISTS commands (id TEXT PRIMARY KEY, device_id TEXT NOT NULL, action TEXT NOT NULL, minutes INTEGER, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL, consumed_at INTEGER, FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE)"
        ),
        env.DB.prepare(
          "CREATE INDEX IF NOT EXISTS idx_commands_device_pending ON commands(device_id, consumed_at, created_at DESC)"
        )
      ]);

      const info = await env.DB.prepare("PRAGMA table_info(devices)").all();
      const columns = new Set((info.results || []).map(row => String(row.name)));
      if (!columns.has("admin_secret_hash")) {
        await env.DB.prepare("ALTER TABLE devices ADD COLUMN admin_secret_hash TEXT").run();
      }
      if (!columns.has("guardian_proof")) {
        await env.DB.prepare("ALTER TABLE devices ADD COLUMN guardian_proof TEXT").run();
      }
      if (!columns.has("pin_salt")) {
        await env.DB.prepare("ALTER TABLE devices ADD COLUMN pin_salt TEXT").run();
      }
      if (!columns.has("pin_iterations")) {
        await env.DB.prepare("ALTER TABLE devices ADD COLUMN pin_iterations INTEGER").run();
      }
      if (!columns.has("pin_attempt_day")) {
        await env.DB.prepare("ALTER TABLE devices ADD COLUMN pin_attempt_day TEXT").run();
      }
      if (!columns.has("pin_failed_attempts")) {
        await env.DB.prepare("ALTER TABLE devices ADD COLUMN pin_failed_attempts INTEGER NOT NULL DEFAULT 0").run();
      }
    })().catch(error => {
      schemaReady = null;
      throw error;
    });
  }
  await schemaReady;
}

async function registerDevice(request, env) {
  const body = await readJson(request);
  const deviceId = validDeviceId(body.deviceId);
  const guardianProof = validGuardianProof(body.guardianProof);
  const secret = bearer(request);
  const adminSecret = request.headers.get("x-device-admin") || "";
  const pinSalt = validPinSalt(body.pinSalt);
  const pinIterations = Number(body.pinIterations || 0);
  const wantsSecureRegistration = !!adminSecret || body.pinSalt != null || body.pinIterations != null;

  if (!deviceId || !guardianProof || !validSecret(secret)) {
    return json({ error: "bad_request" }, 400);
  }
  if (wantsSecureRegistration
      && (!validSecret(adminSecret) || !pinSalt || pinIterations !== PIN_ITERATIONS)) {
    return json({ error: "bad_request" }, 400);
  }

  const hash = await sha256(secret);
  const adminHash = wantsSecureRegistration ? await sha256(adminSecret) : null;
  const existing = await env.DB.prepare(
    "SELECT secret_hash, admin_secret_hash, guardian_proof FROM devices WHERE device_id = ?"
  ).bind(deviceId).first();

  if (existing) {
    if (!timingSafeEqual(existing.secret_hash, hash)) {
      return json({ error: "device_already_registered" }, 409);
    }

    if (existing.admin_secret_hash) {
      if (!wantsSecureRegistration) return json({ error: "device_admin_required" }, 409);
      if (!timingSafeEqual(existing.admin_secret_hash, adminHash)) {
        return json({ error: "device_admin_unauthorized" }, 401);
      }
      await env.DB.prepare(
        "UPDATE devices SET guardian_proof = ?, pin_salt = ?, pin_iterations = ?, pin_attempt_day = ?, pin_failed_attempts = 0 WHERE device_id = ?"
      ).bind(guardianProof, pinSalt, pinIterations, utcDay(), deviceId).run();
      return json({ ok: true, updated: true });
    }

    if (wantsSecureRegistration) {
      return json({ error: "legacy_admin_migration_required" }, 409);
    }
    if (!existing.guardian_proof || !timingSafeEqual(existing.guardian_proof, guardianProof)) {
      return json({ error: "guardian_pin_changed_repair_required" }, 409);
    }
    return json({ ok: true });
  }

  await env.DB.prepare(
    "INSERT INTO devices(device_id, secret_hash, admin_secret_hash, guardian_proof, pin_salt, pin_iterations, pin_attempt_day, pin_failed_attempts, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?)"
  ).bind(
    deviceId,
    hash,
    wantsSecureRegistration ? adminHash : null,
    guardianProof,
    wantsSecureRegistration ? pinSalt : null,
    wantsSecureRegistration ? pinIterations : null,
    utcDay(),
    Date.now()
  ).run();
  return json({ ok: true }, 201);
}

async function getPinInfo(request, env, url) {
  const deviceId = validDeviceId(url.searchParams.get("deviceId"));
  const secret = bearer(request);
  if (!deviceId || !validSecret(secret)) return json({ error: "bad_request" }, 400);
  if (!(await authorized(env, deviceId, secret))) return json({ error: "unauthorized" }, 401);

  const row = await env.DB.prepare(
    "SELECT pin_salt, pin_iterations FROM devices WHERE device_id = ?"
  ).bind(deviceId).first();
  if (!row || !row.pin_salt || Number(row.pin_iterations || 0) !== PIN_ITERATIONS) {
    return json({ error: "pairing_refresh_required" }, 409);
  }
  return json({ pinSalt: row.pin_salt, pinIterations: Number(row.pin_iterations) });
}

async function verifyPin(request, env) {
  const body = await readJson(request);
  const deviceId = validDeviceId(body.deviceId);
  const guardianProof = validGuardianProof(body.guardianProof);
  const secret = bearer(request);
  if (!deviceId || !guardianProof || !validSecret(secret)) return json({ error: "bad_request" }, 400);
  if (!(await authorized(env, deviceId, secret))) return json({ error: "unauthorized" }, 401);

  const result = await checkGuardianProof(env, deviceId, guardianProof);
  if (result.ok) return json({ ok: true });
  if (result.locked) return json({ error: "pin_locked", attemptsRemaining: 0 }, 423);
  return json({ error: "wrong_pin", attemptsRemaining: result.remaining }, 401);
}

async function createUnlock(request, env) {
  const body = await readJson(request);
  const deviceId = validDeviceId(body.deviceId);
  const secret = bearer(request);
  const guardianProof = validGuardianProof(request.headers.get("x-guardian-proof"));
  if (!deviceId || !validSecret(secret) || !guardianProof) return json({ error: "bad_request" }, 400);
  if (!(await authorized(env, deviceId, secret))) return json({ error: "unauthorized" }, 401);

  const pinResult = await checkGuardianProof(env, deviceId, guardianProof);
  if (!pinResult.ok) {
    if (pinResult.locked) return json({ error: "pin_locked", attemptsRemaining: 0 }, 423);
    return json({ error: "wrong_pin", attemptsRemaining: pinResult.remaining }, 401);
  }

  let action = String(body.action || "");
  let minutes = null;
  if (action === "unlock_for_minutes") {
    minutes = Number(body.minutes);
    if (![15, 30, 60].includes(minutes)) return json({ error: "invalid_duration" }, 400);
  } else if (action === "allow_app_installs_15") {
    minutes = 15;
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
  const adminSecret = request.headers.get("x-device-admin") || "";
  if (!deviceId || !validSecret(secret)) return json({ error: "bad_request" }, 400);
  if (!(await authorized(env, deviceId, secret))) return json({ error: "unauthorized" }, 401);

  const row = await env.DB.prepare(
    "SELECT admin_secret_hash FROM devices WHERE device_id = ?"
  ).bind(deviceId).first();
  if (!row) return json({ error: "unauthorized" }, 401);

  if (row.admin_secret_hash) {
    if (!validSecret(adminSecret)) return json({ error: "device_admin_required" }, 401);
    if (!timingSafeEqual(row.admin_secret_hash, await sha256(adminSecret))) {
      return json({ error: "device_admin_unauthorized" }, 401);
    }
  }

  await env.DB.batch([
    env.DB.prepare("DELETE FROM commands WHERE device_id = ?").bind(deviceId),
    env.DB.prepare("DELETE FROM devices WHERE device_id = ?").bind(deviceId)
  ]);
  return json({ ok: true });
}

async function checkGuardianProof(env, deviceId, suppliedProof) {
  const row = await env.DB.prepare(
    "SELECT guardian_proof, pin_attempt_day, pin_failed_attempts FROM devices WHERE device_id = ?"
  ).bind(deviceId).first();
  if (!row || !row.guardian_proof) return { ok: false, locked: true, remaining: 0 };

  const today = utcDay();
  let failed = Number(row.pin_failed_attempts || 0);
  if (row.pin_attempt_day !== today) {
    failed = 0;
    await env.DB.prepare(
      "UPDATE devices SET pin_attempt_day = ?, pin_failed_attempts = 0 WHERE device_id = ?"
    ).bind(today, deviceId).run();
  }

  if (failed >= MAX_PIN_ATTEMPTS_PER_DAY) return { ok: false, locked: true, remaining: 0 };

  if (timingSafeEqual(row.guardian_proof, suppliedProof)) {
    if (failed !== 0) {
      await env.DB.prepare(
        "UPDATE devices SET pin_attempt_day = ?, pin_failed_attempts = 0 WHERE device_id = ?"
      ).bind(today, deviceId).run();
    }
    return { ok: true, locked: false, remaining: MAX_PIN_ATTEMPTS_PER_DAY };
  }

  failed += 1;
  await env.DB.prepare(
    "UPDATE devices SET pin_attempt_day = ?, pin_failed_attempts = ? WHERE device_id = ?"
  ).bind(today, failed, deviceId).run();
  return {
    ok: false,
    locked: failed >= MAX_PIN_ATTEMPTS_PER_DAY,
    remaining: Math.max(0, MAX_PIN_ATTEMPTS_PER_DAY - failed)
  };
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

function validGuardianProof(value) {
  if (typeof value !== "string") return "";
  const v = value.trim().toLowerCase();
  return /^[0-9a-f]{64}$/.test(v) ? v : "";
}

function validPinSalt(value) {
  if (typeof value !== "string") return "";
  const v = value.trim();
  return /^[A-Za-z0-9+/]{20,80}={0,2}$/.test(v) ? v : "";
}

function utcDay() {
  return new Date().toISOString().slice(0, 10);
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
  h.set("access-control-allow-headers", "authorization, content-type, x-guardian-proof, x-device-admin");
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
:root{color-scheme:dark}*{box-sizing:border-box}body{margin:0;background:#05090f;color:#eff6ff;font-family:system-ui,-apple-system,sans-serif;min-height:100vh;display:grid;place-items:center;padding:20px}.card{width:min(480px,100%);background:#142233;border:1px solid #52718c;border-radius:22px;padding:22px}h1{font-size:26px;margin:0 0 6px}h2{font-size:17px;margin:18px 0 8px}p{color:#9bb1c9;line-height:1.45}button,input{width:100%;min-height:52px;border-radius:14px;border:1px solid #52718c;background:#0b1520;color:#eff6ff;padding:12px 14px;font-size:16px;margin-top:10px}button{cursor:pointer;font-weight:650}button.primary{border-color:#24b8ff}.grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}.grid button{margin-top:0}.status{margin:14px 0;padding:12px;border-radius:14px;background:#0b1520;color:#9bb1c9}.ok{color:#49e78c}.bad{color:#ff6778}.small{font-size:12px;word-break:break-all}.hidden{display:none}</style>
</head>
<body><main class="card">
<h1>Parent remote control</h1>
<p>The pairing link can be opened on multiple phones or computers, but the guardian PIN is required each time the page is opened. The PIN is not saved in the browser or stored by the relay.</p>
<div id="setup">
<input id="device" placeholder="Device ID" autocomplete="off">
<input id="secret" placeholder="Pairing secret" autocomplete="off">
<button class="primary" onclick="pair()">Pair this device</button>
</div>
<div id="pinGate" class="hidden">
<div class="status">Paired device<br><span id="pinDeviceLabel" class="small"></span></div>
<input id="guardianPin" type="password" inputmode="numeric" placeholder="Guardian PIN" autocomplete="off">
<button class="primary" onclick="verifyGuardianPin()">Unlock remote controls</button>
<div id="pinResult" class="status">Enter the guardian PIN.</div>
<button onclick="forget()">Forget this device</button>
</div>
<div id="controls" class="hidden">
<div class="status">Paired device<br><span id="deviceLabel" class="small"></span></div>
<h2>Screen time</h2>
<div class="grid">
<button onclick="unlockMinutes(15)">Unlock 15 min</button>
<button onclick="unlockMinutes(30)">Unlock 30 min</button>
<button onclick="unlockMinutes(60)">Unlock 1 hour</button>
<button class="primary" onclick="unlockMidnight()">Until midnight</button>
</div>
<h2>App installation</h2>
<button class="primary" onclick="allowAppInstalls()">Allow app installs for 15 min</button>
<div id="result" class="status">Ready.</div>
<button onclick="lockControls()">Lock controls</button>
<button onclick="forget()">Forget this device</button>
</div>
<script>
const key='stg_parent_pairing_v1';
const proofPrefix='stg-remote-pin-v1|';
let pairing=null;
let guardianProof=null;
let pinInfo=null;
function parseHash(){const p=new URLSearchParams(location.hash.slice(1));const d=p.get('device'),s=p.get('secret');if(d&&s){localStorage.setItem(key,JSON.stringify({device:d,secret:s}));history.replaceState(null,'',location.pathname);}}
function load(){parseHash();try{pairing=JSON.parse(localStorage.getItem(key)||'null')}catch{};render();}
function pair(){const device=document.getElementById('device').value.trim(),secret=document.getElementById('secret').value.trim();if(!device||!secret)return;pairing={device,secret};guardianProof=null;pinInfo=null;localStorage.setItem(key,JSON.stringify(pairing));render();}
function forget(){localStorage.removeItem(key);pairing=null;guardianProof=null;pinInfo=null;render();}
function lockControls(){guardianProof=null;document.getElementById('guardianPin').value='';render();}
function render(){const paired=!!pairing,unlocked=paired&&!!guardianProof;document.getElementById('setup').classList.toggle('hidden',paired);document.getElementById('pinGate').classList.toggle('hidden',!paired||unlocked);document.getElementById('controls').classList.toggle('hidden',!unlocked);if(paired){document.getElementById('pinDeviceLabel').textContent=pairing.device;document.getElementById('deviceLabel').textContent=pairing.device;}}
function fromBase64(value){const binary=atob(value);return Uint8Array.from(binary,c=>c.charCodeAt(0));}
function toBase64(bytes){let binary='';for(const b of bytes)binary+=String.fromCharCode(b);return btoa(binary);}
async function getPinInfo(){if(pinInfo)return pinInfo;const r=await fetch('/v1/pin-info?deviceId='+encodeURIComponent(pairing.device),{headers:{'authorization':'Bearer '+pairing.secret}});let data={};try{data=await r.json()}catch{};if(!r.ok){if(r.status===409)throw new Error('Pairing needs to be refreshed from the Screen Time Guard app.');throw new Error('Could not load PIN information.');}pinInfo=data;return data;}
async function makeProof(pin){const info=await getPinInfo();const keyMaterial=await crypto.subtle.importKey('raw',new TextEncoder().encode(pin),'PBKDF2',false,['deriveBits']);const bits=await crypto.subtle.deriveBits({name:'PBKDF2',salt:fromBase64(info.pinSalt),iterations:info.pinIterations,hash:'SHA-256'},keyMaterial,256);const verifier=toBase64(new Uint8Array(bits));const bytes=new TextEncoder().encode(proofPrefix+pairing.secret+'|'+verifier);const digest=await crypto.subtle.digest('SHA-256',bytes);return Array.from(new Uint8Array(digest),b=>b.toString(16).padStart(2,'0')).join('');}
async function verifyGuardianPin(){const out=document.getElementById('pinResult');const pin=document.getElementById('guardianPin').value;out.className='status';out.textContent='Checking…';if(!pin)return;try{const proof=await makeProof(pin);const r=await fetch('/v1/verify-pin',{method:'POST',headers:{'content-type':'application/json','authorization':'Bearer '+pairing.secret},body:JSON.stringify({deviceId:pairing.device,guardianProof:proof})});let data={};try{data=await r.json()}catch{};if(r.ok){guardianProof=proof;document.getElementById('guardianPin').value='';out.className='status ok';out.textContent='PIN accepted.';render();return;}if(r.status===423){out.className='status bad';out.textContent='3 wrong attempts. Remote PIN is locked until tomorrow.';return;}const remaining=Number.isInteger(data.attemptsRemaining)?data.attemptsRemaining:null;out.className='status bad';out.textContent=remaining===null?'Wrong guardian PIN.':'Wrong guardian PIN · '+remaining+' attempt'+(remaining===1?'':'s')+' remaining today.';}catch(e){out.className='status bad';out.textContent=e.message||'Could not reach the relay.';}}
async function send(body,message){const out=document.getElementById('result');out.className='status';out.textContent='Sending…';try{const r=await fetch('/v1/unlock',{method:'POST',headers:{'content-type':'application/json','authorization':'Bearer '+pairing.secret,'x-guardian-proof':guardianProof},body:JSON.stringify({deviceId:pairing.device,...body})});let data={};try{data=await r.json()}catch{};if(!r.ok){if(r.status===401||r.status===423){guardianProof=null;render();}throw new Error(r.status===423?'Remote PIN locked until tomorrow.':'Request failed ('+r.status+')');}out.className='status ok';out.textContent=message;}catch(e){out.className='status bad';out.textContent=e.message;}}
function unlockMinutes(minutes){send({action:'unlock_for_minutes',minutes},'Screen-time unlock command sent.');}
function unlockMidnight(){send({action:'unlock_until_midnight'},'Screen-time unlock command sent.');}
function allowAppInstalls(){send({action:'allow_app_installs_15'},'App installations allowed for 15 minutes.');}
load();
</script></main></body></html>`;
}
