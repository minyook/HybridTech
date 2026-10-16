import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';

// Web is the visual source of truth. Generated Android resources are committed
// so building the installed app does not require Node or a running web server.
const root = fileURLToPath(new URL('../', import.meta.url));
const css = readFileSync(resolve(root, 'web/src/style.css'), 'utf8');
const ui = readFileSync(resolve(root, 'web/src/ui.tsx'), 'utf8');
const check = process.argv.includes('--check');
let changed = false;
function emit(relative, content) {
  const path = resolve(root, relative);
  let previous = ''; try { previous = readFileSync(path, 'utf8'); } catch {}
  if (previous.replaceAll('\r\n', '\n') === content) return;
  changed = true;
  if (check) console.error(`Outdated web design resource: ${relative}`);
  else { mkdirSync(dirname(path), { recursive: true }); writeFileSync(path, content); }
}
const color = name => {
  const value = css.match(new RegExp(`--${name}:#([a-f0-9]{6})`))?.[1];
  if (!value) throw new Error(`Missing CSS color ${name}`);
  return `Color(0xFF${value.toUpperCase()})`;
};
const dimension = (selector, property) => {
  const body = css.slice(css.indexOf(`${selector}{`)).split('}')[0];
  const value = body.match(new RegExp(`(?:[;{])${property}:(\\d+)px`))?.[1];
  if (!value) throw new Error(`Missing CSS dimension ${selector} ${property}`);
  return value;
};
const icons = [];
for (const line of ui.split('\n')) {
  const entry = line.trim().match(/^(\w+): (.+),$/); if (!entry) continue;
  const [, name, jsx] = entry; const paths = [];
  for (const tag of jsx.matchAll(/<(path|circle|rect)\s+([^>]+)\/>/g)) {
    const attrs = Object.fromEntries([...tag[2].matchAll(/(\w+)="([^"]*)"/g)].map(m => [m[1], m[2]]));
    let d = attrs.d;
    if (tag[1] === 'circle') {
      const x = +attrs.cx, y = +attrs.cy, r = +attrs.r;
      d = `M${x-r},${y}a${r},${r} 0,1 0,${2*r},0a${r},${r} 0,1 0,${-2*r},0`;
    }
    if (tag[1] === 'rect') {
      const x = +attrs.x, y = +attrs.y, w = +attrs.width, h = +attrs.height, r = +(attrs.rx || 0);
      d = `M${x+r},${y}h${w-2*r}q${r},0 ${r},${r}v${h-2*r}q0,${r} ${-r},${r}h${2*r-w}q${-r},0 ${-r},${-r}v${2*r-h}q0,${-r} ${r},${-r}Z`;
    }
    if (d) paths.push(`    <path android:pathData="${d}" android:fillColor="@android:color/transparent" android:strokeColor="#FF6B7684" android:strokeWidth="1.7" android:strokeLineCap="round" android:strokeLineJoin="round" />`);
  }
  if (!paths.length) continue;
  icons.push(name);
  emit(`app/src/main/res/drawable/workspace_icon_${name}.xml`, `<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated from web/src/ui.tsx. Run node tools/sync-web-design.mjs. -->\n<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="22dp" android:height="22dp" android:viewportWidth="24" android:viewportHeight="24">\n${paths.join('\n')}\n</vector>\n`);
}
if (icons.length !== 17) throw new Error(`Web icon extraction is incomplete (${icons.length})`);
emit('app/src/main/java/com/minyook/sllm2/ui/WebDesignTokens.kt', `// Generated from web/src/style.css and ui.tsx. Do not edit by hand.\npackage com.minyook.sllm2.ui\n\nimport androidx.compose.ui.graphics.Color\nimport com.minyook.sllm2.R\n\ninternal object WorkspaceDesign {\n    val Blue = ${color('blue')}\n    val BlueWeak = ${color('blue-weak')}\n    val Ink = ${color('ink')}\n    val Muted = ${color('muted')}\n    val Border = ${color('line')}\n    val Green = ${color('green')}\n    val Canvas = Color(0xFFF7F9FC)\n    const val SidebarWidth = ${dimension('.sidebar', 'width')}\n    const val TopbarHeight = ${dimension('.topbar', 'height')}\n    const val CardRadius = ${dimension('.card', 'border-radius')}\n    const val CardPadding = ${dimension('.card', 'padding')}\n    const val SidebarBreakpoint = 850\n}\n\ninternal enum class WorkspaceSymbol(val resource: Int) {\n${icons.map(name => `    ${name.toUpperCase()}(R.drawable.workspace_icon_${name}),`).join('\n')}\n}\n`);
if (check && changed) process.exitCode = 1;
else console.log(`${icons.length} web icons and Android design tokens ${check ? 'match' : 'synchronized'}.`);
