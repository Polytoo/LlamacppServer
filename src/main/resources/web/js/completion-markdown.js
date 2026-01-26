function escapeHtml(text) {
  return String(text == null ? '' : text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

function isSafeUrl(value) {
  const href = String(value == null ? '' : value).trim();
  if (!href) return false;
  if (href.startsWith('#')) return true;
  try {
    const u = new URL(href, window.location.href);
    const p = u.protocol;
    return p === 'http:' || p === 'https:' || p === 'mailto:' || p === 'tel:';
  } catch (e) {
    return false;
  }
}

function sanitizeMarkdownHtml(html) {
  const allowedTags = new Set([
    'div',
    'p', 'br', 'strong', 'em', 'del',
    'code', 'pre',
    'blockquote',
    'ul', 'ol', 'li',
    'a', 'hr',
    'table', 'thead', 'tbody', 'tr', 'th', 'td',
    'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
    'img', 'span'
  ]);

  const allowedAttrsByTag = {
    a: new Set(['href', 'title', 'target', 'rel']),
    img: new Set(['src', 'alt', 'title']),
    th: new Set(['align']),
    td: new Set(['align']),
    code: new Set(['class']),
    pre: new Set(['class']),
    span: new Set(['class'])
  };

  const doc = new DOMParser().parseFromString('<div>' + String(html || '') + '</div>', 'text/html');
  const root = doc.body && doc.body.firstChild ? doc.body.firstChild : null;
  if (!root) return '';

  const walker = doc.createTreeWalker(root, NodeFilter.SHOW_ELEMENT, null);
  const nodes = [];
  let n = walker.currentNode;
  while (n) {
    nodes.push(n);
    n = walker.nextNode();
  }

  for (const el of nodes) {
    const tag = (el.tagName || '').toLowerCase();
    if (!allowedTags.has(tag)) {
      const parent = el.parentNode;
      if (!parent) continue;
      while (el.firstChild) parent.insertBefore(el.firstChild, el);
      parent.removeChild(el);
      continue;
    }

    const allowedAttrs = allowedAttrsByTag[tag] || null;
    for (const attr of Array.from(el.attributes || [])) {
      const name = (attr.name || '').toLowerCase();
      if (!allowedAttrs || !allowedAttrs.has(name)) {
        el.removeAttribute(attr.name);
        continue;
      }
      if ((tag === 'a' && name === 'href') || (tag === 'img' && name === 'src')) {
        const v = el.getAttribute(attr.name) || '';
        if (!isSafeUrl(v)) el.removeAttribute(attr.name);
      }
    }

    if (tag === 'a') {
      el.setAttribute('rel', 'noopener noreferrer');
      if (!el.getAttribute('target')) el.setAttribute('target', '_blank');
    }
  }

  return root.innerHTML;
}

function markdownToSafeHtml(text) {
  const input = (text == null ? '' : String(text));
  if (!window.marked || typeof window.marked.parse !== 'function') return escapeHtml(input);
  let raw = '';
  try {
    raw = window.marked.parse(input, { gfm: true, breaks: true, mangle: false, headerIds: false });
  } catch (e) {
    return escapeHtml(input);
  }
  return sanitizeMarkdownHtml(raw);
}

let markdownRaf = 0;
let lastMarkdownFlushAt = 0;
const pendingMarkdownRenders = new Map();
const pendingHljsTimers = new WeakMap();

function scheduleHighlight(el, text) {
  if (!el) return;
  if (!window.hljs || typeof window.hljs.highlightElement !== 'function') return;
  const t = (text == null ? '' : String(text));
  if (!(t.includes('```') || t.includes('`'))) return;
  const prev = pendingHljsTimers.get(el);
  if (prev) clearTimeout(prev);
  const timer = setTimeout(() => {
    pendingHljsTimers.delete(el);
    const blocks = el.querySelectorAll('pre code');
    for (const b of blocks) window.hljs.highlightElement(b);
  }, 350);
  pendingHljsTimers.set(el, timer);
}

function renderMessageContentNow(el, text) {
  if (!el) return;
  const t = (text == null ? '' : String(text));
  if (!window.marked || typeof window.marked.parse !== 'function') {
    el.classList.add('plain');
    el.textContent = t;
    return;
  }
  el.classList.remove('plain');
  el.innerHTML = markdownToSafeHtml(t);
  scheduleHighlight(el, t);
}

function flushMarkdown(ts) {
  markdownRaf = 0;
  const now = typeof ts === 'number' ? ts : (typeof performance !== 'undefined' && performance.now ? performance.now() : Date.now());
  if (now - lastMarkdownFlushAt < 50) {
    markdownRaf = requestAnimationFrame(flushMarkdown);
    return;
  }
  lastMarkdownFlushAt = now;
  for (const [node, value] of pendingMarkdownRenders.entries()) {
    renderMessageContentNow(node, value);
  }
  pendingMarkdownRenders.clear();
}

function requestRenderMessageContent(el, text) {
  if (!el) return;
  pendingMarkdownRenders.set(el, text);
  if (markdownRaf) return;
  markdownRaf = requestAnimationFrame(flushMarkdown);
}

