import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, sign, verify, createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { Miniflare, convertV4MiniflareOptions } from 'miniflare';

const packageName = 'com.shihab.diplay.preface', signer = 'b'.repeat(64), token = 'T'.repeat(48);
const hash = data => createHash('sha256').update(data).digest('hex');
const issuer = generateKeyPairSync('rsa', { modulusLength: 3072 });
let mf, db, address = 0;
before(async () => {
  mf = new Miniflare(convertV4MiniflareOptions({ workers: [{ modules: ['worker.js', 'admin.js'].map(name => ({ type: 'ESModule', path: resolve('src', name) })), compatibilityDate: '2026-10-08',
    d1Databases: ['DB'], bindings: { APP_PACKAGE: packageName, APP_SIGNER: signer,
      ADMIN_TOKEN_HASH: hash(token), ISSUER_PRIVATE_KEY: issuer.privateKey.export({ type: 'pkcs8', format: 'der' }).toString('base64') },
  }] }));
  db = await mf.getD1Database('DB');
  const statements = ['0001.sql', '0002_diagnostic_reports.sql'].flatMap(name => readFileSync('migrations/' + name, 'utf8').split(';').map(s => s.trim()).filter(Boolean));
  for (const sql of statements) await db.prepare(sql).run();
});
after(async () => { await mf?.dispose(); });

async function post(path, value, options = {}) {
  const response = await mf.dispatchFetch('https://license.example' + path, { method: 'POST',
    headers: { 'Content-Type': 'application/json', 'CF-Connecting-IP': options.ip || `test-${++address}`,
      ...(options.admin ? { Authorization: 'Bearer ' + token } : {}), ...options.headers }, body: JSON.stringify(value) });
  return { code: response.status, value: await response.json(), headers: response.headers };
}
function device() {
  const key = generateKeyPairSync('rsa', { modulusLength: 2048 });
  const publicKey = key.publicKey.export({ type: 'spki', format: 'der' });
  return { key, publicKey, id: hash(publicKey) };
}
async function requestProof(d, action = 'request', options = {}) {
  const { value: challenge } = await post('/v1/challenge', {});
  const canonical = ['DP-LIC1', action, challenge.id, challenge.nonce, d.id, d.id, '', packageName, signer].join('\n');
  const body = { challengeId: challenge.id, deviceId: d.id, publicKey: d.publicKey.toString('base64'),
    signature: sign('RSA-SHA256', Buffer.from(canonical), d.key.privateKey).toString('base64'), package: packageName, signer, ...options };
  return body;
}
async function apply(d, action = 'request') { return post('/v1/' + action, await requestProof(d, action)); }
async function approve(requestId, days = 365) { return post('/admin/decision', { requestId, status: 'approved', days }, { admin: true }); }

test('real D1 + Worker: request, approve, signed lease, refresh, revoke; reapply cannot reset revocation', async () => {
  const d = device(), pending = await apply(d);
  assert.equal(pending.code, 200); assert.equal(pending.value.status, 'pending');
  assert.match(pending.value.requestId, /^[0-9A-F]{12}$/);
  assert.equal((await apply(d)).value.requestId, pending.value.requestId);
  assert.equal((await apply(d, 'refresh')).value.status, 'pending');
  assert.equal((await approve(pending.value.requestId)).code, 200);
  const proof = await requestProof(d, 'refresh');
  const active = await post('/v1/refresh', proof);
  assert.equal(active.code, 200); assert.equal(active.value.status, 'approved');
  const payload = Buffer.from(active.value.payload, 'base64'), signature = Buffer.from(active.value.signature, 'base64');
  assert.ok(verify('RSA-SHA256', payload, issuer.publicKey, signature));
  const fields = payload.toString().split('\n');
  assert.equal(fields.length, 8); assert.equal(fields[0], 'DP1'); assert.equal(fields[2], d.id);
  assert.equal(Number(fields[4]) - Number(fields[3]), 300);
  assert.deepEqual(fields.slice(5), [packageName, signer, proof.challengeId]);
  if (process.env.DIPLAY_WORKERS_VECTOR) writeFileSync(process.env.DIPLAY_WORKERS_VECTOR, JSON.stringify({
    ...active.value, issuerPublic: issuer.publicKey.export({ type: 'spki', format: 'der' }).toString('base64'),
    device: d.id, package: packageName, signer, challenge: proof.challengeId,
  }));
  assert.equal((await post('/admin/decision', { requestId: pending.value.requestId, status: 'revoked' }, { admin: true })).code, 200);
  assert.equal((await apply(d, 'refresh')).code, 403); assert.equal((await apply(d)).code, 403);
  const events = await db.prepare('SELECT action FROM events WHERE request_id=? ORDER BY id').bind(pending.value.requestId).all();
  assert.deepEqual(events.results.map(r => r.action), ['approved', 'revoked']);
});

