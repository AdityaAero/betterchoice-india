// Asks version 4's database (loaded locally in PGlite, as tools/v4_db_test.mjs does) for shop titles, one per line.
//   node tools/v4_lookup.mjs titles.txt      each line: title<TAB>shop product type (optional)
// Prints the match (name, score, method) or "not listed" with the nearest names.
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const project = join(here, '..');
const req = createRequire(join(project, '..', '.tools', 'pglite', 'package.json'));
const { PGlite } = await import(pathToFileURL(req.resolve('@electric-sql/pglite')).href);
const { vector } = await import(pathToFileURL(req.resolve('@electric-sql/pglite-pgvector')).href);
const { pg_trgm } = await import(pathToFileURL(req.resolve('@electric-sql/pglite/contrib/pg_trgm')).href);

const db = await PGlite.create({ extensions: { vector, pg_trgm } });
await db.exec('create role anon nologin; create role authenticated nologin;');
await db.exec(readFileSync(join(project, 'supabase/migrations/20261015120000_v4_catalogue_vector.sql'), 'utf8'));
await db.exec(readFileSync(join(project, 'supabase/seed/v4/v4_catalogue_all.sql'), 'utf8'));

for (const line of readFileSync(process.argv[2], 'utf8').split(/\r?\n/).filter(l => l.trim())) {
  const [title, type] = line.split('\t');
  const r = (await db.query('select public.bc_lookup($1, $2, $3) j', [title, type || null, 'com.grofers.customerapp'])).rows[0].j;
  console.log(r.match
    ? `MATCH  ${title}  ->  ${r.match.name} (${r.match.method}, ${r.match.score}) [${r.group ? r.group.id : '-'}]`
    : `none   ${title}  (nearest: ${(r.nearest || []).map(n => n.name + ' ' + n.score).join('; ')})`);
}
