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
    return getComputedStyle(document.documentElement).backgroundColor || '';
  }
  var lastBars = '';
  function bars() {
    try {
      if (!B || !document.body || document.hidden) return;
      var w = Math.floor(innerWidth / 2), t = bgAt(w, 1), b = bgAt(w, innerHeight - 2), k = t + '|' + b;
      if (k !== lastBars) { lastBars = k; B.setBars(t, b); }
    } catch (_) {}
  }
  document.addEventListener('DOMContentLoaded', bars);
  window.addEventListener('load', bars);
  setInterval(bars, 700);

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

  // ---- Small app API the site can use ----
  window.YidPlusApp = {
    platform: 'android',
    version: (B && B.version) ? String(B.version()) : '',
    checkForUpdates: function () { post({ type: 'checkUpdate' }); },
    signInWithGoogle: function () { post({ type: 'googleLogin' }); }
  };
})();