test('challenge is single-use, including concurrent requests and wrong-action signatures', async () => {
  const d = device(), proof = await requestProof(d);
  const responses = await Promise.all([post('/v1/request', proof), post('/v1/request', proof)]);
  assert.deepEqual(responses.map(r => r.code).sort(), [200, 403]);
  const wrongAction = await requestProof(d, 'request');
  assert.equal((await post('/v1/refresh', wrongAction)).code, 403);
  assert.equal((await post('/v1/request', wrongAction)).code, 403);
});

test('rejects altered device signatures, package/signer mismatch, foreign keys and expired challenges', async () => {
  const d = device();
  for (const overrides of [{ package: 'other.app' }, { signer: 'f'.repeat(64) }, { deviceId: 'f'.repeat(64) }, { signature: Buffer.alloc(256).toString('base64') }]) {
    assert.equal((await post('/v1/request', await requestProof(d, 'request', overrides))).code, 403);
  }
  const expired = await requestProof(d);
  await db.prepare('UPDATE challenges SET expires=1 WHERE id=?').bind(expired.challengeId).run();
  assert.equal((await post('/v1/request', expired)).code, 403);
});

test('expiry and unknown installations do not acquire leases; short remaining lifetime is respected', async () => {
  const d = device(); assert.equal((await apply(d, 'refresh')).code, 403);
  const pending = await apply(d); await approve(pending.value.requestId);
  const now = Math.floor(Date.now() / 1000);
  await db.prepare('UPDATE devices SET valid_until=? WHERE device=?').bind(now + 20, d.id).run();
  const active = await apply(d, 'refresh'), fields = Buffer.from(active.value.payload, 'base64').toString().split('\n');
  assert.ok(Number(fields[4]) - Number(fields[3]) <= 20);
  await db.prepare('UPDATE devices SET valid_until=1 WHERE device=?').bind(d.id).run();
  assert.equal((await apply(d, 'refresh')).code, 403);
});

test('admin auth, same-origin checks and bounded/validated input protect approvals', async () => {
  assert.equal((await post('/admin/list', {})).code, 401);
  assert.equal((await post('/admin/list', {}, { headers: { Authorization: 'Bearer ' + 'X'.repeat(48) } })).code, 401);
  assert.equal((await post('/admin/list', {}, { admin: true, headers: { Origin: 'https://evil.example' } })).code, 403);
  assert.equal((await post('/admin/list', {}, { admin: true, headers: { Origin: 'null' } })).code, 403);
  const list = await post('/admin/list', {}, { admin: true, headers: { Origin: 'https://license.example' } });
  assert.equal(list.code, 200); assert.ok(list.value.items.length > 0);
  assert.equal(list.headers.get('Access-Control-Allow-Origin'), null);
  assert.equal((await approve('0'.repeat(12), 0)).code, 400);
  assert.equal((await approve('0'.repeat(12), 999999)).code, 400);
  assert.equal((await approve('0'.repeat(12))).code, 404);
  assert.equal((await post('/v1/challenge', { huge: 'x'.repeat(9000) })).code, 413);
  assert.equal((await post('/v1/challenge', {})).headers.get('Cache-Control'), 'no-store');
});

test('D1 rate limiting rejects excessive challenges and admin failures', async () => {
  for (let i = 0; i < 30; i++) assert.equal((await post('/v1/challenge', {}, { ip: 'rate-test' })).code, 200);
  assert.equal((await post('/v1/challenge', {}, { ip: 'rate-test' })).code, 429);
});

test('admin assets contain no secrets, forbid framing and only load same-origin scripts', async () => {
  const response = await mf.dispatchFetch('https://license.example/admin');
  assert.equal(response.status, 200);
  const html = await response.text();
  assert.ok(!html.includes(token)); assert.match(html, /申请号/);
  assert.match(response.headers.get('Content-Security-Policy'), /frame-ancestors 'none'/);
  const js = await (await mf.dispatchFetch('https://license.example/admin.js')).text();
  assert.ok(!/localStorage|sessionStorage|innerHTML/.test(js));
});

