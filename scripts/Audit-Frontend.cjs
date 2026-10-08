const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');
const root = path.resolve(__dirname, '../src/main/resources/static');
const files = [];
function walk(directory) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name);
    if (entry.isDirectory()) walk(file); else files.push(file);
  }
}
walk(root);
const brokenLinks = [], syntaxErrors = [], missingControls = [];
// These controllers require the same DOM contract on every page loading them.
// In particular, customer and staff case views must both support the shared case controller.
const sharedControllers = new Set(['support/cases.js', 'support/reports.js',
  'account/profile-addresses.js', 'account/profile-delete.js', 'manager/dashboard.js',
  'manager/rider-assignments.js', 'staff/receive-items.js', 'staff/processing-board.js',
  'staff/issue-reports.js']);
const pages = files.filter(file => file.endsWith('.html'));
const scripts = files.filter(file => file.endsWith('.js'));
for (const file of pages) {
  const html = fs.readFileSync(file, 'utf8');
  for (const match of html.matchAll(/(?:href|src)\s*=\s*["']([^"']+)["']/g)) {
    const link = match[1].split(/[?#]/)[0];
    if (!link || /^(?:https?:|mailto:|tel:|data:|javascript:|\/api\/)/.test(link)) continue;
    const target = link.startsWith('/') ? path.join(root, link) : path.resolve(path.dirname(file), link);
    if (!fs.existsSync(target)) brokenLinks.push({ file: path.relative(root, file), link });
  }
  for (const match of html.matchAll(/<script\b[^>]*src=["']([^"']+)["']/g)) {
    const script = path.resolve(path.dirname(file), match[1]);
    const relative = path.relative(path.join(root, 'js'), script).replaceAll('\\', '/');
    if (!sharedControllers.has(relative) || !fs.existsSync(script)) continue;
    const source = fs.readFileSync(script, 'utf8');
    const available = new Set([...(`${html}\n${source}`).matchAll(/\bid=["']([^"']+)["']/g)].map(item => item[1]));
    const required = new Set([...source.matchAll(/\$\(['"]([^'"]+)['"]\)/g)].map(item => item[1]));
    for (const id of required) if (!available.has(id)) missingControls.push({file: path.relative(root, file), script: relative, id});
  }
}
for (const file of scripts) {
  const result = spawnSync(process.execPath, ['--check', file], { encoding: 'utf8' });
  if (result.status !== 0) syntaxErrors.push({ file: path.relative(root, file), error: result.stderr || result.error?.message });
}
console.log(JSON.stringify({ htmlPages: pages.length, jsFiles: scripts.length, brokenLinks, syntaxErrors, missingControls }, null, 2));
process.exitCode = brokenLinks.length || syntaxErrors.length || missingControls.length ? 1 : 0;
