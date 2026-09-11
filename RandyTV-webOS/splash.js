/* ────────────────────────────────────────────────────────────────
   splash.js  –  Pantalla de carga con progreso circular
   Compatible ES5 / Chromium 53
──────────────────────────────────────────────────────────────── */

var SPLASH = (function () {
  'use strict';

  var CIRC = 2 * Math.PI * 44; /* 276.46 px — radio del ring */

  var _ring, _pct, _msg;

  function init() {
    _ring = document.getElementById('splash-ring-fg');
    _pct  = document.getElementById('splash-pct');
    _msg  = document.getElementById('splash-msg');
  }

  /* progress: 0.0 → 1.0 */
  function setProgress(p) {
    p = Math.min(1, Math.max(0, p));
    var offset = CIRC * (1 - p);
    if (_ring) _ring.style.strokeDashoffset = offset;
    if (_pct)  _pct.textContent = Math.round(p * 100) + '%';
  }

  function setMsg(text) {
    if (_msg) _msg.textContent = text;
  }

  return { init: init, setProgress: setProgress, setMsg: setMsg };
})();