test('Pages requests clean expired data without cron and retain approval/revocation decisions', async () => {
  for (const path of ['/v1/challenge', '/admin/list']) {
    const prefix = path.split('/').at(-1), now = Math.floor(Date.now() / 1000);
    for (const [suffix, status, lastSeen] of [
      ['stale', 'pending', now - 31 * 86400],
      ['recent', 'pending', now - 29 * 86400],
      ['approved', 'approved', 1], ['revoked', 'revoked', 1],
    ]) {
      const id = prefix + '-' + suffix;
      await db.prepare('INSERT INTO devices(device,request_id,license_id,status,created_at,last_seen) VALUES (?,?,?,?,?,?)')
        .bind(id, id, id, status, 1, lastSeen).run();
    }
    for (const [suffix, expires] of [['expired', now - 1], ['live', now + 120]]) {
      const id = prefix + '-' + suffix;
      await db.prepare('INSERT INTO challenges(id,nonce,expires) VALUES (?,?,?)').bind(id, id, expires).run();
      await db.prepare('INSERT INTO rates(id,count,expires) VALUES (?,1,?)').bind(id, expires).run();
      await db.prepare("INSERT INTO events(at,request_id,action,valid_until) VALUES (?,?,'approved',0)")
        .bind(suffix === 'expired' ? now - 181 * 86400 : now - 179 * 86400, id).run();
    }
    if (path === '/admin/list') {
      assert.equal((await post(path, {})).code, 401);
      assert.ok(await db.prepare('SELECT device FROM devices WHERE device=?').bind(prefix + '-stale').first());
    }
    assert.equal((await post(path, {}, { admin: path === '/admin/list' })).code, 200);
    assert.equal(await db.prepare('SELECT device FROM devices WHERE device=?').bind(prefix + '-stale').first(), null);
    for (const suffix of ['recent', 'approved', 'revoked']) {
      assert.ok(await db.prepare('SELECT device FROM devices WHERE device=?').bind(prefix + '-' + suffix).first());
    }
    for (const table of ['challenges', 'rates']) {
      assert.equal(await db.prepare(`SELECT id FROM ${table} WHERE id=?`).bind(prefix + '-expired').first(), null);
      assert.ok(await db.prepare(`SELECT id FROM ${table} WHERE id=?`).bind(prefix + '-live').first());
    }
    assert.equal(await db.prepare('SELECT id FROM events WHERE request_id=?').bind(prefix + '-expired').first(), null);
    assert.ok(await db.prepare('SELECT id FROM events WHERE request_id=?').bind(prefix + '-live').first());
  }
});

test('cleanup is bounded per request and catches up on subsequent traffic', async () => {
  await db.batch(Array.from({ length: 205 }, (_, i) => db.prepare("INSERT INTO events(at,request_id,action,valid_until) VALUES (1,?,'approved',0)").bind('backlog-' + i)));
  for (const expected of [105, 5, 0]) {
    assert.equal((await post('/admin/list', {}, { admin: true })).code, 200);
    const row = await db.prepare("SELECT count(*) AS count FROM events WHERE request_id LIKE 'backlog-%'").first();
    assert.equal(row.count, expected);
  }
});

