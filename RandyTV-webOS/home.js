/* ────────────────────────────────────────────────────────────────
   home.js  –  Pantalla Home (4 botones)
   Compatible ES5 / Chromium 53
──────────────────────────────────────────────────────────────── */

var HOME = (function () {
  'use strict';

  var _onNavigate; /* callback(action) */

  function init(onNavigate) {
    _onNavigate = onNavigate;
    var btns = document.querySelectorAll('#home-menu .home-btn');
    for (var i = 0; i < btns.length; i++) {
      (function (btn) {
        btn.addEventListener('click', function () {
          var action = btn.getAttribute('data-action');
          if (_onNavigate) _onNavigate(action);
        });
      })(btns[i]);
    }
  }

  function show() {
    NAV.setScope(document.getElementById('screen-home'), null, null);
    NAV.clearFocus();
    /* Dar foco al primer botón con un pequeño delay para que el DOM esté visible */
    setTimeout(function () { NAV.focusFirst(); }, 50);
  }

  return { init: init, show: show };
})();
