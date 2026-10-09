import { generateKeyPairSync, randomBytes, createHash } from 'node:crypto';
import { mkdirSync, realpathSync, writeFileSync } from 'node:fs';
import { dirname, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';

// Run locally when preparing a real deployment. No secrets are sent over the network.
const { values } = parseArgs({ options: { out: { type: 'string' }, url: { type: 'string' } } });
if (!values.out || !values.url) {
  throw new Error('Usage: node setup.mjs --out <new directory outside source> --url https://your-worker.workers.dev');
}
const url = new URL(values.url);
if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash || url.pathname !== '/') throw new Error('Use an HTTPS origin');
const root = realpathSync(resolve(dirname(fileURLToPath(import.meta.url)), '..'));
const requested = resolve(values.out);
// Resolve the existing parent first so junctions cannot redirect private files into source.
const out = resolve(realpathSync(dirname(requested)), requested.split(sep).at(-1));
if (out.toLowerCase().startsWith(root.toLowerCase() + sep) || out.toLowerCase() === root.toLowerCase()) throw new Error('Private outputs must be outside source');
mkdirSync(out, { mode: 0o700 }); // Must not exist; never replace an issuer key.
const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 3072 });
const token = randomBytes(36).toString('base64url');
const write = (name, value) => writeFileSync(resolve(out, name), value, { flag: 'wx', mode: 0o600 });
write('server-secrets.json', JSON.stringify({
  ISSUER_PRIVATE_KEY: privateKey.export({ type: 'pkcs8', format: 'der' }).toString('base64'),
  ADMIN_TOKEN_HASH: createHash('sha256').update(token).digest('hex'),
}, null, 2));
write('admin-token.txt', token + '\n');
mkdirSync(resolve(out, 'client', 'license'), { recursive: true });
write('client/license/server.json', JSON.stringify({
  url: url.origin,
  publicKey: publicKey.export({ type: 'spki', format: 'der' }).toString('base64'),
  package: 'com.shihab.diplay.preface',
  signer: 'db0dc2a34dc06f22db7a3d10103fa011167712ebe61985ca2103084ff542cb82',
  contact: 'starts181004',
}, null, 2));
console.log('Created private server inputs and public client configuration outside source. Back them up privately before deployment.');