test('admin filters search the whole database, distinguish expiry, and paginate without gaps', async () => {
  const now = Math.floor(Date.now()/1000), prefix='FAFABC';
  const fixtures=Array.from({length:106},(_,i)=>({device:hash('admin-ui-fixture-'+i),request:prefix+i.toString(16).padStart(6,'0').toUpperCase(),status:i<103?'pending':i===105?'revoked':'approved',until:i===103?now+86400:i===104?now-1:0}));
  await db.batch(fixtures.map((r,i)=>db.prepare('INSERT INTO devices(device,request_id,license_id,status,valid_until,created_at,last_seen) VALUES (?,?,?,?,?,?,?)').bind(r.device,r.request,'admin-ui-'+i,r.status,r.until,now,now)));
  try {
    const first=await post('/admin/list',{query:prefix.toLowerCase()},{admin:true});
    assert.equal(first.code,200);assert.equal(first.value.items.length,100);assert.ok(first.value.next);
    const second=await post('/admin/list',{query:prefix,before:first.value.next},{admin:true});
    assert.equal(second.value.items.length,6);assert.equal(second.value.next,null);
    assert.equal(new Set([...first.value.items,...second.value.items].map(r=>r.device)).size,106);
    assert.equal(first.value.counts.total,first.value.counts.pending+first.value.counts.approved+first.value.counts.expired+first.value.counts.revoked);
    for(const [status,count] of [['pending',100],['approved',1],['expired',1],['revoked',1]]) {
      const result=await post('/admin/list',{query:prefix,status},{admin:true});assert.equal(result.code,200);assert.equal(result.value.items.length,count);
      if(status==='approved')assert.equal(result.value.items[0].device,fixtures[103].device);
      if(status==='expired')assert.equal(result.value.items[0].device,fixtures[104].device);
    }
    const match=await post('/admin/list',{query:fixtures[105].device},{admin:true});assert.equal(match.code,200,JSON.stringify(match.value));assert.equal(match.value.items.length,1);
    for(const value of [{query:'%'},{query:"' OR 1=1"},{query:'a'.repeat(65)},{query:42},{status:'anything'}])assert.equal((await post('/admin/list',value,{admin:true})).code,400);
    assert.equal((await post('/admin/list',{query:'FEFEFEFEFEFE'},{admin:true})).value.items.length,0);
  } finally { await db.batch(fixtures.map(r=>db.prepare('DELETE FROM devices WHERE device=?').bind(r.device))); }
});

async function reportProof(d, overrides = {}) {
  const document = JSON.stringify({ schema: 1, version: '0.2.16.13', description: '方向盘下一曲无效', report: 'wheel=NEXT map=requested', ...overrides });
  return { ...await requestProof(d, 'report:' + hash(document)), document };
}

test('private diagnostics: receipt, idempotence, admin search/read/delete and no activation side effects', async () => {
  const d = device(), submission = await reportProof(d);
  const uploaded = await post('/v1/diagnostics', submission);
  assert.equal(uploaded.code, 200, JSON.stringify(uploaded.value));
  assert.match(uploaded.value.receipt, /^DPR-[0-9A-F]{16}$/);
  assert.ok(Math.abs(uploaded.value.expiresAt - Date.now()/1000 - 7*86400) < 5);
  assert.equal(await db.prepare('SELECT * FROM devices WHERE device=?').bind(d.id).first(), null);
  assert.equal((await apply(d, 'refresh')).code, 403);
  const repeated = await post('/v1/diagnostics', await reportProof(d));
  assert.equal(repeated.value.receipt, uploaded.value.receipt);
  const id = uploaded.value.receipt;
  for (const path of ['/admin/reports/list', '/admin/reports/read', '/admin/reports/delete']) {
    assert.equal((await post(path, { id })).code, 401);
    assert.equal((await post(path, { id }, { admin:true, headers:{ Origin:'https://evil.example' } })).code, 403);
  }
  const read = await post('/admin/reports/read', { id }, { admin:true });
  assert.equal(read.value.report, 'wheel=NEXT map=requested');
  assert.equal(read.headers.get('Cache-Control'), 'no-store');
  assert.equal(read.value.device, undefined);
  const list = await post('/admin/reports/list', { query:id }, { admin:true });
  assert.equal(list.value.items.length, 1); assert.equal(list.value.items[0].id, id);
  assert.equal(list.value.items[0].report, undefined);
  assert.equal((await post('/admin/reports/delete', {id}, {admin:true})).code, 200);
  assert.equal((await post('/admin/reports/read', {id}, {admin:true})).code, 404);
});

test('diagnostic proofs reject replay, document alteration, wrong action and invalid identity', async () => {
  const d = device(), value = await reportProof(d);
  assert.equal((await post('/v1/diagnostics', value)).code, 200);
  assert.equal((await post('/v1/diagnostics', value)).code, 403);
  const changed = await reportProof(d);
  changed.document = changed.document.replace('NEXT', 'PREVIOUS');
  assert.equal((await post('/v1/diagnostics', changed)).code, 403);
  const cross = await reportProof(d);
  assert.equal((await post('/v1/request', cross)).code, 403);
  assert.equal((await post('/v1/diagnostics', { ...await reportProof(d), signer:'f'.repeat(64) })).code, 403);
  assert.equal((await post('/v1/diagnostics', { ...await requestProof(d), document:value.document })).code, 403);
  assert.equal(await db.prepare('SELECT * FROM devices WHERE device=?').bind(d.id).first(), null);
});

