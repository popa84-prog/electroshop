#!/usr/bin/env node
/**
 * Verifies that every relative import in the frontend resolves to a real file,
 * and that every named import from a local module is actually exported by it.
 *
 * The JSX checker catches syntax; this catches the other half of what stops a
 * Vite build — a path that does not exist, or a name that the target module
 * never exported. Both produce a clean-looking file that fails at bundle time,
 * and with no npm in this sandbox there is nothing else that would notice.
 *
 * Only relative imports are checked. A bare specifier resolves through
 * node_modules, which is not installed here, so anything this could say about
 * one would be a guess.
 */
const fs = require('fs');
const path = require('path');

const ROOT = process.argv[2] || 'frontend/src';
const EXTENSIONS = ['', '.js', '.jsx', '/index.js', '/index.jsx'];

function walk(dir, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, out);
    else if (/\.jsx?$/.test(entry.name)) out.push(full);
  }
  return out;
}

/** Every name a module exports, as far as a regex can tell. */
function exportsOf(file) {
  const src = fs.readFileSync(file, 'utf8');
  const names = new Set();

  if (/export\s+default\b/.test(src)) names.add('default');

  for (const m of src.matchAll(/export\s+(?:const|let|var|function|class)\s+(\w+)/g)) {
    names.add(m[1]);
  }
  // export { a, b as c } — and the re-export form with a source.
  for (const m of src.matchAll(/export\s*\{([^}]*)\}/g)) {
    for (const part of m[1].split(',')) {
      const alias = part.trim().split(/\s+as\s+/);
      const name = (alias[1] || alias[0]).trim();
      if (name) names.add(name);
    }
  }
  return names;
}

function resolve(fromFile, spec) {
  const base = path.resolve(path.dirname(fromFile), spec);
  for (const ext of EXTENSIONS) {
    const candidate = base + ext;
    if (fs.existsSync(candidate) && fs.statSync(candidate).isFile()) return candidate;
  }
  return null;
}

const files = walk(ROOT);
const exportCache = new Map();
let problems = 0;

for (const file of files) {
  const src = fs.readFileSync(file, 'utf8');
  for (const m of src.matchAll(/import\s+([^'"]*?)\s*from\s*['"](\.[^'"]+)['"]/g)) {
    const clause = m[1];
    const spec = m[2];

    const target = resolve(file, spec);
    if (!target) {
      console.log(`${file}: importul '${spec}' nu se rezolvă la niciun fișier`);
      problems++;
      continue;
    }

    if (!exportCache.has(target)) exportCache.set(target, exportsOf(target));
    const available = exportCache.get(target);

    // A barrel that re-exports with `export ... from` lists the names, so the
    // regex above already caught them.
    const braces = clause.match(/\{([^}]*)\}/);
    if (braces) {
      for (const part of braces[1].split(',')) {
        const alias = part.trim().split(/\s+as\s+/);
        const name = alias[0].trim();
        if (name && !available.has(name)) {
          console.log(`${file}: '${name}' nu este exportat de ${path.relative('.', target)}`);
          problems++;
        }
      }
    }

    const def = clause.replace(/\{[^}]*\}/, '').replace(/,/g, '').trim();
    if (def && !available.has('default')) {
      console.log(`${file}: ${path.relative('.', target)} nu are export implicit`);
      problems++;
    }
  }
}

console.log(`\n${files.length} fișiere, ${problems} probleme de import.`);
process.exit(problems === 0 ? 0 : 1);
