#!/usr/bin/env node
/**
 * P0-3 门禁：硬编码颜色扫描（前端，Phase 3 启用）。
 * 检测 .vue/.tsx/.ts/.html/.css 中的 hex/rgb 颜色字面量。
 * 例外：#fff / #ffffff / #000 / #000000 及 CSS 变量 var(--...)。
 * 用法：node scripts/check-hardcoded-color.js [目录]
 */
'use strict';

const fs = require('fs');
const path = require('path');

// 颜色字面量：hex 或 rgb()/rgba()
const COLOR_RE = /#[0-9a-fA-F]{3,8}\b|rgba?\(\s*\d{1,3}\s*,\s*\d{1,3}\s*,\s*\d{1,3}/g;
const ALLOWED = new Set(['#fff', '#ffffff', '#FFF', '#FFFFFF', '#000', '#000000']);

const EXTENSIONS = new Set(['.vue', '.tsx', '.ts', '.html', '.css', '.scss']);
const EXCLUDE_DIRS = new Set(['node_modules', '.git', 'target', 'dist', '.workbuddy', 'build']);

function walk(dir, out) {
  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch (e) {
    return;
  }
  for (const e of entries) {
    if (e.isDirectory()) {
      if (EXCLUDE_DIRS.has(e.name)) continue;
      walk(path.join(dir, e.name), out);
    } else if (EXTENSIONS.has(path.extname(e.name))) {
      out.push(path.join(dir, e.name));
    }
  }
}

const root = process.argv[2] || '.';
const files = [];
walk(root, files);

let violations = 0;
for (const f of files) {
  const text = fs.readFileSync(f, 'utf8');
  const lines = text.split('\n');
  lines.forEach((line, i) => {
    // 跳过 CSS 变量引用
    const stripped = line.replace(/var\(--[^)]*\)/g, '');
    const m = stripped.match(COLOR_RE);
    if (m) {
      const bad = m.filter((c) => !ALLOWED.has(c));
      if (bad.length) {
        violations++;
        console.log(`[color] ${f}:${i + 1} ${bad.join(', ')}`);
      }
    }
  });
}

console.log(`扫描 ${files.length} 个文件，命中硬编码颜色 ${violations} 处`);
if (violations > 0) {
  console.error('FAIL：检测到硬编码颜色，违反 P0-3 规则（颜色须走 Design Token）');
  process.exit(1);
}
console.log('PASS');
