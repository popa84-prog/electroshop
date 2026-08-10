#!/usr/bin/env node
/**
 * A structural sanity check for this project's JavaScript and JSX.
 *
 * ## Why it exists
 *
 * Neither npm nor pip will install anything in this sandbox — the registry
 * answers 403 — so there is no esbuild, no Babel and no JS parser available.
 * Without this, Vercel would be the first thing to ever see a syntax error in
 * the frontend, and finding out there means a failed deployment rather than a
 * failed check.
 *
 * ## What it checks
 *
 *   1. Every brace, bracket and parenthesis is balanced.
 *   2. Every string, template literal and block comment is terminated.
 *   3. Every JSX element that opens also closes, in the right order.
 *
 * It checks nothing semantic — a misspelled import, a missing prop, a hook
 * called conditionally all pass. It catches the class of error that stops a
 * build outright, which is the class hand-written code actually produces.
 *
 * ## Why it tracks JSX context rather than guessing
 *
 * An earlier version worked from local cues alone and was abandoned. Two
 * characters are ambiguous in exactly the way that makes that impossible:
 *
 *   - `<` is both "less than" and "open element". `i < source.length` and
 *     `{count} <span>` are lexically identical up to the character after the
 *     angle bracket.
 *   - `/` is division, the start of a regex, and ordinary text. `a / b`,
 *     `/^x$/`, `</div>` and the literal text `Preț/buc.` differ only by where
 *     they appear.
 *
 * Guessing produced over a thousand phantom errors across files that build
 * correctly — worse than no check at all, because the one real finding would
 * have been invisible in the noise.
 *
 * So the scanner tracks one extra piece of state: whether it is inside JSX
 * children or inside ordinary code. That single distinction resolves both
 * ambiguities. Inside children, `<` always opens or closes an element and `/` is
 * always text. Inside code, the usual "can an expression start here?" rule
 * separates a regex from a division sign.
 *
 * Knowing which one it is comes from comparing the bracket depth now against the
 * depth recorded when the innermost element was entered. Equal depth means the
 * scanner is in that element's children; deeper means it has descended into a
 * `{…}` expression, where ordinary code rules apply again. That is what lets
 * `{items.map((x) => <li>{x.name}</li>)}` scan correctly without a parser.
 *
 * ## Validating the validator
 *
 * The check is run against the whole existing frontend, which builds and deploys
 * today, and reports zero problems there. That is what makes a warning from this
 * tool worth acting on rather than worth suppressing.
 */

const fs = require('fs');
const path = require('path');

/** Elements that never have a closing tag. */
const VOID_TAGS = new Set([
  'area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input',
  'link', 'meta', 'param', 'source', 'track', 'wbr',
]);

/** Keywords after which a `/` starts a regex rather than dividing. */
const EXPRESSION_STARTERS = new Set([
  'return', 'typeof', 'instanceof', 'in', 'of', 'new', 'delete',
  'void', 'case', 'do', 'else', 'yield', 'await',
]);

