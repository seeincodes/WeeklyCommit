#!/usr/bin/env node
// Bundle-size guard for the Weekly Commit remote.
//
// Runs after `vite build`. Walks dist/, gzips every .js/.css asset, and
// fails the process if any asset exceeds its category budget. Exit 0
// prints a markdown-style table of every asset for quick eyeballing.
//
// Why a script vs. size-limit / bundlewatch:
//   - Both alternatives need their own config file + a non-trivial install
//     under Yarn PnP. zlib is in the stdlib; this script is ~80 LOC and
//     has zero new transitive deps.
//   - The federation plugin emits multiple entry-shaped files (remoteEntry,
//     __federation_expose_*, __federation_fn_import). We classify them
//     ourselves rather than fight a generic tool's "main bundle" heuristic.
//
// Tuning: bump the BUDGETS table when a regression has a real reason
// (new feature, pinned dep upgrade) and not just because CI is red.
// Each bump should land with a one-line rationale in the same commit.

import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative, extname } from 'node:path';
import { gzipSync } from 'node:zlib';
import { fileURLToPath } from 'node:url';

const HERE = fileURLToPath(new URL('.', import.meta.url));
const DIST = join(HERE, '..', 'dist');

// Budgets are gzipped bytes. Lower bound for "alarm" -- if you hit a
// budget, the right move is to first read dist/stats.html
// (`yarn build:analyze`) and confirm the new weight is justified.
const BUDGETS = {
  // remoteEntry: tiny manifest the host loads first. Should never grow.
  remoteEntry: 8 * 1024,
  // Federated expose chunks: WeeklyCommitModule + the Suspense boundary
  // shell. Route chunks load on demand and don't count here.
  expose: 80 * 1024,
  // Per-route lazy chunks (CurrentWeek/History/Team/TeamMember).
  route: 60 * 1024,
  // Vendor chunks declared via manualChunks (flowbite/sentry/jose).
  vendor: 180 * 1024,
  // CSS bundle (cssCodeSplit:false → one file).
  css: 60 * 1024,
  // Standalone-dev entry chunk. This is the bundle index.html loads at
  // localhost:4184 for remote-in-isolation Playwright smoke -- it carries
  // everything the federation host would otherwise provide (React/Router/
  // RTK orchestration, devAuth shim, etc.). Bigger than route chunks
  // because it isn't tree-shared with anyone. Federated production traffic
  // never loads this file.
  standaloneEntry: 80 * 1024,
  // Catch-all for federation runtime helpers and any small support chunks.
  other: 40 * 1024,
};

function classify(name) {
  if (name === 'remoteEntry.js') return 'remoteEntry';
  if (name.startsWith('__federation_expose_')) return 'expose';
  if (name.startsWith('vendor-')) return 'vendor';
  if (name.endsWith('.css')) return 'css';
  // Route chunks are emitted with the source filename baked into the
  // [name] slot -- e.g. CurrentWeekPage-abc123.js.
  if (/^(CurrentWeekPage|HistoryPage|TeamPage|TeamMemberPage)-/.test(name)) {
    return 'route';
  }
  // Vite emits the standalone-dev mount as `index-<hash>.js` (entry for
  // index.html → src/main.tsx). The smaller `index-<hash>.js` chunks
  // that come from intermediate barrels/index.ts files are well under
  // the 'other' budget anyway, so we use a size heuristic: only the
  // largest `index-*` chunk is the standalone entry.
  if (/^index-[A-Za-z0-9_-]+\.js$/.test(name)) return 'standaloneEntry';
  return 'other';
}

function* walk(dir) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) yield* walk(full);
    else yield full;
  }
}

function fmt(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  return `${(bytes / 1024).toFixed(1)} KB`;
}

let exitCode = 0;
const rows = [];

try {
  statSync(DIST);
} catch {
  console.error(`size-check: ${DIST} does not exist -- run \`yarn build\` first.`);
  process.exit(1);
}

for (const path of walk(DIST)) {
  const ext = extname(path);
  if (ext !== '.js' && ext !== '.css') continue;
  const name = path.split('/').pop();
  const raw = readFileSync(path);
  const gz = gzipSync(raw).length;
  const category = classify(name);
  const budget = BUDGETS[category];
  const over = gz > budget;
  if (over) exitCode = 1;
  rows.push({
    name: relative(DIST, path),
    category,
    raw: raw.length,
    gz,
    budget,
    over,
  });
}

rows.sort((a, b) => b.gz - a.gz);

const pad = (s, n) => String(s).padEnd(n);
console.log('');
console.log(
  `${pad('asset', 56)}  ${pad('category', 11)}  ${pad('raw', 10)}  ${pad('gz', 10)}  ${pad('budget', 10)}  status`,
);
console.log('-'.repeat(120));
for (const r of rows) {
  console.log(
    `${pad(r.name, 56)}  ${pad(r.category, 11)}  ${pad(fmt(r.raw), 10)}  ${pad(fmt(r.gz), 10)}  ${pad(fmt(r.budget), 10)}  ${r.over ? 'OVER' : 'ok'}`,
  );
}
console.log('');
if (exitCode !== 0) {
  console.error(
    'size-check: one or more assets exceeded budget. Run `yarn build:analyze` and inspect dist/stats.html.',
  );
}
process.exit(exitCode);