test('diagnostics filter credentials and addresses, keep script text inert, and expire', async () => {
  const d=device(), result=await post('/v1/diagnostics', await reportProof(d, { report:
    'Android API=22\npassword=secret-phrase\nphoneName=Private phone\nlatitude=31.2\nnextRoad=Private road\nmail=owner@example.com address=192.168.1.1 mac=aa:bb:cc:dd:ee:ff\n<script>alert(1)</script>' }));
  const id=result.value.receipt;assert.equal(result.code,200);
  const saved=await post('/admin/reports/read',{id},{admin:true});
  for(const secret of ['secret-phrase','Private','31.2','owner@','192.168','aa:bb'])assert.ok(!saved.value.report.includes(secret),secret);
  assert.ok(saved.value.report.includes('Android API=22'));assert.ok(saved.value.report.includes('<script>alert(1)</script>'));
  await db.prepare('UPDATE diagnostic_reports SET expires=1 WHERE id=?').bind(id).run();
  assert.equal((await post('/admin/reports/read',{id},{admin:true})).code,404);
  assert.equal(await db.prepare('SELECT id FROM diagnostic_reports WHERE id=?').bind(id).first(),null);
});

test('diagnostic input bounds and quotas reject excess without disrupting existing approval', async () => {
  const d=device(), pending=await apply(d);await approve(pending.value.requestId);
  const before=await db.prepare('SELECT status,valid_until FROM devices WHERE device=?').bind(d.id).first();
  for(const [override,code] of [[{schema:2},400],[{description:''},400],[{description:'x'.repeat(2001)},400], [{report:'x'.repeat(1024*1024+1)},413]]) {
    assert.equal((await post('/v1/diagnostics',await reportProof(d,override))).code,code);
  }
  for(let i=0;i<5;i++)assert.equal((await post('/v1/diagnostics',await reportProof(d,{description:'unique '+i}))).code,200);
  assert.equal((await post('/v1/diagnostics',await reportProof(d,{description:'excess'}))).code,429);
  assert.equal((await post('/v1/diagnostics',await reportProof(d,{description:'unique 0'}))).code,200);
  assert.deepEqual(await db.prepare('SELECT status,valid_until FROM devices WHERE device=?').bind(d.id).first(),before);
  assert.equal((await apply(d,'refresh')).code,200);
});

test('report pagination and search have no gaps, capacity is bounded atomically', async () => {
  const now=Math.floor(Date.now()/1000), d=device();
  await db.prepare('DELETE FROM diagnostic_reports').run();
  await db.batch(Array.from({length:99},(_,i)=>db.prepare('INSERT INTO diagnostic_reports(id,device,digest,version,description,report,created_at,expires) VALUES (?,?,?,?,?,?,?,?)')
    .bind('DPR-'+i.toString(16).padStart(16,'0').toUpperCase(),hash('fixture-'+i),hash('fixture-doc-'+i),'0.2.16.13','fixture','body',now,now+86400)));
  const ids=[];let cursor;
  do { const page=await post('/admin/reports/list',{before:cursor},{admin:true});ids.push(...page.value.items.map(r=>r.id));cursor=page.value.next; } while(cursor);
  assert.equal(ids.length,99);assert.equal(new Set(ids).size,99);
  const responses=await Promise.all([post('/v1/diagnostics',await reportProof(d,{description:'one'})),post('/v1/diagnostics',await reportProof(d,{description:'two'}))]);
  assert.deepEqual(responses.map(r=>r.code).sort(),[200,503]);
  assert.equal((await db.prepare('SELECT COUNT(*) AS n FROM diagnostic_reports').first()).n,100);
  await db.prepare('DELETE FROM diagnostic_reports').run();
});

test('served admin script compiles and uses text-only report rendering', async () => {
  const js=await (await mf.dispatchFetch('https://license.example/admin.js')).text();
  new Function(js);
  assert.ok(js.includes("el('reportBody').textContent=openedReport.report"));
  assert.ok(!js.includes('innerHTML'));
});
