/* YID PLUS app: makes the site work on old Android web engines (Chrome < 88), e.g. keypad phones.
   Only runs inside the app; it adds rules, it never removes anything. */
(function () {
  'use strict';
  if (window.__ypCompat) return;
  window.__ypCompat = true;
  var S = window.CSS && CSS.supports ? function (p, v) { try { return CSS.supports(p, v); } catch (e) { return true; } } : function () { return true; };

  // ---- JavaScript gaps ----
  try {
    if (!String.prototype.replaceAll) String.prototype.replaceAll = function (a, b) {
      if (a instanceof RegExp) return this.replace(a, b);
      return this.split(String(a)).join(typeof b === 'function' ? b(String(a)) : String(b));
    };
    var at = function (i) { i = Math.trunc(i) || 0; if (i < 0) i += this.length; return (i < 0 || i >= this.length) ? undefined : this[i]; };
    [Array, String].forEach(function (C) { if (!C.prototype.at) Object.defineProperty(C.prototype, 'at', { value: at, configurable: true, writable: true }); });
    if (!Array.prototype.findLast) Object.defineProperty(Array.prototype, 'findLast', { configurable: true, writable: true, value: function (f, t) { for (var i = this.length - 1; i >= 0; i--) if (f.call(t, this[i], i, this)) return this[i]; } });
    if (!Array.prototype.findLastIndex) Object.defineProperty(Array.prototype, 'findLastIndex', { configurable: true, writable: true, value: function (f, t) { for (var i = this.length - 1; i >= 0; i--) if (f.call(t, this[i], i, this)) return i; return -1; } });
    if (!Object.hasOwn) Object.hasOwn = function (o, k) { return Object.prototype.hasOwnProperty.call(o, k); };
    if (!window.structuredClone) window.structuredClone = function (v) { return v === undefined ? v : JSON.parse(JSON.stringify(v)); };
    if (window.crypto && !crypto.randomUUID) crypto.randomUUID = function () {
      var b = crypto.getRandomValues(new Uint8Array(16)); b[6] = (b[6] & 15) | 64; b[8] = (b[8] & 63) | 128;
      var h = Array.prototype.map.call(b, function (x) { return (x + 256).toString(16).slice(1); }).join('');
      return h.slice(0, 8) + '-' + h.slice(8, 12) + '-' + h.slice(12, 16) + '-' + h.slice(16, 20) + '-' + h.slice(20);
    };
    if (window.AbortSignal && !AbortSignal.timeout) AbortSignal.timeout = function (ms) { var c = new AbortController(); setTimeout(function () { c.abort(); }, ms); return c.signal; };
    if (window.Promise && !Promise.any) Promise.any = function (ps) {
      return new Promise(function (res, rej) { var n = 0, errs = []; ps = Array.from(ps); if (!ps.length) rej(new Error('All promises were rejected'));
        ps.forEach(function (p, i) { Promise.resolve(p).then(res, function (e) { errs[i] = e; if (++n === ps.length) rej(new Error('All promises were rejected')); }); }); });
    };
    if (window.Element && !Element.prototype.replaceChildren) Element.prototype.replaceChildren = function () { while (this.firstChild) this.removeChild(this.firstChild); this.append.apply(this, arguments); };
  } catch (e) {}

  // ---- CSS gaps ----
  var NO_INSET = !S('inset', '0'), NO_LOGICAL = !S('margin-inline', '0') || !S('inset-inline-start', '0'),
      NO_DVH = !S('height', '1dvh'), NO_AR = !S('aspect-ratio', '1');
  if (!(NO_INSET || NO_LOGICAL || NO_DVH || NO_AR)) return;
  window.__ypCompatCss = true;

  function splitVals(v) { var out = [], d = 0, cur = ''; for (var i = 0; i < v.length; i++) { var c = v[i]; if (c === '(') d++; if (c === ')') d--; if (/\s/.test(c) && !d) { if (cur) out.push(cur); cur = ''; } else cur += c; } if (cur) out.push(cur); return out; }
  function four(v) { var p = splitVals(v); if (p.length === 1) return [p[0], p[0], p[0], p[0]]; if (p.length === 2) return [p[0], p[1], p[0], p[1]]; if (p.length === 3) return [p[0], p[1], p[2], p[1]]; return p.slice(0, 4); }
  function two(v) { var p = splitVals(v); return [p[0], p[1] || p[0]]; }
  // One declaration in, the longhands an old engine understands out ('' = nothing to add)
  function conv(prop, val) {
    var imp = /!\s*important\s*$/i.test(val) ? ' !important' : ''; val = val.replace(/!\s*important\s*$/i, '').trim();
    var o = [];
    function add(p, v) { o.push(p + ':' + v + imp); }
    if (NO_INSET && prop === 'inset') { var f = four(val); add('top', f[0]); add('right', f[1]); add('bottom', f[2]); add('left', f[3]); }
    else if (NO_LOGICAL && prop === 'inset-inline') { var t = two(val); add('left', t[0]); add('right', t[1]); }
    else if (NO_LOGICAL && prop === 'inset-block') { var b = two(val); add('top', b[0]); add('bottom', b[1]); }
    else if (NO_LOGICAL && prop === 'inset-inline-start') add('left', val);
    else if (NO_LOGICAL && prop === 'inset-inline-end') add('right', val);
    else if (NO_LOGICAL && prop === 'inset-block-start') add('top', val);
    else if (NO_LOGICAL && prop === 'inset-block-end') add('bottom', val);
    else if (NO_LOGICAL && (prop === 'margin-inline' || prop === 'padding-inline')) { var m = two(val), k = prop.split('-')[0]; add('-webkit-' + k + '-start', m[0]); add('-webkit-' + k + '-end', m[1]); }
    else if (NO_LOGICAL && (prop === 'margin-block' || prop === 'padding-block')) { var n = two(val), q = prop.split('-')[0]; add(q + '-top', n[0]); add(q + '-bottom', n[1]); }
    if (NO_DVH && /\d(dvh|svh|lvh)\b/.test(val)) add(prop, val.replace(/(\d)(dvh|svh|lvh)\b/g, '$1vh'));
    if (NO_AR && prop === 'aspect-ratio' && !imp) { var r = val.split('/'); var w = parseFloat(r[0]), h = parseFloat(r[1] || '1'); if (w > 0 && h > 0) o.push('--yp-ar:' + (h / w * 100) + '%'); }
    return o.join(';');
  }
  var WANT = /(^|[;{\s])(inset|inset-inline|inset-block|inset-inline-start|inset-inline-end|inset-block-start|inset-block-end|margin-inline|padding-inline|margin-block|padding-block|aspect-ratio)\s*:|\d(dvh|svh|lvh)\b/;
  function convBody(body) {
    var out = [];
    body.split(';').forEach(function (d) { var i = d.indexOf(':'); if (i < 0) return; var c = conv(d.slice(0, i).trim().toLowerCase(), d.slice(i + 1).trim()); if (c) out.push(c); });
    return out.join(';');
  }
  // Walk the stylesheet text: keep @media/@supports wrappers, rewrite only the rules that need it
  function rewrite(text) {
    text = text.replace(/\/\*[\s\S]*?\*\//g, '');
    var out = '', stack = [], start = 0, ar = [];
    for (var i = 0; i < text.length; i++) {
      var c = text[i];
      if (c === '{') { stack.push(text.slice(start, i).trim()); start = i + 1; }
      else if (c === '}') {
        var pre = stack.pop() || '', body = text.slice(start, i); start = i + 1;
        if (pre && pre[0] !== '@' && body.indexOf('{') < 0 && WANT.test(body)) {
          if (stack.some(function (s) { return /^@(keyframes|-webkit-keyframes|font-face)/i.test(s); })) continue;
          var nb = convBody(body); if (!nb) continue;
          var r = pre + '{' + nb + '}';
          if (NO_AR && /aspect-ratio\s*:/.test(body) && !/(^|;)\s*height\s*:/.test(body)) ar.push(pre);
          for (var j = stack.length - 1; j >= 0; j--) r = stack[j] + '{' + r + '}';
          out += r + '\n';
        }
      }
    }
    return out;
  }
  var done = new WeakSet ? new WeakSet() : null, seen = {};
  var queued = [];
  function inject(css, from) {
    if (!css) return; var s = document.createElement('style'); s.setAttribute('data-yp-compat', from || ''); s.textContent = css;
    var host = document.head || document.documentElement; if (host) host.appendChild(s); else queued.push(s);
  }
  function flush() { var host = document.head || document.documentElement; if (!host) return; while (queued.length) host.appendChild(queued.shift()); }
  function doSheet(el) {
    if (done && done.has(el)) return; if (done) done.add(el);
    if (el.hasAttribute && el.hasAttribute('data-yp-compat')) return;
    if (el.tagName === 'STYLE') { if (!(el.textContent || '').trim()) { if (done) done.delete(el); return; } inject(rewrite(el.textContent || ''), 'inline'); return; }
    var href = el.href; if (!href || seen[href]) return; seen[href] = 1;
    var u; try { u = new URL(href, location.href); } catch (e) { return; }
    if (u.origin !== location.origin) return;
    fetch(u.href, { credentials: 'same-origin' }).then(function (r) { return r.ok ? r.text() : ''; }).then(function (t) { inject(rewrite(t), u.pathname); }).catch(function () {});
  }
  function scan(root) {
    var list = (root || document).querySelectorAll ? (root || document).querySelectorAll('link[rel~="stylesheet"],style') : [];
    for (var i = 0; i < list.length; i++) doSheet(list[i]);
    var styled = (root || document).querySelectorAll ? (root || document).querySelectorAll('[style]') : [];
    for (var k = 0; k < styled.length; k++) fixInline(styled[k]);
  }
  function fixInline(el) {
    var st = el.getAttribute && el.getAttribute('style'); if (!st || !WANT.test(st) || el.__ypStyle === st) return;
    var c = convBody(st); if (c) { c.split(';').forEach(function (d) { var i = d.indexOf(':'); if (i < 0) return; var v = d.slice(i + 1), imp = /!important/.test(v); el.style.setProperty(d.slice(0, i), v.replace(/!important/, '').trim(), imp ? 'important' : ''); }); }
    el.__ypStyle = el.getAttribute('style');
  }
  // el.style.inset = '0' from the site's own scripts
  try {
    var P = CSSStyleDeclaration.prototype;
    ['inset', 'insetInline', 'insetBlock', 'insetInlineStart', 'insetInlineEnd', 'marginInline', 'paddingInline'].forEach(function (name) {
      if (name in P || (name === 'inset' ? !NO_INSET : !NO_LOGICAL)) return;
      var css = name.replace(/[A-Z]/g, function (m) { return '-' + m.toLowerCase(); });
      Object.defineProperty(P, name, { configurable: true, get: function () { return ''; }, set: function (v) {
        var self = this; String(conv(css, String(v))).split(';').forEach(function (d) { var i = d.indexOf(':'); if (i > 0) self.setProperty(d.slice(0, i), d.slice(i + 1)); }); } });
    });
  } catch (e) {}
  if (NO_AR) inject('img,video{max-width:100%}', 'ar');
  scan(document);
  var mo = new MutationObserver(function (ms) {
    for (var i = 0; i < ms.length; i++) {
      var m = ms[i];
      if (queued.length) flush();
      if (m.type === 'attributes') { fixInline(m.target); continue; }
      for (var j = 0; j < m.addedNodes.length; j++) { var n = m.addedNodes[j]; if (n.nodeType !== 1) continue;
        if (n.tagName === 'LINK' || n.tagName === 'STYLE') doSheet(n); else { if (n.hasAttribute('style')) fixInline(n); if (n.firstElementChild) scan(n); } }
    }
  });
  mo.observe(document, { childList: true, subtree: true, attributes: true, attributeFilter: ['style'] });
  document.addEventListener('DOMContentLoaded', function () { flush(); scan(document); });
})();
