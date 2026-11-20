import test from 'node:test';
import assert from 'node:assert/strict';
import { appendReasoning, appendText, splitAnswer, answerText } from '../src/lib/timeline.js';

test('interim text stays in work history, trailing text is the answer', () => {
  let segments = [];
  segments = appendReasoning(segments, 'plan', 1);
  segments = appendText(segments, 'interim report', 2);
  segments = appendReasoning(segments, 'more', 3);
  segments.push({ type: 'tool', name: 'web_search', status: 'done', startedAt: 4, endedAt: 9 });
  segments = appendReasoning(segments, 'after tool', 10);
  segments = appendText(segments, 'final answer', 11);

  const { work, answer } = splitAnswer(segments);
  assert.equal(answer, 'final answer');
  assert.equal(work.length, 5);
  assert.equal(work[1].type, 'text');
  assert.equal(work[1].text, 'interim report');
  assert.equal(work[4].text, 'after tool');
  assert.equal(answerText(segments, 'stored'), 'final answer');
});

test('legacy messages fall back to stored content', () => {
  const segments = [
    { type: 'reasoning', text: 'plan' },
    { type: 'tool', name: 'web_search', status: 'done' },
  ];
  assert.equal(answerText(segments, 'stored body'), 'stored body');
  assert.equal(splitAnswer(segments).answer, '');
});

test('text deltas merge into one block', () => {
  let segments = appendText([], 'hello ', 1);
  segments = appendText(segments, 'world', 2);
  assert.equal(segments.length, 1);
  assert.equal(segments[0].text, 'hello world');
});

test('after a tool, body text belongs to the answer only', () => {
  let segments = [{ type: 'tool', name: 'web_search', status: 'done', startedAt: 1, endedAt: 5 }];
  segments = appendText(segments, 'answer', 6);
  assert.equal(splitAnswer(segments).answer, 'answer');
  assert.equal(splitAnswer(segments).work.length, 1);
});
