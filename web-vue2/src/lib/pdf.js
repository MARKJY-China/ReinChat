import * as pdfjs from 'pdfjs-dist';
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url';

pdfjs.GlobalWorkerOptions.workerSrc = workerUrl;

export async function extractPdfText(file) {
  if (!file) return { name: '', text: '', pages: 0 };
  const data = new Uint8Array(await file.arrayBuffer());
  const doc = await pdfjs.getDocument({ data, isEvalSupported: false }).promise;
  const totalPages = doc.numPages;
  const pages = Math.min(totalPages, 200);
  const chunks = [];
  for (let pageNo = 1; pageNo <= pages; pageNo++) {
    const page = await doc.getPage(pageNo);
    const content = await page.getTextContent();
    const text = content.items.map((item) => item.str || '').join(' ');
    if (text.trim()) chunks.push(`\n--- 第 ${pageNo} 页 ---\n${text}`);
    if (chunks.join('').length > 60000) break;
  }
  await doc.destroy();
  return {
    name: file.name,
    text: chunks.join('\n').slice(0, 60000),
    pages: totalPages,
  };
}
