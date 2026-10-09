import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createPrivateKey, createPublicKey, createHash } from 'node:crypto';
import { mkdtempSync, readFileSync, existsSync, rmSync, realpathSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve, sep } from 'node:path';
import { spawnSync } from 'node:child_process';

test('setup separates public/private material, refuses overwriting and source destinations', () => {
  const parent = mkdtempSync(resolve(tmpdir(), 'diplay-setup-test-'));
  const out = resolve(parent, 'new-issuer');
  const run = (output, url = 'https://license.example') => spawnSync(process.execPath, ['setup.mjs', '--out', output, '--url', url], { encoding: 'utf8' });
  try {
    assert.equal(run(out, 'http://license.example').status, 1);
    assert.equal(existsSync(out), false);
    assert.equal(run(resolve('test', 'private-output')).status, 1);
    assert.equal(existsSync(resolve('test', 'private-output')), false);
    assert.equal(run(out).status, 0);
    const secretBytes = readFileSync(resolve(out, 'server-secrets.json'));
    const secrets = JSON.parse(secretBytes), config = JSON.parse(readFileSync(resolve(out, 'client/license/server.json')));
    const token = readFileSync(resolve(out, 'admin-token.txt'), 'utf8').trim();
    const key = createPrivateKey({ key: Buffer.from(secrets.ISSUER_PRIVATE_KEY, 'base64'), type: 'pkcs8', format: 'der' });
    assert.equal(createPublicKey(key).export({ type: 'spki', format: 'der' }).toString('base64'), config.publicKey);
    assert.equal(createHash('sha256').update(token).digest('hex'), secrets.ADMIN_TOKEN_HASH);
    assert.deepEqual(Object.keys(config).sort(), ['contact', 'package', 'publicKey', 'signer', 'url']);
    assert.equal(config.url, 'https://license.example');
    assert.equal(run(out).status, 1);
    assert.deepEqual(readFileSync(resolve(out, 'server-secrets.json')), secretBytes);
  } finally {
    // Validate the absolute target before recursively removing this test's temporary directory.
    const actual = realpathSync(parent), tempRoot = realpathSync(tmpdir());
    assert.ok(actual.startsWith(tempRoot + sep) && actual.split(sep).at(-1).startsWith('diplay-setup-test-'));
    rmSync(actual, { recursive: true });
  }
});
