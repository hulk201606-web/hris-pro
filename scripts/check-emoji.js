#!/usr/bin/env node
/**
 * P0-1 门禁：emoji 扫描（专家团规范正则）。
 * 用法：node scripts/check-emoji.js [目录]
 * 扫描 .java/.ts/.tsx/.vue/.html/.jsx/.sql/.md 文件中的 emoji 字符，
 * 命中即以非零退出码失败（供 CI 门禁）。
 */
'use strict';

const fs = require('fs');
const path = require('path');

// P0 emoji 检测正则（专家团规范）
const EMOJI_RE = /[\u{1F300}-\u{1F9FF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}\u{FE00}-\u{FE0F}\u{1F000}-\u{1F02F}\u{1F0A0}-\u{1F0FF}\u{1F100}-\u{1F64F}\u{1F680}-\u{1F6FF}\u{1F900}-\u{1F9FF}\u{1FA00}-\u{1FA6F}\u{1FA70}-\u{1FAFF}\u{200D}\u{20E3}\u{E0020}-\u{E007F}]/gu;

const EXTENSIONS = new Set(['.java', '.ts', '.tsx', '.vue', '.html', '.jsx', '.sql', '.md']);
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
    const m = line.match(EMOJI_RE);
    if (m) {
      violations++;
      console.log(`[emoji] ${f}:${i + 1} ${JSON.stringify(m.join(''))}`);
    }
  });
}

console.log(`扫描 ${files.length} 个文件，命中 emoji ${violations} 处`);
if (violations > 0) {
  console.error('FAIL：检测到 emoji 字符，违反 P0-1 规则（禁止 emoji 作功能图标）');
  process.exit(1);
}
console.log('PASS');
