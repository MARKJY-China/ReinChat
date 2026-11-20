/** Ordered reasoning / tool / body timeline helpers shared by the chat view. */

export const SEGMENT_REASONING = 'reasoning';
export const SEGMENT_TOOL = 'tool';
export const SEGMENT_TEXT = 'text';

/** Appends a reasoning delta, merging into a trailing reasoning block. */
export function appendReasoning(segments, text, now = Date.now()) {
  if (!text) return segments;
  const next = segments.slice();
  const last = next[next.length - 1];
  if (last && last.type === SEGMENT_REASONING) {
    next[next.length - 1] = { ...last, text: (last.text || '') + text, startedAt: last.startedAt || now };
  } else {
    next.push({ type: SEGMENT_REASONING, text, status: 'done', startedAt: now });
  }
  return next;
}

/** Appends body text, merging into a trailing text block. */
export function appendText(segments, text, now = Date.now()) {
  if (!text) return segments;
  const next = segments.slice();
  const last = next[next.length - 1];
  if (last && last.type === SEGMENT_TEXT) {
    next[next.length - 1] = { ...last, text: (last.text || '') + text, startedAt: last.startedAt || now };
  } else {
    next.push({ type: SEGMENT_TEXT, text, status: 'done', startedAt: now });
  }
  return next;
}

/**
 * Splits a finished timeline into the folded work history and the final answer.
 * Only trailing text blocks belong to the answer; interim reports written
 * between tool calls stay inside the work history.
 */
export function splitAnswer(segments) {
  const list = segments || [];
  let lastStep = -1;
  for (let i = list.length - 1; i >= 0; i--) {
    if (list[i].type !== SEGMENT_TEXT) { lastStep = i; break; }
  }
  if (lastStep < 0) return { work: [], answer: '' };
  const work = list.slice(0, lastStep + 1);
  const answer = list
    .slice(lastStep + 1)
    .filter((segment) => segment.type === SEGMENT_TEXT)
    .map((segment) => segment.text || '')
    .join('');
  return { work, answer };
}

/** Answer text for rendering; falls back to stored content for legacy messages. */
export function answerText(segments, content) {
  const { answer } = splitAnswer(segments);
  if (answer.trim()) return answer;
  const hasText = (segments || []).some((segment) => segment.type === SEGMENT_TEXT);
  return hasText ? '' : (content || '');
}
