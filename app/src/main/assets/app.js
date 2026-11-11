/* YID PLUS app bridge. Runs at document start on yidplus.com pages only. */
(function () {
  'use strict';
  if (window.__ypApp) return;
  window.__ypApp = true;
  var N = window.YidplusNative;      // origin-locked message channel (yidplus.com only)
  var B = window.YidplusAppBridge;   // tiny state bridge (pull-to-refresh, bar colours)
  function post(m) { try { if (N) N.postMessage(JSON.stringify(m)); } catch (e) {} }

  // ---- Pull to refresh: only when the thing under the finger is scrolled to the top ----
  function scrolledDown(el) {
    for (; el && el !== document.documentElement && el !== document; el = el.parentElement) {
      if (el.scrollTop > 0) {
        var s = getComputedStyle(el).overflowY;
        if (s === 'auto' || s === 'scroll' || s === 'overlay') return true;
      }
    }
    var se = document.scrollingElement;
    return !!(se && se.scrollTop > 0);
  }
  function pullable(target) {
    if (/^\/(shorts|yidplus-shorts)/.test(location.pathname)) return false; // Shorts has its own refresh
    if (document.fullscreenElement) return false;
    if (target && target.closest && target.closest('input,textarea,select,[contenteditable="true"],[role="dialog"],dialog')) return false;
    return !scrolledDown(target);
  }
  document.addEventListener('touchstart', function (e) {
    try { if (B) B.setPullable(pullable(e.target)); } catch (_) {}
  }, { capture: true, passive: true });

  // ---- System bars take the colour of the page's top and bottom edge ----
  function solid(c) { return c && c !== 'transparent' && !/rgba\([^)]*,\s*0(\.0+)?\s*\)$/.test(c); }
  function bgAt(x, y) {
    var el = document.elementFromPoint(x, y);
    for (; el; el = el.parentElement) { var c = getComputedStyle(el).backgroundColor; if (solid(c)) return c; }
    var b = document.body && getComputedStyle(document.body).backgroundColor;
    if (solid(b)) return b;
    var h = getComputedStyle(document.documentElement).backgroundColor;
    return solid(h) ? h : '';
  }
  var lastBars = '';
  function bars() {
    try {
      if (!B || !document.body || document.hidden) return;
      var w = Math.floor(innerWidth / 2), t = bgAt(w, 1), b = bgAt(w, innerHeight - 2), k = t + '|' + b;
      if (k !== lastBars) { lastBars = k; B.setBars(t, b); }
    } catch (_) {}
  }
  // one header (the site's): the status bar takes the colour of the page's top edge
  setInterval(bars, 700); document.addEventListener('DOMContentLoaded', function () { setTimeout(bars, 50); }); window.addEventListener('load', bars);


  // ---- Native share sheet (WebView has no navigator.share) ----
  if (N) {
    var share = function (d) {
      d = d || {};
      if (d.files && d.files.length) return Promise.reject(new DOMException('Sharing files is not supported', 'NotAllowedError'));
      post({ type: 'share', title: String(d.title || ''), text: String(d.text || ''), url: String(d.url || '') });
      return Promise.resolve();
    };
    try {
      Object.defineProperty(navigator, 'share', { value: share, configurable: true });
      Object.defineProperty(navigator, 'canShare', { value: function (d) { return !(d && d.files && d.files.length); }, configurable: true });
    } catch (_) {}
  }

  // ---- Saving downloaded files (music etc. built as blob: links) ----
  var active = null;
  if (N) N.onmessage = function (e) { try { if (active) active(JSON.parse(e.data)); } catch (_) {} };
  function wait() {
    return new Promise(function (resolve, reject) {
      var t = setTimeout(function () { active = null; reject(Error('Save timed out')); }, 125000);
      active = function (m) { clearTimeout(t); active = null; if (m.error) reject(Error(m.error)); else resolve(m); };
    });
  }
  window.YidplusSaveBlob = async function (blob, name) {
    if (!N) throw Error('Saving is not available');
    if (active || window.__ypSaving) throw Error('Another save is in progress');
    if (blob.size > 200 * 1024 * 1024) throw Error('File is larger than 200 MB');
    window.__ypSaving = true;
    var id = (crypto.randomUUID ? crypto.randomUUID() : String(Date.now()) + Math.random()), pending = wait();
    post({ type: 'saveBegin', id: id, size: blob.size, name: name, mime: blob.type || '' });
    try {
      await pending;
      for (var i = 0; i < blob.size; i += 49152) {
        var bytes = new Uint8Array(await blob.slice(i, i + 49152).arrayBuffer()), bin = '';
        for (var j = 0; j < bytes.length; j++) bin += String.fromCharCode(bytes[j]);
        pending = wait(); post({ type: 'saveChunk', id: id, data: btoa(bin) }); await pending;
      }
      pending = wait(); post({ type: 'saveEnd', id: id }); await pending;
    } catch (e) { post({ type: 'saveAbort' }); throw e; }
    finally { window.__ypSaving = false; }
  };
  function toastMsg(m) { if (window.toast) { try { window.toast(m); return; } catch (_) {} } post({ type: 'toast', text: m }); }
  window.__ypSaveUrl = async function (url, name) {
    try { await window.YidplusSaveBlob(await (await fetch(url)).blob(), name || 'download'); }
    catch (e) { toastMsg(e.message); }
  };
  document.addEventListener('click', function (e) {
    var a = e.target && e.target.closest && e.target.closest('a[download]');
    if (!a || !/^(blob|data):/.test(a.href)) return;
    e.preventDefault(); e.stopPropagation();
    window.__ypSaveUrl(a.href, a.getAttribute('download') || 'download');
  }, true);

  try {
    var origClick = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function () {
      if (this.hasAttribute('download') && /^(blob|data):/.test(this.href)) { window.__ypSaveUrl(this.href, this.getAttribute('download') || 'download'); return; }
      return origClick.apply(this, arguments);
    };
    var revoke = URL.revokeObjectURL.bind(URL);
    URL.revokeObjectURL = function (u) { setTimeout(function () { revoke(u); }, 60000); };
  } catch (_) {}


  // ---- App look: native top + bottom bars replace the site's own bars inside the app ----
  var TOP = (function () { try { return window.top === window; } catch (e) { return false; } })();
  var css = '' +
    '.bottom-nav{display:none!important}' +
    '.yp-app-hide{display:none!important}' +
    /* the site leaves room for its own nav; the app's nav sits below the page instead */
    '.scroll-area{padding-bottom:16px!important}' +
    'html{--src-nav-h:0px!important}' +
    '@media (max-width:600px){.src-cont,.src-chips,.shorts-topbar{max-width:none!important;border-inline:0!important;box-shadow:none!important}}' +
    /* Shorts on a small screen (e.g. 480x640 keypad phones): tabs on one line, chips below them, the action rail fits the height */
    '@media (max-width:380px){.shorts-tabs{gap:10px!important}.shorts-tab{font-size:14px!important;white-space:nowrap;padding:8px 1px 10px!important}.shorts-brand{width:26px!important;height:26px!important}}' +
    '@media (max-height:520px){.src-chips{top:44px!important;padding:4px 10px 6px!important}.src-chip{padding:5px 11px!important;font-size:12.5px!important}' +
      '.src-rail{gap:0!important;bottom:10px!important;right:4px!important}.src-act{min-height:34px!important;min-width:50px!important;gap:1px!important}.src-ic{width:36px!important;height:30px!important}.src-ic svg{width:24px;height:24px}' +
      '.src-act span:not(.src-ic){font-size:10.5px!important}.src-info{bottom:12px!important;right:64px!important;left:10px!important}.src-av.big{width:36px!important;height:36px!important}}' +
    /* one compact header: the big collapsing "YID PLUS" title repeats the logo on a small screen */
    '#home-topbar .m3-topbar-title-large{display:none!important}' +
    '#home-topbar .app-title-display{display:none!important}' +
    '.topbar{min-height:48px;box-sizing:border-box}' +
    '.fab-speed-dial,#explore-fab{bottom:16px!important}' +
    '[style*="bottom:80px"][style*="position:fixed"]{bottom:16px!important}' +
    'html.yp-keys :focus,html.yp-keys .yp-kfocus{outline:3px solid #D4AF37!important;outline-offset:-3px!important;border-radius:6px}' +
    '#yp-android-chip,.pwa-install-banner,.pwa-ib,#pwa-install-banner{display:none!important}' +
    '#yp-app-row{display:flex;align-items:center;gap:12px;margin:12px 12px 0;padding:12px 14px;background:var(--surface,#fff);border:1px solid var(--border,#e3e9e7);border-radius:14px;font-family:inherit}' +
    '#yp-app-row .t{flex:1;min-width:0}#yp-app-row .t b{display:block;font-size:.92rem}#yp-app-row .t span{font-size:.76rem;color:var(--muted,#6b7a76)}' +
    '#yp-app-row button{border:0;border-radius:20px;padding:.45rem .9rem;background:var(--accent,#1F6F5C);color:#fff;font:inherit;font-size:.8rem;font-weight:700}';
  function addCss() {
    if (document.getElementById('yp-app-css')) return;
    var host = document.head || document.documentElement; if (!host) return;
    var s = document.createElement('style'); s.id = 'yp-app-css'; s.textContent = css; host.appendChild(s);
  }
  addCss(); document.addEventListener('DOMContentLoaded', addCss);

  var path = function () { return location.pathname.replace(/\.html$/, ''); };
  function visible(el) { if (!el) return false; var r = el.getBoundingClientRect(); return r.width > 0 && r.height > 0 && getComputedStyle(el).visibility !== 'hidden'; }
  function activeScreen() {
    var list = document.querySelectorAll('.screen.active,.chat-page:not(.hidden)');
    for (var i = list.length - 1; i >= 0; i--) if (visible(list[i])) return list[i];
    return null;
  }
  // ---- The bottom nav is the site's own: same items, order, labels and icons, minus what the admin turned off ----
  var FEAT = { shorts: 'shorts', music: 'music', explore: 'channels' }; // nav key -> admin feature switch (Admin -> Features)
  var flags = null;
  function loadFlags() {
    try {
      fetch('/api/settings', { credentials: 'include' }).then(function (r) { return r.json(); })
        .then(function (res) { flags = (res && res.settings) || {}; report(); }).catch(function () {});
    } catch (_) {}
  }
  function featureOff(key) {
    var s = flags || (window.STATE && STATE.settings) || null; if (!s) return null;
    var f = FEAT[key]; if (!f) return false;
    if (s['feat_' + f] !== 'off') return false;
    return s['feat_' + f + '_allowed_me'] !== 'true';
  }
  function siteNav() {
    var navs = document.querySelectorAll('.bottom-nav'); if (!navs.length) return null;
    var nav = navs[0], btns = nav.querySelectorAll('.nav-item[data-nav]'), items = [];
    for (var i = 0; i < btns.length; i++) {
      var b = btns[i], key = b.getAttribute('data-nav');
      var lab = b.querySelector('.nav-label'), ic = b.querySelector('.material-symbols-rounded,.material-icons');
      var off = featureOff(key);
      if (off === null) off = b.style.display === 'none';   // settings not read yet: what the site itself shows
      if (off) continue;
      items.push({ key: key, label: ((lab && lab.textContent) || key).trim(), icon: ((ic && ic.textContent) || '').trim() });
    }
    return items.length ? { items: items } : null;
  }
  var lastNav = '';
  function reportNav() {
    if (!B || !B.setNav || flags === null) return;
    var n = siteNav(); if (!n) return;
    var k = JSON.stringify(n); if (k !== lastNav) { lastNav = k; B.setNav(k); }
  }
  function section() {
    var p = path(), scr = activeScreen(), id = scr && scr.id ? scr.id.replace(/^screen-/, '') : '', tab = '';
    if (/^\/(yidplus-)?chat/.test(p)) tab = 'chats';
    else if (/^\/(yidplus-)?music/.test(p)) tab = 'music';
    else if (/^\/(yidplus-)?shorts/.test(p)) tab = 'shorts';
    else if (scr) { var a = scr.querySelector('.bottom-nav .nav-item.active[data-nav]'); tab = a ? a.getAttribute('data-nav') : (id === 'explore' || id === 'settings' ? id : ''); }
    var signedIn = !!(window.STATE && STATE.user && STATE.user.id);
    return { tab: tab, id: id || tab || 'page', signedIn: signedIn };
  }
  // The site's title-only header ("Channels") is kept: it is the page's one header now.
  function hideDupHeaders() {}
  // Settings: the app's version and a "Check for updates" button
  function appRow() {
    if (!B) return;
    var scr = document.getElementById('screen-settings'); if (!scr || document.getElementById('yp-app-row')) return;
    var area = scr.querySelector('.scroll-area'); if (!area) return;
    var row = document.createElement('div'); row.id = 'yp-app-row';
    var v = (B.version ? String(B.version()) : '');
    row.innerHTML = '<div class="t"><b>YID PLUS app</b><span>Version ' + v.replace(/[^0-9a-z.\-]/gi, '') + '</span></div><button type="button">Check for updates</button>';
    row.querySelector('button').addEventListener('click', function () { post({ type: 'checkUpdate' }); });
    area.insertBefore(row, area.firstChild);
  }
  var lastSec = '';
  function report() {
    try {
      if (!TOP || !B || !document.body) return;
      reportNav(); appRow();
      if (!B.setSection) return;
      var s = section(), k = JSON.stringify(s);
      if (k !== lastSec) { lastSec = k; B.setSection(k); }
    } catch (_) {}
  }
  if (TOP) loadFlags();
  document.addEventListener('DOMContentLoaded', report);
  window.addEventListener('load', report);
  window.addEventListener('popstate', function () { setTimeout(report, 50); });
  document.addEventListener('click', function () { setTimeout(report, 80); setTimeout(report, 450); }, true);
  setInterval(report, 600);

  // The native bottom bar calls this: press the site's own nav button, so it does exactly what the site does
  window.__ypGo = function (key) {
    var scr = activeScreen(), b = (scr && scr.querySelector('.bottom-nav .nav-item[data-nav="' + key + '"]')) || document.querySelector('.bottom-nav .nav-item[data-nav="' + key + '"]');
    if (!b) return false;
    b.click(); setTimeout(report, 60); setTimeout(report, 400);
    return true;
  };

  // ---- Keypad phones: D-pad scrolls and moves between buttons, OK clicks, 1-5 pick a tab ----
  var FOCUSABLE = 'a[href],button,input,select,textarea,[onclick],[tabindex],[role="button"],[contenteditable="true"],summary,label[for]';
  function editing(el) { return el && (el.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName)) && !/^(button|checkbox|radio|submit|range)$/i.test(el.type || ''); }
  function scrollerOf(el) {
    for (; el && el !== document.body && el !== document.documentElement; el = el.parentElement) {
      var o = getComputedStyle(el).overflowY;
      if ((o === 'auto' || o === 'scroll') && el.scrollHeight > el.clientHeight + 2) return el;
    }
    return null;
  }
  function mainScroller() {
    var scr = activeScreen() || document.body, best = null, area = 0, list = scr.querySelectorAll('*');
    for (var i = 0; i < list.length; i++) {
      var el = list[i], o = getComputedStyle(el).overflowY;
      if (!(o === 'auto' || o === 'scroll') || el.scrollHeight <= el.clientHeight + 2) continue;
      var r = el.getBoundingClientRect(), a = r.width * r.height;
      if (a > area && r.width > innerWidth / 2) { area = a; best = el; }
    }
    if (best) return best;
    var se = document.scrollingElement; return (se && se.scrollHeight > se.clientHeight + 2) ? se : null;
  }
  function inView(r) { return r.bottom > 0 && r.top < innerHeight && r.right > 0 && r.left < innerWidth; }
  function clickable(el) {
    for (var d = 0; el && el !== document.body && el !== document.documentElement && d < 6; el = el.parentElement, d++) {
      if (el.closest('.yp-app-hide,.bottom-nav')) return null;
      if (el.matches(FOCUSABLE)) return el;
      if (getComputedStyle(el).cursor === 'pointer') {
        // the outermost element that is still the same pointer target
        var p = el.parentElement;
        while (p && p !== document.body && getComputedStyle(p).cursor === 'pointer' && !p.matches(FOCUSABLE) && p.getBoundingClientRect().height < innerHeight / 2) { el = p; p = p.parentElement; }
        return el;
      }
    }
    return null;
  }
  // Everything the D-pad can land on: sample the screen on a grid and keep the clickable thing under each point
  function candidates() {
    var out = [], seenEl = [], step = Math.max(14, Math.round(Math.min(innerWidth, innerHeight) / 22));
    for (var y = step / 2; y < innerHeight; y += step) {
      for (var x = step / 2; x < innerWidth; x += step) {
        var hit = document.elementFromPoint(x, y), el = hit && clickable(hit);
        if (!el || seenEl.indexOf(el) >= 0 || el.disabled) continue;
        seenEl.push(el);
        var r = el.getBoundingClientRect(); if (r.width < 4 || r.height < 4) continue;
        out.push({ el: el, r: r });
      }
    }
    return out;
  }
  function pick(dir, from) {
    var fr = from ? from.getBoundingClientRect() : { left: innerWidth / 2, right: innerWidth / 2, top: dir === 'down' ? -1 : innerHeight + 1, bottom: dir === 'down' ? -1 : innerHeight + 1, width: 0, height: 0 };
    var fcx = (fr.left + fr.right) / 2, fcy = (fr.top + fr.bottom) / 2, best = null, bs = 1e9, list = candidates();
    for (var i = 0; i < list.length; i++) {
      var c = list[i], r = c.r; if (c.el === from || (from && (from.contains(c.el)))) continue;
      if (!from && editing(c.el)) continue;
      var cx = (r.left + r.right) / 2, cy = (r.top + r.bottom) / 2, main, cross;
      if (dir === 'down') { if (r.top < fr.bottom - 2 && cy <= fcy + 2) continue; main = Math.max(0, r.top - fr.bottom); cross = Math.abs(cx - fcx); }
      else if (dir === 'up') { if (r.bottom > fr.top + 2 && cy >= fcy - 2) continue; main = Math.max(0, fr.top - r.bottom); cross = Math.abs(cx - fcx); }
      else if (dir === 'right') { if (cx <= fcx + 2 || r.bottom < fr.top || r.top > fr.bottom) continue; main = Math.max(0, r.left - fr.right); cross = Math.abs(cy - fcy); }
      else { if (cx >= fcx - 2 || r.bottom < fr.top || r.top > fr.bottom) continue; main = Math.max(0, fr.left - r.right); cross = Math.abs(cy - fcy); }
      var score = main + cross * (dir === 'up' || dir === 'down' ? 0.3 : 2);
      if (score < bs) { bs = score; best = c.el; }
    }
    return best;
  }
  window.__ypKeys = { candidates: candidates, pick: pick };
  var kfocus = null;
  function setFocus(el) {
    if (kfocus && kfocus !== el) kfocus.classList.remove('yp-kfocus');
    kfocus = el;
    if (!el.matches('a[href],button,input,select,textarea,summary,[contenteditable="true"]') && !el.hasAttribute('tabindex')) el.setAttribute('tabindex', '-1');
    if (document.activeElement && document.activeElement !== el && document.activeElement.blur) document.activeElement.blur();
    try { el.focus({ preventScroll: true }); } catch (e) { el.focus(); }
    el.classList.add('yp-kfocus');
    var r = el.getBoundingClientRect();
    if (r.top < 0 || r.bottom > innerHeight) { try { el.scrollIntoView({ block: 'nearest' }); } catch (e) { el.scrollIntoView(false); } }
  }
  function current() { var a = document.activeElement; if (a && a !== document.body && a !== document.documentElement && visible(a)) return a; return null; }
  document.addEventListener('touchstart', function () { document.documentElement.classList.remove('yp-keys'); if (kfocus) kfocus.classList.remove('yp-kfocus'); }, { capture: true, passive: true });
  if (TOP) document.addEventListener('keydown', function (e) {
    if (e.defaultPrevented || e.altKey || e.ctrlKey || e.metaKey) return;
    var k = e.key, cur = current(), onShorts = /^\/(yidplus-)?shorts/.test(path());
    if (/^[1-5]$/.test(k) && !editing(cur)) {
      if (B && B.tab) { B.tab(Number(k)); e.preventDefault(); }
      return;
    }
    if (!/^Arrow(Up|Down|Left|Right)$/.test(k)) {
      if ((k === 'Enter' || k === ' ') && cur && !editing(cur) && !cur.matches('a[href],button,input,select,textarea,summary')) { e.preventDefault(); cur.click(); }
      return;
    }
    if (editing(cur) && (k === 'ArrowLeft' || k === 'ArrowRight' || cur.tagName === 'TEXTAREA' || cur.isContentEditable || cur.tagName === 'SELECT')) return;
    if (onShorts && !document.querySelector('.cmt-drawer.open,.cmt-drawer.show,[role="dialog"][open]')) return; // Shorts uses up/down for the next video
    document.documentElement.classList.add('yp-keys');
    var dir = k.slice(5).toLowerCase(), next = pick(dir, cur);
    if (dir === 'up' || dir === 'down') {
      e.preventDefault();
      if (next) { setFocus(next); return; }
      var sc = (cur && scrollerOf(cur)) || mainScroller();
      if (sc) {
        var step = Math.max(60, Math.round((sc === document.scrollingElement ? innerHeight : sc.clientHeight) * 0.6)), before = sc.scrollTop;
        sc.scrollTop += dir === 'down' ? step : -step;
        if (sc.scrollTop !== before) {
          var again = pick(dir, cur);
          if (again) setFocus(again);
          return;
        }
      }
      if (cur) cur.blur();
      if (dir === 'down' && B && B.focusNav) B.focusNav();
      else if (dir === 'up' && B && B.focusTop) B.focusTop();
      return;
    }
    if (next) { e.preventDefault(); setFocus(next); }
  }, true);

  // ---- Small app API the site can use ----
  window.YidPlusApp = {
    platform: 'android',
    version: (B && B.version) ? String(B.version()) : '',
    checkForUpdates: function () { post({ type: 'checkUpdate' }); },
    signInWithGoogle: function () { post({ type: 'googleLogin' }); }
  };
})();
