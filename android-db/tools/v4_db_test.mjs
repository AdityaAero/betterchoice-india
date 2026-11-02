// Version 4's database, tested locally: the migration and the seed loaded into PGlite (Postgres 18 in WebAssembly, with
// pgvector and pg_trgm), then the lookup measured for correctness and speed.
//   node tools/v4_db_test.mjs
// Needs ../.tools/pglite (npm install @electric-sql/pglite @electric-sql/pglite-pgvector). Writes ../export/db_test.json.
import { readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const project = join(here, '..');
const v4 = join(project, '..');
const req = createRequire(join(v4, '.tools', 'pglite', 'package.json'));
const { PGlite } = await import(pathToFileURL(req.resolve('@electric-sql/pglite')).href);
const { vector } = await import(pathToFileURL(req.resolve('@electric-sql/pglite-pgvector')).href);
const { pg_trgm } = await import(pathToFileURL(req.resolve('@electric-sql/pglite/contrib/pg_trgm')).href);

const db = await PGlite.create({ extensions: { vector, pg_trgm } });
await db.exec('create role anon nologin; create role authenticated nologin;');
await db.exec(readFileSync(join(project, 'supabase/migrations/20261015120000_v4_catalogue_vector.sql'), 'utf8'));
await db.exec(readFileSync(join(project, 'supabase/seed/v4/v4_catalogue_all.sql'), 'utf8'));
const count = (await db.query('select count(*)::int n from catalogue_v4.products')).rows[0].n;
console.log('loaded', count, 'products');

const catalogue = JSON.parse(readFileSync(join(project, 'app/src/main/assets/catalogue/swaps_v1.json'), 'utf8'));
const exported = new Map(readFileSync(join(v4, 'export/products_export.jsonl'), 'utf8').trim().split('\n')
  .map(l => JSON.parse(l)).map(r => [r.gtin, r]));
const display = p => {
  const brand = p.brand || '', first = brand.toLowerCase().split(/[^\p{L}\p{N}]+/u)[0];
  return !brand ? p.name : (first && p.name.toLowerCase().includes(first) ? p.name : brand + ' ' + p.name);
};
const keyOf = async t => (await db.query('select catalogue_v4.norm($1) k', [t])).rows[0].k;

const timings = { exact: [], hybrid: [], miss: [] };
async function lookup(name) {
  const t0 = performance.now();
  const r = (await db.query('select public.bc_lookup($1, null, $2) j', [name, 'com.zeptoconsumerapp'])).rows[0].j;
  const ms = performance.now() - t0;
  timings[r.match ? r.match.method : 'miss'].push(ms);
  return r;
}

// 1. Every listed name, exactly as listed.
let exactOk = 0;
for (const p of catalogue.products) {
  const r = await lookup(display(p));
  if (r.match && (r.match.gtin === p.gtin || (await keyOf(r.match.name)) === (await keyOf(display(p))))) exactOk++;
}

// 2. The way shops write the same products.
const typo = s => {
  const words = s.split(' ');
  let i = words.reduce((b, w, k) => (w.length > words[b].length ? k : b), 0);
  const w = words[i];
  if (w.length < 5) return s;
  const j = Math.floor(w.length / 2);
  words[i] = w.slice(0, j - 1) + w[j] + w[j - 1] + w.slice(j + 1);
  return words.join(' ');
};
const variants = p => {
  const d = display(p), brand = p.brand || '';
  const out = [
    ['claims and pack', d + ' | Crunchy & Tasty, 1 pack (' + (p.pack || 200) + ' ' + (p.unit || 'g') + ')'],
    ['lower case, no punctuation', d.toLowerCase().replace(/[^\p{L}\p{N} ]+/gu, ' ') + ' ' + (p.pack || 200) + (p.unit || 'g')],
    ['one typo', typo(d)],
  ];
  if (brand && !p.name.toLowerCase().includes(brand.toLowerCase().split(' ')[0])) out.push(['"Name By Brand"', p.name + ' By ' + brand]);
  if (brand && p.name.split(' ').length >= 3) out.push(['brand left out', p.name]);
  return out;
};
const byKind = {};
const wrong = [];
for (const p of catalogue.products) {
  for (const [kind, title] of variants(p)) {
    const r = await lookup(title);
    const k = byKind[kind] ||= { n: 0, right: 0, wrong: 0, none: 0 };
    k.n++;
    if (!r.match) { k.none++; continue; }
    const same = r.match.gtin === p.gtin || (await keyOf(r.match.name)) === (await keyOf(display(p)));
    if (same) k.right++; else { k.wrong++; if (wrong.length < 25) wrong.push([kind, title, r.match.name, r.match.score]); }
  }
}

// 3. Products that are not listed (other Open Food Facts India records): a match must be the same product.
const listed = new Set(catalogue.products.map(p => p.gtin));
const offProducts = JSON.parse(readFileSync(join(v4, '..', 'catalogue_build/off_india/products_2025.json'), 'utf8'))
  .filter(p => !listed.has(p.gtin));
const words = s => new Set(s.toLowerCase().split(/[^\p{L}\p{N}]+/u).filter(w => w.length > 2));
const overlap = (a, b) => { const A = words(a), B = words(b); let n = 0; for (const w of A) if (B.has(w)) n++; return n / Math.max(1, Math.min(A.size, B.size)); };
const unlisted = { n: 0, none: 0, sameNamed: 0, other: 0, examples: [] };
for (const p of offProducts) {
  const name = display(p);
  const r = await lookup(name);
  unlisted.n++;
  if (!r.match) { unlisted.none++; continue; }
  if (overlap(name, r.match.name) >= 0.75) unlisted.sameNamed++;
  else { unlisted.other++; if (unlisted.examples.length < 20) unlisted.examples.push([name, r.match.name, r.match.score]); }
}

// 4. Real shop titles from the recorded Zepto pages.
const recorded = [];
for (const line of readFileSync(join(v4, '..', 'audit/zepto-3.8.0/_app_results.jsonl'), 'utf8').trim().split('\n')) {
  const row = JSON.parse(line);
  if (row.name && !recorded.some(x => x[0] === row.name)) recorded.push([row.name]);
}
for (const item of recorded) {
  const r = await lookup(item[0]);
  item.push(r.match ? r.match.name : null, r.match ? r.match.score : (r.nearest[0] || {}).score ?? null);
}

// 5. The answer carries exactly the engine's candidates; the tables are closed to the public role.
const sample = catalogue.products.find(p => (exported.get(p.gtin).alternatives || []).length >= 10);
const answer = await lookup(display(sample));
const candidatesOk = answer.alternatives.length === exported.get(sample.gtin).alternatives.length
  && answer.alternatives.every((a, i) => a.gtin === exported.get(sample.gtin).alternatives[i]);
let closed = false;
try { await db.exec('set role anon; select count(*) from catalogue_v4.products;'); } catch (e) { closed = /permission denied/i.test(e.message); }
await db.exec('reset role;');
let functionOpen = false;
try { await db.exec("set role anon; select public.bc_lookup('Lays Classic Salted');"); functionOpen = true; } catch (e) { functionOpen = false; }
await db.exec('reset role;');

const pct = (a, q) => { const s = [...a].sort((x, y) => x - y); return s.length ? +s[Math.min(s.length - 1, Math.floor(q * s.length))].toFixed(2) : null; };
const summary = {
  products: count,
  exact_names: { n: catalogue.products.length, matched: exactOk },
  shop_variants: byKind,
  wrong_examples: wrong,
  unlisted_products: unlisted,
  recorded_zepto_titles: recorded,
  answer_candidates_match_engine: candidatesOk,
  tables_closed_to_public: closed,
  lookup_open_to_public: functionOpen,
  latency_ms_pglite: Object.fromEntries(Object.entries(timings).map(([k, v]) => [k, { n: v.length, p50: pct(v, 0.5), p95: pct(v, 0.95) }])),
};
writeFileSync(join(v4, 'export/db_test.json'), JSON.stringify(summary, null, 1));
console.log(JSON.stringify({ ...summary, recorded_zepto_titles: recorded.filter(x => x[1]).length + ' of ' + recorded.length + ' matched',
  wrong_examples: wrong.slice(0, 8), unlisted_products: { ...unlisted, examples: unlisted.examples.slice(0, 8) } }, null, 1));
