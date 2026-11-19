export function uuid() {
  if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export function escapeHtml(value) {
  return String(value == null ? '' : value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

export function cleanDSML(text) {
  return String(text || '')
    .replace(/<[^>]*DSML[^>]*calls[^>]*>[\s\S]*?<\/[^>]*DSML[^>]*calls[^>]*>/gi, '')
    .replace(/<[^>]*DSML[^>]*>/gi, '')
    .replace(/(^|\n)\s*<\s*(?=\n|$)/g, '$1')
    .replace(/<$/, '')
    .trim();
}

function inline(text) {
  let value = escapeHtml(text);
  const codes = [];
  value = value.replace(/`([^`]+)`/g, (_, code) => {
    codes.push('<code>' + code + '</code>');
    return '\u0000' + (codes.length - 1) + '\u0000';
  });
  value = value.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g,
    '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');
  value = value.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  value = value.replace(/__([^_]+)__/g, '<strong>$1</strong>');
  value = value.replace(/~~([^~]+)~~/g, '<del>$1</del>');
  value = value.replace(/(^|[^*])\*([^*\n]+)\*/g, '$1<em>$2</em>');
  return value.replace(/\u0000(\d+)\u0000/g, (_, i) => codes[Number(i)] || '');
}

/** Small safe CommonMark subset, including GFM tables. */
export function renderMarkdown(raw) {
  const source = cleanDSML(raw).replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  if (!source.trim()) return '';
  const lines = source.split('\n');
  const out = [];
  let paragraph = [];
  const flush = () => {
    if (paragraph.length) { out.push('<p>' + paragraph.join('<br>') + '</p>'); paragraph = []; }
  };
  const isBlock = (line, i) => /^\s*```/.test(line) || /^\s{0,3}#{1,6}\s+/.test(line) ||
    /^\s{0,3}(?:[-*+]|\d+[.)])\s+/.test(line) || /^\s{0,3}>\s?/.test(line) ||
    /^\s{0,3}(?:-{3,}|\*{3,}|_{3,})\s*$/.test(line) ||
    (line.includes('|') && i + 1 < lines.length && /^\s*\|?\s*:?-{2,}/.test(lines[i + 1]));
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    if (!line.trim()) { flush(); i++; continue; }
    const fence = line.match(/^\s*```(.*)$/);
    if (fence) {
      flush(); i++;
      const code = [];
      while (i < lines.length && !/^\s*```\s*$/.test(lines[i])) code.push(lines[i++]);
      if (i < lines.length) i++;
      out.push('<pre><code>' + escapeHtml(code.join('\n')) + '</code></pre>');
      continue;
    }
    const heading = line.match(/^\s{0,3}(#{1,6})\s+(.*)$/);
    if (heading) { flush(); const n = heading[1].length; out.push(`<h${n}>${inline(heading[2])}</h${n}>`); i++; continue; }
    if (/^\s{0,3}(?:-{3,}|\*{3,}|_{3,})\s*$/.test(line)) { flush(); out.push('<hr>'); i++; continue; }
    if (/^\s{0,3}>\s?/.test(line)) {
      flush(); const quote = [];
      while (i < lines.length && /^\s{0,3}>\s?/.test(lines[i])) quote.push(inline(lines[i++].replace(/^\s{0,3}>\s?/, '')));
      out.push('<blockquote>' + quote.join('<br>') + '</blockquote>'); continue;
    }
    const list = line.match(/^\s{0,3}([-*+]|\d+[.)])\s+(.*)$/);
    if (list) {
      flush(); const ordered = /^\d/.test(list[1]); const items = [];
      while (i < lines.length) {
        const item = lines[i].match(/^\s{0,3}([-*+]|\d+[.)])\s+(.*)$/);
        if (!item) break;
        items.push('<li>' + inline(item[2].trim()) + '</li>'); i++;
      }
      out.push(ordered ? '<ol>' + items.join('') + '</ol>' : '<ul>' + items.join('') + '</ul>');
      continue;
    }
    if (line.includes('|') && i + 1 < lines.length && /^\s*\|?\s*:?-{2,}/.test(lines[i + 1])) {
      const split = (row) => {
        let v = row.trim();
        if (v.startsWith('|')) v = v.slice(1);
        if (v.endsWith('|')) v = v.slice(0, -1);
        return v.split('|').map((c) => c.trim());
      };
      const header = split(line); const align = split(lines[i + 1]).map((c) => c.startsWith(':') && c.endsWith(':') ? 'center' : c.endsWith(':') ? 'right' : 'left');
      i += 2; const rows = [];
      while (i < lines.length && lines[i].includes('|') && lines[i].trim()) rows.push(split(lines[i++]));
      out.push('<div class="md-table-wrap"><table><thead><tr>' +
        header.map((c, n) => `<th style="text-align:${align[n] || 'left'}">${inline(c)}</th>`).join('') +
        '</tr></thead><tbody>' + rows.map((r) => '<tr>' + header.map((c, n) => `<td style="text-align:${align[n] || 'left'}">${inline(r[n] || '')}</td>`).join('') + '</tr>').join('') +
        '</tbody></table></div>');
      continue;
    }
    paragraph.push(inline(line.trim())); i++;
    if (i < lines.length && isBlock(lines[i], i)) flush();
  }
  flush();
  return out.join('');
}
