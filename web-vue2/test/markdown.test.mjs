import test from 'node:test';
import assert from 'node:assert/strict';
import { renderMarkdown } from '../src/lib/markdown.js';

const sources = [
  { index: 1, title: 'ReinChat 官网', url: 'https://example.com/reinchat' },
  { index: 2, title: 'Docs', url: 'https://example.com/docs' },
];

test('renders [n] as clickable superscript citation badges', () => {
  const html = renderMarkdown('答案见[1]，补充见[2]。', sources);
  assert.match(html, /<sup class="citation">/);
  assert.match(html, /href="https:\/\/example\.com\/reinchat"/);
  assert.match(html, /href="https:\/\/example\.com\/docs"/);
  assert.match(html, /title="ReinChat 官网"/);
  assert.match(html, /data-source-index="2"/);
  assert.ok(!html.includes('[1]'), 'raw [1] should not survive when linked');
  assert.ok(!html.includes('[2]'), 'raw [2] should not survive when linked');
});

test('keeps unknown citation numbers literal', () => {
  const html = renderMarkdown('未知来源[9]', sources);
  assert.ok(html.includes('[9]'));
  assert.ok(!html.includes('citation'));
});

test('does not turn citations inside code spans into links', () => {
  const html = renderMarkdown('`[1]`', sources);
  assert.ok(html.includes('<code>[1]</code>'));
  assert.ok(!html.includes('citation'));
});

test('does not convert markdown links whose label is a number', () => {
  const html = renderMarkdown('[1](https://example.com/other)', sources);
  assert.ok(html.includes('href="https://example.com/other"'));
  assert.ok(!html.includes('citation'));
});

test('ignores non-http source URLs', () => {
  const html = renderMarkdown('[1]', [{ index: 1, title: 'bad', url: 'javascript:alert(1)' }]);
  assert.ok(html.includes('[1]'));
  assert.ok(!html.includes('javascript:'));
  assert.ok(!html.includes('citation'));
});

test('numbers sources by position when index is missing', () => {
  const html = renderMarkdown('[2]', [{ title: 'No index', url: 'https://example.com/no-index' }]);
  assert.ok(html.includes('[2]'));
});
