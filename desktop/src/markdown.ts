import { escapeHtml } from "./util";

const TOKEN_PREFIX = "\u0000C";

function inline(raw: string): string {
  let text = escapeHtml(raw);
  const codeSpans: string[] = [];
  text = text.replace(/`([^`\n]+)`/g, (_match, code: string) => {
    const token = `${TOKEN_PREFIX}${codeSpans.length}\u0000`;
    codeSpans.push(`<code>${code}</code>`);
    return token;
  });

  text = text.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, (_match, label: string, url: string) => {
    const safeUrl = escapeHtml(url);
    return `<a href="${safeUrl}" target="_blank" rel="noreferrer" data-external-link="1">${label}</a>`;
  });
  text = text.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
  text = text.replace(/__([^_]+)__/g, "<strong>$1</strong>");
  text = text.replace(/(^|[^*])\*([^*\n]+)\*/g, "$1<em>$2</em>");
  text = text.replace(/~~([^~]+)~~/g, "<del>$1</del>");

  text = text.replace(new RegExp(`${TOKEN_PREFIX}(\\d+)\\u0000`, "g"), (_match, index: string) => {
    return codeSpans[Number(index)] ?? "";
  });
  return text;
}

function codeBlock(language: string, code: string): string {
  const lang = language.trim().slice(0, 24);
  return `<div class="code-block"><div class="code-head"><span>${escapeHtml(lang || "code")}</span><button type="button" class="code-copy" data-action="copy-code">复制</button></div><pre><code>${escapeHtml(code.replace(/\n$/, ""))}</code></pre></div>`;
}

export function renderMarkdown(source: string): string {
  const lines = source.replace(/\r\n/g, "\n").split("\n");
  const out: string[] = [];
  let index = 0;

  while (index < lines.length) {
    const line = lines[index];
    if (line === undefined) break;

    if (/^```/.test(line)) {
      const language = line.slice(3).trim();
      const code: string[] = [];
      index += 1;
      while (index < lines.length && !/^```/.test(lines[index] ?? "")) {
        code.push(lines[index] ?? "");
        index += 1;
      }
      if (index < lines.length) index += 1;
      out.push(codeBlock(language, code.join("\n")));
      continue;
    }

    if (!line.trim()) {
      index += 1;
      continue;
    }

    const heading = /^(#{1,4})\s+(.+)$/.exec(line);
    if (heading) {
      const level = Math.min(4, heading[1].length);
      out.push(`<h${level}>${inline(heading[2])}</h${level}>`);
      index += 1;
      continue;
    }

    if (/^(-{3,}|\*{3,}|_{3,})$/.test(line.trim())) {
      out.push("<hr />");
      index += 1;
      continue;
    }

    if (/^>\s?/.test(line)) {
      const quote: string[] = [];
      while (index < lines.length && /^>\s?/.test(lines[index] ?? "")) {
        quote.push((lines[index] ?? "").replace(/^>\s?/, ""));
        index += 1;
      }
      out.push(`<blockquote>${quote.map(inline).join("<br />")}</blockquote>`);
      continue;
    }

    if (/^\s*[-*+]\s+/.test(line)) {
      const items: string[] = [];
      while (index < lines.length && /^\s*[-*+]\s+/.test(lines[index] ?? "")) {
        items.push((lines[index] ?? "").replace(/^\s*[-*+]\s+/, ""));
        index += 1;
      }
      out.push(`<ul>${items.map((item) => `<li>${inline(item)}</li>`).join("")}</ul>`);
      continue;
    }

    if (/^\s*\d+[.)]\s+/.test(line)) {
      const items: string[] = [];
      while (index < lines.length && /^\s*\d+[.)]\s+/.test(lines[index] ?? "")) {
        items.push((lines[index] ?? "").replace(/^\s*\d+[.)]\s+/, ""));
        index += 1;
      }
      out.push(`<ol>${items.map((item) => `<li>${inline(item)}</li>`).join("")}</ol>`);
      continue;
    }

    const paragraph: string[] = [line];
    index += 1;
    while (index < lines.length) {
      const next = lines[index] ?? "";
      if (!next.trim()) break;
      if (/^```/.test(next) || /^(#{1,4})\s+/.test(next) || /^>\s?/.test(next)) break;
      if (/^\s*[-*+]\s+/.test(next) || /^\s*\d+[.)]\s+/.test(next)) break;
      paragraph.push(next);
      index += 1;
    }
    out.push(`<p>${paragraph.map(inline).join("<br />")}</p>`);
  }

  return `<div class="md">${out.join("")}</div>`;
}