function check(source, filename) {
  const errors = [];

  /** Open ( [ { in source order. */
  const brackets = [];
  /** Open JSX elements, each recording the bracket depth it was opened at. */
  const elements = [];

  let i = 0;
  let line = 1;
  /** Whether a `/` here would begin a regex. Only consulted in code context. */
  let regexAllowed = true;

  const OPENERS = { '(': ')', '[': ']', '{': '}' };
  const CLOSERS = { ')': '(', ']': '[', '}': '{' };

  const advance = (n = 1) => {
    for (let k = 0; k < n; k++) {
      if (source[i] === '\n') line++;
      i++;
    }
  };

  /**
   * True when the scanner sits directly in an element's children.
   *
   * Equal bracket depth means nothing has been opened since the element was
   * entered, so what follows is markup and text. A deeper stack means the
   * scanner has descended into a `{…}` expression, where JavaScript rules apply
   * again.
   */
  const inJsxText = () =>
    elements.length > 0 && brackets.length === elements[elements.length - 1].depth;

  while (i < source.length) {
    const ch = source[i];
    const next = source[i + 1];
    const jsxText = inJsxText();

    // ---- comments ----
    if (!jsxText && ch === '/' && next === '/') {
      while (i < source.length && source[i] !== '\n') advance();
      continue;
    }
    if (!jsxText && ch === '/' && next === '*') {
      const startLine = line;
      advance(2);
      let closed = false;
      while (i < source.length) {
        if (source[i] === '*' && source[i + 1] === '/') {
          advance(2);
          closed = true;
          break;
        }
        advance();
      }
      if (!closed) errors.push(`${filename}:${startLine} comentariu bloc neînchis`);
      continue;
    }

    // ---- closing tag ----
    // Recognised in both contexts and without consulting regexAllowed, because
    // `</` is never valid JavaScript in any position.
    if (ch === '<' && next === '/') {
      const match = source.slice(i).match(/^<\/\s*([A-Za-z][\w.]*)?\s*>/);
      if (match) {
        const name = match[1] || '';
        const open = elements.pop();
        if (!open) {
          errors.push(`${filename}:${line} </${name}> fără etichetă de deschidere`);
        } else if (open.name !== name) {
          errors.push(
            `${filename}:${line} </${name}> închide <${open.name}> deschis la linia ${open.line}`
          );
        }
        advance(match[0].length);
        regexAllowed = false;
        continue;
      }
    }

    // ---- opening tag ----
    // In children any `<` before a name or `>` opens an element. In code it does
    // so only where an expression could start, which is what separates it from a
    // comparison operator.
    if (ch === '<' && /[A-Za-z>]/.test(next || '') && (jsxText || regexAllowed)) {
      const match = source.slice(i).match(/^<\s*([A-Za-z][\w.]*)?/);
      if (match) {
        const name = match[1] || '';
        const startLine = line;
        advance(match[0].length);

        // Walk the attribute region. Quoted values may span lines — a long
        // Tailwind class list routinely does — so a newline does not terminate
        // one here, unlike a string in ordinary code.
        let selfClosing = false;
        let depth = 0;
        while (i < source.length) {
          const c = source[i];
          if (c === '"' || c === "'") {
            const quote = c;
            advance();
            while (i < source.length && source[i] !== quote) {
              if (source[i] === '\\') advance();
              advance();
            }
            advance();
            continue;
          }
          if (c === '{') { depth++; advance(); continue; }
          if (c === '}') { depth = Math.max(0, depth - 1); advance(); continue; }
          if (depth === 0 && c === '/' && source[i + 1] === '>') { selfClosing = true; advance(2); break; }
          if (depth === 0 && c === '>') { advance(); break; }
          advance();
        }

        if (!selfClosing && !VOID_TAGS.has(name)) {
          elements.push({ name, line: startLine, depth: brackets.length });
        }
        regexAllowed = false;
        continue;
      }
    }

    // ---- JSX text ----
    // Quotes, slashes and backticks in children are literal characters. An
    // apostrophe in Romanian prose and the slash in "Preț/buc." are text, and
    // reading either as code is what broke every earlier attempt.
    if (jsxText) {
      if (ch === '{') {
        brackets.push({ char: ch, line });
        advance();
        regexAllowed = true;
        continue;
      }
      if (ch === '}') {
        const open = brackets.pop();
        if (!open) errors.push(`${filename}:${line} '}' fără pereche de deschidere`);
        advance();
        regexAllowed = false;
        continue;
      }
      advance();
      continue;
    }

    // ---- strings ----
    if (ch === '"' || ch === "'") {
      const quote = ch;
      const startLine = line;
      advance();
      let closed = false;
      while (i < source.length) {
        if (source[i] === '\\') { advance(2); continue; }
        if (source[i] === quote) { advance(); closed = true; break; }
        if (source[i] === '\n') break;
        advance();
      }
      if (!closed) errors.push(`${filename}:${startLine} șir de caractere neînchis`);
      regexAllowed = false;
      continue;
    }

    // ---- template literals ----
    if (ch === '`') {
      const startLine = line;
      advance();
      let closed = false;
      let depth = 0;
      while (i < source.length) {
        if (source[i] === '\\') { advance(2); continue; }
        // `${` opens an expression that may itself contain a backtick, so the
        // nesting is tracked rather than assumed flat.
        if (source[i] === '$' && source[i + 1] === '{') { depth++; advance(2); continue; }
        if (source[i] === '}' && depth > 0) { depth--; advance(); continue; }
        if (source[i] === '`' && depth === 0) { advance(); closed = true; break; }
        advance();
      }
      if (!closed) errors.push(`${filename}:${startLine} template literal neînchis`);
      regexAllowed = false;
      continue;
    }

    // ---- regular expressions ----
    if (ch === '/' && regexAllowed) {
      const startLine = line;
      advance();
      let closed = false;
      let inClass = false;
      while (i < source.length) {
        if (source[i] === '\\') { advance(2); continue; }
        if (source[i] === '[') inClass = true;
        else if (source[i] === ']') inClass = false;
        else if (source[i] === '/' && !inClass) { advance(); closed = true; break; }
        else if (source[i] === '\n') break;
        advance();
      }
      if (!closed) errors.push(`${filename}:${startLine} expresie regulată neînchisă`);
      regexAllowed = false;
      continue;
    }

    // ---- brackets ----
    if (OPENERS[ch]) {
      brackets.push({ char: ch, line });
      advance();
      regexAllowed = true;
      continue;
    }
    if (CLOSERS[ch]) {
      const open = brackets.pop();
      if (!open) {
        errors.push(`${filename}:${line} '${ch}' fără pereche de deschidere`);
      } else if (open.char !== CLOSERS[ch]) {
        errors.push(
          `${filename}:${line} '${ch}' închide '${open.char}' deschis la linia ${open.line}`
        );
      }
      advance();
      regexAllowed = false;
      continue;
    }

    // ---- identifiers, numbers and operators ----
    if (/[A-Za-z0-9_$]/.test(ch)) {
      const start = i;
      while (i < source.length && /[A-Za-z0-9_$.]/.test(source[i])) advance();
      regexAllowed = EXPRESSION_STARTERS.has(source.slice(start, i));
      continue;
    }

    if (!/\s/.test(ch)) regexAllowed = true;
    advance();
  }

  for (const open of brackets) {
    errors.push(`${filename}:${open.line} '${open.char}' nu a fost închis`);
  }
  for (const element of elements) {
    errors.push(`${filename}:${element.line} <${element.name}> nu a fost închis`);
  }

  return errors;
}

function walk(dir, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === 'node_modules' || entry.name === 'dist') continue;
      walk(full, out);
    } else if (/\.jsx?$/.test(entry.name)) {
      out.push(full);
    }
  }
  return out;
}

const roots = process.argv.slice(2);
if (roots.length === 0) {
  console.error('folosire: node check_jsx.js <fișier sau director>…');
  process.exit(2);
}

let files = [];
for (const root of roots) {
  files = files.concat(fs.statSync(root).isDirectory() ? walk(root) : [root]);
}

let total = 0;
for (const file of files) {
  const problems = check(fs.readFileSync(file, 'utf8'), path.relative(process.cwd(), file));
  for (const problem of problems) console.log(problem);
  total += problems.length;
}

console.log(`\n${files.length} fișiere verificate, ${total} probleme.`);
process.exit(total === 0 ? 0 : 1);
