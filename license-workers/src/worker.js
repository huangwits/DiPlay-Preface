import { adminHtml, adminJs, adminCss } from './admin.js';

const encoder = new TextEncoder();
const rsa = { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' };
const hex64 = /^[0-9a-f]{64}$/;
const securityHeaders = {
  'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'no-referrer', 'X-Frame-Options': 'DENY',
  'Content-Security-Policy': "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'",
};
class ApiError extends Error {
  constructor(status, code) { super(code); this.status = status; }
}
function requireValue(condition, status = 400, code = 'invalid_request') {
  if (!condition) throw new ApiError(status, code);
}
export function base64(bytes) { return btoa(String.fromCharCode(...new Uint8Array(bytes))); }
function unbase64(text, max) {
  requireValue(typeof text === 'string' && text.length <= max * 2 && /^[A-Za-z0-9+/]+={0,2}$/.test(text));
  try {
    const data = Uint8Array.from(atob(text), c => c.charCodeAt(0));
    requireValue(data.length <= max && base64(data) === text);
    return data;
  } catch { throw new ApiError(400, 'invalid_encoding'); }
}
export async function sha(bytes) {
  return [...new Uint8Array(await crypto.subtle.digest('SHA-256', bytes))].map(x => x.toString(16).padStart(2, '0')).join('');
}
function randomHex(length) {
  return [...crypto.getRandomValues(new Uint8Array(length))].map(x => x.toString(16).padStart(2, '0')).join('');
}
function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: { ...securityHeaders, 'Content-Type': 'application/json; charset=utf-8' } });
}
async function body(request) {
  requireValue(request.headers.get('Content-Type')?.split(';')[0].trim() === 'application/json', 415, 'json_required');
  requireValue(Number(request.headers.get('Content-Length') || 0) <= 8192, 413, 'body_too_large');
  const reader = request.body?.getReader();
  requireValue(reader);
  let size = 0; const chunks = [];
  try {
    for (;;) {
      const { done, value } = await reader.read(); if (done) break;
      size += value.length;
      if (size > 8192) { await reader.cancel(); throw new ApiError(413, 'body_too_large'); }
      chunks.push(value);
    }
    const bytes = new Uint8Array(size); let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
    const result = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes));
    requireValue(result && typeof result === 'object' && !Array.isArray(result));
    return result;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError(400, 'invalid_json');
  } finally { reader.releaseLock(); }
}
async function rate(db, env, request, bucket, limit, now) {
  // Raw IPs and credentials are not retained. The keyed hash expires after two minutes.
  const address = request.headers.get('CF-Connecting-IP') || 'local';
  const id = await sha(encoder.encode(`${env.ADMIN_TOKEN_HASH}\n${address}\n${bucket}\n${Math.floor(now / 60)}`));
  const row = await db.prepare('INSERT INTO rates(id,count,expires) VALUES (?,1,?) ON CONFLICT(id) DO UPDATE SET count=count+1 RETURNING count')
    .bind(id, now + 120).first();
  requireValue(row.count <= limit, 429, 'rate_limited');
}
async function admin(request, env) {
  const token = request.headers.get('Authorization') || '';
  requireValue(/^Bearer [A-Za-z0-9_-]{40,128}$/.test(token), 401, 'admin_required');
  const actual = await sha(encoder.encode(token.slice(7)));
  let mismatch = 0;
  for (let i = 0; i < 64; i++) mismatch |= actual.charCodeAt(i) ^ env.ADMIN_TOKEN_HASH.charCodeAt(i);
  requireValue(mismatch === 0, 401, 'admin_required');
}
export function proof(action, id, nonce, device, packageName, signer) {
  return encoder.encode(['DP-LIC1', action, id, nonce, device, device, '', packageName, signer].join('\n'));
}
async function authenticate(db, env, value, action, now) {
  const { challengeId, deviceId, package: packageName, signer } = value;
  requireValue(typeof challengeId === 'string' && /^[0-9a-f]{48}$/.test(challengeId));
  requireValue(typeof deviceId === 'string' && hex64.test(deviceId));
  requireValue(packageName === env.APP_PACKAGE && signer === env.APP_SIGNER, 403, 'app_mismatch');
  const publicKey = unbase64(value.publicKey, 512), signature = unbase64(value.signature, 256);
  requireValue(signature.length === 256 && await sha(publicKey) === deviceId, 403, 'invalid_device_proof');
  let key;
  try { key = await crypto.subtle.importKey('spki', publicKey, rsa, false, ['verify']); }
  catch { throw new ApiError(400, 'invalid_public_key'); }
  requireValue(key.algorithm.modulusLength === 2048, 400, 'invalid_public_key');
  // Atomic consume: simultaneous replays cannot both read the same challenge.
  const challenge = await db.prepare('DELETE FROM challenges WHERE id=? RETURNING nonce,expires').bind(challengeId).first();
  requireValue(challenge && challenge.expires > now, 403, 'challenge_expired');
  requireValue(await crypto.subtle.verify(rsa, key, signature, proof(action, challengeId, challenge.nonce, deviceId, packageName, signer)), 403, 'invalid_device_proof');
  return deviceId;
}
async function lease(env, row, challenge, now) {
  requireValue(row.status === 'approved' && row.valid_until > now, 403, 'license_unavailable');
  const payload = encoder.encode(['DP1', row.license_id, row.device, now, Math.min(now + 300, row.valid_until),
    env.APP_PACKAGE, env.APP_SIGNER, challenge].join('\n'));
  const key = await crypto.subtle.importKey('pkcs8', unbase64(env.ISSUER_PRIVATE_KEY, 4096), rsa, false, ['sign']);
  requireValue(key.algorithm.modulusLength >= 3072 && key.algorithm.modulusLength <= 4096, 503, 'service_not_configured');
  return json({ status: 'approved', requestId: row.request_id, payload: base64(payload),
    signature: base64(await crypto.subtle.sign(rsa, key, payload)) });
}
function cleanupStatements(db, now) {
  // Pages has no cron dependency. Bounded, idempotent batches run during normal use.
  // Never remove approved/revoked installations: reapplying must preserve the decision.
  return [
    db.prepare('DELETE FROM challenges WHERE id IN (SELECT id FROM challenges WHERE expires<=? LIMIT 100)').bind(now),
    db.prepare('DELETE FROM rates WHERE id IN (SELECT id FROM rates WHERE expires<=? LIMIT 100)').bind(now),
    db.prepare("DELETE FROM devices WHERE device IN (SELECT device FROM devices WHERE status='pending' AND last_seen<? LIMIT 100)").bind(now - 30 * 86400),
    db.prepare('DELETE FROM events WHERE id IN (SELECT id FROM events WHERE at<? LIMIT 100)').bind(now - 180 * 86400),
  ];
}
async function route(request, env) {
  const url = new URL(request.url), path = url.pathname;
  if (request.method === 'GET' && ['/admin', '/admin.js', '/admin.css'].includes(path)) {
    const [text, type] = path === '/admin' ? [adminHtml, 'text/html'] : path === '/admin.js' ? [adminJs, 'text/javascript'] : [adminCss, 'text/css'];
    return new Response(text, { headers: { ...securityHeaders, 'Content-Type': `${type}; charset=utf-8` } });
  }
  requireValue(request.method === 'POST', 405, 'post_required');
  requireValue(['/v1/challenge', '/v1/request', '/v1/refresh', '/admin/list', '/admin/decision'].includes(path), 404, 'not_found');
  requireValue(!request.headers.has('Origin') || request.headers.get('Origin') === url.origin, 403, 'origin_rejected');
  requireValue(env.DB && hex64.test(env.ADMIN_TOKEN_HASH || '') && env.ISSUER_PRIVATE_KEY &&
    /^[a-zA-Z0-9_.]{3,150}$/.test(env.APP_PACKAGE || '') && hex64.test(env.APP_SIGNER || ''), 503, 'service_not_configured');
  const db = env.DB.withSession('first-primary'), now = Math.floor(Date.now() / 1000);
  await rate(db, env, request, path.startsWith('/admin/') ? 'admin' : 'device', 60, now);
  if (path.startsWith('/admin/')) await admin(request, env);
  const value = await body(request);
  if (path === '/v1/challenge') {
    await rate(db, env, request, 'challenge', 30, now);
    const id = randomHex(24), nonce = randomHex(32);
    await db.batch([
      ...cleanupStatements(db, now),
      db.prepare('INSERT INTO challenges(id,nonce,expires) VALUES (?,?,?)').bind(id, nonce, now + 120),
    ]);
    return json({ id, nonce });
  }
  if (path === '/admin/list') {
    const before = value.before || [Number.MAX_SAFE_INTEGER, 'z'];
    requireValue(Array.isArray(before) && before.length === 2 && Number.isSafeInteger(before[0]) && typeof before[1] === 'string' && before[1].length <= 64);
    const status = value.status ?? 'all', query = value.query ?? '';
    requireValue(['all', 'pending', 'approved', 'expired', 'revoked'].includes(status));
    requireValue(typeof query === 'string' && /^[0-9a-fA-F]{0,64}$/.test(query));
    await db.batch(cleanupStatements(db, now));
    const where = ['(created_at,device)<(?,?)'], params = [...before];
    if (status === 'expired' || status === 'approved') {
      where.push("status='approved' AND valid_until" + (status === 'expired' ? '<=?' : '>?'));
      params.push(now);
    } else if (status !== 'all') { where.push('status=?'); params.push(status); }
    if (query) { where.push('(instr(lower(request_id),?)>0 OR instr(device,?)>0)'); params.push(query.toLowerCase(), query.toLowerCase()); }
    const { results } = await db.prepare('SELECT device,request_id,status,valid_until,created_at,last_seen FROM devices WHERE ' + where.join(' AND ') + ' ORDER BY created_at DESC,device DESC LIMIT 101').bind(...params).all();
    const counts = await db.prepare("SELECT COUNT(*) AS total, COALESCE(SUM(status='pending'),0) AS pending, COALESCE(SUM(status='approved' AND valid_until>?),0) AS approved, COALESCE(SUM(status='approved' AND valid_until<=?),0) AS expired, COALESCE(SUM(status='revoked'),0) AS revoked FROM devices").bind(now, now).first();
    const items = results.slice(0, 100), last = items.at(-1);
    return json({ items, counts, now, next: results.length > 100 ? [last.created_at, last.device] : null });
  }
  if (path === '/admin/decision') {
    requireValue(typeof value.requestId === 'string' && /^[0-9A-F]{12}$/.test(value.requestId));
    requireValue(['approved', 'revoked'].includes(value.status));
    requireValue(value.status === 'revoked' || Number.isInteger(value.days) && value.days >= 1 && value.days <= 3650);
    const until = value.status === 'approved' ? now + value.days * 86400 : 0;
    const results = await db.batch([
      db.prepare('UPDATE devices SET status=?,valid_until=? WHERE request_id=? RETURNING request_id').bind(value.status, until, value.requestId),
      db.prepare('INSERT INTO events(at,request_id,action,valid_until) SELECT ?,request_id,?,? FROM devices WHERE request_id=?').bind(now, value.status, until, value.requestId),
    ]);
    requireValue(results[0].results.length === 1, 404, 'request_not_found');
    return json({ status: value.status });
  }
  const action = path === '/v1/request' ? 'request' : 'refresh';
  const device = await authenticate(db, env, value, action, now);
  if (action === 'request') {
    // Repeated requests preserve an admin decision, including revocation.
    await db.prepare("INSERT INTO devices(device,request_id,license_id,status,created_at,last_seen) VALUES (?,?,?,'pending',?,?) ON CONFLICT(device) DO UPDATE SET last_seen=excluded.last_seen")
      .bind(device, randomHex(6).toUpperCase(), crypto.randomUUID(), now, now).run();
  }
  const row = await db.prepare('UPDATE devices SET last_seen=? WHERE device=? RETURNING *').bind(now, device).first();
  requireValue(row, 403, 'request_required');
  if (row.status === 'pending') return json({ status: 'pending', requestId: row.request_id });
  return lease(env, row, value.challengeId, now);
}
export default {
  async fetch(request, env) {
    try { return await route(request, env); }
    catch (error) {
      // Never include exception messages, device proofs or secrets in errors/logs.
      return json({ error: error instanceof ApiError ? error.message : 'service_unavailable' }, error instanceof ApiError ? error.status : 503);
    }
  },
};
